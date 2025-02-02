package org.dsl.mining

import com.tdunning.math.stats.TDigest
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SparkSession
import org.apache.spark.util.CollectionAccumulator
import org.dsl.dataStruct.Interval
import org.dsl.dataStruct.evidenceSet.builders.{EvidenceSetBuilder, SplitReconsEviBuilder}
import org.dsl.dataStruct.evidenceSet.{HPEvidenceSet, IEvidenceSet}
import org.dsl.emperical.table.TypedColTable
import org.dsl.mining.PredSpace.PredSpace
import org.dsl.mining.REEMiner.EvidencePairList
import org.dsl.pb.ProgressBar
import org.dsl.reasoning.predicate.HumeType.HString
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate._
import org.dsl.utils.{$, Config, HUMELogger, Wrappers}

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import scala.collection.compat._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}


object PMiners {

  // todo: use accumulator to get log
  //  val logger = HU、ELogger
  private val logger = HUMELogger(getClass.getPackage.getName)

  private def sampleLevelCDF(levelp: mutable.Map[State, Unit])
  : mutable.Map[PredicateSet, TDigest] = {
    sampleQueueCDF(levelp.map(p => (p._1.selected, p._1.conf)).toMap)
  }

  private def sampleQueueCDF(queue: Iterable[(PredicateSet, Double)])
  : mutable.Map[PredicateSet, TDigest] = {
    val K = Config.SAMPLE_DIST
    val res = mutable.Map.empty[PredicateSet, TDigest]

    //    val parallelNum = 4 * Config.nproc

    //val workList = queue.grouped(parallelNum)
    // filter NaNs
    for ((p, conf) <- queue) addNeighborOrNewSampleCDF(K, res, (p, conf))
    res
  }


  private def sampleQueue(queue: Iterable[(PredicateSet, Double)])
  : mutable.Map[PredicateSet, Interval[Double]] = {
    val K = Config.SAMPLE_DIST
    val res = TrieMap.empty[PredicateSet, Interval[Double]]

    val parallelNum = 4 * Config.nproc

    val workList = queue.grouped(parallelNum)
    // filter NaNs

    for (chunk <- workList) {
      for ((p, conf) <- chunk) addNeighborOrNewSample(K, res, (p, conf))
    }

    res
  }


  def hasPredecessor(K: Int, sampled: mutable.Map[PredicateSet, TDigest],
                     pred: PredicateSet, mineArg: MineArg): Option[(PredicateSet, TDigest)] = {

    val p2i = mineArg.p2i
    val candidates = getPredecessors(K, pred)
      .view.flatMap {
      case (prec, mask) =>
        mask.map(p => prec :+ p)
    }

    for (can <- candidates) {
      sampled.get(can) match {
        case Some(t) => return Some(can -> t)
        case None => ()
      }
    }

    None
  }


  private def addPredecessorOrNewSample(K: Int, sampled: mutable.Map[PredicateSet, TDigest],
                                        in: (PredicateSet, Double), mineArg: MineArg)
  = {


    val (pred, conf) = in
    // val predecessors: Iterable[PredicateSet] = getPredecessors(K, pred)
    hasPredecessor(K, sampled, pred, mineArg) match {
      case Some((_, tdigest)) =>
        tdigest.add(conf)
      // Config.sample_merged_n +=1
      case None =>
        // need special case for level = 0
        // get the first predecessor

        val td = TDigest.createAvlTreeDigest(100)
        td.add(conf)

        val tobeAdd = pred -> td

        sampled.+=(tobeAdd)
    }

    sampled
  }


  def sampleLevelByPredecessor(level: LevelSet, dist: Int, mineArg: MineArg, rhs: Expression)
  : Iterable[Sample]
  = {
    val K = dist
    val res = mutable.Map.empty[PredicateSet, Sample]
    val sampler = PSampler() // inner states
    for ((p, conf) <- level.map(s => (s._1.selected, s._1.conf))) {
      val ree = REE(p, rhs)
      sampler.addPredecessorOrNewSample(K, (ree, conf), mineArg)
    }

    sampler.samples.toSet
  }

  private def addNeighborOrNewSample(K: Int, sampled: mutable.Map[PredicateSet, Interval[Double]],
                                     in: (PredicateSet, Double)) = {
    addNeighborOrNewSample1(K, sampled, (in._1, Interval(in._2, in._2)))
  }

  private def addNeighborOrNewSample1(K: Int, sampled: mutable.Map[PredicateSet, Interval[Double]],
                                      in: (PredicateSet, Interval[Double])) = {
    val (pred, inIntr) = in
    hasNeighbor(K, sampled, pred) match {
      case Some((p, intr)) =>
        val intrNew = Interval(math.min(intr.begin, inIntr.begin), math.max(intr.end, inIntr.end))
        sampled.update(p, intrNew)
      case None =>
        sampled.update(pred, inIntr)
    }

    sampled
  }


  def addNeighborOrNewSampleCDF(K: Int, sampled: mutable.Map[PredicateSet, TDigest],
                                in: (PredicateSet, Double)): mutable.Map[PredicateSet, TDigest] = {
    val (pred, conf) = in
    hasNeighbor(K, sampled, pred) match {
      case Some((_, tdigest)) =>
        tdigest.add(conf)
      case None =>
        val tdigest = TDigest.createAvlTreeDigest(100)
        tdigest.add(conf)
        sampled.+=(pred -> tdigest)
    }

    sampled
  }

  private def expandOneLevel(in: Iterable[REEWithStat], mineArg: MineArg): Iterable[REEWithStat] = {
    val p2i = mineArg.p2i
    val eviSet = mineArg.eviSet
    val sc = mineArg.spark.sparkContext

    val min_supp_thresh_cnt = mineArg.supp_threshold * mineArg.fullEviSize
    (for {
      reeWithStat <- in.par
      ree = reeWithStat.ree
      rhs = ree.p0
      x = ree.X
      rest = x ^ _allOnePredSet(p2i)

      addP <- rest
      newREE = REE(x :+ addP, rhs)
      supp = getSuppCnt(newREE, eviSet)
      if supp >= min_supp_thresh_cnt
      conf = getConfFromSupp(newREE, eviSet, supp)

    } yield REEWithStat(newREE, supp, conf)).toIndexedSeq
  }

  def augmentSamples(rb1: MineResult, mineArgInc: MineArg, suppOld: Double, confOld: Double): MineResult = {
    val p2i = mineArgInc.p2i
    val eviSet = mineArgInc.eviSet
    val fullEviSize = mineArgInc.fullEviSize
    val suppNew = mineArgInc.supp_threshold
    val confNew = mineArgInc.conf_threshold
    val new_supp_cnt = suppNew * fullEviSize
    val old_supp_cnt = suppOld * fullEviSize
    val radius = mineArgInc.K

    def expand(s: Sample) = {
      val min_supp_cnt = math.min(new_supp_cnt, old_supp_cnt)
      val rhs = s.rhs
      (for {
        prec <- s.predecessors.par
        rest = _allOnePredSet(p2i) ^ (prec :+ rhs)
        newP <- rest
        ree = REE(prec :+ newP, rhs)
        supp = getSuppCnt(ree, eviSet)
        // adopt supp+ and supp-
        if supp >= min_supp_cnt
        conf = getConfFromSupp(ree, eviSet, supp)
      } yield REEWithStat(ree, supp, conf))
    }

    def expandSamples(ss: Iterable[Sample]) = {
      ss.flatMap(s => expand(s))
    }

    def augmentSampleOneRHS(group: Iterable[Sample], rhs: Expression): Iterable[Sample] = {
      val levelWise = group.groupBy(s => {
        if (s.predecessors.nonEmpty)
          s.predecessors.head.size
        else 0
      })

      var lastLevelSuccessors = ArrayBuffer.empty[REEWithStat]
      // drop nodes that go to pruned set
      val minSupp = math.min(suppOld, suppNew)

      val r = for {
        (l: Int, ss: Iterable[Sample]) <- levelWise
      } yield {
        val expanded = expandSamples(ss)
        val candidateREEs: Iterable[REEWithStat] = expanded ++ lastLevelSuccessors

        // todo: do not vary supp
        val topKOld: Set[State] = {
          // 1e-6
          val _old = candidateREEs.filter(c => c.supp >= old_supp_cnt && c.conf < confOld)
          val levelOld: LevelSet = mutable.Map.empty[State, Unit] ++= _old.map(_.toState -> ())
          val oldTopK = {
            if (Config.ENABLE_TOPK_OVERLAP) dropStatByTopkOverlap(levelOld, rhs, mineArgInc)
            else if (Config.ENABLE_TOPK) dropStatByTopk(levelOld, rhs, mineArgInc)
            else levelOld

          }

          oldTopK.keySet.toSet
        }

        $.WriteResult(s"${l}_aug_old.txt", topKOld.toIndexedSeq.sortBy(_.selected.size).mkString("\n"))


        val topKNew: Set[State] = {
          // 1e-5
          val _new = candidateREEs.filter(c => c.supp >= new_supp_cnt && c.conf < confNew)
          val _newResult = candidateREEs.filter(c => c.supp >= new_supp_cnt && c.conf >= confNew)
          $.WriteResult("newResult.txt", _newResult.mkString("\n"))
          val levelNew: LevelSet = mutable.Map.empty[State, Unit] ++= _new.map(_.toState -> ())
          val newTopK = {
            if (Config.ENABLE_TOPK_OVERLAP) dropStatByTopkOverlap(levelNew, rhs, mineArgInc)
            else if (Config.ENABLE_TOPK) dropStatByTopk(levelNew, rhs, mineArgInc)
            else levelNew

          }

          newTopK.keySet.toSet
        }

        $.WriteResult(s"${l}_aug_new.txt", topKNew.toIndexedSeq.sortBy(_.selected.size).mkString("\n"))

        //        assert(topKNew.forall())
        val _diff = topKNew diff topKOld

        lastLevelSuccessors = ArrayBuffer.empty[REEWithStat] ++= {
          val in = _diff.map(state => REEWithStat(REE(state.selected, rhs), state.supp, state.conf))
          // todo: expand next Level by diff
          expandOneLevel(in, mineArgInc.updateThreshold(minSupp, confNew))
        }

        val level: LevelSet = mutable.Map.empty[State, Unit] ++= candidateREEs.map(_.toState -> ())
        sampleLevelByPredecessor(level, radius, mineArgInc, rhs)
      }


      r.flatten
    }

    rb1 match {
      case MineResult(result, pruned, samples) =>
        val rhsGroups = samples.groupBy(s => s.rhs)
        val pb = new ProgressBar(rhsGroups.size)
        val augmentedSamples: Iterable[Sample] = rhsGroups.flatMap {
          case (rhs, sampleGroup) =>
            val r = augmentSampleOneRHS(sampleGroup, rhs)
            pb += 1
            r
        }

        MineResult(result, pruned, (augmentedSamples ++ samples).toSet)
    }
  }

  private def hasNeighbor[T](K: Int, sampled: mutable.Map[PredicateSet, T],
                             in: PredicateSet): Option[(PredicateSet, T)] = {

    for ((sample, intr) <- sampled) {
      if (sample.size == in.size && sample.dist(in) <= K) {
        return Some(sample, intr)
      }
    }

    None

  }


  def stopState(stateArg: StateConsArg) = {
    stateArg match {
      case StateConsArg(lhsSpace, p2i, rhs, evi) =>
        val predRestInit = PredicateSet.empty(p2i)
        val predSelectedInit = _allOnePredSet(p2i)
        val startUpState = State(predSelectedInit, predRestInit, supp = -1, conf = -1, 0)
        startUpState
    }
  }


  // rhs -> rest
  private def toState(reeStat: REEWithStat, stateConsArg: StateConsArg)(memo: mutable.Map[Expression, PredicateSet]): State = {
    (reeStat, stateConsArg) match {
      case (REEWithStat(ree, _, conf), StateConsArg(lhsSpace, p2i, rhs, eviSet)) =>
        val x = ree.X
        val corr = memo.getOrElseUpdate(rhs, getCorrelatedPreds(stateConsArg))
        val rest = getRest(x, corr)
        val supp = getSuppCnt(ree, eviSet)

        val level = x.size
        State(x, rest, supp = supp, conf = conf, level)
    }
  }


  private def toState(ree: REE, stateConsArg: StateConsArg)(memo: mutable.Map[Expression, PredicateSet]): State = {
    val supp = getSuppCnt(ree, stateConsArg.evidencePairList)
    val conf = getConfFromSupp(ree, stateConsArg.evidencePairList, supp)
    val reeWithState = REEWithStat(ree, supp, conf)
    toState(reeWithState, stateConsArg)(memo)
  }

  private def incMineCont(in: Iterable[REEWithStat], newMineArg: MineArg): MineResult = newMineArg match {
    case MineArg(spark, allSpace, lhsSpace, rhsSpace,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>

      val (stateRHSMapFilled, time) = Wrappers.timerWrapperRet {
        // TODO: correlation analysis
        val groupedRees = in.groupBy(_.ree.p0)
        val memo = mutable.Map.empty[Expression, PredicateSet]
        val stateRHSMap = groupedRees.map {
          case (rhs, reeGroup) =>
            val stateArg = StateConsArg(lhsSpace = lhsSpace, p2i = p2i, rhs = rhs, evidencePairList = eviSet)
            rhs -> reeGroup.map(reeWithStat => toState(reeWithStat, stateConsArg = stateArg)(memo))
        }

        stateRHSMap
      }

      logger.info(s"[AAA] PREPROCESS TIME ${time}.")


      implicit val transStat: ((PredicateSet, Stat), Expression) => REEWithStat
      = (t: (PredicateSet, Stat), rhs: Expression) => {
        t._2 match {
          case Stat(supp, conf) => REEWithStat(REE(t._1, rhs), supp = supp, conf = conf)
        }
      }


      logger.info(s"Search REE From ${lhsSpace.size} X ${rhsSpace.size} space with new supp > $supp_threshold")
      val pbar = new ProgressBar(stateRHSMapFilled.size)

      def expand = {
        val timeRHSs = ArrayBuffer.empty[Double]
        val all = stateRHSMapFilled.map {
          case (rhs, states) =>
            logger.info(s"[INC] expanding ${rhs}")

            val expandArg = ExpandArg(mineArg = newMineArg, rhs = rhs, startUpState = states)
            val (p, time) = Wrappers.timerWrapperRet(expandParWithState111(expandArg = expandArg))
            timeRHSs += time
            p match {
              case (expandResult: ExpandResult[Stat],
              sampledResult) =>
                expandResult match {
                  case ExpandResult(resultX, prunedX, _) =>
                    val sampledInner = sampledResult.m
                    val minimal = resultX.toMap
                    val minPruned = prunedX

                    // tranform result to rees
                    val results = trans(rhs, minimal)
                    val pruned = trans(rhs, minPruned)
                    val sampled = sampledInner

                    pbar += 1

                    MineResult(results, pruned, sampled)

                }
              case _ => ???
            }
        }

        logger.info(s"time each rhs: ${timeRHSs}")

        all
      }

      val (result, timeMine) = Wrappers.timerWrapperRet(expand)

      logger.info(s"Search Time All ${timeMine}.")
      val (r, timeRed) = Wrappers.timerWrapperRet(result.reduce(_ ++ _))
      logger.info(s"Reduce Time  ${timeRed}.")
      r
    case _ => ???
  }


  def getPredecessors(K: Int, pred: PredicateSet): Seq[(PredicateSet, PredicateSet)] = {

    def secureZone(predSet: PredicateSet, p: Expression) = {
      val p2i = predSet.getP2I
      val i = p2i.getOrElse(p, ???)
      val rest = _allOnePredSet(p2i) ^ predSet
      val secureZoneMask = PredicateSet.from(mutable.BitSet.fromSpecific(0 to i))(p2i)
      rest & secureZoneMask
    }

    val r = (for {
      p <- pred
      predecessor = pred :- p
    } yield (predecessor, secureZone(pred, p))).toSeq


    if (r.size < K) {
      r
    } else {
      r.take(K)
    }
  }


  type LevelSet = mutable.Map[State, Unit]


  private def dropStatByTopk(nextLevel: LevelSet, rhs: Expression, mineArg: MineArg): LevelSet = {
    val topK = if (Config.TOPK < nextLevel.size) Config.TOPK else nextLevel.size
    mutable.Map.empty ++= nextLevel.keySet.toIndexedSeq.sortBy(getGini).map(s => s -> ()).take(topK)
  }

  private def getGini(s: State) = {
    // 1-p
    val conf = s.conf
    val r = 4 * conf * (1 - conf)
    //    assert(0 <= r && r <= 1)
    r
  }

  private def getGini(reeWithStat: REEWithStat) = {
    val conf = reeWithStat.conf
    val r = 4 * conf * (1 - conf)
    r
  }


  // calculate overlaps
  private def dropStatByTopkOverlap(nextLevel: LevelSet, rhs: Expression, mineArg: MineArg)
  : LevelSet = {
    val evi = mineArg.eviSet
    val sc = mineArg.spark.sparkContext
    val p2i = mineArg.p2i

    def getOverlaps(e: State, covered: EvidencePairList) = {
      val r = covered.filter(p => e.selected.isSubsetOf(p._1)).map(_._2).sum
      // work around div by 0
      (if (r > 0) r.toDouble else 1d) / e.supp
    }

    def getCovered(queue: IndexedSeq[State]): EvidencePairList = {
      queue.par.map {
        s => evi.filter(e => s.selected.isSubsetOf(e._1))
      }.foldLeft(HPEvidenceSet())((acc, x) => acc.mergeAll(x)).toIndexedSeq
    }

    val topK = Config.TOPK
    val instanceNum = sc.getConf.get(Config.INSTANCE_NUM_KEY).toInt

    @tailrec
    def f(k: Int, queue: Set[State], res: ArrayBuffer[State]): ArrayBuffer[State] = {
      if (k == topK) {
        res
      } else {
        val (covered, time1) = Wrappers.timerWrapperRet(getCovered(res))
        val ((car, metric), time2) = Wrappers.timerWrapperRet(
          queue.par.map {
            s => s -> getGini(s) * getOverlaps(s, covered)
          }.minBy(_._2))

        logger.profile(s"time covered: ${time1}, sort ${time2}")
        logger.debug(s"find node=${car} with metric=${metric}")
        f(k + 1, queue diff Set(car), res += car)
      }
    }


    if (Config.TOPK < nextLevel.size) {
      val queue = nextLevel.keySet.toSet
      mutable.Map.empty[State, Unit] ++= f(0, queue, res = ArrayBuffer.empty[State]).map(_ -> ())
    }
    else {
      nextLevel
    }
  }

  private def expandParWithState111(expandArg: ExpandArg)

  : (ExpandResult[Stat], SampleResult[TDigest]) = {
    expandArg match {
      case ExpandArg(mineArg, pRHS, startUpState) =>
        if (startUpState.isEmpty) (ExpandResult.empty, SampleResult(Set.empty[Sample]))
        else
          mineArg match {
            case MineArg(spark,
            allSpace, lhsSpace, rhsSpace,
            supp_threshold, conf_threshold,
            p2i,
            eviSet, fullEviSize, recall, dist, _) =>
              // make evidence smaller

              // X -> p0
              // invariant: lattice-based levelwise search
              val eviSetForRHS = eviSet.filter(p => p._1.contains(pRHS))

              val resultAll = mutable.Map.empty[PredicateSet, Stat]
              val prunedAll = mutable.Map.empty[PredicateSet, Stat]
              // todo: extract abstract idx
              val sampled: ArrayBuffer[Sample] = ArrayBuffer.empty[Sample]
              val (levelGroupedStates, _) = Wrappers.timerWrapperRet(startUpState.
                groupBy(s => s.selected.size))

              val lowest = levelGroupedStates.keySet.min

              val level0: LevelSet =
                mutable.Map.empty ++= levelGroupedStates.getOrElse(lowest, Set[State]()).map(_ -> ())

              var levelp = level0
              var levelN = lowest

              var timeSampleAll = 0d

              // for Profiling
              var visitedNodeNum = 0L

              def dumplog(levelp: Iterable[State], levelN: Int) = {
                if (levelN == 4)
                  $.WriteResult(
                    s"log/log_${pRHS}_${supp_threshold}_${conf_threshold}_${levelN}.jsonl",
                    s"SIZE:${levelp.size}\n${levelp.toIndexedSeq.sortBy(-_.conf).map(s => s.toJson).mkString("\n")}")
              }

              for (levelN <- lowest to Config.levelUpperBound
                   if levelp.nonEmpty) {

                //            while(levelp.nonEmpty) {
                dumplog(levelp.keys, levelN)
                val _ = Wrappers.timerWrapper0({
                  val ((newLevel, visitedLevel), _) = Wrappers.timerWrapperRet {
                    val fill = levelGroupedStates.getOrElse(levelN + 1, Set[State]()).toSet

                    val newLevel = mutable.Map.empty ++ fill.map(_ -> ())
                    val visitedLevel: mutable.Map[PredicateSet, Unit] = newLevel.map(_._1.selected -> ())
                    (newLevel, visitedLevel)
                  }

                  val newLevelCArg = CollectionArg.withQueue[Stat](newLevel)

                  /**
                   * profiling
                   */

                  val visitedNumLevel = new AtomicLong(0L)

                  /**
                   * evidence set filter profiling
                   */

                  val filteredEviSetSizes = new ConcurrentLinkedDeque[Int]()

                  val instanceNum = spark.sparkContext.getConf.get(Config.INSTANCE_NUM_KEY).toInt

                  val chunkNum = getChunkNum(levelp.size, instanceNum)
                  logger.info(s"levelsize:${levelp.size},CHUNK NUM:${chunkNum}")
                  val rdd = spark.sparkContext.parallelize(levelp.toIndexedSeq, chunkNum)

                  val conf_thresholdb = spark.sparkContext.broadcast(conf_threshold)


                  // broadcast heavy arguments
                  val sc = spark.sparkContext
                  val mineArgBroadCast = sc.broadcast(mineArg)
                  val min_supp_threshold = Config.MIN_SUPP * fullEviSize

                  println(s"RHS:${pRHS}, Level ${levelN}")
                  val chunkSizes = sc.collectionAccumulator[Int]
                  val ccOut = rdd.mapPartitions {
                    chunk =>

                      mineArgBroadCast.value match {
                        case MineArg(spark,
                        allSpace, lhsSpace, rhsSpace,
                        supp_threshold, conf_threshold,
                        p2i,
                        eviSet, fullEviSize, recall, dist, _) =>

                          val ccChunk = {
                            val work = if (Config.enable_profiling) {
                              val t = chunk.toIndexedSeq
                              chunkSizes.add(t.size)
                              t
                            } else chunk

                            work.map(_._1).map {
                              case s@State(selectedPred, restPred, supp, conf, level) =>
                                val supp_threshold_cnt = (fullEviSize * supp_threshold).toLong
                                val conf_threshold = conf_thresholdb.value

                                val eviSetFilteredX = eviSet.filter(p => selectedPred.isSubsetOf(p._1))
                                val eviSetFilteredForRHS = eviSetForRHS.filter(p => selectedPred.isSubsetOf(p._1))
                                // prune nodes that supp is too small
                                //                                 if (supp >= supp_threshold_cnt && conf >= conf_threshold) {
                                //                                   val resArg = ResArg(None, Some(selectedPred, Stat(supp, conf)), None)
                                //                                   BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)
                                //
                                //                                 }
                                //                                 else if (supp < supp_threshold_cnt) {
                                //
                                //                                   val resArg = {
                                //                                     if (supp >= min_supp_threshold) {
                                //                                       ResArg(None, None, Some(selectedPred, Stat(supp, conf)))
                                //                                     } else {
                                //                                       ResArg[Stat](None, None, None)
                                //                                     }
                                //                                   }
                                //
                                //                                   BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)
                                //
                                //                                 }
                                //                                 else
                                //
                                if (levelN == Config.levelUpperBound) {
                                  if (conf >= Config.MIN_CONF) {
                                    val resArg = ResArg(None, None, Some(selectedPred, Stat(supp, conf)))
                                    BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)
                                  } else {
                                    CollectionArg.empty[Stat]
                                  }

                                }
                                else {
                                  val t = (for {
                                    p <- restPred
                                    if p != pRHS
                                    newSelected = selectedPred :+ p
                                    newRest = restPred :- p

                                    newREE = REE(newSelected, pRHS)
                                    newSupp = getSuppCnt(newREE, eviSetFilteredForRHS)
                                    newConf = getConfFromSupp(newREE, eviSetFilteredX, newSupp)
                                    newS = State(newSelected, newRest, newSupp, newConf, level + 1)
                                    l = LevelArg(s, newS, visited = visitedLevel, eviSet, fullEviSize = fullEviSize, mutable.Map.empty[PredicateSet, Stat], supp_threshold, conf_threshold)
                                    resArg = BatchStatMiningOpsStatic.process(l)
                                  } yield {
                                    /**
                                     * Profiling
                                     */
                                    if (Config.enable_profiling) {
                                      //visitedNumLevel.getAndIncrement()
                                      //longAcc.add(ccPartial.nextLevel.size)
                                    }
                                    resArg
                                  }).foldLeft(CollectionArg.empty[Stat])((coll, resArg) => BatchStatMiningOpsStatic.merge(coll, resArg))

                                  t
                                }
                              //                            }.foldLeft(CollectionArg.emptyPar[Stat])(BatchStatMiningOpsStatic.mergeAll)
                            }.foldLeft(CollectionArg.empty[Stat])(BatchStatMiningOpsStatic.mergeAll)
                          }
                          Iterator(ccChunk)
                      }

                  }.fold(newLevelCArg)(BatchStatMiningOpsStatic.mergeAll)


                  logger.info(
                    s"""
                       |LEVEL $levelN,
                       |CHUNK NUM=${chunkNum},
                       |CHUNK SIZE=${chunkSizes.value},
                       |LEVEL TOTAL=${levelp.size}
                   """.stripMargin)

                  /**
                   * Profiling
                   */
                  if (Config.enable_profiling) {
                    visitedNodeNum += visitedNumLevel.get()
                  }

                  if (levelN <= Config.levelUpperBound && Config.ENABLE_SAMPLING) {
                    // sample
                    // logger.profile(s"Sampling Level:${levelN}...")

                    val (_, timeSample) = Wrappers.timerWrapperRet {
                      //val s = sampleLevelCDF(levelp.filter(p => p._1.conf < conf_threshold))
                      val (s, time) = Wrappers.timerWrapperRet(
                        sampleLevelByPredecessor(levelp.filter(s => s._1.conf < conf_threshold), dist, mineArg, pRHS)
                      )
                      sampled ++= s
                      logger.info(s"Sample Level ${levelN} with RHS ${pRHS} with time: ${time}, with size= ${s.size}")
                    }

                    timeSampleAll += timeSample

                  }

                  val (minPrunedLevel, timeMinPrune) = Wrappers.timerWrapperRet(eliminateNonMinimal(ccOut.prunedLevel, prunedAll))
                  prunedAll ++= minPrunedLevel
                  logger.profile(s"Minimized Pruned Set with time ${timeMinPrune}, size=${ccOut.prunedLevel.size}->${minPrunedLevel.size}")

                  val minResultLevel = eliminateNonMinimal(ccOut.resultLevel, resultAll)
                  resultAll ++= minResultLevel

                  logger.debug(s"Starting Select Topk in Level:${levelN} with Level Size: ${ccOut.nextLevel.size}")
                  levelp = if (Config.ENABLE_TOPK_OVERLAP) {
                    dropStatByTopkOverlap(ccOut.nextLevel, pRHS, mineArg)
                  } else if (Config.ENABLE_TOPK) {
                    dropStatByTopk(ccOut.nextLevel, pRHS, mineArg)
                  } else {
                    ccOut.nextLevel
                  }

                  logger.debug(s"Done with Select Topk in Level:$levelN with Level Size: ${levelp.size}")

                })
              }

              val prunedMin = prunedAll
              val resultMin = resultAll
              (ExpandResult(resultMin, prunedMin, timeSampleAll), SampleResult(sampled))
          }

    }

  }

  private def getChunkNum(size: Int, instanceNum: Int): Int = {
    val EXPAND_CHUNK_SIZE = Config.EXPAND_CHUNK_SIZE
    if (EXPAND_CHUNK_SIZE <= 0) instanceNum
    else if (size <= EXPAND_CHUNK_SIZE) 1
    else size / EXPAND_CHUNK_SIZE
  }

  def _allOnePredSet(p2i: PredicateIndexProvider): PredicateSet = {
    PredicateSet.from(p2i.getObjects)(p2i)
  }

  private def getCorrelatedPreds(stateArg: StateConsArg): PredicateSet = {
    stateArg match {
      case sca@StateConsArg(lhsSpace, p2i, rhs, evi) =>
        val topkN = (lhsSpace.size * Config.TOPK_RATE).toInt
        val topk = (for (lhs <- lhsSpace.par) yield {
          val p = PredicateSet.from(Vector(rhs, lhs))(p2i)
          val supp = getSuppCnt(p, evi)
          lhs -> supp
        }).toIndexedSeq.sortBy(_._2).take(topkN).map(_._1)

        PredicateSet.from(topk)(p2i)
    }
  }

  private def emptyState(stateArg: StateConsArg): State = {
    stateArg match {
      case sca@StateConsArg(lhsSpace, p2i, rhs, evi) =>
        val predRestInit = getCorrelatedPreds(sca)
        val predSelectedInit = PredicateSet.empty(p2i)

        val supp = getSuppCnt(REE(predSelectedInit, rhs), evi)
        val conf = getConf(REE(predSelectedInit, rhs), evi)
        val startUpState = State(predSelectedInit, predRestInit, supp = supp, conf = conf, 0)
        startUpState
    }

  }

  def getConfFromSupp(tREE: REE, eviSetX: EvidencePairList, cntRHSSupp: Long): Double = {
    val suppX = getSuppCnt(tREE.X, eviSetX)
    // todo: opt
    val suppREE = cntRHSSupp
    if (suppX == 0) {
      0.0D
    } else {
      suppREE.toDouble / suppX.toDouble
    }
  }

  private def getConf(tREE: REE, eviSet: EvidencePairList): Double = {
    val suppX = getSuppCnt(tREE.X, eviSet)
    val suppREE = getSuppCnt(tREE, eviSet)
    if (suppX == 0) {
      0.0D
    } else {
      suppREE.toDouble / suppX.toDouble
    }
  }

  private def getConf(tREE: (PredicateSet, Expression), eviSetX: EvidencePairList): Double = {
    val suppX = getSuppCnt(tREE._1, eviSetX)
    // todo: opt
    val suppREE = getSuppCnt(tREE._1 :+ tREE._2, eviSetX)
    if (suppX == 0) {
      0.0D
    } else {
      suppREE.toDouble / suppX.toDouble
    }
  }

  def getSuppCnt(ree: REE, eviSet: EvidencePairList): Long = {
    getSuppCnt(ree.X :+ ree.p0, eviSet)
  }

  def getSuppCnt(X: PredicateSet, eviSet: EvidencePairList): Long = {


    def inner(X: PredicateSet, eviSet: EvidencePairList) = {
      if (X.size == 0) {
        val _totalSpCount = eviSet.map(_._2).sum
        _totalSpCount
      } else {
        val predSet = X

        eviSet.view.filter(p => predSet.isSubsetOf(p._1))
          .foldLeft(0L)((a, p) => a + p._2)
      }

    }

    inner(X, eviSet)


  }

  def eliminateNonMinimal[T](workLoads: Iterable[(PredicateSet, T)], minimal: Iterable[(PredicateSet, T)])
  : Iterable[(PredicateSet, T)] = {
    val mins = minimal.toIndexedSeq.sortBy(_._1.size)
    workLoads.flatMap {
      work =>
        val isMin = mins.forall(p => !p._1.isSubsetOf(work._1))
        if (isMin) Some(work) else None
    }
  }

  def minimize[T](t: Iterable[(PredicateSet, T)]): Iterable[(PredicateSet, T)] = {
    val workList0 = t.toList.sortBy(_._1.size)

    val minimal = mutable.Map.empty[PredicateSet, T]

    @tailrec
    def inner(workList: List[(PredicateSet, T)],
              r: mutable.Map[PredicateSet, T]): Unit = {
      workList match {
        case Nil => ()
        case car :: cdr =>
          val (pred, _) = car
          inner(
            cdr.filter(larger => !pred.isSubsetOf(larger._1)),
            r += car)
      }
    }

    inner(workList0, minimal)
    minimal

  }

  private def trans[Idx, R](rhs: Expression, localResult: Iterable[(PredicateSet, Idx)])
                           (implicit f: ((PredicateSet, Idx), Expression) => R)
  = localResult.map {
    case (p, idx) => f((p, idx), rhs)
  }


  private def getNeighborsWithFilter(expandSampleArg: ExpandSampleArg)

  : Map[PredicateSet, Stat] = expandSampleArg match {
    case ExpandSampleArg(sample, mineArg, rhs, _) =>
      mineArg match {
        case MineArg(spark,
        allSpace, lhsSpace, rhsSpace,
        supp_threshold, conf_threshold,
        p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>

          (for {
            premove <- sample
            prec = sample :- premove
            rest = prec ^ _allOnePredSet(p2i)

            succP <- rest

            if succP != rhs && succP != premove

            tobeEval = prec :+ succP
            ree = REE(tobeEval, rhs)
            supp = getSuppCnt(ree, eviSet)
            conf = getConfFromSupp(ree, eviSet, supp)

            if supp >= supp_threshold && conf >= conf_threshold
          } yield tobeEval -> Stat(supp, conf)).toMap

      }
  }


  private def restoreSamplesCDF(samples: Iterable[Sample],
                                resultFiltered: IndexedSeq[REEWithStat],
                                mineCtx: MineArg) = {


    val supp_threshold = mineCtx.supp_threshold
    val conf_threshold = mineCtx.conf_threshold

    logger.info(s"supp>${supp_threshold}， conf>${conf_threshold}")
    val resultFilteredMap =
      resultFiltered.groupBy(_.ree.p0)
        .map { case (rhs, v) => rhs -> v }

    val sampleRHSGroups = samples.groupBy(e => e.rhs)

    //      val pb = new ProgressBar(sampleRHSGroups.size)
    def expandRHS: PartialFunction[(Expression, Iterable[Sample]), (Expression, Iterable[(PredicateSet, Stat)])] = {
      case (rhs: Expression, rhsgroup: Iterable[Sample]) =>
        val levelWise = rhsgroup.groupBy {
          s =>
            if (s.predecessors.nonEmpty) s.predecessors.head.size + 1 else 0
        }
        val resultMapByRHS = resultFilteredMap.getOrElse(rhs, Iterable())
        val result = expandSamplesCDF(rhs, rhsgroup, resultMapByRHS, mineCtx)

        rhs -> result
    }

    val pb = new ProgressBar(sampleRHSGroups.size)
    val t = if (Config.ENABLE_DEBUG_SINGLE_RHS) {
      sampleRHSGroups.filter(_._1== mineCtx.rhsSpace.toIndexedSeq(Config.DEBUG_RHS_INDEX)).map {
        pb += 1
        expandRHS
      }
    }
    else {
      sampleRHSGroups.map {
        pb += 1
        expandRHS
      }
    }


    // rhs -> Iterable[PredicateSet] Map to REE
    val r = t.flatMap {
      case (rhs, xs) =>
        xs.map(x => REEWithStat(REE(x._1, rhs), x._2.supp, x._2.conf))
    }


    logger.debug(s"Find Smaller REEs...")
    val (smallerREEs, time) = Wrappers.timerWrapperRet(r)//findSmallerREE(r, mineCtx))

    logger.debug(s"Find Smaller Time: ${time}")


    logger.info(
      s"""
         |resultRaw: ${r.size}
         |smaller: ${smallerREEs.size}
         |""".stripMargin)

    logger.info("Minimizing all...")
    val min = minimizeREEStat(smallerREEs ++ resultFiltered)
    logger.info(s"Minimize Done. min size=${min.size}")

    MineResult(min, Vector(), Vector())

  }


  def findSmallerX(rhs: Expression, m: Iterable[(PredicateSet, Stat)], mineArg: MineArg)
  : Iterable[(PredicateSet, Stat)] = mineArg match {
    case MineArg(spark, allSpace, lhsSpace, rhsSpace,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>

      val supp_threshold_cnt = supp_threshold * fullEviSize

      @tailrec
      def inner(validp: Iterable[(PredicateSet, Stat)], prevSize: Int)
      : Iterable[(PredicateSet, Stat)] = {

        if (prevSize != validp.size) {


          val validNew = validp.flatMap {
            case (predSet, stat: Stat) =>
              for {
                p <- predSet
                newPredSet = predSet :- p
                ree = REE(newPredSet, rhs)
                supp = getSuppCnt(ree, eviSet)
                conf = getConfFromSupp(ree, eviSet, supp)
                if conf >= conf_threshold && supp >= supp_threshold_cnt
              } yield newPredSet -> Stat(supp, conf)
          }

          val nextRound = validNew.toMap ++ validp
          inner(nextRound, validp.size)
        } else {
          validp
        }

      }


      val valid = inner(m, -1)

      //      logger.debug(s"valid size: ${valid.size}")
      //$.WriteResult(s"${p2i.get(rhs).get}_valid.log", valid.mkString("\n\n"))

      val min = minimize(valid)
      min
  }

  private def toRHSMap(t: Iterable[REEWithStat]) = {
    t.groupBy(_.ree.p0).map(p => p._1 -> p._2.map(v => (v.ree.X, Stat(v.supp, v.conf))))
  }

  def findSmallerREE(t: Iterable[REEWithStat], mineArg: MineArg): Iterable[REEWithStat] = mineArg match {
    case mineArg@MineArg(spark,
    allSpace, lhsSpace, rhsSpace,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>

      val rhsMap = toRHSMap(t)


      val t1 = rhsMap.toIndexedSeq.map {
        case (rhs, m) =>
          rhs -> findSmallerX(rhs, m, mineArg)
      }

      t1.flatMap {
        case (rhs, ps) => ps.map {
          case (x, stat) => REEWithStat(REE(x, rhs), stat.supp, stat.conf)
        }
      }
  }


  private def wellDefCDF(td: TDigest, num: Double): Double = {
    val cdfDirect = td.cdf(num)
    if (cdfDirect > 0) cdfDirect else 0
  }

  private def expandSamplesCDF(rhs: Expression,
                               rhsGroup: Iterable[Sample],
                               result: Iterable[REEWithStat],
                               mineArg: MineArg) = mineArg match {
    case MineArg(spark,
    allSpace, lhsSpace, rhsSpace,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>
      val K = dist
      val sc = spark.sparkContext
      // ================================== profiling ==================================
      var (falsePositive, falseNegative) = (0L, 0L)
      var sampleCleanedN = 0L
      var discarded = 0L
      var fullEval = 0L
      var evalSize = 0L
      var overlapTotal = 0L
      // ================================== profiling ==================================

      val supp_threshold_cnt = (supp_threshold * fullEviSize).toLong

      logger.debug(s"recall: ${recall * 100}%; ")
      logger.info(s"Expand Sample With RHS:$rhs")

      def expand: PartialFunction[Sample, Iterable[(PredicateSet, Stat)]] = {
        case Sample(predecessors, rhs, digest, p2i) =>
          evalSize += 1
          val t = predecessors.flatMap(p => {
            val eviSetFiltered = eviSet.filter(evi => p.isSubsetOf(evi._1))
            val mineArgFilterEvi = mineArg.updateEvi(eviSetFiltered)
            val expandArg = ExpandSampleArg(p, mineArgFilterEvi, rhs, K)
            //            val t = getNeighborsWithFilter(expandArg)
            val t = getSuccessorsWithFilter(expandArg)
            t
          }).toMap
          fullEval += 1
          t
      }


      val samples = rhsGroup
      // filter
      val cleanedSample = samples.filter(p => conf_threshold <= p.digest.getMax).toIndexedSeq
      val valid_ree_size_order: Sample => Int = {
        case Sample(predecessors, rhs, tdigest, p2i) =>
          val cdf = wellDefCDF(tdigest, conf_threshold)
          ((1 - cdf) * tdigest.size()).toInt
      }

      val allSampleN = recall * cleanedSample.map(s => (1 - wellDefCDF(s.digest, conf_threshold)) * s.digest.size()).sum

      val (tobeEvals, sortTime) = if (recall == 1.0d) {
        (cleanedSample, 0)
      } else {
        var mined = 0L
        /** Sort samples by covered valid REEs in decesending order */
        val chunkNum = getChunkNum(cleanedSample.size, instanceNum)
        //        val (sortedSamples,sortTime) = Wrappers.timerWrapperRet(
        //          sc.parallelize(cleanedSample,chunkNum)
        //            .map(p => p -> valid_ree_size_order(p)).sortBy(-_._2).collect())
        val (sortedSamples, sortTime) = Wrappers.timerWrapperRet(
          cleanedSample
            .par.map(p => p -> valid_ree_size_order(p)).toIndexedSeq.sortBy(_._2)(Ordering[Int].reverse))
        val tobeEval = sortedSamples.takeWhile {
          case (Sample(predecessors, rhs, cdf, p2i), estimatedResN) =>
            mined += (estimatedResN.toLong)
            mined < allSampleN
        }.map(_._1)

        (tobeEval, sortTime)
      }

      logger.info(
        s"""
           |original: ${samples.size}
           |cleaned: ${cleanedSample.size}
           |tobeEval: ${tobeEvals.length}
           |sortTime: $sortTime
           |""".stripMargin)


      //      evalSize += tobeEvals.length
      //      println(samples.map(_.digest.getMax).mkString(","))


      val chunkNum = getChunkNum(tobeEvals.size, instanceNum)
      val sampleResSet = sc.parallelize(tobeEvals, chunkNum).mapPartitions {
        chunk => chunk.map(expand)
      }

      val sampleResSetOut = sampleResSet

      val sampleREEResSet: Iterable[(PredicateSet, Stat)] = sampleResSetOut.fold(Map.empty[PredicateSet, Stat])(_ ++ _)
      val resultTranformed: Iterable[(PredicateSet, Stat)] = result.map(rws => (rws.ree.X, Stat(rws.supp, rws.conf)))

      // minimality
      logger.info(s"$rhs\nMINIMIZING...: ${sampleREEResSet.size}\n")
      val (min,timeMin) = Wrappers.timerWrapperRet(minimize(sampleREEResSet ++ resultTranformed))
      logger.info(s"$rhs\nMINIMIZE BEFORE: ${sampleREEResSet.size}\n AFTER:${min.size}, time=${timeMin}")

      min

  }


  private def expandSamplesCDF1(rhs: Expression,
                                levelwiseSamples: Map[Int, Iterable[Sample]],
                                result: Iterable[REEWithStat],
                                mineArg: MineArg) = mineArg match {
    case MineArg(spark,
    allSpace, lhsSpace, rhsSpace,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>
      val K = dist
      //val sampleResSet = TrieMap.empty[PredicateSet, Double]

      val levelwiseSamples1 = levelwiseSamples.filter(_._1 <= Config.levelUpperBound)
      val levelwiseSamples2 = levelwiseSamples1.map(p => p._1 -> p._2.filter(e => e.digest.getMax >= conf_threshold))

      // ================================== profiling ==================================
      var (falsePositive, falseNegative) = (0L, 0L)
      var sampleCleanedN = 0L
      var discarded = 0L
      var fullEval = 0L
      var evalSize = 0L
      var overlapTotal = 0L
      // ================================== profiling ==================================

      val supp_threshold_cnt = (supp_threshold * fullEviSize).toLong

      logger.info(s"recall: ${recall * 100}%; ")

      val expand: PartialFunction[Sample, Iterable[(PredicateSet, Stat)]] = {

        //        cleanedSample.par.foreach {
        //                rdd.map {
        case predSample: Sample =>
          //logger.info(s"fpNew ${fpNew}, npNew ${npNew}, prob: ${prob}")
          evalSize += 1
          //                    val (t, overlap) = getNeighborsWithFilter(expandArg)
          val precs = predSample.predecessors
          val t = precs.flatMap(p => {
            val expandArg = ExpandSampleArg(p, mineArg, rhs, K)
            getSuccessorsWithFilter(expandArg)
          })
          t.toIndexedSeq
      }

      val sc = spark.sparkContext
      val (sampleResSet, timeAllLevel) = {
        Wrappers.timerWrapperRet {
          //          sc.parallelize(levelwiseSamples2.toIndexedSeq).map {
          levelwiseSamples2.toIndexedSeq.map {
            case (l, samples) =>
              logger.info(s"[INC] expanding by CONF. I am in Level $l")

              val cleanedSample = samples.filter(p => conf_threshold <= p.digest.getMax).toIndexedSeq


              val N = cleanedSample.map(s => (1 - wellDefCDF(s.digest, conf_threshold)) * s.digest.size()).sum
              val FN_max = (N * (1 - recall)).toLong
              var FN = 0D

              sampleCleanedN += cleanedSample.size
              // S_\approx in line 14

              val valid_ree_size_order = (p: Sample) => {
                p match {
                  case Sample(predecessors, rhs, tdigest, p2i) =>
                    val cdf = wellDefCDF(tdigest, conf_threshold)
                    (1 - cdf) * tdigest.size()
                }
              }


              var possible_mined = 0L
              val max_mined = (N * recall).toLong
              val (tobeEval, time) = Wrappers.timerWrapperRet {
                if (recall == 1.0d) {
                  cleanedSample
                } else {
                  val tobeEval = mutable.ArrayBuffer.empty[Sample]
                  breakable {
                    cleanedSample.sortBy(p => -valid_ree_size_order(p)).foreach {
                      //                      cleanedSample.foreach {
                      case p@Sample(predecessors, rhs, digest, p2i) =>
                        //val expandN = predecessors.size * (lhsSpace.size - predecessors.size)
                        val expandN = digest.size()
                        val n = ((1 - wellDefCDF(digest, conf_threshold)) * expandN).toLong
                        if (possible_mined > max_mined) {
                          break
                        } else {
                          tobeEval += p
                        }
                        possible_mined += n
                    }
                  }
                  tobeEval
                }


              }


              logger.info(
                s"""
                   |original: ${samples.size}
                   |cleanedSample: ${cleanedSample.size}
                   |tobeEval: ${tobeEval.size}
                   |""".stripMargin)

              discarded += (cleanedSample.size - tobeEval.size)


              val (resLevel, evalTime) = Wrappers.timerWrapperRet {
                val rdd = sc.parallelize(tobeEval.toIndexedSeq)
                rdd.map {
                  expand
                }.fold(Map.empty[PredicateSet, Stat])((acc, p) => acc ++ p)
              }
              resLevel
          }
        }

      }


      val emptyX = PredicateSet.empty(p2i)
      val ree0 = REE(emptyX, rhs)
      val supp0 = getSuppCnt(ree0, eviSet)
      val conf0 = getConfFromSupp(ree0, eviSet, supp0)

      val sampleResSet0 = sampleResSet.fold(Map.empty[PredicateSet, Stat])(_ ++ _).toMap

      val sampleResSetOut = if (supp0 >= supp_threshold_cnt && conf0 >= conf_threshold) {
        sampleResSet0 + (emptyX -> Stat(supp0, conf0))
      } else {
        sampleResSet0
      }


      val sampleREEResSet: Iterable[(PredicateSet, Stat)] = sampleResSetOut
      val resultTranformed: Iterable[(PredicateSet, Stat)] = result.map(rws => (rws.ree.X, Stat(rws.supp, rws.conf)))

      // minimality
      val min = minimize(sampleREEResSet ++ resultTranformed)
      logger.debug(s"$rhs\nMINIMIZE BEFORE: ${sampleResSet0.size}\n AFTER:${min.size}")

      min

  }

  private def getSuccessorsWithFilter(expandSampleArg: ExpandSampleArg) = expandSampleArg match {
    case ExpandSampleArg(sample, mineArg, rhs, dist) =>
      mineArg match {
        case MineArg(spark,
        allSpace, lhsSpace, rhsSpace,
        supp_threshold, conf_threshold,
        p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>

          val new_supp_cnt = supp_threshold * fullEviSize
          val arg = StateConsArg(lhsSpace, p2i, rhs, eviSet)
          val corr = getCorrelatedPreds(arg)
          val rest = getRest(sample, corr)

          val out = for {
            p <- rest
            succ = sample :+ p
            ree = REE(succ, rhs)
            supp = getSuppCnt(ree, eviSet)
            conf = getConfFromSupp(ree, eviSet, supp)
            if supp >= new_supp_cnt && conf >= conf_threshold && p != rhs

          } yield succ -> Stat(supp, conf)

          out.toMap
      }

  }

  private def getRest(sample: PredicateSet, corr: PredicateSet) = (sample & corr) ^ corr


  private def elimTrivial[T](tResult: Iterable[(PredicateSet, T)], rhs: Expression): Iterable[(PredicateSet, T)] = {
    for ((predSet, conf) <- tResult if !predSet.contains(rhs)) yield predSet -> conf
  }


  def toRhsMap[T](in: Iterable[REEWithStat]) = {
    in.groupBy(_.ree.p0).map {
      case (rhs, m) =>
        rhs -> m.map {
          case REEWithStat(ree, supp, conf) => ree.X -> conf
        }
    }
  }

  def minimizeREE(t: Iterable[REE]): Iterable[REE] = {
    REEMiner
      .minimizeREEStat(t.map(ree => REEWithStat(ree, -1, -1)))
      .map(_.ree)
  }

  def minimizeREEStat[T](t: Iterable[REEWithStat]): Iterable[REEWithStat] = {
    val rhsMap = toRhsMap(t)
    val t1 = for ((rhs, m) <- rhsMap) yield {
      rhs -> minimize(m)
    }

    t1.flatMap {
      case (rhs, ps) => ps.map {
        case (x, conf) => REEWithStat(REE(x, rhs), -1L, conf)
      }
    }
  }


  def mineParallelLevel(sc: SparkContext,
                        levelp: Iterable[State],
                        visitedB: Broadcast[Iterable[(PredicateSet, Stat)]],
                        mineArgB: Broadcast[MineArg],
                        instanceNum: Int, rhs: Expression, levelN: Int):
  (Iterable[REEWithStat], Iterable[State]) = {
    val chunkNum = getChunkNum(levelp.size, instanceNum)
    val rdd = sc.parallelize(levelp.toIndexedSeq, chunkNum)

    val rdd1 = rdd.mapPartitions {
      chunk =>
        val visited = visitedB.value.map(_._1).toSet
        val mineArg = mineArgB.value
        val evi = mineArg.eviSet
        val supp_threshold_cnt = mineArg.supp_threshold * mineArg.fullEviSize
        val conf_threshold = mineArg.conf_threshold
        //        val eviRHS = evi.filter(e => e._1.contains(rhs))


        val nextLevelPartial = mutable.Set.empty[State]
        val resLevelPartial = mutable.Map.empty[PredicateSet, Stat]
        chunk.foreach {
          case state@State(selectedPred, restPred, supp, conf, level) =>
            //           prune nodes that supp is too small
            if (supp >= supp_threshold_cnt && conf >= conf_threshold) {

              resLevelPartial += selectedPred -> Stat(supp, conf)
            } else if (supp < supp_threshold_cnt) {

              ()

            } else if (levelN == Config.levelUpperBound) {
              if (conf >= Config.MIN_CONF) {
                val resArg = ResArg(None, None, Some(selectedPred, Stat(supp, conf)))
                BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)
              } else {
                CollectionArg.empty[Stat]
              }

            } else {
              //              val eviSetX = evi.filter(e => selectedPred.isSubsetOf(e._1))
              //              val eviREE = eviRHS.filter(e => selectedPred.isSubsetOf(e._1))
              val n = for {p <- restPred
                           if p != rhs
                           newP = selectedPred :+ p
                           //if !visited.contains(newP)
                           newRest = restPred :- p
                           ree = REE(newP, rhs)
                           supp = getSuppCnt(ree, eviSet = evi)
                           conf = getConfFromSupp(ree, eviSetX = evi, supp)
                           stateNew = State(newP, newRest, supp, conf, level + 1)
                           } yield stateNew

              nextLevelPartial ++= n
            }
        }

        Iterator((resLevelPartial, nextLevelPartial))
    }


    rdd1.map {
      case (rRaw, nextLevel) =>
        (rRaw.map(p => REEWithStat(REE(p._1, rhs), p._2.supp, p._2.conf)), nextLevel)
    }.fold((mutable.Set.empty[REEWithStat], mutable.Set.empty[State])) {
      case (acc, x) => (acc._1 ++ x._1, acc._2 ++ x._2)
    }
  }


  def incMineContBaseline(in: Iterable[REEWithStat], arg: MineArg): Iterable[REEWithStat] = arg match {
    case MineArg(spark,
    allSpace, lhsSpace, rhsSpace,
    newSupp, newConf,
    p2i, evi, fullEviSize, recall, dist, instanceNum) =>

      def expand(visited: Iterable[(PredicateSet, Stat)], expandArg: ExpandArg): Iterable[REEWithStat] = {
        expandArg match {
          case ExpandArg(mineArg, rhs, startUpState) =>
            mineArg match {
              case MineArg(spark, allSpace, lhsSpace, rhsSpace, supp_threshold, conf_threshold, p2i, eviSet, fullEviSize, recall, _, instanceNum) =>

                var levelp: Iterable[State] = mutable.Set.empty[State] ++= startUpState
                val sc = spark.sparkContext
                val mineArgB = sc.broadcast(mineArg)


                val levels = for {levelN <- 0 to Config.levelUpperBound
                                  if levelp.nonEmpty
                                  visitedB = sc.broadcast(visited.filter(p => p._1.size == levelN + 1))
                                  // parallel
                                  (resLevel, nextLevel) = mineParallelLevel(sc, levelp,
                                    visitedB, mineArgB,
                                    instanceNum, rhs, levelN)
                                  } yield {
                  if (levelN == 4) $.WriteResult(s"log_inc/${rhs}_${levelN}.log", s"SIZE:${levelp.size}\n${levelp.map(s => s.toJson).mkString("\n")}")
                  levelp = nextLevel
                  resLevel
                }

                levels.flatten
            }
        }
      }

      val rhsCandidates = if (Config.ENABLE_DEBUG_SINGLE_RHS) {
        val r = Vector(rhsSpace.toIndexedSeq(Config.DEBUG_RHS_INDEX))
        logger.info(s"Single RHS = ${r}")
        //assert(r.head.asInstanceOf[TypedTupleBin].col1.getValue == "ZIP_Code(String)")
        r
      } else {
        rhsSpace
      }
      val pb = new ProgressBar(rhsSpace.size)
      val rhss = for (rhs <- rhsCandidates) yield {
        val visited = in.filter(ree => ree.ree.p0 == rhs)
          .map(ree => ree.ree.X -> Stat(ree.supp, ree.conf))

        val statConArg = StateConsArg(lhsSpace, p2i, rhs, evi)
        val expandArg = ExpandArg(arg, rhs, Set(emptyState(statConArg)))
        val r = expand(visited, expandArg)
        pb += 1
        r
      }

      rhss.flatten

  }

  def incMine(mineResult: MineResult, mineCtx: MineArg,
              oldSupp: Double, oldConf: Double): MineResult = mineCtx match {

    case MineArg(_,
    allSpace, _, rhsSpace,
    newSupp, newConf,
    p2i, _, fullEviSize, recall, dist, instanceNum) =>

      val deltaSupp = newSupp - oldSupp
      val deltaConf = newConf - oldConf

      val old_supp_threshold_cnt = (fullEviSize * oldSupp).toLong
      val new_supp_threshold_cnt = (fullEviSize * newSupp).toLong

      // policies
      def supp_up_conf_up = mineResult match {
        case MineResult(results, _, _) =>

          logger.info("SUPP+,CONF+")
          incMineCont(results, mineCtx.updateThreshold(newSupp, newConf)) match {
            case MineResult(r, p, s) =>
              MineResult(r, p, s)
          }
      }

      def supp_up_conf_down = mineResult match {
        case MineResult(results, _, samples) => // todo: sample redundant result
          logger.info("SUPP+,CONF-")

          val resultKeep = results
            .filter({ case REEWithStat(_, supp, conf) => conf >= newConf && supp >= new_supp_threshold_cnt })
          if (Config.ENABLE_SAMPLING) {
            restoreSamplesCDF(samples.toIndexedSeq, resultKeep.toIndexedSeq, mineCtx.updateThreshold(newSupp, newConf))
          } else {
            //            assert(false,"bp hit")
            logger.info(s"BASELINE MINING WITH THRESHOLD ${(newSupp, newConf)}")
            val r = batchMine(mineArg = mineCtx.updateThreshold(newSupp, newConf))
            r
          }
      }

      def supp_down_conf_up = mineResult match {
        case MineResult(results, pruneds, _) =>
          logger.info("SUPP-,CONF+")

          val resultKeep = results.filter { case REEWithStat(_, _, conf) => conf >= newConf }
          val prunedFiltered = pruneds.filter { case REEWithStat(ree, supp, conf) =>
            supp >= new_supp_threshold_cnt && conf < newConf && ree.X.size < Config.levelUpperBound
          }

          val prunedKeep = pruneds.filter { case REEWithStat(_, supp, conf) =>
            supp >= new_supp_threshold_cnt && conf >= newConf
          }

          val in = minimizeREEStat((results ++ prunedFiltered).toSet)

          logger.info(s"Pruned p SIZE:${prunedFiltered.size} -> ${in.size}")
          logger.info(s"In size: ${in.size}")

          val (r, _) = Wrappers.timerWrapperRet(
            incMineCont(in, mineCtx.updateThreshold(newSupp, newConf))
            match {
              case MineResult(resultInc, pruned, samples) =>
                val min = minimizeREEStat(resultInc ++ prunedKeep)
                MineResult(min, pruned, samples)
            })

          r
      }

      def supp_down_conf_down =
        mineResult match {
          case MineResult(results, pruneds, samples) =>
            // todo: sample redundant result
            logger.info("SUPP-,CONF-")
            val prunedFiltered = pruneds.filter {
              case REEWithStat(ree, supp, conf) =>
                conf < newConf && supp >= new_supp_threshold_cnt && ree.X.size < Config.levelUpperBound
            }

            val prunedKeep = pruneds.filter {
              case REEWithStat(_, supp, conf) =>
                conf >= newConf && supp >= new_supp_threshold_cnt
            }

            val phase1 = if (Config.ENABLE_SAMPLING) {
              restoreSamplesCDF(samples.toIndexedSeq, results.toIndexedSeq, mineCtx.updateThreshold(newSupp, newConf))
              match {
                case MineResult(resultExpand, _, _) =>
                  val in = minimizeREEStat(prunedFiltered)
                  val mineRes = incMineCont(in, mineCtx.updateThreshold(newSupp, newConf))
                  mineRes match {
                    case MineResult(result, pruned, samples) =>
                      MineResult(minimizeREEStat(result ++ prunedKeep ++ resultExpand), pruned, samples)
                  }
              }
            } else {
              //              assert(false,"bp hit")
              val r = batchMine(mineArg = mineCtx.updateThreshold(newSupp, newConf))
              r
            }

            phase1


        }

      def supp_up = mineResult match {
        case MineResult(results, pruneds, samples) => // todo: sample redundant result
          logger.info("SUPP+,CONF=")
          val resultKeep = results.filter {
            case REEWithStat(p, supp, conf) =>
              supp >= new_supp_threshold_cnt && conf >= newConf
          }

          MineResult(resultKeep, mutable.Iterable(), mutable.Iterable())
      }


      if (deltaSupp >= 0 && deltaConf > 0) {
        supp_up_conf_up
      } else if (deltaSupp >= 0 && deltaConf == 0) {
        supp_up
      } else if (deltaSupp >= 0 && deltaConf < 0) {
        supp_up_conf_down
      } else if (deltaSupp < 0 && deltaConf >= 0) {
        supp_down_conf_up
      } else if (deltaSupp < 0 && deltaConf < 0) {
        supp_down_conf_down
      } else {
        logger.fatal(s"Cannot Find Threshold ${(oldSupp, newSupp, oldConf, newConf)}")
      }
    case _ => ???
  }

  def batchMine(mineArg: MineArg): MineResult = mineArg match {
    case MineArg(spark: SparkSession,
    allSpace,
    lhsSpace,
    rhsSpace,
    supp_threshold: Double, conf_threshold: Double,
    p2i,
    eviSet,
    fullEviSize, recall, dist, instanceNum) =>
      // TODO: cross table
      // TODO: sampling

      assert(supp_threshold > 0 && conf_threshold > 0)

      val resultCollector = spark.sparkContext.collectionAccumulator[REEWithStat]("REEs")
      val prunedCollector = spark.sparkContext.collectionAccumulator[REEWithStat]("PRUNEDs")
      val sampledCollector = spark.sparkContext.collectionAccumulator[REEWithT[TDigest]]("Samples")

      //      val pb = new ProgressBar(rhsSpace.size)
      logger.info(s"Search REE From ${lhsSpace.size} X ${rhsSpace.size} space with supp > $supp_threshold | conf >$conf_threshold | With Top K=${Config.TOPK}")

      val timeSampleAll = spark.sparkContext.doubleAccumulator("Time Sample All")
      val timeSearch = spark.sparkContext.doubleAccumulator("Time Search All")
      val timeCollector = spark.sparkContext.collectionAccumulator[String]("time")

      // val rhsRDD = spark.sparkContext.parallelize(rhsSpace.toIndexedSeq)
      // spark context cannot be referred in executors,
      val rhsCandidates = if (Config.ENABLE_DEBUG_SINGLE_RHS) {
        val r = Vector(rhsSpace.toIndexedSeq(Config.DEBUG_RHS_INDEX))
        logger.info(s"Single RHS = ${r}")
        //assert(r.head.asInstanceOf[TypedTupleBin].col1.getValue == "ZIP_Code(String)")
        r
      } else {
        rhsSpace
      }


      val pbar = new ProgressBar(rhsSpace.size)
      val rs =
      // todo
        for (pRHS <- rhsCandidates) yield {
          //          val eviSetForRHS = eviSet.filter(p=>p._1.contains(pRHS))
          logger.info(s"[BATCH] expanding ${pRHS}")
          val emptyStates = Vector(emptyState(StateConsArg(lhsSpace, p2i, pRHS, eviSet)))
          val ((expandResult, sampleResult), time) = {
            Wrappers.timerWrapperRet(
              expandParWithState111(
                ExpandArg(
                  mineArg = mineArg.updateEvi(eviSet),
                  rhs = pRHS,
                  startUpState = emptyStates)))
          }

          pbar += 1

          val sampledInner = sampleResult
          expandResult match {
            case ExpandResult(resultX, prunedX, timeSample) =>
              //val minimal = minimize(resultX.toMap)
              //val minPruned = minimize(prunedX)
              timeSampleAll.add(timeSample)
              implicit val transStat: ((PredicateSet, Stat), Expression) => REEWithStat
              =
                (t: (PredicateSet, Stat), rhs: Expression) => {
                  t._2 match {
                    case Stat(supp, conf) =>
                      REEWithStat(REE(t._1, rhs), supp = supp, conf = conf)
                  }

                }

              val result = trans(rhs = pRHS, resultX)
              val pruned = trans(rhs = pRHS, prunedX)
              val sampled = sampledInner.m

              //val smaller = findSmallerREE(result, mineArg)
              MineResult(result, pruned, sampled)

            case _ => ???
          }

        }
      //todo
      val fullMineResult = rs.reduce(_ ++ _)
      //      val fullMineResult = rs

      fullMineResult

    case _ => logger.fatal(s"Fatal: Match Error: ${mineArg}")
  }


  private def filterPredicates(predSpace: PredSpace, opset: Set[Operator]): IndexedSeq[Expression] = {
    predSpace.filter {
      case (op, _) =>
        opset.contains(op)
    }.flatten(_._2).toIndexedSeq
  }

  private def getRHSSet(predSpace: PredSpace): IndexedSeq[Expression] = {
    filterPredicates(predSpace, Config.REE_RHS).filter {
      case TypedTupleBin(op, _, _, col1, col2) =>
        col1 == col2 && op == Eq
      case TypedConstantBin(op, _, _, _) =>
        op == Eq
    }
  }

  // todo: generalize to < > !=
  private def getLHSSet(predSpace: PredSpace): IndexedSeq[Expression] =
    filterPredicates(predSpace, Config.REE_LHS)

  def initMineArg(spark: SparkSession,
                  db: Iterable[TypedColTable], instanceNum: Int): MineArg = {
    initMineArg(spark, db, -1d, -1d, 1, 1, instanceNum = instanceNum)
  }

  def initMineArg(spark: SparkSession,
                  db: Iterable[TypedColTable],
                  supp_threshold: Double, conf_threshold: Double, recall: Double, K: Int, instanceNum: Int): MineArg = {
    val predSpace = PredSpace(db.head)
    val rhsSpace = getRHSSet(predSpace)

    // TODO: correlation analysis (causality learning)
    val lhsSpace = getLHSSet(predSpace)
    val allSpace = (lhsSpace ++ rhsSpace).toSet
    logger.info(
      s"""
         |All Space: ${allSpace.size}
         |TypedTupleBins: ${allSpace.count(_.isInstanceOf[TypedTupleBin])}
         |TypedConstBins: ${allSpace.count(_.isInstanceOf[TypedConstantBin])}
         |"""".stripMargin)

    val p2i: PredicateIndexProvider = PredSpace.getP2I(allSpace)


    val eviSet: IEvidenceSet = {
      $.GetJsonIndexOrElseBuild(Config.EVIDENCE_IDX_NAME(db.head.getName), p2i,
        {
          val evb = EvidenceSetBuilder(db.head, predSpace, SplitReconsEviBuilder.FRAG_SIZE)(p2i)
          logger.info(s"Evidence Building With PredSpace ${predSpace.values.map(_.size).sum}...")
          val (ret, time) = Wrappers.timerWrapperRet(SplitReconsEviBuilder.buildFullEvi(evb, db.head.rowNum))
          logger.info(s"Evidence Building Time $time; With Evidence Size: ${ret.size}")
          $.WriteResult("evidence.txt", ret.mkString(",\n"))
          ret
        })
    }


    val eviList = eviSet.to(mutable.IndexedSeq)
    if (Config.SANITY_CHECK) {
      logger.info(s"${eviList.head._1.getP2I.size} =?= ${p2i.size}")
      logger.info(s"${eviList.head._1.getP2I.getObjects} \n ${p2i.getObjects}")
      assert(eviList.nonEmpty && (eviList.head._1.getP2I equals p2i))
    }


    MineArg(spark = spark,
      allSpace = allSpace,
      lhsSpace = lhsSpace,
      rhsSpace = rhsSpace,
      supp_threshold = supp_threshold,
      conf_threshold = conf_threshold,
      p2i = p2i,
      eviSet = eviList,
      fullEviSize = eviList.map(_._2).sum, recall, K,
      instanceNum = instanceNum)

  }


  private def withOppositePreds(l: Iterable[Expression]) = {
    l.flatMap {
      case t: TCalc => List(t.asInstanceOf[Expression], t.withOpposite)
      case _ => ???
    }.toSet
  }

  def subst(tempXs: IndexedSeq[Expression], concreteConstPreds: Iterable[Expression])
  : Iterable[Iterable[Expression]] = {
    type PredicateSetCon = IndexedSeq[Expression]
    val cps = concreteConstPreds.asInstanceOf[Iterable[TypedConstantBin]]

    def f(i: Int, path: PredicateSetCon): IndexedSeq[PredicateSetCon] = {
      if (i == tempXs.size) {
        Vector(path, path.filter(_.isInstanceOf[TypedTupleBin]))
      } else {
        tempXs.flatMap {
          case t: TypedTupleBin =>
            f(i + 1, path :+ t)
          case c@TypedConstantBin(op0, _, col0, const0) =>
            val valids = cps.filter(e => e.op == op0 && e.col == col0)
            if (valids.isEmpty) ???
            else valids.flatMap(v => f(i + 1, path :+ v))
        }
      }
    }

    f(0, Vector())


  }


  private def recoverConstantCkpt(results: Iterable[REEWithStat], p2iNew: PredicateIndexProvider,
                                  causalityTable: Map[Expression, Iterable[Expression]]) = {
    // 1. mine result constant replacement
    // 2. constant pred as rhs

    //logger.info("Filling Up Constants to Generate Ckpt...")
    val constPredsConcre = causalityTable.keySet.filter(_.isInstanceOf[TypedConstantBin])

    val ckptConcre =
      results.flatMap {
        case REEWithStat(ree, supp, conf) =>
          ree match {
            case REE(x, p0) =>

              p0 match {
                case t: TypedTupleBin =>
                  // fill up with
                  val xsNew = subst(x.toIndexedSeq, constPredsConcre)
                    .map(e => PredicateSet.from(e)(p2iNew))

                  val reesNew = xsNew.map(xNew => REE(xNew, t))
                  REEMiner.minimizeREE(reesNew)

                case TypedConstantBin(op, _, col, const) =>
                  val r = constPredsConcre.filter {
                    case TypedConstantBin(op1, _, col1, _) => col1 == col
                    case _ => false
                  }

                  r.map(p0 => REE(x.withP2I(p2iNew), p0))
                case _ => ???
              }

          }

      }

    ckptConcre

  }

  private def toStateWithCausality(reesp: REEWithStat, p2i: PredicateIndexProvider, causalityTable: CausalityTable)(eviSet: EvidencePairList): State = {

    reesp match {
      case REEWithStat(REE(x, p0), supp, conf) =>
        val zero = PredicateSet.empty(p2i)
        val ps = causalityTable.getOrElse(p0, zero)
        val relatedPredicateSet = PredicateSet.from(ps)(p2i)
        val rest = (relatedPredicateSet | x) ^ x
        val level = x.size
        State(x, rest, supp = supp, conf = conf, level)
    }


  }

  type CausalityTable = Map[Expression, Set[Expression]]

  private def emptyCauseTable: CausalityTable = Map.empty[Expression, Set[Expression]]

  private def mergeCauseTables(a: Option[CausalityTable], b: Option[CausalityTable]): Option[CausalityTable] = {
    val aa = a.getOrElse(emptyCauseTable)
    val bb = b.getOrElse(emptyCauseTable)
    Some((aa.keySet ++ bb.keySet).foldLeft(emptyCauseTable) { (acc, key) =>
      acc.updated(key, aa.getOrElse(key, Set.empty) ++ bb.getOrElse(key, Set.empty))
    })
  }

  // todo: correlatation analysis
  private def buildCausalityTable(recoverArg: PConstRecovArg) = recoverArg match {
    // 1. filter typed tuple binarys with tuple (t,s) coltable D
    // 1.1 based on typed tuple binarys, we recover P_c satisfies (t, s)
    // 2. resume mining
    case PConstRecovArg(typedColTable, mineResult, mineArg) =>
      mineArg match {
        case MineArg(spark, _, _, rhsSpace, supp_threshold, conf_threshold, p2i, eviSet, fullEviSize, recall, dist, instanceNum) => {
          val typedTupleBins = p2i.getObjects.filter(_.isInstanceOf[TypedTupleBin])
          val wildCards = p2i.getObjects.filter(_.isInstanceOf[TypedConstantBin])
          val memo = TrieMap.empty[Expression, mutable.BitSet]
          // build causality table
          //              val rdd = spark.sparkContext.parallelize(typedTupleBins.toIndexedSeq)
          val rowNum = typedColTable.rowNum
          val causalityTable: CausalityTable

          //              = rdd.map {
          = typedTupleBins.par.map {
            case pred@TypedTupleBin(op, _, _, _, _) =>
              op match {
                case Eq =>
                  val idxsBin = evalMemo(pred, typedColTable)(memo)
                  logger.debug(s"[AAA] PRED:$pred -> TUPLES:${idxsBin.size}")
                  // todo: filter constant predicates

                  type Assoc = (Expression, Option[Expression])
                  val causalityPairs = wildCards.flatMap {
                    case tem@TypedConstantBin(_, _, col, _) =>
                      val idxValVec = typedColTable.getColumnIdxValVector(col)
                      val predPairs: Set[Assoc] =
                        if (idxsBin.isEmpty) {
                          Set.empty[Assoc] + (pred -> None)
                        } else {
                          val s: Set[Assoc] = idxsBin.map(i => idxValVec(i)).toSet
                            .flatMap {
                              e: Int =>
                                val constAtom = ConstantAtom(typedColTable.getConst[String](HString, e))
                                val constPred = tem.withConst(constAtom)
                                val idxsConst = evalMemo(constPred, typedColTable)(memo)

                                // $.WriteResult("debug.txt", s"${idxsConst.mkString("\n")}")
                                // correlation analysis
                                val t = ((idxsConst & idxsBin).size.toDouble / rowNum) >= Config.CONSTANT_FILTER_RATE
                                Set.empty[Assoc] ++ (if (t) {
                                  List(constPred -> Some(pred), pred -> Some(constPred))
                                } else {
                                  List(constPred -> None, pred -> None)
                                })
                            }
                          s
                        }
                      predPairs
                  }

                  val causalityTablePartial: CausalityTable = causalityPairs.groupBy(_._1).mapValues(e => e.flatMap(_._2).toSet).map(p => p)
                  Some(causalityTablePartial)
                case NEq | Ge | Lt | Le | Gt => //println(s"Skip:$pred");
                  Some(Map.empty[Expression, Set[Expression]] + (pred -> Set.empty[Expression]))
                case _ => None
              }
          }.reduce(mergeCauseTables).getOrElse(emptyCauseTable)

          // output all preds
          causalityTable
        }
      }


  }

  private def getFullPredSpaceWithConstFilterRate(causalityTable: CausalityTable) = causalityTable.keySet

  private def evalMemo(pred: Expression, table: TypedColTable)(memo: mutable.Map[Expression, mutable.BitSet]) = {
    pred match {
      case t: TypedTupleBin =>
        memo.getOrElseUpdate(pred, {
          t.eval(table)
        })
      case c: TypedConstantBin =>
        memo.getOrElseUpdate(pred, {
          c.eval(table)
        })
    }
  }


  def initConstantRecovery(recoverArg: PConstRecovArg): CRecoverArg1 = {
    recoverArg match {
      case PConstRecovArg(data, mineResult, mineArg) =>
        logger.info("Fill Template Predicates with Concrete Ones.")
        val causalityTable = buildCausalityTable(recoverArg)
        $.WriteResult("CAUSALITY.txt", s"CAUSALITY TABLE:${causalityTable.mkString("\n")}")
        logger.info("Fill Template Predicates with Concrete Ones Done.")

        val predsConcrete = getFullPredSpaceWithConstFilterRate(causalityTable)
        val predSpaceCon = PredSpace.from(predsConcrete)
        val p2iConcrete = PredSpace.getP2I(predsConcrete)

        logger.info(s"New P2I with size:${p2iConcrete.size}")
        logger.debug("p2iNew Mapping:\n", p2iConcrete.getMapping.mkString("\n"))
        // building concrete evidence
        val eviName = Config.EVIDENCE_IDX_NAME(data.getName + s"_concrete_constfilter=${Config.CONSTANT_FILTER_RATE}")
        val eviCon = $.GetJsonIndexOrElseBuild(eviName, p2iConcrete, $.defaultEvidenceBuild(data, predSpaceCon, p2iConcrete))
        val eviListCon = eviCon.toIndexedSeq

        // recover LHS constants
        // attach ree with new P2I
        logger.info("Filling Up Constants to Generate Ckpt...")
        val ckpt = recoverConstantCkpt(mineResult.result, p2iConcrete, causalityTable).toSet
        $.WriteResult(s"[READABLE][SUBST].txt", s"${mineResult.result.size}->${ckpt.size}\n===\n\n${ckpt.map(e => (e.readable, getSuppCnt(e, eviListCon), getConf(e, eviListCon))).mkString("\n")}")

        val mineArgNew = mineArg.updateP2i(p2iConcrete).updateEvi(eviListCon)
        CRecoverArg1(ckpt, causalityTable, mineArgNew)
    }


  }


  case class CRecoverArg1(ckpt: Iterable[REE], causalityTable: CausalityTable, mineArg: MineArg)

  def recoverConstants(cRecoverArg1: CRecoverArg1): MineResult = {
    cRecoverArg1 match {
      case CRecoverArg1(ckpt, causalityTable, mineArg: MineArg) =>
        mineArg match {
          case MineArg(spark, _, _, _, supp_threshold, conf_threshold, p2iConcrete, eviListCon, fullEviSize, _, _, _) =>

            logger.info("Continue Mining For Constant Recovery...")

            var validSupp = 0
            var validConf = 0
            val supp_threshold_cnt = fullEviSize * supp_threshold


            val groupByRHS = ckpt.groupBy(_.p0)
            val pbar = new ProgressBar(groupByRHS.size)
            //            val rddCkpt = spark.sparkContext.parallelize(ckpt.groupBy(_.p0).toIndexedSeq)
            val reeResults = groupByRHS.map {
              //            val reeResults = ckpt.groupBy(_.p0).toIndexedSeq.par.map {
              case (p0, rees) =>
                logger.debug(s"Expand RHS: $p0")
                logger.debug(s"$p0 -> ${
                  PredicateSet.from(causalityTable.getOrElse(p0, PredicateSet.empty(p2iConcrete)))(p2iConcrete)
                }")

                val reesStats = rees.map {
                  ree =>
                    val supp = getSuppCnt(ree, eviListCon)
                    val conf = getConfFromSupp(ree, eviListCon, supp)
                    REEWithStat(ree, supp, conf)
                }

                val reesKeep = reesStats.filter(e => e.supp >= supp_threshold_cnt && e.conf >= conf_threshold)
                val reesMine = reesStats.filter(e => e.supp >= supp_threshold_cnt && e.conf < conf_threshold)

                val startUpState = reesMine.map(reeS => {
                  val state = toStateWithCausality(reeS, p2iConcrete, causalityTable)(eviListCon)
                  if (state.supp > supp_threshold_cnt && p0.isInstanceOf[TypedTupleBin]) validSupp += 1
                  if (state.conf > conf_threshold && p0.isInstanceOf[TypedTupleBin]) validConf += 1
                  state
                })

                // update p2i -> p2iConcrete
                val mineArgNew = mineArg.updateP2i(p2iConcrete).updateEvi(eviListCon)
                val expandArg = ExpandArg(mineArgNew, p0, startUpState = startUpState)
                val (expandResult, _) = expandParWithState111(expandArg)

                pbar += 1

                val mined = expandResult.resultX.map {
                  case (x, stat) =>
                    REEWithStat(REE(x, p0), stat.supp, stat.conf)
                }

                mined ++ reesKeep
            }

            val result = reeResults.foldLeft(IndexedSeq.empty[REEWithStat])(_ ++ _)
            val min = PMiners.minimizeREEStat(result)
            MineResult(min, Iterable.empty[REEWithStat], Iterable.empty[Sample])
        }


    }


  }
}

// table not serializable, thus not in MineArg
// table only used by Evi Build
case class MineArg(spark: SparkSession,
                   allSpace: Iterable[Expression],
                   lhsSpace: Iterable[Expression],
                   rhsSpace: Iterable[Expression],
                   supp_threshold: Double, conf_threshold: Double,
                   p2i: PredicateIndexProvider,
                   eviSet: EvidencePairList,
                   fullEviSize: Long,
                   recall: Double,
                   K: Int,
                   instanceNum: Int) {


  // update parameter with new supp new conf recall and radius
  def updateRecallAndRadius(recallNew: Double, radiusNew: Int): MineArg = {
    this match {
      case MineArg(spark, allSpace, lhsSpace, rhsSpace, supp_threshold, conf_threshold, p2i, eviSet, fullEviSize, _, _, instanceNum) =>
        MineArg(spark, allSpace, lhsSpace, rhsSpace, supp_threshold, conf_threshold, p2i, eviSet, fullEviSize, recallNew, radiusNew, instanceNum)
    }

  }

  def updateP2i(p2iNew: PredicateIndexProvider): MineArg = {
    this match {
      case MineArg(spark, allSpace, lhsSpace, rhsSpace, supp_threshold, conf_threshold, _, eviSet, fullEviSize, recall, dist, instanceNum) =>
        MineArg(spark = spark, allSpace = allSpace, lhsSpace = lhsSpace, rhsSpace = rhsSpace, supp_threshold = supp_threshold, conf_threshold = conf_threshold, p2i = p2iNew, eviSet = eviSet, fullEviSize = fullEviSize, recall = recall, K = dist, instanceNum = instanceNum)
    }
  }

  def updateEvi(newEvi: EvidencePairList): MineArg = {
    this match {
      case MineArg(spark, allSpace, lhsSpace, rhsSpace, supp_threshold, conf_threshold, p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>
        MineArg(spark = spark, allSpace = allSpace, lhsSpace = lhsSpace, rhsSpace = rhsSpace, supp_threshold = supp_threshold, conf_threshold = conf_threshold, p2i = p2i, eviSet = newEvi, fullEviSize = fullEviSize, recall = recall, K = dist, instanceNum = instanceNum)
    }
  }

  def updateThreshold(newSupp: Double, newConf: Double): MineArg = {
    this match {
      case MineArg(spark, allSpace, lhsSpace, rhsSpace, _, _, p2i, eviSet, fullEviSize, recall, dist, instanceNum) =>
        MineArg(spark = spark,
          allSpace = allSpace,
          lhsSpace = lhsSpace,
          rhsSpace = rhsSpace,
          supp_threshold = newSupp,
          conf_threshold = newConf,
          p2i = p2i,
          eviSet = eviSet,
          fullEviSize = fullEviSize, recall = recall, K = dist, instanceNum)
    }

  }


}

case class ExpandArg(mineArg: MineArg, rhs: Expression,
                     startUpState: Iterable[State])

case class ExpandSampleArg(predicateX: PredicateSet, mineArg: MineArg, rhs: Expression, K: Int)

private case class SampleResult[T](m: Iterable[Sample])

case class StateConsArg(lhsSpace: Iterable[Expression],
                        p2i: PredicateIndexProvider,
                        rhs: Expression,
                        evidencePairList: EvidencePairList)

case class PConstRecovArg(typedColTable: TypedColTable, mineResult: MineResult, mineArg: MineArg) {
  def withMineResult(result: MineResult) = {
    PConstRecovArg(typedColTable, result, mineArg)
  }
}