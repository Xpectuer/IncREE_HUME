package org.dsl.emperical


import org.dsl.dataStruct.Env
import org.dsl.emperical.table.{ColTable, RowTable}
import org.dsl.reasoning.predicate.{TableInstanceAtom, TupleAtom}
import org.dsl.utils.HUMELogger

import scala.collection.mutable

// scheme -> instanceMap( name -> tableInstance)
class Database(private val db: mutable.Map[TableInstanceAtom, Table]) {
  def addOne(table: Table): Unit = {
    val tAtom = TableInstanceAtom(table.getName)
    db.update(tAtom, table)
  }


  def get(instanceName: TableInstanceAtom): Option[Table] = {
    db.get(instanceName)
  }

  def getOrElse(instanceName: TableInstanceAtom, f: => Table): Table =
    this.get(instanceName) match {
      case None => f
      case Some(v) => v
    }

  def mapExtract[K2, V2](f: ((TableInstanceAtom, Table)) => (K2, V2)): Map[K2, V2]
  = db.map(f).toMap

  def getDB: mutable.Map[TableInstanceAtom, Table] = db

  def getFromEnv(t: TupleAtom, env: Env): Option[Table] = {
    val tAtom = env.lookup(t)
    this.get(tAtom)
  }

  def getFromEnvOrElse(t: TupleAtom, env: Env, f: => Table) = {
    this.getFromEnv(t, env) match {
      case None => f
      case Some(t) => t
    }
  }

  def foreach[U](f: ((TableInstanceAtom, Table)) => U): Unit = {
    db.foreach(f)
  }


  def toDeltaDB: DeltaDatabase = {
    val delta = db.map {
      case (k, v) =>
        val record = v match {
          case r: RowTable =>
            r.toCRUDSeq
          case ColTable(name, _) => throw DatabaseException("ColTable Not Supported." + s"$name")
        }
        k -> record.toIndexedSeq
    }

    new DeltaDatabase(delta.toMap)
  }

  def size: Int = {
    db.map { case (_, v) => v.rowNum }.toSeq.sum
  }

  override def toString: String = s"<Database:${
    db.map {
      case (_, v) => v match {
        case ColTable(name, _) => "ColTable: " + name
        case RowTable(name, _) => "RowTable: " + name
      }
    }.mkString(" || ")
  }>"
}

object Database {


  val logger: HUMELogger = HUMELogger(getClass.getName)

  def apply(db: mutable.Map[TableInstanceAtom, Table]): Database = {
    new Database(db)
  }


}

case class DatabaseException(message: String) extends Exception