package org.dsl.reasoning.predicate

import scala.collection.compat._
import org.dsl.exception.PredicateToBitException
import org.dsl.mining.PredSpace.logger
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider

import scala.collection.mutable

/**
 * Employs BitSet from Scala Library
 *
 * @param bitSet bitSet
 * @param p2i    IndexProvider Of Predicate Space
 */


@SerialVersionUID(114514L)
class PredicateSet(private val bitSet: mutable.BitSet,
                   private val p2i: PredicateIndexProvider)
  extends Iterable[Expression] with Serializable {
  def or(other: PredicateSet): PredicateSet = PredicateSet.from(this.bitSet | other.getBitSet)(p2i)


  def getP2I = p2i
  def getId(expr: Expression)
  = {
    p2i.getOrElse(expr, -1)
  }

  def subsets(n: Int): Iterator[PredicateSet] = {
    bitSet.subsets(n).map(PredicateSet.from(_)(p2i))
  }

  def dist(other: PredicateSet): Int = {
    assert(this.size == other.size)
    (this.getBitSet diff (this.getBitSet & other.getBitSet)).size
  }


  def hasConflict(pred: Expression): Boolean = {
    pred match {
      case TypedTupleBin(op, t0, t1, col1, col2) =>
        val nExpr = TypedTupleBin(getNegation(op), t0, t1, col1, col2)
        this.contains(nExpr)
      case _ => ???
    }
  }

  def contains(pred: Expression): Boolean = {
    val idx = getIndex(pred)
    bitSet(idx)
  }

  private def getNegation(op: Operator): Operator = {
    op match {
      case Eq => NEq
      case Ge => Lt
      case Gt => Le
      case Lt => Ge
      case Le => Gt
      case NEq => Eq
    }
  }


  def isSubsetOf(p: PredicateSet): Boolean = this.bitSet.subsetOf(p.bitSet)

  override def size: Int = bitSet.size

  override def hashCode(): Int = {
    31 + faster_hashCode
  }

  private def faster_hashCode = {
    var code = 0L

    val mUnits = this.bitSet.toBitMask
    for (ii <- mUnits.indices) {
      code ^= (0x00000000ffffffffL & mUnits(ii))
      code ^= (0x00000000ffffffffL & (mUnits(ii) >>> 32))
    }
    code.toInt
  }

  def xor(fix: PredicateSet): PredicateSet = {
    this.bitSet ^= fix.getBitSet
    this
  }

  /**
   * ^ will copy the original operands
   * @param other operand2
   * @return
   */
  def ^(other: PredicateSet): PredicateSet = {
    val cp = this.copy
    cp xor other
  }

  def and(other: PredicateSet): PredicateSet = {
    PredicateSet.from(this.bitSet & other.getBitSet)(p2i)
  }

  def &(other: PredicateSet): PredicateSet = {
    val cp = this.copy
    cp and other
  }

  def |(other: PredicateSet): PredicateSet = {
    val cp = this.copy
    cp or other
  }


  private def getIndex(eToBeAdded: Expression): Int =
    p2i.get(eToBeAdded) match {
      case None => logger.fatal(s"No Such Element: $eToBeAdded in IndexProvider!", eToBeAdded)
      case Some(i) => i
    }

  def addAndCheckVirgin(e: Expression): Boolean = {
    val index = getIndex(e)
    val isNewAdded = !bitSet(index)
    addOne(e)
    isNewAdded
  }

  def addOne(e: Expression): PredicateSet = {
    val index = try {
      getIndex(e)
    } catch {
      case p: PredicateToBitException =>
        p.printStackTrace()
        logger.fatal(p)

    }

    bitSet(index) = true
    this
  }

  def :+(e: Expression): PredicateSet = {
    val cp = this.copy
    cp.addOne(e)
  }

  def :-(e: Expression): PredicateSet = {
    val cp = this.copy
    val index = getIndex(e)
    cp.getBitSet(index) = false
    cp
  }


  def getBitSet: mutable.BitSet = bitSet


  override def iterator: Iterator[Expression] = bitSet.iterator.map(e => p2i.getObject(e))

  def copy: PredicateSet = {
    val bitsetp: mutable.BitSet = mutable.BitSet.fromSpecific(bitSet)
    PredicateSet.apply(bitsetp, p2i)
  }

  private def faster_equals(set: PredicateSet, thatP: PredicateSet): Boolean = {
    val (b1, b2) = (set.bitSet.toBitMask, thatP.bitSet.toBitMask)

    val len = Math.min(b1.length, b2.length)
    for (ii <- 0 until len) {
      if (b1(ii) != b2(ii)) return false
    }
    for (ii <- len until b1.length) {
      if (b1(ii) != 0L) return false
    }
    for (ii <- len until b2.length) {
      if (b2(ii) != 0L) return false
    }

    true

  }

  override def equals(that: Any): Boolean = {

    that match {
      case thatP: PredicateSet =>
        if (this eq thatP) true
        else {
          this.p2i == thatP.p2i && faster_equals(this, thatP)
        }
      case _ => false
    }
  }

  override def toString(): String = {
    //        var s = new StringBuffer
    //        for (p <- this) {
    //          s.append(p + " ")
    //        }
    //        s.toString
    this.bitSet.mkString("|") + s"(${size})"
  }

  def readable(): String = {
    this.bitSet.map(i => p2i.getObject(i)).mkString(" ^ ")
  }

  def withP2I(otherP2I: PredicateIndexProvider): PredicateSet = {
    PredicateSet.from(this)(otherP2I)
  }


}


object PredicateSet {

  //  implicit case object CanGenerateHashFromPredicateSet extends CanGenerateHashFrom[PredicateSet] {
  //    override def generateHash(from: PredicateSet): Long = from.bitSet.hashCode().toLong
  //  }

  val Nil = new PredicateSet(bitSet = mutable.BitSet.empty, PredicateIndexProviderBuilder.empty)

  def empty(p2i: PredicateIndexProvider): PredicateSet = {
    val bitSet = mutable.BitSet.empty
    new PredicateSet(bitSet, p2i)
  }

  //  def getBitSet(e: Expression)(implicit p2i: PredicateToBitIndex): IBitSet = {
  //    val index = p2i.getIndex(e)
  //    val bitSet = bf.create()
  //    bitSet.set(index)
  //
  //    bitSet
  //  }

  def copy(origin: PredicateSet): PredicateSet = {
    val newBitSet = mutable.BitSet.fromBitMask(origin.getBitSet.toBitMask)
    new PredicateSet(newBitSet, origin.p2i)
  }

  def from(bitset: mutable.BitSet)(implicit p2i: PredicateIndexProvider): PredicateSet = {
    new PredicateSet(bitSet = bitset, p2i = p2i)
  }

  def from(l: Iterable[Expression])(implicit p2i: PredicateIndexProvider): PredicateSet = {

    val bitSet = mutable.BitSet.empty
    for (p <- l) {
      p2i.get(p) match {
        case Some(i) => bitSet(i) = true
        case None => logger.fatal("Predicate Not Found in Mapping!", p)
      }
    }
    new PredicateSet(bitSet, p2i)
  }

  def from(l: Expression)(implicit p2i: PredicateIndexProvider): PredicateSet = {
    from(List(l))(p2i)
  }


  def from(mask: Array[Long])(implicit p2i: PredicateIndexProvider): PredicateSet = {
    val bitSet = mutable.BitSet.fromBitMask(mask)
    from(bitSet)
  }

  def apply(bitSet: mutable.BitSet, p2i: PredicateIndexProvider): PredicateSet = new PredicateSet(bitSet, p2i)
}