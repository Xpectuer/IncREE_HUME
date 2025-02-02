package org.dsl.emperical.pli


trait ITypedPLI extends Serializable{

  def get(e: Int): Option[IndexedSeq[Int]]

  def getIndexForValueThatIsLessThan(value: Int): Int

  def getValues: Iterable[Int]

  def getRowCount: Long

  def getClusters: IndexedSeq[IndexedSeq[Int]]

  def isNumerical:Boolean


}
