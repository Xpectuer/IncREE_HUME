package org.dsl.utils

import org.dsl.dataStruct.Interval

object TPID {
  type TPIDInterval = Interval[TPID]

  type TPID = Long

  def getTPID(idx1: Int, idx2: Int, size: Int): TPID = (idx1 * size).toLong + idx2.toLong

  def getTupleID(tpid: TPID, size: Int): Int = (tpid / size).toInt

  def getTuplePair(tpid: TPID, size: Int): (Int, Int) = {
    val t1 = getTupleID(tpid, size)
    val t2 = (tpid - t1* size).toInt
    (t1, t2)
  }

  def isIn(interval: TPIDInterval, tpid: TPID) = interval.begin <= tpid && tpid < interval.end

  def isIn(interval: TPIDInterval, idx1: Int, idx2: Int, size: Int) = {
    val tpid = getTPID(idx1, idx2, size)
    interval.begin <= tpid && tpid < interval.end
  }


}

