package org.dsl.dataStruct.evidenceSet.builders

import org.dsl.dataStruct.Interval
import org.dsl.dataStruct.evidenceSet.{HPEvidenceSet, IEvidenceSet}
import org.dsl.emperical.pli.{ITypedPLI, TypedListedPLI}
import org.dsl.emperical.table.TypedColTable.isComparable
import org.dsl.emperical.table.{TypedColTable, TypedColumn}
import org.dsl.exception.PredicateToBitException
import org.dsl.mining.PredSpace.PredSpace
import org.dsl.reasoning.predicate.HumeType._
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate._
import org.dsl.utils.TPID.{TPID, TPIDInterval}
import org.dsl.utils.{Config, HUMELogger, TPID}

import java.util
import scala.collection.Searching.{Found, InsertionPoint, search}
import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}


class EvidenceSetBuilder(private val typedColTable: TypedColTable,
                         private val predSpace: PredSpace, private val fragSize: Int)
                        (p2i: PredicateIndexProvider) {
  private val logger: HUMELogger = HUMELogger(getClass.getName)


  private val head: PredicateSet = initializeHead(predSpace)


  val maskMap: Map[Expression, PredicateSet] = initializeMaskMapping(predSpace)


  def mergePartialEvi(eviSet: IEvidenceSet, builderArray: Array[PredicateSet]): IEvidenceSet = {
    for (predSet <- builderArray) {
      eviSet.add(predSet)
    }

    eviSet
  }

  private val constPreds = getConstPreds

  private def getConstPreds = predSpace.flatten(_._2).filter(_.isInstanceOf[TypedConstantBin])


  private def isValidConstPred(tcb: TypedConstantBin, tid1: Int, tid2: Int) = tcb match {
    case TypedConstantBin(op, _, col, const) =>
      op match {
        case Eq =>
          col.htype match {
            case HString =>
              val tColumn: TypedColumn[String] = typedColTable.getColumn(col).asInstanceOf[TypedColumn[String]]
              val judge0 = tColumn.get(tid1) match {
                case Some(v0) => v0 == const.getValue
                case None => ???
              }

              lazy val judge1 = tColumn.get(tid2) match {
                case Some(v1) => v1 == const.getValue
                case None => ???
              }

              judge0 && judge1

            case _ => ???
          }

        case _ => ???
      }
  }

  // todo: add constant mask
  private def getConstantMask(begin: TPID, offset: Int) = {
    val size = typedColTable.rowNum
    val constPreds = this.constPreds
    val tuplePairId: TPID = begin + offset

    val (tid1, tid2) = TPID.getTuplePair(tpid = tuplePairId, size)

    val validConstPreds = constPreds.filter {
      case t: TypedConstantBin =>
        isValidConstPred(t, tid1, tid2)
      case _ => false
    }

    PredicateSet.from(validConstPreds)(p2i)
  }

  // todo
  private def fillAndBuildArray(predicate2BitSetMap: Iterable[(Expression, util.BitSet)],
                                begin: Int, end: Int, fragSize: Int) = {
    val builderArray: Array[PredicateSet] =
      initBuilderArray(fragSize, PredicateSet.copy(head))


    for {(p, tpids) <- predicate2BitSetMap
         mask = getMaskCached(p)
         } {

      // i: offset within chunk
      var i = tpids.nextSetBit(begin)
      breakable {
        while (i >= 0) {
          if (i >= end) {
            break
          }

          builderArray(i - begin).xor(mask)

          // todo: add constant
          i = tpids.nextSetBit(i + 1)
        }
      }

    }


    builderArray

  }

  private def unfoldTasks(chunkSize: Int, fragSize: Int): Iterator[Interval[Int]] = {
    val initInterval = Interval(0, fragSize)
    val steps = Iterator.iterate(initInterval)(i => Interval(i.begin + fragSize, i.end + fragSize)).takeWhile(_.begin <= chunkSize)
    if (chunkSize % fragSize == 0)
      steps
    else // add on last frag
      steps ++ Iterator.single(Interval(fragSize * (chunkSize / fragSize), chunkSize))
  }

  def buildPartialEvi(begin: TPID, end: TPID): HPEvidenceSet = {
    // predicate -> [Tpid...]
    logger.debug(s"Process Chunk: ($begin, $end)")

    // array B in DCFinder

    // val headcopy = head.copy
    val chunkSize = (end - begin).toInt
    val _fragSize = if (chunkSize > fragSize) {
      fragSize
    } else {
      chunkSize
    }


    val _fragNum = if (chunkSize > _fragSize) {
      chunkSize / _fragSize
    } else {
      1
    }

    val fragIntervals: Iterator[Interval[Int]] = unfoldTasks(chunkSize, _fragSize)

    val partialEvi = HPEvidenceSet()

    // todo:
    val predicate2BitSetMap: Iterable[(Expression, util.BitSet)]
    = getPredicate2BitSetMap(begin, end)

    //    if (predicate2BitSetMap.map(_._2.size).sum != chunkSize) {
    //      logger.info(s"left=${predicate2BitSetMap.map(_._2.size).sum}, right=${chunkSize}")
    //      assert(false)
    //    }


    for (fintr <- fragIntervals) {

      val fbegin = fintr.begin
      val fend = fintr.end

      logger.debug(s"Process Fragment: ($fbegin, $fend)")

      val builderArray =
        fillAndBuildArray(predicate2BitSetMap, fbegin, fend, _fragSize)

      for (slot <- builderArray) {
        partialEvi.add(slot)
      }

      //val headCount = evi.remove(head)
      //logger.debug(s"Removed $headCount redundant heads")
      // relative indices
    }

    // initialize array B: Algorithm 2 (line 1)
    partialEvi

  }

  private def initMasks() = {
    val predWithGtEq = predSpace.filter(pair => pair._1 == Gt || pair._1 == Eq).flatten(_._2)
    (for (p <- predWithGtEq) yield p -> getMask(p)).toMap
  }

  private lazy val getMaskCached: Map[Expression, PredicateSet] = initMasks()


  private def getMask(p: Expression): PredicateSet = {
    p match {
      case TypedTupleBin(op, t0, t1, col1, col2) =>
        col1.htype match {
          case HString =>
            op match {
              case Eq =>
                val pNeq: Expression = TypedTupleBin(NEq, t0, t1, col1, col2)
                val pEq = p
                try {
                  PredicateSet.from(List(pNeq, pEq))(p2i)
                } catch {
                  case p: PredicateToBitException =>
                    p.printStackTrace()
                    logger.fatal(p.getMsg)
                }

              case _ => ???
            }
          case HFloat | HInt | HLong =>
            op match {


              case Eq =>

                val r = {
                  val r = mutable.ArrayBuffer.empty[Expression]
                  val pEq = p
                  val pNeq: Expression = TypedTupleBin(NEq, t0, t1, col1, col2)
                  r.+=(pEq).+=(pNeq)

                  // compatible for the situation:
                  // 2 numerical column, joinable but not comparable.
                  if (isComparable(typedColTable, col1, col2)) {
                    val pLt: Expression = TypedTupleBin(Lt, t0, t1, col1, col2)
                    val pGe: Expression = TypedTupleBin(Ge, t0, t1, col1, col2)
                    r.+=(pLt).+=(pGe)
                  }

                  r
                }


                PredicateSet.from(r)(p2i)
              case Gt =>
                val pGt = p
                val pLt = TypedTupleBin(Lt, t0, t1, col1, col2)
                val pGe = TypedTupleBin(Ge, t0, t1, col1, col2)
                val pLe = TypedTupleBin(Le, t0, t1, col1, col2)
                PredicateSet.from(List(pGt, pLt, pGe, pLe))(p2i)
              case _ => ???
            }
        }
      case t: TypedConstantBin =>
        // todo: mask for constant ???
        PredicateSet.from(List(t))(p2i)
      case _ => ???
    }
  }

  def initializeMaskMapping(predSpace: PredSpace): Map[Expression, PredicateSet] = {
    val headOps: Set[Operator] = Set[Operator](Eq, Gt)
    // see DCFinder paper Algorithm2(initialize head) pp.271
    val predsWithGtEq = (for ((op, v) <- predSpace if headOps.contains(op)) yield v).flatten

    (for (p <- predsWithGtEq) yield p -> getMask(p)).toMap
  }


  private def initBuilderArray(fragSize: Int, head: PredicateSet) =
    Array.fill(fragSize)(PredicateSet.copy(head))

  private def initializeHead(predSpace: PredSpace): PredicateSet = {
    val headOps: Set[Operator] = Set[Operator](NEq, Lt, Le)
    // see DCFinder paper Algorithm2(initialize head) pp.271
    val predList = (for ((op, v) <- predSpace if headOps.contains(op)) yield v).flatten

    logger.debug(s"Evidence Head Length: ${predList.toSet.size}")
    PredicateSet.from(predList)(p2i)
  }

  private def searchValue(sorted: collection.IndexedSeq[Int], value: Int) = {
    sorted.search(value) match {
      case Found(v) => v
      case InsertionPoint(i) => i
    }
  }


  private def collectSameColEq(pli1: ITypedPLI, size: Int, interval: TPIDInterval) = {
    val tpids = new util.BitSet()
    tpids.clear()


    val clusters = pli1.getClusters

    val tupleInterval = getTupleInterval(interval, size)

    clusters
      .withFilter(_.size > 1)
      .foreach(tids => {
        // range begin -> end

        // todo: binsearch

        val idxLow = searchValue(tids, tupleInterval.begin)
        val idxHigh = searchValue(tids, tupleInterval.end)

        tids.slice(idxLow, idxHigh)
          .foreach(i =>
            tids.withFilter(j => j != i)
              .foreach { j =>
                val offset = getOffSetOfBitSet(TPID.getTPID(i, j, size), interval)
                if (offset > 0) {
                  tpids.set(offset)
                }
              }
          )
      })


    tpids
  }


  private def collectCrossColEq(pliPivot: ITypedPLI, pliProbe: ITypedPLI, size: Int, interval: TPIDInterval) = {
    val tpids = new util.BitSet()
    tpids.clear()


    val valuesPivot = pliPivot.getValues

    val tupleInterval = getTupleInterval(interval, size)
    for (vPivot <- valuesPivot) {
      pliProbe.get(vPivot) match {
        case Some(tidsProbe) if (tidsProbe.nonEmpty) =>

          pliPivot.get(vPivot) match {
            case Some(tidsPivot) =>
              val idxLow = searchValue(tidsPivot, tupleInterval.begin)
              val idxHigh = searchValue(tidsPivot, tupleInterval.end)

              tidsPivot.view.slice(idxLow, idxHigh)
                .foreach(tidPivot =>
                  tidsProbe.withFilter(_ != tidPivot)
                    .foreach { tidProbe => {
                      val offset = getOffSetOfBitSet(TPID.getTPID(tidPivot, tidProbe, size), interval)
                      if (offset >= 0) {
                        tpids.set(offset)
                      }
                    }
                    })


            case None => ()
          }


        case _ => ()
      }

    }

    tpids
  }

  private def getOffSetOfBitSet(tpid: TPID, interval: TPIDInterval) = (tpid - interval.begin).toInt

  def getTupleInterval(interval: TPIDInterval, size: Int): Interval[Int] = {
    Interval[Int](TPID.getTupleID(interval.begin, size), TPID.getTupleID(interval.end, size))
  }

  private def collectSameColGt(pli1: ITypedPLI, size: Int, interval: TPIDInterval) = {

    val tpidSet = new util.BitSet()
    tpidSet.clear()

    val tupleInterval = getTupleInterval(interval, size)

    val lpli = pli1.asInstanceOf[TypedListedPLI]
    val clusters = lpli.getClusters
    (0 until clusters.size - 1).foreach {
      i =>
        val greaterTupleIDs = clusters(i)

        val idxLow = searchValue(greaterTupleIDs, tupleInterval.begin)
        val idxHigh = searchValue(greaterTupleIDs, tupleInterval.end)

        greaterTupleIDs.slice(idxLow, idxHigh)
          .foreach {
            greaterTupleID =>
              (i + 1 until clusters.size).foreach {
                j =>
                  val smallerTupleIDs = clusters(j)
                  smallerTupleIDs.withFilter(greaterTupleID != _)
                    .foreach { smallerTupleID =>
                      val offset = getOffSetOfBitSet(TPID.getTPID(greaterTupleID, smallerTupleID, size), interval)
                      if (offset >= 0) {
                        tpidSet.set(offset)
                      }

                    }
              }
          }
    }


    tpidSet

  }

  private def collectCrossColGt(pliPivot: ITypedPLI, pliProbe: ITypedPLI, size: Int, interval: TPIDInterval) = {
    val tpids = new util.BitSet()
    tpids.clear()

    val valuesPivot = pliPivot.getValues
    val tidsListProbe = pliProbe.getClusters

    val tupleInterval = getTupleInterval(interval, size)

    breakable {
      for {vPivot <- valuesPivot} {
        // get pivot
        val indexProbe: Int = pliProbe.getIndexForValueThatIsLessThan(vPivot)
        if (indexProbe >= 0) {

          pliPivot.get(vPivot) match {
            case Some(tidsPivot) =>
              val idxLow = searchValue(tidsPivot, tupleInterval.begin)
              val idxHigh = searchValue(tidsPivot, tupleInterval.end)

              tidsPivot.view.slice(idxLow, idxHigh)
                .foreach {
                  tidPivot =>
                    (indexProbe until tidsListProbe.size)
                      .foreach {
                        j =>
                          val tidsProbe = tidsListProbe(j)
                          tidsProbe.withFilter(_ != tidPivot)
                            .foreach {
                              smallerTid =>
                                val tid = getOffSetOfBitSet(TPID.getTPID(tidPivot, smallerTid, size), interval)
                                if (tid >= 0) {
                                  tpids.set(tid)
                                }
                            }
                      }
                }

            case None => ()
          }
        } else {
          break
        }


      }
    }


    tpids


  }


  // todo: AI
  def collectConstEq(tcb: TypedConstantBin, interval: TPIDInterval): (util.BitSet) = {

    val bset = new util.BitSet()
    bset.clear()
    val size = typedColTable.rowNum


    val nullConstIndex = typedColTable.getStrProvider.get("") match {
      case Some(i) => i
      case None => -1
    }


    tcb match {
      case TypedConstantBin(op, t0, col, const) =>
        val low = TPID.getTupleID(interval.begin, size)
        val _high = TPID.getTupleID(interval.end, size)

        val high = if (_high > size) size else _high
        val indexMat = typedColTable.getColumnIdxValVector(columnAtom = col)
        (low until high).foreach {
          i =>
            val valIdx = indexMat(i)
            // not null
            val isEval = const match {
              case ConstantAtom(Config.WILDCARD) => true
              case ConstantAtom(value) => valIdx == typedColTable.getStrProvider.get(value).getOrElse(-1)
            }

            if (isEval) {
              (0 until size).foreach {
                j =>

                  val tpid = getOffSetOfBitSet(TPID.getTPID(i, j, size), interval)
                  if (tpid >= 0) {
                    bset.set(tpid)
                  }
              }
            }


        }


    }

    bset


  }

  //// todo: 直接写入barray，task list拷贝导致缓慢
  private def collectTaskOfPredicate(p: Expression, typedColTable: TypedColTable, interval: TPIDInterval) = {
    val size = typedColTable.rowNum

    p match {
      case TypedTupleBin(op, _, _, col1, col2) =>

        val pli1 = typedColTable.getPli(col1)
        val pli2 = typedColTable.getPli(col2)

        /**
         * set T in Algorithm 1 (line 6)
         */
        val tpids = op match {
          case Gt =>
            if (col1 == col2) {
              collectSameColGt(pli1, size, interval)
            } else {
              collectCrossColGt(pli1, pli2, size, interval)
            }

          case Eq =>
            if (col1 == col2) { // same col
              collectSameColEq(pli1, size, interval)
            } else { // different col
              collectCrossColEq(pli1, pli2, size, interval)
            }

          case _ => ???
        }

        //        assert(tpids.size <= interval.end - interval.begin)
        p -> tpids

      case tcb@TypedConstantBin(op, _, col, const) =>

        p -> collectConstEq(tcb, interval)

      case _ => ???
    }


  }

  /**
   * DC Finder ALgorithm 1 (line 6-9)
   * Find Inconsistent Tuple Pairs
   */
  private def getPredicate2BitSetMap(begin: TPID, end: TPID) = {

    val predsWithGtEq = predSpace.filter {
      case (op, _) => (op == Eq) || (op == Gt)
    }.flatten(p => p._2)

    for (p <- predsWithGtEq) yield {
      collectTaskOfPredicate(p, typedColTable, Interval(begin, end))
    }

  }
}

object EvidenceSetBuilder {

  def apply(typedColTable: TypedColTable, predSpace: PredSpace, fragSize: Int)(p2i: PredicateIndexProvider): EvidenceSetBuilder =
    new EvidenceSetBuilder(typedColTable, predSpace, fragSize)(p2i)

}
