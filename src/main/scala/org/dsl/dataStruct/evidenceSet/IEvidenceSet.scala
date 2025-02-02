package org.dsl.dataStruct.evidenceSet

import org.dsl.reasoning.predicate.PredicateSet


trait IEvidenceSet extends Iterable[(PredicateSet, Long)] with Serializable {
  def add(predicateSet: PredicateSet): Boolean

  def add(predicateSet: PredicateSet, count: Long): Boolean

  def getCount(predicateSet: PredicateSet):Long

  override def iterator: Iterator[(PredicateSet, Long)]

  def sum(): Long

  def isEmpty(): Boolean

  def jsonify: String


}
