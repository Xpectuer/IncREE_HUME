package org.dsl.dataStruct

import scala.collection.mutable

class RBTreeMap[T](private val treeMap: mutable.TreeMap[Int, T]) extends mutable.Iterable[T] {

  def remove(key: Int): Unit = treeMap.remove(key)


  val clazzName: String = getClass.getName + "@"

  def merge(other: RBTreeMap[T]): Unit = {
    other.foreach {
       t => treeMap.+=((size,t))
    }
  }

  def addOne(row:  T): Unit = {
    treeMap.update(size, row)
  }

  override def head: T = treeMap.head._2

  def lookup(idx: Int): Option[T] = {
    treeMap.get(idx)
  }

  def getOrElse[T1 >: T](idx: Int, f: => T1): T1 = {
    treeMap.getOrElse(idx, f)
  }

  override def iterator: Iterator[T] = treeMap.iterator.map(p=>p._2)


  override def take(n: Int): RBTreeMap[T] = RBTreeMap(treeMap.take(n))

  override def drop(n: Int): RBTreeMap[T] = RBTreeMap(treeMap.drop(n))

  def apply(idx: Int): Option[T] = {
    // fixme: type mismatch;
    // found   : String
    // required: T

    treeMap.get(idx)
  }

  override def size: Int = treeMap.size


  override def toString: String = {
    treeMap.toMap.mkString(",")
  }

  def getTreeMap: mutable.TreeMap[Int, T] = {
    treeMap
  }

}

object RBTreeMap {

  def empty[T]: RBTreeMap[T] = new RBTreeMap[T](mutable.TreeMap.empty[Int, T])

  def apply[T](seq: Iterable[(Int, T)]): RBTreeMap[T] = {
    val tree = mutable.TreeMap.empty[Int, T]
    seq.foreach {
      case (k, v) => tree.update(k, v)
    }

    new RBTreeMap[T](tree)
  }




}