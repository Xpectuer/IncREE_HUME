package org.dsl.utils

import org.dsl.dataStruct.RBTreeMap
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.Random

object Sampling {
  val rand = new Random()

  // 水库采样算法（Reservoir Sampling）
  def sampleReservoir[T: ClassTag](list: List[T], n: Int): List[T] = {
    assert(list.size >= n)
    if(list.size == n) return list

    val result: Array[T] = new Array(n)
    for (i <- 0 until n) {
      result(i) = list(i)
    }
    val rand = new Random()
    for (i <- n until list.length) {
      val idx = rand.nextInt(i + 1)
      if (idx < n) {
        result(idx) = list(i)
      }
    }

    result.toList
  }

  def sampleSplitVanilla[T](source: RBTreeMap[T], length: Int): (RBTreeMap[T],RBTreeMap[T]) = {
    // val shuffled = Random.shuffle(source)
    (source.take(length), source.drop(length))
  }


}
