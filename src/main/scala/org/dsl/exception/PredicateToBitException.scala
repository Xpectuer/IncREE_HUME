package org.dsl.exception

import org.dsl.reasoning.predicate.Expression

case class PredicateToBitException(msg:String, predicate: Expression) extends HumeException {
  override def getMsg: String =
    s"""
       |PredicateToBitException with predicate: ${predicate}
       |Error Message: ${this.msg}""".stripMargin
}
