package org.dsl.emperical.table

import org.dsl.emperical.Table
import org.dsl.emperical.pli.{ITypedPLI, TypedPLISet}
import org.dsl.exception.{EmpericalException, ParserException}
import org.dsl.reasoning.predicate.HumeType._
import org.dsl.reasoning.predicate.{ColumnAtom, TypedColumnAtom}
import org.dsl.utils.{HUMELogger, IndexProvider, Wrappers}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class TypedColTable(private val name: String,
                    private val columns: IndexedSeq[TypedColumn[_]],
                    private val lineCount: Int,
                    private val col2IndexMap: Map[TypedColumnAtom, Int],
                    private val providers: Map[HumeType, IndexProvider[_ >: String with Int with Long with Double]])

  extends Table with Iterable[TypedColumn[_]] with Serializable {


  // TODO: entropy



def getConst[T](_type: HumeType, valIdx: Int): T = {
  val provider: IndexProvider[T] = providers
    .getOrElse(_type, IndexProvider.Nil).asInstanceOf[IndexProvider[T]]
  provider.getObject(valIdx)
}

val logger: HUMELogger = HUMELogger(getClass.getName)
private val _indexMat: Array[Array[Int]] = getIndexedVals

private val _pliSet: TypedPLISet = {
  logger.debug("Setting PLI SET")
  buildPLISet
}

def getPli(columnAtom: TypedColumnAtom): ITypedPLI = {
  val idx = col2IndexMap.getOrElse(columnAtom, ???)
  _pliSet.get(idx)
}

private def buildPLISet: TypedPLISet = {
  val (r, time) = Wrappers.timerWrapperRet {
    TypedPLISet.from(this)
  }
  logger.info(s"Build PLI with Time ${time}(s).")
  r
}


def getStrProvider: IndexProvider[String]
= providers.getOrElse(HString, IndexProvider.Nil).asInstanceOf[IndexProvider[String]]

// todo: maybe a mapping here
override def getHeader: IndexedSeq[ColumnAtom] = columns.map(c => ColumnAtom(c.getName))

override def rowNum: Int = lineCount

override def colNum: Int = columns.length

override def getName: String = name

override def size: Int = rowNum

def indexMat: Array[Array[Int]] = _indexMat


def lookupProvider[T](htype: HumeType): IndexProvider[T] = {
  providers.getOrElse(htype, throw EmpericalException("Provider does not exists!")).asInstanceOf[IndexProvider[T]]
}

private def getIndexedVals: Array[Array[Int]] = {
  val m = mutable.Map.empty[HumeType, IndexProvider[_]]
  val l = for (typedCol <- columns) yield {
    typedCol.getType match {
      case HString =>
        val ts = typedCol.asInstanceOf[TypedColumn[String]]
        val provider = lookupProvider[String](HString)

        m.update(HString, provider)
        getIndexVal[String](ts, provider)
      case HInt =>
        val ti = typedCol.asInstanceOf[TypedColumn[Int]]
        val provider = lookupProvider[Int](HInt)
        val by: (Int, Int) => Boolean = (a, b) => a < b
        val sortedProvider = IndexProvider.getSorted(provider)(by)
        m.update(HInt, sortedProvider)
        getIndexVal[Int](ti, sortedProvider)
      case HFloat =>
        val tf = typedCol.asInstanceOf[TypedColumn[Double]]
        val provider = lookupProvider[Double](HFloat)

        val by: (Double, Double) => Boolean = (a, b) => a < b
        val sortedProvider = IndexProvider.getSorted(provider)(by)
        m.update(HFloat, sortedProvider)
        getIndexVal[Double](tf, sortedProvider)
      case HLong =>
        val tf = typedCol.asInstanceOf[TypedColumn[Long]]
        val provider = lookupProvider[Long](HLong)

        val by: (Long, Long) => Boolean = (a, b) => a < b
        val sortedProvider = IndexProvider.getSorted(provider)(by)
        m.update(HLong, sortedProvider)
        getIndexVal[Long](tf, sortedProvider)

    }
  }

  l.toArray
}

private def getIndexVal[T](typedColumn: TypedColumn[T], provider: IndexProvider[T]): Array[Int] = {
  val colList = typedColumn.getValues
  val idxList = colList.map(
    e => provider.getOrElse(e, throw EmpericalException("No Such Index in Provider.")))
  idxList.toArray
}


override def toString: String = s"${columns.mkString("\n\n\n")}"

override def iterator: Iterator[TypedColumn[_]] = columns.iterator

def getColumn(columnAtom: TypedColumnAtom): TypedColumn[_] =
  col2IndexMap.get(columnAtom) match {
    case Some(i) => columns(i)
    case None => ???
  }

def getColumnIdxValVector(columnAtom: TypedColumnAtom) = {
  col2IndexMap.get(columnAtom) match {
    case Some(i) => indexMat(i).toIndexedSeq
    case None => ???
  }
}


def getColumns: IndexedSeq[TypedColumn[_]] = columns

def getColumn(i: Int): TypedColumn[_] = columns(i)

def getColumnAtom(i: Int): TypedColumnAtom = columns(i).getTypedColumnAtom
}

object TypedColTable {

  def parseTypedColumn(column: ColumnAtom): TypedColumnAtom = {
    val columnWithType1 = column.getValue
    val htypePattern = "\\(.*\\)".r
    val s = htypePattern.findFirstIn(columnWithType1) match {
      case Some(s) => s // kill parenthesis
      case None => throw ParserException(s"No Type Annotation in Column $column. Please Check the dataset!!! ")
    }

    val htype = s match {
      case "(Integer)" => HInt
      case "(String)" => HString
      case "(Long)" => HLong
      case "(Float)" => HFloat
      case "(Double)" => HFloat
    }

    TypedColumnAtom(columnWithType1, htype)
  }


  private def castColType(raw: mutable.Iterable[String],
                          tableName: String,
                          tColAtom: TypedColumnAtom,
                          index: Int) = {

    def cast[E](f: String => E) = {
      val typedColumn = TypedColumn.empty[E](tableName, tColAtom, index)
      for (e <- raw) {
        val ee = f(e)
        typedColumn.addLine(ee)
      }

      typedColumn
    }


    tColAtom.htype match {
      case HString =>
        cast(s => s)
      case HInt =>
        cast(s => try {
          s.toInt
        } catch {
          case _: NumberFormatException => Int.MinValue
          case e: Throwable => throw e
        })
      case HLong =>
        cast(s => try {
          s.toLong
        } catch {
          case _: NumberFormatException => Long.MinValue
          case e: Throwable => throw e
        })
      case HFloat =>
        cast(s => try {
          s.toDouble
        } catch {
          case _: NumberFormatException => Double.MinValue
          case e: Throwable => throw e
        })
    }
  }


  private def createProviders(typedCols: Iterable[TypedColumn[_ >: String with Int with Long with Double]]) = {
    val m = mutable.Map.empty[HumeType, ListBuffer[_]]

    def f[T](humeType: HumeType, col: TypedColumn[T]) = {
      val buf: ListBuffer[T] =
        m.getOrElseUpdate(humeType, ListBuffer.empty[T])
          .asInstanceOf[ListBuffer[T]]
      val _ = buf.++=(col.getValues)
    }

    for (col <- typedCols) {
      val t = col.getType
      t match {
        case HString => f[String](t, col.asInstanceOf[TypedColumn[String]])
        case HInt => f[Int](t, col.asInstanceOf[TypedColumn[Int]])
        case HLong => f[Long](t, col.asInstanceOf[TypedColumn[Long]])
        case HFloat => f[Double](t, col.asInstanceOf[TypedColumn[Double]])
      }
    }

    m.map {
      case (t, col) =>
        t match {
          case HString => t -> IndexProvider[String](col.toList.asInstanceOf[List[String]])
          case HInt => t -> IndexProvider[Int](col.toList.asInstanceOf[List[Int]])
          case HLong => t -> IndexProvider[Long](col.toList.asInstanceOf[List[Long]])
          case HFloat => t -> IndexProvider[Double](col.toList.asInstanceOf[List[Double]])
        }
    }.toMap
  }

  def from(coltable: ColTable): TypedColTable = {
    val name = coltable.getName
    val size = coltable.rowNum

    var idx = 0
    val m = mutable.Map.empty[TypedColumnAtom, Int]
    val typedCols = coltable.getHeader.map {

      col =>
        val colRaw = coltable.getCol(col)

        val typedColAtom = parseTypedColumn(col)
        val typedCol = castColType(colRaw, coltable.getName, typedColAtom, idx)


        m.update(typedColAtom, idx)
        idx = idx + 1
        typedCol
    }

    val providers = createProviders(typedCols)


    new TypedColTable(name, typedCols.toVector, size, m.toMap, providers)
  }

  def from(rowTable: RowTable): TypedColTable = {
    val coltable = rowTable.transpose
    from(coltable)
  }

  private val COMPARE_AVG_RATIO = 0.3D

  private val MIN_SHARED_RATIO = 0.3D

  def isJoinable(table: TypedColTable, col1: TypedColumnAtom, col2: TypedColumnAtom): Boolean = {
    val c1 = table.getColumn(col1)
    val c2 = table.getColumn(col2)
    if (col1.htype != col2.htype) false
    else c1.getSharedPercent(c2) > MIN_SHARED_RATIO
  }

  def isComparable(table: TypedColTable, c1: TypedColumnAtom, c2: TypedColumnAtom): Boolean = {
    if (c1.htype != c2.htype) {
      false
    } else {
      // => type1 = type2
      c1.htype match {
        case HLong | HFloat | HInt =>
          if (c1 eq c2) true
          else {
            val avg1 = table.getColumn(c1).getAverage
            val avg2 = table.getColumn(c2).getAverage
            math.min(avg1, avg2) / math.max(avg1, avg2) > COMPARE_AVG_RATIO
          }
        case HString => false

      }


    }

  }
}