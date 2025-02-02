package org.dsl.dataStruct.support

import org.dsl.dataStruct.IdxPair.IdxPair

import scala.collection.mutable

class LinearSupportSet(private val set: mutable.Set[Int]) extends ISupportSet {
  override def addOne(e: IdxPair): Unit = {

    set.+=(e._1._2)
    set.+=(e._2._2)
  }

  override def remove(e: IdxPair): Unit = {

  }

  override def foreach(f: IdxPair => Unit): Unit = ???

  override def size: Int = ???

  override def removeBatch(idx: Seq[Int]): Unit = ???
}

object LinearSupportSet {
  def apply(indices: List[Int]): LinearSupportSet = {
    val buf = mutable.Set(indices.toArray:_*)
    new LinearSupportSet(buf)
  }

  def apply(indices: Int*): LinearSupportSet = {
    val buf = mutable.Set(indices:_*)
    new LinearSupportSet(buf)
  }

  def empty: LinearSupportSet = new LinearSupportSet(mutable.Set.empty[Int])


}