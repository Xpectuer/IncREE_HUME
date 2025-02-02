package org.dsl.emperical.pli

import gnu.trove.map.TIntIntMap
import gnu.trove.map.hash.TIntIntHashMap
import gnu.trove.set.hash.TIntHashSet
import org.dsl.emperical.table.TypedColTable
import org.dsl.pb.ProgressBar
import org.dsl.reasoning.predicate.HumeType._
import org.dsl.utils.{TupleIDProvider, Wrappers}

import scala.collection.mutable


@SerialVersionUID(777777L)
class TypedPLISet(name: String,
                  columns: Array[ITypedPLI]) extends Serializable {
  def getName = name

  def get(i: Int): ITypedPLI = columns(i)

  override def toString: String = columns.mkString("\n\n").toString
}

object TypedPLISet {
  def from(typedColTable: TypedColTable): TypedPLISet = {
    // list pli
    val COLUMN_COUNT = typedColTable.colNum
    val ROW_COUNT = typedColTable.rowNum

    val tIDs: IndexedSeq[Int] = TupleIDProvider(ROW_COUNT).gettIDs

    val indexMat: Array[Array[Int]] = typedColTable.indexMat

    val pb = new ProgressBar(indexMat.length)

    val pliList =
        for ((colArray, col) <- indexMat.zipWithIndex) yield {
//    for ((colArray, col) <- indexMat.zipWithIndex.par) yield {
      Wrappers.progressBarWrapperRet({

        val distincts = new TIntHashSet()
        for (e <- colArray) {
          distincts.add(e)
        }

        val distinctsArray = distincts.toArray
        // sort
        val distinctsArraySORTED = if (!(typedColTable.getColumn(col).getType == HString)) {
          distinctsArray.sorted
        } else {
          distinctsArray
        }

        val translator: TIntIntMap = new TIntIntHashMap()
        for (i <- distinctsArraySORTED.indices) {
          translator.put(distinctsArraySORTED(i), i)
        }

        val setPlis = mutable.IndexedSeq.fill(distinctsArray.length)(mutable.TreeSet.empty[Int])

        for (line <- 0 until ROW_COUNT) {
          // global index to column local index
          val tid = tIDs(line)
          setPlis(translator.get(indexMat(col)(line))).add(tid)
        }

        //val setPlis = setPlis.map(_.toSet).toIndexedSeq
        // todo:
        val values = Array(colArray:_*)

        val toAdd = if (!(typedColTable.getColumn(col).getType == HString)) {
          TypedListedPLI.from(setPlis, ROW_COUNT, numerical = true, values)
        } else {
          TypedListedPLI.from(setPlis, ROW_COUNT, numerical = false, values)
        }

        toAdd

      }, pb)


    }

    new TypedPLISet(typedColTable.getName, pliList.toArray)
  }


}