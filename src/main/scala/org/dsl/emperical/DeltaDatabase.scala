package org.dsl.emperical

import scala.collection.compat._
import org.dsl.dataStruct.Env
import org.dsl.emperical.table.RowTable
import org.dsl.reasoning.predicate.{Condition, TableInstanceAtom, TupleAtom, Where}
import org.dsl.utils.{$, Config}

import scala.collection.immutable.Seq
import scala.collection.mutable


class DeltaDatabase(private val crudDb: Map[TableInstanceAtom, IndexedSeq[CRUD]]) {

  def lookup(k: TableInstanceAtom): IndexedSeq[CRUD] = {
    crudDb(k)
  }

  def lookupFromEnv(t: TupleAtom, env: Env): IndexedSeq[CRUD] = {
    val schm = env.lookup(t)
    this.lookup(schm)
  }

  def foreach[U](f: ((TableInstanceAtom, IndexedSeq[CRUD])) => U): Unit = {
    crudDb.foreach(f)
  }

  def getData: Map[TableInstanceAtom, IndexedSeq[CRUD]] = crudDb

  // todo: should be process before interp rules
  private def splitPosNeg(journal: IndexedSeq[CRUD]): (RowTable, IndexedSeq[Int]) = {
    val posData = RowTable.empty
    val negData = IndexedSeq.empty[Int]

    journal.foreach {
      case Insert(row) =>
        val id = row.head._2.toInt

        posData.addRow(id, row)

      case Delete(index) => negData :+ index
    }

    (posData, negData)
  }

  private def splitPosNegRec: Map[TableInstanceAtom, (RowTable, IndexedSeq[Int])] = {
    crudDb.map {
      case (schm, journal) => schm -> splitPosNeg(journal)
    }
  }

  def toPosDBAndDelJournal: (Database, Map[TableInstanceAtom, IndexedSeq[Int]]) = {
    val m = splitPosNegRec
    val n = Database(mutable.Map.from(m.map { case (k, v) => k -> (v._1) })
    )
    val l = m.map { case (k, v) => k -> v._2 }
    (n, l)
  }


}


object DeltaDatabase {

  /**
   * Given a list of expressions as predicate like SQL,
   * Returns tuples to be deleted.
   *
   * @param where where clause
   * @return index of deleted rows
   */
  def evalPredicate(where: Where, db: Database): List[Int] = {
    where match {
      case Condition(op, schm, col, const) =>
        DeltaDatabaseOps.EvalWhereClause(op, schm, col, const, db)
    }
  }

  def apply(db: Database): DeltaDatabase = {
    db.toDeltaDB
  }

  def apply(crudSeq: Map[TableInstanceAtom, IndexedSeq[CRUD]]): DeltaDatabase =
    new DeltaDatabase(crudSeq)


  def main(args: Array[String]): Unit = {
    val path = Config.addProjPath("/datasets/airport_full.csv")
    val tableName = $.GetDSName(path)
    val db = $.LoadDataSet(tableName, path)



  }
}

trait CRUD

case class Insert(row: List[(String, String)]) extends CRUD


/**
 * Initially we only support AND clause lists
 *
 * @param wheres
 */
case class Delete(wheres: List[Where]) extends CRUD
