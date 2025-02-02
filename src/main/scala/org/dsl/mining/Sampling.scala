package org.dsl.mining


import scala.collection.compat._

import org.dsl.dataStruct.Interval

import scala.annotation.tailrec
import scala.collection.immutable.Iterable
import scala.collection.mutable

case class Sampling[T](head: T, range: Interval[Double])


object Sampling {

  // 2- approximate algorithm for Vertex Cover
  def ApproximateSampling[T](subsets: Iterable[T], K: Int)(implicit distFun: (T, T) => Int) = {
    var workSet = mutable.Set.from(subsets)

    val r = mutable.ArrayBuffer.empty[T]
    while (workSet.nonEmpty) {

      val (car, cdr) = (workSet.head, workSet.drop(0))
      for (c <- cdr if distFun(car, c) <= K) {
        workSet -= c
      }
      r+=(car)
    }

    r.toIndexedSeq
  }

  def interval(iterable1: Iterable[Double]): Interval[Double]
  = if (iterable1.nonEmpty) {
    Interval(iterable1.min, iterable1.max)
  } else {
    Interval(0d, 0d)
  }


  def ApproximateSampling1[T](subsets: Seq[T], K: Int)
                             (distFun: (T, T) => Int): IndexedSeq[T] = {
    @tailrec
    def inner(workSet: Seq[T], res: mutable.ArrayBuffer[T]): Unit = {
      workSet match {
        case Nil => ()
        case car :: cdr =>
          val rest = for (c <- cdr if distFun(car, c) > K) yield c
          val affected = for (c <- cdr if distFun(car, c) <= K) yield c
          // reduce
          res+=(car)
          inner(rest, res)
      }
    }

    // val shuffle = Random.shuffle(subsets)
    val r = mutable.ArrayBuffer.empty[T]
    inner(subsets, r)
    r.toIndexedSeq
  }


  def ApproximateSamplingWithConfRange[T](subsets: Seq[T], K: Int)
                             (distFun: (T, T) => Int)(idxFun: T=> Double): Iterable[Sampling[T]] = {
    @tailrec
    def inner(workSet: Seq[T], res: IndexedSeq[Sampling[T]]): Unit = {
      workSet match {
        case Nil => ()
        case car :: cdr =>
          val rest = for (c <- cdr if distFun(car, c) > K) yield c
          val affected = for (c <- cdr if distFun(car, c) <= K) yield c

          val confs = if (affected.nonEmpty) {affected.map(a => idxFun(a))} else {List()}

          // reduce
          inner(rest, res :+ Sampling(car, interval(confs)))
      }
    }

    // val shuffle = Random.shuffle(subsets)
    val r = IndexedSeq.empty[Sampling[T]]
    inner(subsets, r)
    r.toIndexedSeq
  }

  def optK[T](subsets: Seq[T], maxK: Int, maxNSamples: Int)(distFun: (T, T) => Int): (IndexedSeq[T], Int) = {
    for (k <- 1 to maxK) {

      val res = ApproximateSampling1(subsets, k)(distFun)
      //println(k, res)
      if (res.size <= maxNSamples) {
        return (res, k)
      }
    }

    (IndexedSeq(), -1)
  }


}