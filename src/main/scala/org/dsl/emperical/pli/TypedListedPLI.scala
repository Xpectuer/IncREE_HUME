package org.dsl.emperical.pli

import org.dsl.emperical.table.{TypedColTable, TypedColumn}
import org.dsl.reasoning.predicate.HumeType.HumeType
import org.dsl.utils.IndexProvider

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// light-weight PLI with performance ensurance
@SerialVersionUID(66666L)
class TypedListedPLI(private val clusters: IndexedSeq[IndexedSeq[Int]],
                     var rowCount: Long,
                     numerical: Boolean,
                     values: Array[Int]) extends ITypedPLI {



  private val tpIDsIndexer = if (numerical) {
    NumericalTpIDsIndexer(clusters, values)
  } else {
    StringTpIDsIndexer(clusters, values)
  }
  override def getClusters: IndexedSeq[IndexedSeq[Int]] = clusters
  override def getRowCount: Long = rowCount
  override def isNumerical: Boolean = numerical
  override def get(e: Int): Option[IndexedSeq[Int]] = {
    tpIDsIndexer.getTupleIDsFromValue(e)
  }

  def getIndexForValueThatIsLessThan(value: Int): Int =
    tpIDsIndexer.getIndexForValueThatIsLessThan(value)

  override def getValues: Iterable[Int] = tpIDsIndexer.getValues

}

object TypedListedPLI {
  def from(setPlis: mutable.IndexedSeq[mutable.TreeSet[Int]], rowCount: Int, numerical: Boolean, values: Array[Int]): TypedListedPLI = {
    val tupleIDs = ArrayBuffer.empty[IndexedSeq[Int]]
    for (set <- setPlis) {
      val tidsList: IndexedSeq[Int] = IndexedSeq(set.toArray :_*)
      tupleIDs.+=(tidsList)
    }

    new TypedListedPLI(tupleIDs.toVector, rowCount, numerical, values)

  }


}
