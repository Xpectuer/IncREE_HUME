package org.dsl.dataStruct.support

import org.dsl.dataStruct.IdxPair.IdxPair

trait ISupportSet {
  def addOne(e: IdxPair): Unit
  def remove(e: IdxPair): Unit
  def foreach(f: IdxPair => Unit): Unit
  def size: Int
  def removeBatch(idx: Seq[Int]): Unit
}
