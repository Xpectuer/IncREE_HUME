//package org.dsl.emperical.pli
//
//import org.dsl.emperical.table.RowTable
//import org.dsl.emperical.Table
//import org.dsl.emperical.dag.Dag
//import org.dsl.reasoning.predicate.{ColumnAtom, ConstantAtom}
//
//import scala.collection.mutable
//
//
//class Bucket(private val name: String,
//             private val data: mutable.Map[ColumnAtom, mutable.Map[ConstantAtom, Int]]) extends Table {
//
//  private var origin: RowTable = RowTable.empty
//
//
//  private val dag: Dag = Dag()
//
//  def getDag: Dag = dag
//
//  def setOrigin(rowTable: RowTable): Unit = origin = rowTable
//
//  def getOrigin: RowTable = origin
//
//  def getData = data
//
//  private val totalKind = {
//    data.map { case (_, v) => v.keys.size }.sum
//  }
//
//  override def getHeader: Seq[ColumnAtom] = data.keys.toSeq
//
//  private var _size: Int = -1
//
//  // size = #rows of data in RowTable
//  override def rowNum: Int =
//    if (_size < 0) {
//      _size = data.head._2.map { case (_, cnt) => cnt }.sum
//      _size
//    } else {
//      _size
//    }
//
//  override def getName: String = name
//
//  private val entropyCache = mutable.Map.from(data.keys.map(e => e -> (-1.0)))
//
//  def getEntropy(col: ColumnAtom): Double = {
//    val entropy = entropyCache.getOrElse(col, -1.0)
//    if (entropy > 0) {
//      entropy
//    } else {
//      val newEntropy = data.getOrElse(col, mutable.Map.empty[ConstantAtom, Int])
//        .map {
//          case (_, cnt) =>
//            val p = cnt.toDouble / rowNum.toDouble
//            -p * math.log(p)
//        }.sum
//
//      entropyCache.update(col, newEntropy)
//      newEntropy
//    }
//
//  }
//
//  def getInfoQ(col: ColumnAtom, value: ConstantAtom): Double = {
//    val count = data
//      .getOrElse(col, mutable.Map.empty[ConstantAtom, Int])
//      .getOrElse(value, 0)
//
//    count.toDouble / rowNum.toDouble
//  }
//
//  def getCount(col: ColumnAtom, value: ConstantAtom): Int = {
//    val columnData = data.getOrElse(col, throw new NoSuchElementException)
//    val valueCount = columnData.getOrElse(value, throw new NoSuchElementException)
//    valueCount
//  }
//
//  def getCol(col: ColumnAtom): Seq[(ConstantAtom, Int)] = {
//    data.getOrElse(col, Seq()).toSeq
//  }
//
//  private def applyOnEntry(f: Int => Int)(col: ColumnAtom, value: ConstantAtom): Unit = {
//    val columnData = data.getOrElse(col, {
//      data.update(col, mutable.Map.empty[ConstantAtom, Int])
//      data.getOrElse(col, mutable.Map.empty[ConstantAtom, Int])
//    })
//
//
//    val valueCount: Int = columnData.getOrElse(value, {
//      columnData.update(value, 0)
//      0
//    })
//
//    val newValue = f(valueCount)
//    columnData.update(value, newValue)
//  }
//
//
//  def add1: (ColumnAtom, ConstantAtom) => Unit = applyOnEntry(i => i + 1)
//
//  def sub1: (ColumnAtom, ConstantAtom) => Unit = applyOnEntry(i => i - 1)
//
//  override def toString: String = data.toString
//
//  override def colNum: Int = data.keys.size
//}
//
//object Bucket {
//
//  private def apply(name: String,
//                    data: mutable.Map[String, mutable.Map[String, Int]]): Bucket = {
//    val a = data.map {
//      case (k, v) => ColumnAtom(k) ->
//        v.map { case (k1, v1) => ConstantAtom(k1) -> v1 }
//    }
//
//    new Bucket(name, mutable.Map.from(a))
//  }
//
//  private def empty(name: String): Bucket = {
//    val m = mutable.Map.empty[String, mutable.Map[String, Int]]
//    apply(name, m)
//  }
//
//  def from(r: RowTable): Bucket = {
//    val rowData = r.getData.getTreeMap
//
//    val bucket = Bucket.empty(r.getName)
//    bucket.setOrigin(r)
//
//    for (row <- rowData) {
//      for ((col, value) <- row._2) {
//        bucket.add1(
//          ColumnAtom(col),
//          ConstantAtom(value))
//      }
//    }
//
//    bucket
//  }
//
//
//}
