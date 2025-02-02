package org.dsl.dataStruct

import org.dsl.emperical.Table
import org.dsl.exception.InterpException
import org.dsl.reasoning.predicate.{TableInstanceAtom, TupleAtom}

import scala.collection.mutable

class Env(private val env: mutable.Map[TupleAtom, TableInstanceAtom]) {

  def update(k: TupleAtom, tableAtom: TableInstanceAtom):Unit  = env.update(k,tableAtom)

  def lookup(k: TupleAtom): TableInstanceAtom  = {
    env.getOrElse(k,  throw InterpException("Tuple Not Declared"))
  }

}

object Env {
  def apply(env: Map[TupleAtom, TableInstanceAtom]): Env = {
    new Env(mutable.Map(env.toArray:_*))
  }

  def empty: Env = {
    new Env(mutable.Map.empty[TupleAtom, TableInstanceAtom])
  }
}
