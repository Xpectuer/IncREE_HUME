package org.dsl.emperical.pli


trait ITPIDSIndexer extends Serializable {
  def getValues: Iterable[Int]


  def getTupleIDsFromValue(value: Int): Option[IndexedSeq[Int]]

  def getIndexForValueThatIsLessThan(value: Int): Int
}
