package org.dsl.mining

import com.tdunning.math.stats.TDigest
import org.dsl.dataStruct.Interval
import org.dsl.dataStruct.evidenceSet.builders.{EvidenceSetBuilder, SplitReconsEviBuilder}
import org.dsl.dataStruct.evidenceSet.{HPEvidenceSet, IEvidenceSet}
import org.dsl.emperical.table.TypedColTable
import org.dsl.mining.PMiners.minimize
import org.dsl.mining.PredSpace.{PredSpace, logger}
import org.dsl.mining.REEMiner.{EvidencePairList, getPredecessors, minimizeREEStat, toRhsMap}
import org.dsl.mining.REESample.REESample
import org.dsl.pb.ProgressBar
import org.dsl.reasoning.predicate.HumeType.HString
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate._
import org.dsl.utils.{$, Config, Profile, Wrappers}

import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import scala.collection.compat._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}
//import scala.collection.parallel.CollectionConverters._
import scala.util.Random

@deprecated
class REEMiner(private val tables: Iterable[TypedColTable])
              (allSpace: IndexedSeq[Expression],
               lhsSpace: IndexedSeq[Expression], rhsSpace: IndexedSeq[Expression], p2i: PredicateIndexProvider)
              (eviSet: EvidencePairList) {


  def getP2I: PredicateIndexProvider = {
    logger.info(s"p2i size=${p2i.size}")
    p2i
  }


  private lazy val _allOnePredSet = PredicateSet.from(lhsSpace)(p2i)
  val size: Int = tables.head.size

  private val eviFullSize = eviSet.map(_._2).sum

  private def hasNeighbor[T](K: Int, sampled: mutable.Map[PredicateSet, T],
                             in: PredicateSet): Option[(PredicateSet, T)] = {

    for ((sample, intr) <- sampled) {
      if (sample.size == in.size && sample.dist(in) <= K) {
        return Some(sample, intr)
      }
    }

    None
  }

  private def hasNeighbor1[T](K: Int, sampled: mutable.Map[PredicateSet, T],
                              in: PredicateSet): Option[(PredicateSet, T)] = {


    for {(p, t) <- sampled} {
      if ((p & in) == in) return Some(p, t)
    }

    None
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


  private def sampleLevel(levelp: mutable.Map[State, Unit])
  : mutable.Map[PredicateSet, Interval[Double]] = {
    sampleQueue(levelp.map(p => (p._1.selected, p._1.conf)).toMap)
  }

  def addNeighborOrNewSampleCDF(K: Int, sampled: TrieMap[PredicateSet, TDigest],
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


  def sampleQueueCDF(queue: Map[PredicateSet, Double]): mutable.Map[PredicateSet, TDigest] = {
    val K = Config.SAMPLE_DIST
    val res = TrieMap.empty[PredicateSet, TDigest]

    for ((p, conf) <- queue) {
      addNeighborOrNewSampleCDF(K, res, (p, conf))
    }

    res
  }

  private def sampleLevelCDF(levelp: mutable.Map[State, Unit])
  : mutable.Map[PredicateSet, TDigest] = {
    sampleQueueCDF(levelp.map(p => (p._1.selected, p._1.conf)).toMap)
  }

  def sampleLevelByPredecessor(level: mutable.Map[State, Unit], p2i: PredicateIndexProvider)
  = {
    val K = Config.SAMPLE_DIST
    val res = mutable.Map.empty[PredicateSet, TDigest]
    for ((p, conf) <- level.map(s => (s._1.selected, s._1.conf))) {
      addPredecessorOrNewSample(K, res, (p, conf), p2i)
    }

    res
  }


  private def addPredecessorOrNewSample(K: Int, sampled: mutable.Map[PredicateSet, TDigest],
                                        in: (PredicateSet, Double), p2i: PredicateIndexProvider)
  = {

    val (pred, conf) = in
    // val predecessors: Iterable[PredicateSet] = getPredecessors(K, pred)
    hasPredecessor(K, sampled, pred, p2i) match {
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

  def hasPredecessor(K: Int, sampled: mutable.Map[PredicateSet, TDigest],
                     pred: PredicateSet, p2i: PredicateIndexProvider): Option[(PredicateSet, TDigest)] = {

    //val predecessors = getPredecessors(K, pred)
    val neighbours = getPredecessors(K, pred)
      .view.flatMap(p => {
      val rest = _allOnePredSet(p2i) ^ p
      rest.map(r => p :+ r)
    })

    for (neighbour <- neighbours) {
      sampled.get(neighbour) match {
        case Some(t) => return Some(neighbour -> t)
        case None => ()
      }
    }

    None
  }


  private def sampleQueue(pruned: Iterable[(PredicateSet, Double)])
  : mutable.Map[PredicateSet, Interval[Double]] = {
    val K = Config.SAMPLE_DIST
    val res = TrieMap.empty[PredicateSet, Interval[Double]]

    val parallelNum = 4 * Config.nproc

    val workList = pruned.grouped(parallelNum)
    // filter NaNs

    for (chunk <- workList) {
      for ((p, conf) <- chunk) addNeighborOrNewSample(K, res, (p, conf))
    }

    res
  }


  private case class SampleResult[T](m: mutable.Map[PredicateSet, T])


  private def minimizePruned[T](pruned: mutable.Map[PredicateSet, T]): Seq[(PredicateSet, T)] = {
    def isMinimalPruned[S](work: (PredicateSet, S), part: mutable.Iterable[(PredicateSet, S)]): Boolean = {
      for (p <- part) {
        if (p._1.isSubsetOf(work._1)) {
          return false
        }
      }
      true
    }

    val workList = pruned.toIndexedSeq.sortBy(_._1.size)
    val res = mutable.ArrayBuffer.empty[(PredicateSet, T)]
    for (work <- workList) {
      if (isMinimalPruned(work, res)) {
        res += work
      }
    }

    res.toIndexedSeq
  }


  private def sampleSampled(sampled: mutable.Map[PredicateSet, Interval[Double]]) = {
    val K = Config.SAMPLE_DIST
    val res = mutable.Map.empty[PredicateSet, Interval[Double]]

    val workList = sampled
    // filter NaNsf

    for ((p, intr) <- workList) {
      addNeighborOrNewSample1(K, res, (p, intr))
    }

    res
  }

  private def wellDefCDF(td: TDigest, num: Double): Double = {
    val cdfDirect = td.cdf(num)
    if (cdfDirect > 0) cdfDirect else 0
  }


  private def expandParWithState111(tableSampled: TypedColTable, pRHS: Expression,
                                    supp_threshold: Double, conf_threshold: Double, p2i: PredicateIndexProvider)
                                   (startUpState: Iterable[State])(eviList: EvidencePairList)
                                   (implicit bfsops: BFSOps[LevelArg[Stat], ResArg[Stat], CollectionArg[Stat]])
  : (ExpandResult[Stat], SampleResult[TDigest]) = {


    // make evidence smaller
    val eviSetForRHS = mutable.IndexedSeq.from(eviList.filter(p => p._1.contains(pRHS)))

    // X -> p0
    // invariant: lattice-based levelwise search
    val result = TrieMap.empty[PredicateSet, Stat]
    var pruned = TrieMap.empty[PredicateSet, Stat]
    // todo: extract abstract idx

    val sampled = mutable.Map.empty[PredicateSet, TDigest]


    val (levelGroupedStates, _) = Wrappers.timerWrapperRet(startUpState.
      groupBy(s => s.selected.size))


    val lowest = levelGroupedStates.keySet.min

    // for algorithm termination

    val level0: mutable.Map[State, Unit] =
      TrieMap(levelGroupedStates.getOrElse(lowest, Set[State]()).map(_ -> ()).toArray: _*)
    var levelp = level0

    var timeSampleAll = 0d

    /**
     * Profiling
     */
    var visitedNodeNum = 0L


    /**
     * evidence set filter
     */

    val chunkNumForLevel = Config.BFS_LEVEL_CHUNK_SIZE * Config.nproc
    //    val fullEviSize = eviList.map(_._2).sum

    for (levelN <- lowest to Config.levelUpperBound) {
      val (_, time) = Wrappers.timerWrapperRet({
        /**
         * fixme: duplicate key here
         *
         *
         */

        val ((newLevel, visitedLevel), time) = Wrappers.timerWrapperRet {
          val fill: Array[State] = levelGroupedStates.getOrElse(levelN + 1, Set[State]()).toArray

          val newLevel: mutable.Map[State, Unit] = TrieMap(fill.map(_ -> ()).toArray: _*)
          val visitedLevel: mutable.Map[PredicateSet, Unit] = TrieMap(fill.map(_.selected -> ()): _*)
          (newLevel, visitedLevel)
        }


        //logger.profile(s"level $levelN//PREPROCESSING: $time")
        //logger.profile(s"BEFORE $levelN RHS=$pRHS:[supp=$supp_threshold]\n levelP SIZE: ${newLevel.size} \n ")

        val chunksListForLevelp = levelp.grouped(chunkNumForLevel).toIndexedSeq

        val resultLevel = TrieMap.empty[PredicateSet, Stat]
        val prunedLevel = TrieMap.empty[PredicateSet, Stat]


        /**
         * profiling
         */

        val visitedNumLevel = new AtomicLong(0L)

        /**
         * evidence set filter profiling
         */

        //        val filteredEviSetSizes = new ConcurrentLinkedDeque[Int]()

        val newLevelCArg = CollectionArg.withQueue[Stat](newLevel)

        val supp_threshold_cnt = (eviFullSize * supp_threshold).toLong
        // open in parallel
        // in-level processing

        logger.debug(s"Start Mining $pRHS; $levelN")
        val ccAll = (for (chunk <- chunksListForLevelp) yield {
          //          val ccChunk = chunk.keys.map {
          val ccChunk = chunk.keys.par.map {
            case s@State(selectedPred, restPred, supp, conf, level) =>

              logger.debug(s"[DEBUG],$pRHS, ${s.supp > supp_threshold_cnt}, ${s.conf > conf_threshold}")
              //              val eviList = eviList //.filter(p => selectedPred.isSubsetOf(p._1))
              //              val eviSetForRHS = eviSetForRHS //.filter(p => selectedPred.isSubsetOf(p._1))

              // prune nodes that supp is too small
              if (supp >= supp_threshold_cnt && conf >= conf_threshold) {

                val resArg = ResArg(None, Some(selectedPred, Stat(supp, conf)), None)
                BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)

              } else if (supp < supp_threshold_cnt) {

                val resArg = ResArg(None, None, Some(selectedPred, Stat(supp, conf)))
                BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)
              } else if (levelN == Config.levelUpperBound) { // supp > threshold & conf <

                if (conf >= Config.MIN_CONF) {
                  val resArg = ResArg(None, None, Some(selectedPred, Stat(supp, conf)))
                  BatchStatMiningOpsStatic.merge(CollectionArg.empty[Stat], resArg)
                } else {
                  CollectionArg.empty[Stat]
                }

              } else {

                (for {
                  p <- restPred
                  if p != pRHS
                  newSelected = selectedPred :+ p
                  newRest = restPred :- p
                  newREE = REE(newSelected, pRHS)
                  newSupp = getSuppCnt(newREE, eviSetForRHS)
                  newConf = getConfFromSupp(newREE, eviList, newSupp)

                  newS = State(newSelected, newRest, newSupp, newConf, level + 1)

                  l = LevelArg(oldS = s, newS = newS,
                    visited = visitedLevel,
                    eviSet = eviList, fullEviSize = eviFullSize,
                    result = result,
                    supp_threshold = supp_threshold,
                    conf_threshold = conf_threshold)

                  resArg = BatchStatMiningOpsStatic.process(l)
                } yield {

                  /**
                   * Profiling
                   */
                  if (Config.enable_profiling) {
                    visitedNumLevel.getAndIncrement()
                  }

                  resArg
                }).foldLeft(CollectionArg.empty[Stat])((coll, resArg) => BatchStatMiningOpsStatic.merge(coll, resArg))
              }
          }.foldLeft(CollectionArg.empty[Stat])(BatchStatMiningOpsStatic.mergeAll)

          ccChunk
        }).foldLeft(newLevelCArg)(BatchStatMiningOpsStatic.mergeAll)


        //val sum = filteredEviSetSizes.stream().reduce((a, b) => a + b).orElse(0)
        //logger.profile(s"Average Evidence Set Size: ${if (levelp.nonEmpty) sum / levelp.size else levelp.size}")

        /**
         * Profiling
         */
        if (Config.enable_profiling) {
          //logger.profile(s"[pRHS=$pRHS,Level $levelN] Evaluated Node Number: ${visitedNumLevel.get()}")
          visitedNodeNum += visitedNumLevel.get()
        }

        logger.debug(s"Start Sampling RHS=${pRHS}; LEVEL=${levelN}...")


        bfsops match {
          case _ if levelN <= Config.levelUpperBound =>


            // sample
            //logger.profile(s"Sampling Level:${levelN}...")

            val (_, timeSample) = Wrappers.timerWrapperRet {
              //val s = sampleLevelCDF(levelp.filter(s => s._1.conf < conf_threshold))
              val s = sampleLevelByPredecessor(levelp.filter(s => s._1.conf < conf_threshold), p2i)
              sampled ++= s
            }

            //logger.profile(s"Sampling Time $timeSample")
            timeSampleAll += timeSample
          case _ => ()
        }


        val min = REEMiner.minimize(ccAll.resultLevel.toMap)
        val eliminated = elimTrivial(min, pRHS)
        result ++= eliminated
        pruned = TrieMap.empty ++= minimize(pruned ++ prunedLevel)

        //logger.profile(s"AFTER:$levelN[supp=${supp_threshold}] newLevel SIZE: ${newLevel.size}\n")
        levelp = ccAll.nextLevel
      })
    }

    logger.profile(s"rhs:$pRHS, $visitedNodeNum Nodes Evaluated.")
    logger.profile(s"time sampling total for $pRHS: $timeSampleAll")

    val minPruned = mutable.Map.from(minimizePruned(pruned))

    // profiling
    Profile.visitedNodeNumTotal += visitedNodeNum


    //val (sp,timeSamplePrune) = Wrappers.timerWrapperRet(samplePruned(pruned.toIndexedSeq))
    //timeSampleAll += timeSamplePrune

    (ExpandResult(result, minPruned, timeSampleAll), SampleResult(sampled))
  }


  // todo: par BFS
  private def expandPar(tableSampled: TypedColTable, pRHS: Expression,
                        supp_threshold: Double, conf_threshold: Double, p2i: PredicateIndexProvider)(eviSet: EvidencePairList)
                       (implicit bfsOps: BFSOps[LevelArg[Stat], ResArg[Stat], CollectionArg[Stat]])
  : (ExpandResult[Stat], SampleResult[TDigest]) = {


    // X -> p0
    val startUpState = IndexedSeq(emptyState(pRHS)(eviSet))
    expandParWithState111(tableSampled, pRHS, supp_threshold, conf_threshold, p2i: PredicateIndexProvider)(startUpState)(eviSet)
  }

  private def elimTrivial[T](tResult: Iterable[(PredicateSet, T)], rhs: Expression): Iterable[(PredicateSet, T)] = {
    for ((predSet, conf) <- tResult if !predSet.contains(rhs)) yield predSet -> conf
  }


  private def stopState(rhs: Expression)(eviSet: EvidencePairList): State = {


    val predSelectedInit = _allOnePredSet(p2i)
    val predRestInit = PredicateSet.empty(p2i)

    val supp = getSuppCnt(REE(predSelectedInit, rhs), eviSet)
    val conf = getConf(REE(predSelectedInit, rhs), eviSet)
    val startUpState = State(predSelectedInit, predRestInit, supp = supp, conf = conf, 0)
    startUpState
  }

  private def emptyState(rhs: Expression)(eviSet: EvidencePairList): State = {
    val predRestInit = _allOnePredSet(p2i)
    val predSelectedInit = PredicateSet.empty(p2i)

    val supp = getSuppCnt(REE(predSelectedInit, rhs), eviSet)
    val conf = getConf(REE(predSelectedInit, rhs), eviSet)
    val startUpState = State(predSelectedInit, predRestInit, supp = supp, conf = conf, 0)
    startUpState
  }

  private def emptyRes[A, B, C] = IndexedSeq.empty[(Iterable[(A, B)], C)]


  @deprecated
  @tailrec
  private def findNeighbors(samples: Iterable[REESample], newSelected: PredicateSet, newConf: Double)
                           (K: Int)(updateIntervalFun: (REESample, Double) => REESample): Iterable[REESample] = {

    val isNeighbour = (a: PredicateSet, b: PredicateSet) => a.size == b.size && (a dist b) <= K

    samples match {
      case Nil => Iterable(Sampling(newSelected, Interval(newConf, newConf)))
      case car :: cdr =>
        if (isNeighbour(car.head, newSelected)) {

          updateIntervalFun(car, newConf) +: cdr
        }
        else {
          findNeighbors(cdr, newSelected, newConf)(K)(updateIntervalFun)
        }
    }

  }

  def getDataset: TypedColTable = tables.head

  def batchMine(supp_threshold: Double, conf_threshold: Double): MineResultWithSTime = {
    // TODO: cross table
    // TODO: sampling
    ???
//    val tableSampled = tables.head
//
//
//    var result = emptyRes[PredicateSet, Stat, Expression]
//    var pruned = emptyRes[PredicateSet, Stat, Expression]
//    var sampled = emptyRes[PredicateSet, TDigest, Expression]
//
//    val pb = new ProgressBar(rhsSpace.size)
//    logger.info(s"Search REE From ${lhsSpace.size} X ${rhsSpace.size} space with supp > $supp_threshold conf > $conf_threshold")
//
//    var timeSampleAll = 0d
//
//    var timeSearch = 0d
//    for (pRHS <- rhsSpace) {
//
//      //    val pRHS = rhsSpace(7)
//      Wrappers.progressBarWrapper({
//        // rule set
//        // todo: par BFS
//        implicit val batchMineOps: BatchStatMiningOps = new BatchStatMiningOps()
//        val (p, time) = Wrappers.timerWrapperRet(expandPar(tableSampled, pRHS,
//          supp_threshold, conf_threshold, p2i)(eviSet))
//
//        p match {
//          case (expandResult, s) =>
//
//            timeSearch += time
//            val sampledInner = s.m.toMap
//            val minimal = REEMiner.minimize(expandResult.resultX.toMap)
//            val elimited = elimTrivial(minimal, pRHS)
//            //logger.debug(s"expand DONE. $elimited with size=${elimited.size}")
//            result = result :+ (elimited, pRHS)
//            pruned = pruned :+ (expandResult.prunedX.toMap, pRHS)
//            sampled = sampled :+ (sampledInner, pRHS)
//            timeSampleAll += p._1.timeSample
//        }
//
//      }, pb)
//
//
//    }
//
//    Profile.flushVisitedNodesNum
//
//
//    val resultREE = result.flatMap(mixed => mixed._1.map(x => REEWithStat(REE(x._1, mixed._2), supp = x._2.supp, conf = x._2.conf)))
//    val prunedREE = pruned.flatMap(mixed => mixed._1.map(x => REEWithStat(REE(x._1, mixed._2), supp = x._2.supp, conf = x._2.conf)))
//    val sampledREE = sampled.flatMap(mixed => mixed._1.map(x => REEWithT[TDigest](REE(x._1, mixed._2), t = x._2)))
//
//    val mineRes = MineResult(resultREE, prunedREE, sampledREE)
//
//
//    MineResultWithSTime(mineRes, timeSampleAll)
  }


  private def buildCausalityTable(recoverArg: ConstRecovArg) = {
    // 1. filter typed tuple binarys with tuple (t,s) coltable D
    // 1.1 based on typed tuple binarys, we recover P_c satisfies (t, s)
    // 2. resume mining

    recoverArg match {
      case ConstRecovArg(typedColTable, mineResult, _, _, p2i) =>

        val typedTupleBins = p2i.getObjects.filter(_.isInstanceOf[TypedTupleBin])

        val wildCards = p2i.getObjects.filter(_.isInstanceOf[TypedConstantBin])

        // build causality table

        def merge(a: CausalityTable, b: CausalityTable): CausalityTable = {
          (a.keySet ++ b.keySet).foldLeft(Map.empty[Expression, Set[Expression]]) { (acc, key) =>
            acc.updated(key, a.getOrElse(key, Set.empty) ++ b.getOrElse(key, Set.empty))
          }
        }

        val causalityTable: CausalityTable
        = typedTupleBins.par.flatMap {
          case pred@TypedTupleBin(op, _, _, _, _) =>
            op match {
              case Eq =>
                val idxs = pred.eval(typedColTable)
                logger.info(s"[AAA] PRED:$pred -> TUPLES:${idxs.size}")
                // todo: filter constant predicates
                // fixme: bug
                val causalityPairs = wildCards.flatMap {
                  case tem@TypedConstantBin(_, _, col, _) =>
                    val idxValVec = typedColTable.getColumnIdxValVector(col)
                    val predPairs: Set[(Expression, Option[Expression])] = if (idxs.isEmpty) {
                      Set.empty[(Expression, Option[Expression])] + (pred -> None)
                    } else {
                      val s: Set[(Expression, Option[Expression])] = idxs.map(i => idxValVec(i)).toSet
                        .flatMap {
                          e: Int =>
                            val constAtom = ConstantAtom(typedColTable.getConst[String](HString, e))
                            val constPred = tem.withConst(constAtom)
                            Set.empty[(Expression, Option[Expression])] ++ List(constPred -> Some(pred), pred -> Some(constPred))
                        }
                      s
                    }
                    predPairs
                }

                val causalityTablePartial: CausalityTable = causalityPairs.groupBy(_._1).mapValues(e=>e.flatMap(_._2).toSet)
                Some(causalityTablePartial)
              case _ => // println(s"Skip:$pred");
                Some(Map.empty[Expression, Set[Expression]] + (pred -> Set.empty[Expression]))

            }
        }.reduce(merge)


        // output all preds
        causalityTable
    }
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
        Vector(path)
      } else {
        tempXs.flatMap {
          case t: TypedTupleBin =>
            f(i + 1, path :+ t)
          case c@TypedConstantBin(op0, _, col0, const0) =>
            val valids = cps.filter(e => e.op == op0 && e.col == col0)
            if (valids.isEmpty) ??? else valids.flatMap(v => f(i + 1, path :+ v))
        }
      }
    }

    f(0, Vector())


  }


  private def recoverConstantCkpt(results: Iterable[REEWithStat], p2iNew: PredicateIndexProvider,
                                  causalityTable: Map[Expression, Iterable[Expression]]) = {
    // 1. mine result constant replacement
    // 2. constant pred as rhs

    val constPredsConcre = causalityTable.keySet

    val ckptConcre =
      results.flatMap {
        case REEWithStat(ree, _, _) =>
          ree match {
            case REE(x, p0) =>
              p0 match {
                case t: TypedTupleBin =>
                  // fill up with
                  val xsNew = subst(x.toIndexedSeq, constPredsConcre)
                    .map(e => PredicateSet.from(e)(p2iNew))
                  val reesNew = xsNew.map(xNew => REE(xNew, t))
                  REEMiner.minimizeREE(reesNew)

                case TypedConstantBin(_, _, col, _) =>
                  val r = constPredsConcre.filter {
                    case TypedConstantBin(_, _, col1, _) => col1 == col
                    case _ => false
                  }

                  r.map(p0 => REE(x.withP2I(p2iNew), p0))
                case _ => ???
              }

          }

      }

    logger.info("Filling Up Constants Done.")
    ckptConcre

  }


  type CausalityTable = Map[Expression, Set[Expression]]

  private def getFullPredSpaceWithConstFilterRate(causalityTable: CausalityTable) =causalityTable.keySet

  def recoverConstants(recoverArg: ConstRecovArg): MineResult = {
    ???
//    recoverArg match {
//      case recoverArg@ConstRecovArg(typedColTable, mineResult, supp_threshold, conf_threshold, p2i) =>
//        val data = recoverArg.typedColTable
//        logger.info("Fill Template Predicates with Concrete Ones.")
//        val causalityTable = buildCausalityTable(recoverArg)
//        logger.debug(s"CAUSALITY TABLE:${causalityTable.keySet.mkString("\n")}")
//        logger.info("Fill Template Predicates with Concrete Ones Done.")
//
//
//        val eviName = Config.EVIDENCE_IDX_NAME(data.getName + s"_concrete_constfilter=${Config.CONSTANT_FILTER_RATE}")
//        val predsConcrete = getFullPredSpaceWithConstFilterRate(causalityTable)
//        val predSpaceCon = PredSpace.from(predsConcrete)
//        val p2iConcrete = PredSpace.getP2I(predsConcrete)
//
//        logger.debug("p2iNew Mapping:\n", p2iConcrete.getMapping.mkString("\n"))
//
//        // building concrete evidence
//        val evi = $.GetJsonIndexOrElseBuild(eviName, p2iConcrete, $.defaultEvidenceBuild(data, predSpaceCon, p2iConcrete))
//        val eviList = evi.toIndexedSeq
//
//
//        // recover LHS constants
//        // attach ree with new P2I
//        logger.info("Filling Up Constants to Generate Ckpt...")
//        val ckpt = recoverConstantCkpt(mineResult.result, p2iConcrete, causalityTable)
//
//        implicit val bfsops: BatchStatMiningOps = new BatchStatMiningOps
//
//        val pb = new ProgressBar(ckpt.map(_.p0).toSet.size)
//
//
//        var validSupp = 0
//        var validConf = 0
//
//        val supp_threshold_cnt = eviFullSize * supp_threshold
//
//        val reeResults = ckpt.groupBy(_.p0).flatMap {
//          case (p0, rees) =>
//            logger.info(s"Expand RHS: $p0")
//            logger.info(s"$p0 -> ${PredicateSet.from(causalityTable.getOrElse(p0, PredicateSet.empty(p2iConcrete)))(p2iConcrete)}")
//
//            val startUpState = rees.map(ree => {
//              val state = toStateWithCausality(ree, p2iConcrete, causalityTable)(eviList)
//              if (state.supp > supp_threshold_cnt && p0.isInstanceOf[TypedTupleBin]) validSupp += 1
//              if (state.conf > conf_threshold && p0.isInstanceOf[TypedTupleBin]) validConf += 1
//              state
//            })
//
//
//            val (expandResult, _) =
//              expandParWithState111(typedColTable,
//                  p0, supp_threshold, conf_threshold,
//                  p2iConcrete)(startUpState)(eviList)
//
//            pb += 1
//            expandResult.resultX.map {
//              case (x, stat) => REEWithStat(REE(x, p0), stat.supp, stat.conf)
//            }
//        }
//
//        val min = REEMiner.minimizeREEStat(reeResults)
//
//        MineResult(min, Iterable.empty[REEWithStat], Iterable.empty[REEWithT[TDigest]])
//    }

  }

  def _allOnePredSet(p2i: PredicateIndexProvider): PredicateSet = {
    PredicateSet.from(p2i.getObjects)(p2i)
  }

  private def toState(reesp: REE, p2i: PredicateIndexProvider)(eviSet: EvidencePairList): State = {
    val x = reesp.X

    val rest = _allOnePredSet(p2i) ^ x
    val supp = getSuppCnt(reesp, eviSet)
    val conf = getConf(reesp, eviSet)
    val level = x.size
    State(x, rest, supp = supp, conf = conf, level)
  }


  private def toStateWithCausality(reesp: REE, p2iCon: PredicateIndexProvider, causalityTable: CausalityTable)(eviSet: EvidencePairList): State = {
    reesp match {
      case REE(x, p0) =>

        val relatedPredicateSet = PredicateSet.from(causalityTable.getOrElse(p0, PredicateSet.empty(p2iCon)))(p2iCon)
        val rest = (relatedPredicateSet | x) ^ x
        val supp = getSuppCnt(reesp, eviSet)
        val conf = getConf(reesp, eviSet)
        val level = x.size
        State(x, rest, supp = supp, conf = conf, level)
    }
  }


  private def toState(reeStat: REEWithStat)(eviSet: EvidencePairList): State = {
    reeStat match {
      case REEWithStat(ree, supp, conf) =>
        val x = ree.X
        val rest = _allOnePredSet ^ x
        val supp = getSuppCnt(ree, eviSet)

        val level = x.size
        State(x, rest, supp = supp, conf = conf, level)
    }


  }


  private def incMineCont(in: Iterable[REEWithStat], supp_threshold: Double, conf_threshold: Double): MineResult = {
???
//    implicit val incMiningOps: BatchStatMiningOps = new BatchStatMiningOps
//    // todo: debug
//
//    // trivially mine
//    val tableSampled = tables.head
//
//    val pb = new ProgressBar(rhsSpace.size)
//
//    val (stateRHSMapFilled, time) = Wrappers.timerWrapperRet {
//      val groupedRees = in.groupBy(_.ree.p0)
//
//      logger.info(s"Search REE From ${lhsSpace.size} X ${rhsSpace.size} space with new supp > $supp_threshold")
//
//      val stateRHSMap = groupedRees.map {
//        case (rhs, rees) =>
//          rhs -> rees.view.map(reeWithStat => toState(reeWithStat.ree, p2i)(eviSet)).to(Set)
//      }
//
//      // stop mine if there is no pruned
//      val stateRHSMapFilled = for {rhs <- rhsSpace} yield
//        rhs -> stateRHSMap.getOrElse(rhs, Set(stopState(rhs)(eviSet)))
//
//      stateRHSMapFilled
//    }
//
//    logger.info(s"PREPROCESSING TIME: $time")
//
//    var result = emptyRes[PredicateSet, Stat, Expression]
//    var pruned1 = emptyRes[PredicateSet, Stat, Expression]
//    var sampled = emptyRes[PredicateSet, TDigest, Expression]
//
//    var timeSearch = 0d
//
//    for ((rhs, states) <- stateRHSMapFilled) {
//      //    val (rhs,states) = stateRHSMapFilled(7)
//      Wrappers.progressBarWrapperRet({
//        val eviSetFiltered = HPEvidenceSet.from(eviSet.filter(p => p._1.contains(rhs)))
//
//        val (p, time) = Wrappers.timerWrapperRet {
//          expandParWithState111(tableSampled, rhs,
//            supp_threshold, conf_threshold, p2i)(states)(eviSet)
//        }
//
//
//        p match {
//          case (expandResult, sampledResult) =>
//            timeSearch += time
//            val minimal = REEMiner.minimize(expandResult.resultX.toMap)
//            val elimited = elimTrivial(minimal, rhs)
//
//            //val elimited = expandResult.resultX.toMap
//            //logger.debug(s"expand DONE. $elimited with size=${elimited.size}")
//
//            result = result :+ (elimited, rhs)
//            pruned1 = pruned1 :+ (expandResult.prunedX.toMap, rhs)
//            sampled = sampled :+ (sampledResult.m.toMap, rhs)
//
//          // todo: observe
//          //assert(expandResult.resultX.size == minimalize(expandResult.resultX.toMap).size)
//          case _ => ???
//        }
//
//
//      }, pb)
//    }
//
//    Profile.flushVisitedNodesNum
//
//
//    logger.info(s"SEARCH PART: time=$timeSearch")
//
//
//    val resultREE = result.flatMap(mixed => mixed._1.map(x => REEWithStat(REE(x._1, mixed._2), supp = x._2.supp, conf = x._2.conf)))
//    val prunedREE = pruned1.flatMap(mixed => mixed._1.map(x => REEWithStat(REE(x._1, mixed._2), supp = x._2.supp, conf = x._2.conf)))
//    val sampledREE = sampled.flatMap(mixed => mixed._1.map(x => REEWithT[TDigest](REE(x._1, mixed._2), t = x._2)))
//
//    MineResult(resultREE, prunedREE, sampledREE)
  }

  private def getSuccessorsWithFilter(sample: PredicateSet, K: Int,
                                      rhs: Expression,
                                      newSupp: Double, newConf: Double)
  : Map[PredicateSet, Double] = {

    val new_supp_cnt = newSupp * eviFullSize

    // todo: covered
    //    val neighbours = getNeighbors(sample, 1, rhs, newSupp, newConf)
    val rest = _allOnePredSet ^ sample
    val out = for {
      //      s <- neighbours
      p <- rest
      succ = sample :+ p
      ree = REE(succ, rhs)
      supp = getSuppCnt(ree, eviSet)
      conf = getConfFromSupp(ree, eviSet, supp)
      if supp >= new_supp_cnt && conf >= newConf && p != rhs
    } yield succ -> conf

    out.toMap
  }

  private def getNeighborsWithFilter(predSample: PredicateSet, K: Int,
                                     rhs: Expression, newSupp: Double, newConf: Double)

  : (Map[PredicateSet, Double], Int) = {
    var overlaps = 0

    val eviSet1 = eviSet.filter(p => p._1.contains(rhs))
    val new_supp_cnt = newSupp * eviFullSize

    def inner(current: PredicateSet, base: PredicateSet,
              restK: Int, rhs: Expression,
              res: mutable.Map[PredicateSet, Double]): Unit = {


      if (restK > 0) {
        for {newP <- lhsSpace;

             p = base.head
             if newP != p && newP != rhs && !current.contains(newP)
             //if newP != p && !current.contains(newP)

             newPredSet = (current :- p) :+ newP
             //if ! res.contains(newPredSet)
             supp = getSuppCnt(REE(newPredSet, rhs), eviSet1)
             conf = getConfFromSupp(REE(newPredSet, rhs), eviSet, supp)
             if conf >= newConf && supp >= new_supp_cnt} {

          res.getOrElseUpdate(newPredSet, {
            overlaps += 1;
            conf
          })

          inner(newPredSet, base :- p, restK - 1, rhs, res)
        }

      }

    }

    val res = mutable.Map.empty[PredicateSet, Double]


    for (k <- 1 to K) {
      val bases = predSample.subsets(k)
      // side effect: res ++ [p..]
      if (predSample.size > 0) {
        for (b <- bases) {
          inner(predSample, b, k, rhs, res)

        }
      } else {
        val sample = predSample
        val supp = getSuppCnt(REE(sample, rhs), eviSet1)
        val conf = getConfFromSupp(REE(sample, rhs), eviSet, supp)
        if (conf >= newConf && supp >= newSupp) {
          res.+=((sample, conf))
        }

      }


    }

    (res.toMap, overlaps)
  }


  def getNeighbourUnitTest(predSample: PredicateSet, K: Int, rhs: Expression, newSupp: Double, newConf: Double): (Map[PredicateSet, Double], Int) = {
    val (t, ovrlap) = getNeighborsWithFilter(predSample: PredicateSet, K: Int, rhs: Expression, newSupp, newConf: Double)

    (t, ovrlap)
  }


  def getEvidenceSet = eviSet

  def getNeighbourSize(level: Int, strSize: Int) = {
    assert(level <= strSize)
    val K = Config.SAMPLE_DIST

    logger.info(s"level:${level},strSize:${strSize}")

    val t = if (level == 0) BigInt(0) else $.binom(level, K)
    (t * $.binom(strSize - level, K)).toLong
    // 11100 -> bi(3,)
  }

  //  private def eliminateBack(rhs: Expression, xs:Iterable[(PredicateSet, Double)]) = {
  //    // descending order
  //    val sortedXs = xs.toIndexedSeq.sortBy(- _._1.size)
  //    val sortedXs
  //    for((x,conf) <- sortedXs) {
  //      for{
  //        p <- x
  //        smaller = x :- p
  //        if get
  //      }
  //    }
  //  }

  private def expandSamplesCDF(rhs: Expression,
                               levelwiseSamples: Map[Int, Iterable[(PredicateSet, TDigest)]],
                               result: Iterable[REEWithStat],
                               newSupp: Double, newConf: Double, recall: Double): Map[PredicateSet, Double] = {
    val K = Config.SAMPLE_DIST
    //val sampleResSet = TrieMap.empty[PredicateSet, Double]

    val levelwiseSamples1 = levelwiseSamples.filter(_._1 <= Config.levelUpperBound)
    val supp_threshold_cnt = (newSupp * eviFullSize).toLong

    // ================================== profiling ==================================
    var (falsePositive, falseNegative) = (0L, 0L)
    var sampleCleanedN = 0L
    var discarded = 0L
    var fullEval = 0L
    var evalSize = 0L
    var overlapTotal = 0L
    // ================================== profiling ==================================

    //    val N = levelwiseSamples1.values.flatten.map(s => {
    //      val cdf = wellDefCDF(s._2, newConf)
    //      val r = (1 - cdf) * s._2.size()
    //      r
    //    }).sum
    //    val max_mined = (N * recall).toLong
    //    //        val FN = new AtomicLong(0L)
    //    var possible_mined = 0L
    //
    //    logger.debug(
    //      s"""
    //        |N=${N}
    //        |max=${max_mined}
    //        |recall=${recall}
    //        |""".stripMargin)

    val sampleResSet = levelwiseSamples1.toIndexedSeq.flatMap {
      case (l, samples) =>

        val N = samples.map(s => {
          val n = s._2.size()
          val cdf = wellDefCDF(s._2, newConf)
          val r = (1 - cdf) * n
          r
        }).sum
        val max_mined = (N * recall).toLong
        //        val FN = new AtomicLong(0L)
        var possible_mined = 0L

        logger.debug(s"[INC] expanding by CONF. I am in Level $l")
        val singleWolfSamples = samples.filter(_._2.size() == 1).toIndexedSeq
        val cleanedSample = samples.filter(p => newConf <= p._2.getMax && p._2.size() > 1).toIndexedSeq

        sampleCleanedN += cleanedSample.size


        val valid_ree_size_order = (p: (PredicateSet, TDigest)) => {
          p match {
            case (_, tdigest) =>
              val cdf = wellDefCDF(tdigest, newConf)
              (1 - cdf) * tdigest.size()
          }
        }


        val (tobeEval, time) = Wrappers.timerWrapperRet {
          if (recall == 1) {
            cleanedSample
          } else {
            val tobeEval = mutable.Map.empty[PredicateSet, TDigest]
            breakable {

              cleanedSample.sortBy(p => -valid_ree_size_order(p)).foreach {
                //              cleanedSample.foreach {
                case p@(_, tdigest) =>
                  val expandN = p._1.size * (lhsSpace.size - p._1.size)
                  val n = ((1 - wellDefCDF(tdigest, newConf)) * expandN).toLong
                  possible_mined += n
                  if (possible_mined > max_mined) {
                    break
                  } else {
                    tobeEval += p
                  }
              }
            }
            tobeEval
          }


        }


        logger.debug(
          s"""
             |original: ${samples.size}
             |cleanedSample: ${cleanedSample.size}
             |tobeEval: ${tobeEval.size}
             |""".stripMargin)


        discarded += (cleanedSample.size - tobeEval.size)


        //        val resLevel = TrieMap.empty[PredicateSet, Double]

        val resLevel = tobeEval.par.map {
          //        cleanedSample.par.foreach {

          case (predSample, tdigest) =>
            //logger.info(s"fpNew ${fpNew}, npNew ${npNew}, prob: ${prob}")

            evalSize += 1

            // $$$
            //            val (t, overlap) = getNeighborsWithFilter(predSample, K, rhs, newSupp, newConf)
            val precs = getPredecessors(K, predSample)
            val t = precs.flatMap(p => getSuccessorsWithFilter(p, K, rhs, newSupp, newConf)).toMap
            //            overlapTotal += overlap

            fullEval += 1

            val ree = REE(predSample, rhs)
            val supp = getSuppCnt(ree, eviSet)
            val conf = getConfFromSupp(ree, eviSet, supp)
            if (supp >= supp_threshold_cnt && conf >= newConf) {
              t + (predSample -> conf)
            } else {
              t
            }

        }

        val singles = singleWolfSamples.par.map {
          case (p, td) =>
            val ree = REE(p, rhs)
            val supp = getSuppCnt(ree, eviSet)
            val conf = td.getMax
            p -> Stat(supp, conf)
        }.filter {
          case ((p, stat)) =>
            stat.supp >= supp_threshold_cnt && stat.conf >= newConf

        }.map(p => p._1 -> p._2.conf).toIndexedSeq

        val resLevelOut = resLevel.fold(Map.empty[PredicateSet, Double])(_ ++ _)

        resLevelOut //++ singles
    }


    logger.debug(
      s"""
         |Discarded: $discarded
         |Full Eval: $fullEval
         |discarded percentage: ${(discarded.toDouble / sampleCleanedN) * 100}(%)
         |""".stripMargin)

    val sampleREEResSet: Iterable[(PredicateSet, Double)] = sampleResSet
    val resultTranformed: Iterable[(PredicateSet, Double)] = result.map(rws => (rws.ree.X, rws.conf))
    // minimality
    val min = REEMiner.minimize(sampleREEResSet ++ resultTranformed)
    val q1 = elimTrivial(min, rhs)

    logger.info(s"$rhs\nMINIMIZE BEFORE: ${sampleResSet.size}\n AFTER:${min.size}")

    q1.toMap
  }


  var smallerTime = 0D

  private def restoreSamplesCDF(samples: Iterable[REEWithT[TDigest]],
                                resultFiltered: Iterable[REEWithStat],
                                newSupp: Double, newConf: Double): MineResult = {

    val new_supp_cnt = newSupp * eviFullSize

    def preProcess(samples: Iterable[REEWithT[TDigest]]) = {
      samples.groupBy(_.ree.p0).map { case (rhs, v) => rhs -> v.map(s => s.ree.X -> s.t).groupBy(_._1.size) }
    }


    val levelwiseSamplesMap = preProcess(samples)

    val resultFilteredMap =
      resultFiltered.groupBy(_.ree.p0)
        .map { case (rhs, v) => rhs -> v }

    var resSize = 0L
    val t = for ((rhs, levelwiseSamples) <- levelwiseSamplesMap) yield {

      val resultMapByRHS = resultFilteredMap.getOrElse(rhs, Iterable())

      val result = expandSamplesCDF(rhs, levelwiseSamples,
        resultMapByRHS, newSupp, newConf, Config.recall)


      //      val resultOut = if (supp >= new_supp_cnt && conf >= newConf) {
      //        result + (emptyX -> conf)
      //      } else {
      //        result
      //      }
      val resultOut = result


      resSize += resultOut.size
      rhs -> resultOut
    }


    val t1 = for {rhs <- rhsSpace
                  emptyX = PredicateSet.empty(p2i)
                  zeroREE = REE(emptyX, rhs)
                  supp = getSuppCnt(zeroREE, eviSet)
                  conf = getConfFromSupp(zeroREE, eviSet, supp)
                  if supp >= new_supp_cnt && conf >= newConf
                  }
    yield {
      rhs -> Map(emptyX -> conf)
    }


    val r = t.flatMap {
      case (rhs, xs) =>
        xs.map(x => REEWithStat(REE(x._1, rhs), -1L, x._2))
    }

    val r1 = t1.flatMap {
      case (rhs, xs) =>
        xs.map(x => REEWithStat(REE(x._1, rhs), -1L, x._2))
    }

    val (smallerREEs, findSmallerTime) = Wrappers.timerWrapperRet(findSmallerREE(r ++ r1, newConf))


    smallerTime += findSmallerTime

    //val smallerREEs = r
    logger.info(
      s"""
         |findSmallerTime: ${findSmallerTime}
         |r:${r.size}
         |smaller:${smallerREEs.size}
         |""".stripMargin)
    //val minimizedR = minimizeREE(expandedR)

    MineResult(smallerREEs, Vector(), Vector())
  }


  def incMine(mineResult: MineResult,
              oldSupp: Double, oldConf: Double,
              newSupp: Double, newConf: Double): MineResult = {
    ???

//    val deltaSupp = newSupp - oldSupp
//    val deltaConf = newConf - oldConf
//
//    mineResult match {
//      case MineResult(results, pruneds, samples) =>
//        val (rees, pruned) = (results.map(_.ree), pruneds.map(_.ree))
//
//        if (deltaSupp > 0 && deltaConf > 0) {
//
//          logger.info("SUPP+,CONF+")
//          val (r, time) = Wrappers.timerWrapperRet {
//            val resultp = results.filter {
//              case REEWithStat(p, supp, conf) =>
//                val supp = getSuppRatio(p, eviSet)
//                supp >= newSupp && conf < newConf
//            }
//
//            val resultKeep = results.filter {
//              case REEWithStat(p, supp, conf) =>
//                val supp = getSuppRatio(p, eviSet)
//                supp >= newSupp && conf >= newConf
//            }
//
//            (resultp, resultKeep)
//          }
//
//          val (resultp, resultKeep) = r
//
//          //logger.profile(s"[INC] preProcess time: ${time}")
//
//          // result caused by minimal
//          incMineCont(resultp.toSet, newSupp, newConf)
//          match {
//            case MineResult(result, pruned, samples) =>
//              MineResult(minimizeREEStat(result ++ resultKeep), pruned, samples)
//          }
//
//          // try deeper for confidences is not monotonic
//          //          restoreSamplesCDF(samples.toIndexedSeq, (resultKeep ++ mineResultNegMin.result).toIndexedSeq, newSupp, newConf)
//          //          match {
//          //            case MineResult(result, pruned, samples) =>
//          //              MineResult(result, pruned, samples)
//          //          }
//
//
//        }
//        else if (deltaSupp == 0 && deltaConf > 0) {
//          logger.info("SUPP=,CONF+")
//
//          val resultKeep = results.filter {
//            case REEWithStat(p, supp, conf) =>
//              conf >= newConf
//
//          }
//
//
//          val resultFiltered = results.filter {
//            case REEWithStat(p, supp, conf) =>
//              conf < newConf
//          }
//
//          logger.info(resultFiltered)
//
//          incMineCont(resultFiltered, newSupp, newConf) match {
//            case MineResult(result, pruned, samples) =>
//              MineResult(minimizeREEStat(resultKeep ++ result), Iterable(), Iterable())
//          }
//
//
//        }
//        else if (deltaSupp > 0 && deltaConf == 0) {
//          val (r, time) = Wrappers.timerWrapperRet {
//            val resultp = results.filter {
//              case REEWithStat(p, supp, conf) => val supp = getSuppRatio(p, eviSet)
//                supp >= newSupp && conf < newConf
//            }
//
//            val resultKeep = results.filter {
//              case REEWithStat(p, supp, conf) => val supp = getSuppRatio(p, eviSet)
//                supp >= newSupp && conf >= newConf
//            }
//
//            (resultp, resultKeep)
//          }
//
//          MineResult(r._2, Iterable(), Iterable())
//
//
//        }
//        else if (deltaSupp >= 0 && deltaConf < 0) {
//
//          // todo: sample redundant result
//          logger.info("SUPP+,CONF-")
//          val resultKeep = results
//            .filter({ case REEWithStat(p, supp, conf) => conf >= newConf && getSuppRatio(p, eviSet) >= newSupp })
//
//          // val resultKeep = resultMap
//
//          restoreSamplesCDF(samples.toIndexedSeq, resultKeep.toIndexedSeq, newSupp, newConf)
//          match {
//            case MineResult(result, pruned, samples) =>
//              MineResult(result, pruned, samples)
//          }
//
//        }
//        else if (deltaSupp < 0 && deltaConf >= 0) {
//          // todo: slow on AMiner
//
//          logger.info("SUPP-,CONF+")
//
//          val new_supp_threshold_cnt = eviFullSize * newSupp
//          val resultFiltered = results.filter { case REEWithStat(p, supp, conf) => conf < newConf }
//          val resultKeep = results.filter { case REEWithStat(p, supp, conf) => conf >= newConf }
//          val prunedp = pruneds.filter {
//            case REEWithStat(ree, supp, conf) =>
//              supp >= new_supp_threshold_cnt &&
//                conf < newConf && ree.X.size < Config.levelUpperBound
//          }
//
//          val prunedKeep = pruneds.filter {
//            case REEWithStat(ree, supp, conf) =>
//              supp >= new_supp_threshold_cnt &&
//                conf >= newConf && ree.X.size < Config.levelUpperBound
//          }
//
//          logger.debug(s"Pruned p SIZE:${pruneds.size}->${prunedp.size}")
//
//
//          val in = minimizeREEStat((resultFiltered ++ prunedp).toIndexedSeq)
//          logger.debug(s"Input SIZE:${in.size}")
//
//          incMineCont(in, newSupp, newConf) match {
//            case MineResult(resultInc, pruned, samples) =>
//              val min = minimizeREEStat(resultInc ++ resultKeep ++ prunedKeep)
//              MineResult(min, pruned, samples)
//          }
//        }
//        else if (deltaSupp < 0 && deltaConf < 0) {
//
//          logger.info("SUPP-,CONF-")
//          val new_supp_threshold_cnt = (newSupp * eviFullSize).toLong
//
//          val prunedp = pruneds.filter { case REEWithStat(ree, supp, conf) =>
//            ree.X.size <= Config.levelUpperBound &&
//              supp >= new_supp_threshold_cnt &&
//              conf < newConf
//          }
//
//          val prunedKeep = pruneds.filter { case REEWithStat(ree, supp, conf) =>
//            ree.X.size <= Config.levelUpperBound &&
//              supp >= new_supp_threshold_cnt &&
//              conf >= newConf
//          }
//
//          val in = minimizeREEStat(prunedp ++ results)
//          val phase1 = incMineCont(in, newSupp, newConf)
//
//          phase1 match {
//            case MineResult(result, _, _) =>
//              restoreSamplesCDF(
//                samples.toIndexedSeq,
//                (result ++ prunedKeep).toIndexedSeq,
//                newSupp, newConf)
//          }
//        } else {
//          ???
//        }
//    }


  }

  /**
   * For Testing
   *
   * @return
   */

  private def randomBetween(min: Int, max: Int): Int = {
    val rnd = new Random
    min + rnd.nextInt((max - min) + 1)
  }

  def generateOneREE: REE = {
    //val searchSpaceN: Long = (1L << (p2i.size - 1))

    val N = randomBetween(0, rhsSpace.size)
    generateOneREE(N)

  }


  def generateOneREE(N: Int): REE = {
    //val searchSpaceN: Long = (1L << (p2i.size - 1))


    val X = {
      val protoXI = Random.shuffle(List.from(0 until p2i.size)).take(N)
      val protoX = protoXI.map(i => p2i.getObject(i))

      PredicateSet.from(protoX)(p2i)
    }


    val rhs = {
      val rhsIdx: Int = randomBetween(0, rhsSpace.size)
      rhsSpace(rhsIdx)
    }


    REE(X, rhs)

  }


  def info() = {
    s"""
       |lhsSpace SIZE:${lhsSpace.size}
       |rhsSpace SIZE:${rhsSpace.size}
       |allSpace SIZE:${allSpace.size}
       |""".stripMargin
  }


  def getSuppRatio(tREE: REE, eviSet: EvidencePairList): Double = {
    getSuppRatio(tREE.X :+ tREE.p0, eviSet)
  }

  def getSuppRatio(pairREE: (PredicateSet, Expression), eviSet: EvidencePairList): Double = {
    getSuppRatio(pairREE._1 :+ pairREE._2, eviSet)
  }


  def getConf(tREE: REE, eviSet: EvidencePairList): Double = {
    val suppX = getSuppCnt(tREE.X, eviSet)
    val suppREE = getSuppCnt(tREE, eviSet)
    if (suppX == 0) {
      0.0D
    } else {
      suppREE.toDouble / suppX.toDouble
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

  def getConf(tREE: (PredicateSet, Expression), eviSetX: EvidencePairList): Double = {
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


  private def getSuppCnt(X: PredicateSet, eviSet: EvidencePairList): Long = {


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


  /**
   * NO CACHE NOW
   *
   * @param X      X predicate set
   * @param eviSet evidence set
   * @return
   */
  private def getSuppRatio(X: PredicateSet, eviSet: EvidencePairList): Double = {
    getSuppCnt(X, eviSet).toDouble / eviFullSize.toDouble
  }


  // fixme: bug , kill valid rees
  def findSmallerX(rhs: Expression, m: Iterable[(PredicateSet, Double)], conf_threshold: Double)
  : Iterable[(PredicateSet, Double)] = {

    @tailrec
    def inner(validp: Iterable[(PredicateSet, Double)], prevSize: Int): Iterable[(PredicateSet, Double)] = {

      if (prevSize != validp.size) {
        val validpNew = validp.toMap ++ validp.flatMap {
          case (predSet, conf) =>
            for {
              p <- predSet
              newPredSet = predSet :- p
              conf = getConf(REE(newPredSet, rhs), eviSet)
              if conf >= conf_threshold
            } yield newPredSet -> conf
        }

        inner(validpNew, validp.size)
      } else {
        validp
      }

    }


    val valid = inner(m, -1)


    minimize(valid)

  }

  def findSmallerREE(t: Iterable[REEWithStat], newConf: Double): Iterable[REEWithStat] = {
    val rhsMap = toRhsMap(t)
    val t1 = for ((rhs, m) <- rhsMap.par) yield {
      rhs -> findSmallerX(rhs, m, newConf)
    }

    t1.flatMap {
      case (rhs, ps) => ps.map {
        case (x, conf) => REEWithStat(REE(x, rhs), -1L, conf)
      }
    }.toIndexedSeq
  }

}


object REEMiner {

  def getPredecessors(K: Int, pred: PredicateSet): Iterable[PredicateSet] = {
    val r = for {
      p <- pred
      predecessor = pred :- p
    } yield predecessor


    if (r.size < K) {
      r
    }
    else {
      r.take(K)
    }
  }

  def toRhsMap[T](in: Iterable[REEWithStat]): Map[Expression, Iterable[(PredicateSet, Double)]] = {
    in.groupBy(_.ree.p0).map {
      case (rhs, m) =>

        rhs -> m.map {
          case REEWithStat(ree, supp, conf) => ree.X -> conf
        }
    }
  }

  def toREEMap[T](in: Map[Expression, Map[PredicateSet, T]]): Map[REE, T] = {
    in.flatMap {
      case (rhs, m) => m.map {
        case (p, idx) => REE(p, rhs) -> idx
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
    minimal.toMap

  }

  def isMinimal(r: Iterable[PredicateSet], p: PredicateSet): Boolean = {
    for (e <- r) {
      if (e.isSubsetOf(p)) {
        return false
      }
    }

    true
  }


  def apply(db: Iterable[TypedColTable]): REEMiner = {

    val predSpace = PredSpace(db.head)


    val rhsSpace = REEMiner.getRHSSet(predSpace)
    // TODO: correlation analysis (causality learning)
    val lhsSpace = REEMiner.getLHSSet(predSpace)
    val allSpace = Set.from(lhsSpace ++ rhsSpace).toIndexedSeq

    val p2i: PredicateIndexProvider
    = PredSpace.getP2I(allSpace)

    println("All Space:", allSpace.size)

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


    val miner = new REEMiner(db)(allSpace, lhsSpace, rhsSpace, p2i)(mutable.IndexedSeq.from(eviSet))
    miner
  }


  /**
   * read evidence set checkpoint from s3 bucket
   *
   * @param db a set of coltables
   * @return
   */
  def fromS3(db: Iterable[TypedColTable]): REEMiner = {

    val predSpace = PredSpace(db.head)

    val rhsSpace = REEMiner.getRHSSet(predSpace)
    // TODO: correlation analysis (causality learning)
    val lhsSpace = REEMiner.getLHSSet(predSpace)
    val allSpace = Set.from(lhsSpace ++ rhsSpace).toIndexedSeq

    val p2i: PredicateIndexProvider
    = PredSpace.getP2I(allSpace)


    val evi_filename = db.head.getName
    val eviSet: IEvidenceSet = $.GetJsonIndexFromS3OrElseBuild(Config.EVIDENCE_IDX_NAME(evi_filename), p2i,
      {
        ???
        val evb = EvidenceSetBuilder(db.head, predSpace, SplitReconsEviBuilder.FRAG_SIZE)(p2i)
        logger.info(s"Evidence Building With PredSpace ${predSpace.values.map(_.size).sum}...")
        val (ret, time) = Wrappers.timerWrapperRet(SplitReconsEviBuilder.buildFullEvi(evb, db.head.rowNum))
        logger.info(s"Evidence Building Time $time; With Evidence Size: ${ret.size}")
        $.WriteResult("evidence.txt", ret.mkString(",\n"))
        ret
      })


    val miner = new REEMiner(db)(allSpace, lhsSpace, rhsSpace, p2i)(mutable.IndexedSeq.from(eviSet))
    miner
  }


  private def filterPredicates(predSpace: PredSpace, opset: Set[Operator]): IndexedSeq[Expression] = {
    predSpace.filter {
      case (op, pred) => opset.contains(op)
    }.flatten(_._2).toIndexedSeq
  }

  private def getRHSSet(predSpace: PredSpace): IndexedSeq[Expression] = {
    filterPredicates(predSpace, Config.REE_RHS).filter {
      case TypedTupleBin(_, _, _, col1, col2) =>
        col1 == col2
      case TypedConstantBin(op, _, _, _) =>
        true
    }
  }

  // todo: generalize to < > !=
  private def getLHSSet(predSpace: PredSpace): IndexedSeq[Expression] =
    filterPredicates(predSpace, Config.REE_LHS)


  type EvidencePairList = IndexedSeq[(PredicateSet, Long)]

}

case class ConstRecovArg(typedColTable: TypedColTable, mineResult: MineResult, supp: Double, conf: Double, p2i: PredicateIndexProvider) {
  def withMineResult(result: MineResult): ConstRecovArg = {
    ConstRecovArg(typedColTable, result, supp, conf, p2i)
  }
}
