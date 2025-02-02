package org.dsl.dataStruct

import org.dsl.emperical.Database
import org.dsl.reasoning.predicate.TableInstanceAtom

object IdxPair {
  type IdxPair = ((TableInstanceAtom, Int), (TableInstanceAtom, Int))

  def pairComp(idxp1: IdxPair, idxp2: IdxPair): Boolean = idxp1 == idxp2

  def pairSchm(idxp: IdxPair): (TableInstanceAtom, TableInstanceAtom) = (idxp._1._1, idxp._2._1)

  def pairIdx(idxp: IdxPair): (Int, Int) = (idxp._1._2, idxp._2._2)
}
