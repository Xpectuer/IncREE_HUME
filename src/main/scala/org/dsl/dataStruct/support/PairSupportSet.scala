package org.dsl.dataStruct.support

import org.dsl.dataStruct.IdxPair.{IdxPair, pairIdx}

import scala.collection.mutable
import scala.math.Numeric.IntIsIntegral

// TODO: Better DataStruct
// TODO: Pair (Int, Int) => ((db1, Int), (db2, Int))
class PairSupportSet(val set: mutable.TreeSet[IdxPair]) extends ISupportSet {
  def removeBatch(idx: Seq[Int]): Unit = {
    val idxSet = idx.toSet
    set.foreach{
      p =>
        val idxp = pairIdx(p)
        if(idxSet.contains(idxp._1) || idxSet.contains(idxp._2)) {
          set.remove(p)
        }
    }
  }


  // def isMember(pair: IdxPair): Boolean = set.contains(pair)

  // def add(pair: (Int, Int)): Unit = set.add(pair)
  def foreach(f: IdxPair => Unit): Unit = set.foreach(f)

  def size: Int = set.size

  def addOne(e: IdxPair): Unit = {
    val swapped = (e._2, e._1)
    // todo: contains time consuming
    if (!set.contains(swapped)) {
      set.+=(e)
    }
  }

  def remove(e: IdxPair): Unit = {
    set.diff(Set(e))
  }

  override def toString: String =
    s"${set.take(5)}"

}



object PairSupportSet {

  // todo: may override different
  implicit val idxPairOrdering: Ordering[IdxPair] =
    Ordering.Tuple2(Ordering[Int], Ordering[Int]).on(pairIdx)

  def empty: PairSupportSet = new PairSupportSet(mutable.TreeSet.empty[IdxPair])
  private def swap(p:IdxPair) = (p._2, p._1)

  // keep"p._1 < p._2" and identity
  private def elimDup(pairs: Iterable[IdxPair]) = {
    val set = mutable.TreeSet.empty[IdxPair]
    for (p <- pairs) {
      lazy val swapped = swap(p)
      val (idx1, idx2) = pairIdx(p)
      if (!set.contains(swapped)) {
        val t = if(idx1 < idx2) p else swapped
        set.+=(t)
      }
    }
    set
  }
  def apply(pairs: IdxPair*): PairSupportSet = {
    new PairSupportSet(elimDup(pairs))
  }

  def apply(pairs: List[IdxPair]): PairSupportSet =
    new PairSupportSet(elimDup(pairs))

  def apply(set: Set[IdxPair]): PairSupportSet =
    new PairSupportSet(elimDup(set.toList))

  def apply(set: mutable.Set[IdxPair]): PairSupportSet =
    apply(set.toList)
}
