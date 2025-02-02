package org.dsl.emperical.table

import scala.collection.compat._
import com.amazonaws.services.s3.model.S3Object
import com.github.tototoshi.csv.CSVReader
import org.dsl.dataStruct.RBTreeMap
import org.dsl.emperical.{CRUD, Insert, Table}
import org.dsl.reasoning.predicate.ColumnAtom
import org.dsl.utils.Config

import java.io.File
import java.util.UUID
import scala.collection.mutable
import scala.io.Source

class RowTable(private val name: String,
               private val data: RBTreeMap[List[(String, String)]]) extends Table {
  override def getHeader: IndexedSeq[ColumnAtom] = data.getTreeMap.head._2.map(t => ColumnAtom(t._1)).toIndexedSeq

  override def rowNum: Int = data.size

  override def colNum: Int = data.getTreeMap.head._2.length

  def getName: String = name

  def getData: RBTreeMap[List[(String, String)]] = data


  def getVal(idx: Int, col: ColumnAtom): String = {
    data.lookup(idx) match {
      case None => "-1"
      case Some(v) => val t = v.map {
        case (col1, value) if col1 == col.getValue => value
      }
        t.head
    }
  }

  def getRow(idx: Int): List[(String, String)] =
    data.lookup(idx).getOrElse(List("NULL" -> "NULL"))

  def addRow(id: Int, row: List[(String, String)]): Unit = data.getTreeMap.+=((id, row))

  def foreach(f: ((Int, List[(String, String)])) => Unit): Unit = data.getTreeMap.foreach(f)

  // Transform the RowTable into a column-first table.
  @deprecated
  def transpose: ColTable = {
    // Transpose the map
    // todo: rb tree
    val treeMap = data.getTreeMap

    val ckpt1 = treeMap
      .flatMap {
        case (row, colMap) =>
          colMap.map {
            case (col, value) => col -> Seq(row -> value)
          }
      }

    val ckpt2 = ckpt1.groupBy { case (col, _) => col }
      .map { case (col, pairs)
      => col -> mutable.TreeMap(pairs.map { case (_, valueSeq) => valueSeq.head }.toArray: _*)
      }

    val ckpt3: Map[String, RBTreeMap[String]] = ckpt2.map { case (k, v) => k -> RBTreeMap(v) }

    ColTable.from(name, ckpt3, getHeader.toList)
  }

  def toCRUDSeq: mutable.Iterable[CRUD] = {
    data.getTreeMap.map {
      case (_, v) => Insert(v)
    }
  }

  // Add other methods here to manipulate or analyze the tabular data if needed

  def merge(other: RowTable): RowTable = {
    other.data.foreach(p => data.addOne(p))
    this
  }

  override def toString: String = {

    val header = data.getTreeMap.headOption.map(_._2.mkString(",")).getOrElse("")

    val rows = data.getTreeMap.map(_._2.mkString(","))
    name + " : " + (header + rows).mkString("\n")
  }

}

object RowTable {

  def empty(name: String): RowTable = {
    val content = RBTreeMap.empty[List[(String, String)]]
    new RowTable(name, content)
  }


  def apply(s3Obj: S3Object): RowTable = {
    val content = parseCSV(s3Obj)
    val tableName = s3Obj.getKey.split("/").last
    new RowTable(tableName, content)
  }

  private def parseCSV(s3Obj: S3Object) = {
    val iStream = s3Obj.getObjectContent
//    val lines = Source.fromInputStream(iStream).getLines().toList
//
//
//    val r: List[(Int, List[(String, String)])] = toRowData(lines)
    val source = Source.fromInputStream(iStream)
    val csvReader = CSVReader.open(source)
    val r = toRowData(csvReader.all())
    val rbtree = RBTreeMap(r)
    rbtree
  }


  private def toRowData(lines: List[List[String]]) = {
    val heads = lines.head
    val dataIndex = lines.tail.zipWithIndex


    val r = dataIndex.map {
      case (data, i)
      => i ->
        data.zipWithIndex.map {
          case (d, i) => (heads(i), d)
        }
    }
    r
  }

  private def parseCSV(filePath: String) = {

//    val source = Source.fromFile(filePath)
//
//
//    val lines = source.getLines().toList
//    source.close()
//
//
//
//
//
//    val r = toRowData(lines)

    val csvReader = CSVReader.open(new File(filePath))

    val protoData = csvReader.all()

    val r = toRowData(protoData)

    val rbtree = RBTreeMap(r)
    rbtree
  }

  def apply(filePath: String): RowTable = {

    val name = filePath.split("/").last
    val rbtree = parseCSV(filePath)

    new RowTable(name, rbtree) // Reversing the list as we are prepending elements

  }

  def apply(name: String, rows: Map[Int, List[(String, String)]]): RowTable = {
    val rbtree = RBTreeMap(rows.toSeq)
    new RowTable(name, rbtree)
  }

  def apply(name: String, rbtree: RBTreeMap[List[(String, String)]]): RowTable = {
    new RowTable(name, rbtree)
  }

  def unapply(rowTable: RowTable): Option[(String, RBTreeMap[List[(String, String)]])] = Some(rowTable.getName, rowTable.getData)


  def empty: RowTable = empty("anonymous table@" + UUID.randomUUID())
}

object Main {
  def main(args: Array[String]): Unit = {

    val filePath = Config.PROJ_PATH + "/projects/java_proj/HUME/datasets/airport.test.csv"


    val table = RowTable(filePath)
    println(table.getData.head)
    // println(table.transpose)

  }
}




