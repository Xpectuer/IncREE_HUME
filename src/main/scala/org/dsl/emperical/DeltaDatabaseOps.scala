package org.dsl.emperical

import org.dsl.emperical.pli.PLISet
import org.dsl.emperical.table.{ColTable, RowTable}
import org.dsl.reasoning.predicate._

object DeltaDatabaseOps {
  def EvalWhereClause(op: Operator,
                      schm: TableInstanceAtom,
                      col: ColumnAtom,
                      const: ConstantAtom,
                      db: Database): List[Int] = {
    op match {
      case Eq => evalEquality(schm, col, const, db)
      case Gt => evalGreaterThan(schm, col, const)
      case Lt => evalLessThan(schm, col, const)
      case _ => ???
    }
  }

  private def evalEquality(schm: TableInstanceAtom,
                           col: ColumnAtom,
                           const: ConstantAtom,
                           db: Database) = {
    val tableO = db.get(schm)
    tableO match {
      case Some(table) => table match {
        case r: RowTable =>
          (for ((id, _) <- r.getData.getTreeMap if {
            r.getVal(id, col) == const.getValue
          }) yield id).toList

        case _: ColTable => ???
        case pli: PLISet => pli.getIdxList(col, const)
        case _ => ???
      }
      case None => ???

    }

  }

  def evalGreaterThan(schm: TableInstanceAtom,
                      col: ColumnAtom,
                      const: ConstantAtom) = {
    ???
  }

  def evalLessThan(schm: TableInstanceAtom,
                   col: ColumnAtom,
                   const: ConstantAtom) = {
    ???
  }

  def evalGreaterEqual(schm: TableInstanceAtom,
                       col: ColumnAtom,
                       const: ConstantAtom) = {
    ???
  }

  def evalNotEqual(schm: TableInstanceAtom) = {
    ???
  }

}
