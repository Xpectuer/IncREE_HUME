package org.dsl.utils

// todo: modify for cross table
class TupleIDProvider(size: Int) {
  val tIDs = (0 until size)
  def gettIDs: IndexedSeq[Int] = tIDs
}

object TupleIDProvider {
  def apply(size: Int): TupleIDProvider = new TupleIDProvider(size)
}
