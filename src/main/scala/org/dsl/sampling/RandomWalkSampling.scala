package org.dsl.sampling

import org.dsl.emperical.pli.ITypedPLI
import org.dsl.emperical.table.TypedColTable
import org.dsl.reasoning.predicate.{Expression, TypedTupleBin}

import scala.collection.{breakOut, mutable}
import scala.util.Random
import scala.util.control.Breaks.{break, breakable}

case class SamplingArg(table: TypedColTable,
                       predSpace: IndexedSeq[Expression],
                       sampleNum: Long)

object RandomWalkSampling {


  /**
   * sampling against predicate space
   *
   * @param allPredSpace predicate space
   * @return a set of tuple ids
   */
//  def randomWalkSampling(samplingArg: SamplingArg): Iterable[Long] = samplingArg match {
//    case SamplingArg(table, predSpace, sampleNum) =>
//      val predSpaceSize = predSpace.size
//      val res = mutable.Set.empty[Long]
//      val random = new Random()
//      while (res.size < sampleNum) {
//        breakable {
//          // random select predicate
//          val predId = random.nextInt(predSpaceSize)
//          val predSelect = predSpace(predId)
//
//          predSelect match {
//            case TypedTupleBin(op, _, _, col1, col2) =>
//              val pli1: ITypedPLI = table.getPli(col1)
//              val pli2: ITypedPLI = table.getPli(col2)
//              // randomly choose a value
//              val attr_value_id1 = random.nextInt(pli1.getValues().size)
//              val attr_value = pli1.getValues()(attr_value_id1)
//
//              val cluster1: Iterable[Int] = pli1.get(attr_value) match {
//                case Some(tIds1)  =>
//
//                  val tpId1Index = random.nextInt(tIds1.size)
//                  val tId1 = tIds1(tpId1Index)
//
//                  val pli2ValSet = pli2.get(attr_value) match {
//                    case Some(tIds2) =>
//                      val tpId2Index = random.nextInt(tIds2.size)
//                      var tId2 = tIds2(tpId2Index)
//
//                      // retry
//                      while (tId1 == tId2) {
//                        val tpId2Index = random.nextInt(tIds2.size)
//                        tId2 = tIds2(tpId2Index)
//                      }
//
//                      res += (tId1)
//                      res += (tId2)
//
//                    case None => break
//                  }
//
//
//                case None => ???
//              }
//
//          }
//        }
//
//
//      }
//
//      res
//  }
}
