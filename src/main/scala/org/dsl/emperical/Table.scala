package org.dsl.emperical

import scala.collection.compat._
import org.dsl.reasoning.predicate.ColumnAtom

trait Table {
  def getHeader: Iterable[ColumnAtom]

  def rowNum: Int

  def colNum: Int

  def getName: String
}


object NilTable extends Table {
  override def getHeader: IndexedSeq[ColumnAtom] = ???

  override def rowNum: Int = ???

  override def colNum: Int = ???

  override def getName: String = ???
}

object Table {
  def Nil = NilTable
}