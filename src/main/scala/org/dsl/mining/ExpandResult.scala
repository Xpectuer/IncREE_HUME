package org.dsl.mining

import org.dsl.reasoning.predicate.PredicateSet

import scala.collection.mutable


case class ExpandResult[T](resultX: mutable.Map[PredicateSet,T],
                        prunedX: mutable.Map[PredicateSet, T], timeSample:Double)
{


  override def toString: String = {
    val resultSorted = resultX.toIndexedSeq.sortBy(e => e._1.size)
    val prunedSorted = prunedX.toIndexedSeq.sortBy(e => e._1.size)

    s"""
       |result SIZE: ${resultX.size}
       |
       |pruned SIZE: ${prunedX.size}
       |
       |result:
       | ${resultSorted.mkString("\n")}
       |
       |pruned:
       | ${prunedSorted.mkString("\n")}
       | ...
       |
       |""".stripMargin
  }

  def ++(other: ExpandResult[T]): ExpandResult[T] = {
    ExpandResult(this.resultX ++ other.resultX, this.prunedX ++ other.prunedX, this.timeSample + other.timeSample)
  }



}

object ExpandResult {
  def empty = {
    ExpandResult(mutable.Map.empty[PredicateSet, Stat],
      mutable.Map.empty[PredicateSet, Stat],
    -1)
  }
}

