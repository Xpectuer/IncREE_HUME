package org.dsl.emperical.table

import org.apache.commons.collections4.MultiSet
import org.apache.commons.collections4.multiset.HashMultiSet
import org.dsl.reasoning.predicate.HumeType.{HFloat, HInt, HLong, HumeType}
import org.dsl.reasoning.predicate.TypedColumnAtom

import scala.collection.mutable.{ArrayBuffer, ListBuffer}


// todo: make immutable data structures
class TypedColumn[T](private val tableName: String,
                     private var col: TypedColumnAtom,
                     private val valueSet: MultiSet[T],
                     private val values: ArrayBuffer[T],
                     private val index: Int) extends Serializable {
  def getSharedPercent(other: TypedColumn[_]): Double = {
    var totalCount = 0
    var sharedCount = 0

    valueSet.entrySet().forEach(e=> {
      val thisCount = valueSet.getCount(e.getElement)
      val otherCount = other.valueSet.getCount(e.getElement)
      sharedCount += math.min(thisCount, otherCount)
      totalCount += math.max(thisCount, otherCount)
    })

    sharedCount.toDouble / totalCount.toDouble
  }


  def getTypedColumnAtom: TypedColumnAtom = col

  def addLine(value: T): Unit = {
    valueSet.add(value)
    values.+=(value)
  }

  def get(tid: Int):Option[T] ={
    values.lift(tid)
  }

  def getValueSet: MultiSet[T] = valueSet

  def getName: String = col.getValue

  def getTableName: String = tableName

  def getIndex: Int = index

  def getType: HumeType = col.htype

  def getValues: IndexedSeq[T] = {
    values
  }

  private lazy val _avg = {
    _getAverage()
  }

  def getAverage: Double = _avg

  private def _getAverage(): Double = {
    val size = values.size
    getType match {
      case HFloat =>
        values.view.map(e=>e.asInstanceOf[Double] / size).sum

      case HInt =>
        values.view.map(e=>e.asInstanceOf[Int].toDouble / size).sum

      case HLong =>
        values.view.map(e=>e.asInstanceOf[Long].toDouble / size).sum
    }


  }

  override def toString: String = s"$getName,$getType,${values.take(5).mkString(", ")}"

  def setColumnAtom(columnAtom: TypedColumnAtom): Unit = col = columnAtom
}

object TypedColumn {
  def apply[T <: Comparable[T]](tableName: String,
                                col: TypedColumnAtom,
                                valueSet: MultiSet[T],
                                values: ArrayBuffer[T],
                                index: Int): TypedColumn[T] = {
    new TypedColumn[T](tableName, col, valueSet, values, index)
  }

  def empty[T](tableName: String,
               col: TypedColumnAtom,
               index: Int): TypedColumn[T] = {

    val valueSet = new HashMultiSet[T]()
    val values = ArrayBuffer.empty[T]

    new TypedColumn[T](tableName, col, valueSet, values, index)
  }
}
