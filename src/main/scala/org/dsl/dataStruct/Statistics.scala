package org.dsl.dataStruct

import org.dsl.dataStruct.support.{ISupportSet, PairSupportSet, Universe}

case class StatException(msg: String) extends Exception {
  override def getMessage: String = "Statistics Error: " + msg
}

class Statistics(var support: BigInt, var spset: ISupportSet,
                 var confidence: Double, var conciseness: Double) {

  def dropFromIdxSeq(negs: Seq[Int]): Unit = spset.removeBatch(negs)


  def setConfidence(newSp: BigInt, oldSp: BigInt): Double = {
    val zero = BigInt(0)
    newSp match {
      case `zero` => 0.0
      case x if x > 0 => (oldSp / x).doubleValue
      case x if x < 0 => throw StatException("Negative Support Found")
    }
  }

  def merge(other: Statistics): Statistics = {
    val spSetOther = other.spset
    this.spset match {
      case p: PairSupportSet =>
        spSetOther.foreach(evi => p.addOne(evi))
        this.support = spset.size
        this
      case _: Universe =>
        this.spset = other.spset
        this.support = other.spset.size
        other
    }

  }

  @inline
  def addSupp1(): Unit = (support = support + 1)

  @inline
  def subSupp1(): Unit = (support = support - 1)

  override def toString: String = {
    s"\t support: $support," +
      s"\t Support Set:${spset} ${spset.getClass.getName}" +
      s"\t confidence: $confidence," +
      s"\t conciseness: $conciseness,"
  }

  // consider
  def isEmpty: Boolean = spset.size == 0

  def update(other: Statistics): Statistics = {
    spset = other.spset
    support = other.support
    confidence = other.confidence
    conciseness = other.conciseness

    this
  }

  def setSupp(supp: BigInt) = {
    this.support = supp
  }

  def getSupp = this.support

  def setSpSet(spset: ISupportSet) = this.spset = spset

  def clearSpSet = this.spset = Universe()
}

object Statistics {
  def apply(support: BigInt, spset: ISupportSet,
            confidence: Double, conciseness: Double): Statistics = {

    new Statistics(support, spset, confidence, conciseness)
  }

  def apply(support: BigInt, spset: ISupportSet): Statistics = {
    new Statistics(support, spset, 0.0, 0.0)
  }


  def update(stat: Statistics, other: Statistics): Unit = {
    stat.spset = other.spset
    stat.support = other.support
    stat.confidence = other.confidence
    stat.conciseness = other.conciseness
  }


  // create a new instance
  def empty = new Statistics(-1, Universe(), 0.0, 0.0)
}
