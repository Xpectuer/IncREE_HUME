package org.dsl.dataStruct.evidenceSet

import org.dsl.reasoning.predicate.PredicateSet

import scala.collection.mutable

class HashEvidenceSet(private val evidence: mutable.Map[PredicateSet, Long]
                      = mutable.Map.empty[PredicateSet, Long]) extends IEvidenceSet {
  /**
   *
   * @param predicateSet to add
   * @return true if map did not contain predicate set.
   */
  override def add(predicateSet: PredicateSet): Boolean = {
    //val oldCnt = evidence.getOrElse(predicateSet, 0L)
    val oldCnt = evidence.getOrElseUpdate(predicateSet, 1L)
    if (oldCnt > 1L) {
      evidence.update(predicateSet, oldCnt + 1L)
      false
    }  else if(oldCnt == 1L) {
      true
    } else {
      ???
    }
  }

  override def add(predicateSet: PredicateSet, count: Long): Boolean = {
    val oldCnt = evidence.getOrElseUpdate(predicateSet, count)
    if (oldCnt > count) {
      evidence.update(predicateSet, oldCnt + count)
      false
    } else if (oldCnt == count) {
      true
    } else {
      ???
    }
  }

  override def getCount(predicateSet: PredicateSet): Long = evidence.getOrElse(predicateSet, 0)

  override def iterator: Iterator[(PredicateSet,Long)] = evidence.iterator

  override def sum(): Long = evidence.values.sum

  override def isEmpty(): Boolean = evidence.isEmpty

  override def jsonify: String = ???
}
