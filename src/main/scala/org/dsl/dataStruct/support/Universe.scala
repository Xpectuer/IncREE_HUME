package org.dsl.dataStruct.support

import org.dsl.reasoning.predicate.TableInstanceAtom

case class Universe() extends ISupportSet {
  override def addOne(e: ((TableInstanceAtom, Int), (TableInstanceAtom, Int))): Unit = ()

  override def remove(e: ((TableInstanceAtom, Int), (TableInstanceAtom, Int))): Unit = ()

  override def foreach(f: (((TableInstanceAtom, Int), (TableInstanceAtom, Int))) => Unit): Unit = ()

  override def size: Int = 0

  override def removeBatch(idx: Seq[Int]): Unit = ()
}

