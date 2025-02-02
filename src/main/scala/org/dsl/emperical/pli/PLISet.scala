package org.dsl.emperical.pli

import scala.collection.compat._
import org.dsl.emperical.Table
import org.dsl.emperical.table.{ColTable, RowTable}
import org.dsl.reasoning.predicate.{ColumnAtom, ConstantAtom}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

// position list indexes
// Index list is sorted


class PLISet(private val name: String,
             private val data: mutable.Map[ColumnAtom, mutable.Map[ConstantAtom, ListBuffer[Int]]]) extends Table {

  def getData: mutable.Map[ColumnAtom, mutable.Map[ConstantAtom, ListBuffer[Int]]]
  = data

  override def getHeader: Iterable[ColumnAtom] = data.keys


  override def rowNum: Int = -1

  override def getName: String = name

  def getIdxList(col: ColumnAtom, value: ConstantAtom): List[Int] = {
    val columnData = data.getOrElse(col, throw new NoSuchElementException)
    val valueCount = columnData.getOrElse(value, throw new NoSuchElementException)
    valueCount.toList
  }

  def getCol(col: ColumnAtom): Seq[(ConstantAtom, ListBuffer[Int])] = {
    data.getOrElse(col, Seq()).toSeq
  }

  def addOne(col: ColumnAtom, value: ConstantAtom, id: Int): Unit = {

    val columnData = data.getOrElse(col, {
      val newMap = mutable.Map.empty[ConstantAtom, ListBuffer[Int]]

      data.update(col, newMap)
      newMap
    })

    val idxList = columnData.getOrElse(value, {
      val newList = ListBuffer.empty[Int]
      columnData.update(value, newList)
      newList
    })

    idxList.+=(id)

  }

  override def toString: String = data.toString

  override def colNum: Int = data.keys.size
}

object PLISet {

  private def apply(name: String,
                    data: mutable.Map[String, mutable.Map[String, ListBuffer[Int]]]): PLISet = {
    val a = data.map {
      case (k, v) => ColumnAtom(k) ->
        v.map { case (k1, v1) => ConstantAtom(k1) -> v1 }
    }

    new PLISet(name, a)
  }

  private def empty(name: String): PLISet = {
    val m = mutable.Map.empty[String, mutable.Map[String, ListBuffer[Int]]]
    apply(name, m)
  }

  def from(t: Table): PLISet = {
    t match {
      case rowTable: RowTable => from(rowTable)
      case colTable: ColTable => from(colTable)
      case pli: PLISet => pli
    }
  }

  def from(c: ColTable): PLISet = {
    val pli = PLISet.empty(c.getName)

    for ((colName, colData) <- c.getData; value <- colData.getTreeMap) {
      pli.addOne(ColumnAtom(colName), ConstantAtom(value._2), value._1)
    }
    pli
  }

  def from(r: RowTable): PLISet = {

    val rowData = r.getData
    val pli = PLISet.empty(r.getName)

    for (row <- rowData.getTreeMap) {
      for ((col, value) <- row._2) {
        val idx = row._1
        pli.addOne(ColumnAtom(col),
          ConstantAtom(value), idx)
      }
    }

    pli
  }


}

