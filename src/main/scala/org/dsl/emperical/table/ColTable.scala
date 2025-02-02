package org.dsl.emperical.table

import scala.collection.compat._
import com.amazonaws.services.s3.model.S3Object
import com.github.tototoshi.csv.CSVReader
import org.dsl.dataStruct.RBTreeMap
import org.dsl.emperical.Table
import org.dsl.reasoning.predicate.ColumnAtom

import java.io.File
import scala.io.Source


// fixme: column name not ordered sequential
class ColTable(private val name: String,
               private val data: Map[String, RBTreeMap[String]],
               private val heads: List[ColumnAtom]) extends Table {
  def batchDrop(neg: Seq[Int]): Unit = neg.foreach(i => data.foreach {
    case (_, colMap) => colMap.remove(i)
  })

  def merge(other: ColTable): ColTable = {
    other.getData.toSeq.foreach {
      case (col, colMap) => data(col).merge(colMap)
    }
    this
  }

  def rowNum: Int = data.head._2.size

  override def colNum: Int = data.size

  def getData: Map[String, RBTreeMap[String]] = data

  def getCol(col: String): RBTreeMap[String] = {
    data(col)
  }

  def getCol(col: ColumnAtom): RBTreeMap[String] = {
    data(col.getValue)
  }


  def getName: String = name

  override def toString: String = {
    data.keys.map(k =>
      k + " || " + data(k)
    ).mkString("\n")
  }

  // override def getHeader: Seq[_ <: Expression] = data.keys.map(k=>ColumnAtom(k)).toList

  override def getHeader: IndexedSeq[ColumnAtom] = heads.toIndexedSeq

}

object ColTable {
  def apply(rowTable: RowTable): ColTable = {
    rowTable.transpose
  }


  // todo: smaller space used
  private def readCsv(filepath: String) = {

    val csvReader = CSVReader.open(new File(filepath))
    val lines = csvReader.all()
    toColTable(lines)
  }

  private def toColTable(lines: List[List[String]]) = {

    val heads = lines.head
    val data = lines.tail

    val dataIndex = data.zipWithIndex

    val content = heads.zipWithIndex.map { case (columnName, i) =>
      val columnData = dataIndex.map { case (line, rowId) =>
        val stringValue = line(i)
        (rowId, stringValue)
      }
      (columnName, columnData)
    }.toMap

    (heads, content)
  }

  private def parseCSV(s3Obj: S3Object) = {
    val iStream = s3Obj.getObjectContent
    val src = Source.fromInputStream(iStream)

    val csvReader = CSVReader.open(src)
    val lines = csvReader.all()

    toColTable(lines)
  }

  def apply(csvpath: String) = {

    //println("dataPath", csvpath)
    val name = csvpath.split("/").last
    val (heads, dataView) = readCsv(csvpath)
    val data = dataView.map {
      case (k, v) => k -> RBTreeMap(v)
    }

    val heads1 = heads.map(ColumnAtom)

    new ColTable(name, data, heads1)
  }


  def apply(s3Obj: S3Object) = {
    val (heads, dataV) = parseCSV(s3Obj)
    val tableName = s3Obj.getKey.split("/").last
    val data = dataV.map(e => e._1 -> RBTreeMap(e._2))

    new ColTable(tableName, data, heads.map(ColumnAtom).toList)
  }

  def from(name: String, data: Map[String, RBTreeMap[String]], heads: List[ColumnAtom]): ColTable = new ColTable(name, data, heads)

  def unapply(colTable: ColTable): Option[(String, Map[String, RBTreeMap[String]])] = Some(colTable.getName, colTable.getData)


}