package org.dsl.reasoning.predicate

import org.dsl.utils.Config

// type Operator1 = Operator with Product
trait Operator {
  def getEntropyWeight: Double
}

case object BottomOp extends Operator {
  override def hashCode(): Int = (Config.OP_PREFIX + "bottom").hashCode

  override def getEntropyWeight: Double = -1
}

case object Eq extends Operator {
  override def hashCode(): Int = (Config.OP_PREFIX + "eq").hashCode

  override def getEntropyWeight: Double = 1

  override def toString: String = "=="
}

case object NEq extends Operator {

  override def toString: String = "!="
  override def hashCode(): Int = (Config.OP_PREFIX + "neq").hashCode

  override def getEntropyWeight: Double = 0.1
}

/**
 * >
 */
case object Gt extends Operator {
  override def toString: String = ">"
  override def hashCode(): Int = (Config.OP_PREFIX + "gt").hashCode

  override def getEntropyWeight: Double = 10
}

/**
 * >=
 */
case object Ge extends Operator {

  override def toString: String = ">="
  override def hashCode(): Int = (Config.OP_PREFIX + "ge").hashCode

  override def getEntropyWeight: Double = 10
}

/**
 * <
 */
case object Lt extends Operator {

  override def toString: String = "<"
  override def hashCode(): Int = (Config.OP_PREFIX + "lt").hashCode

  override def getEntropyWeight: Double = 10
}

/**
 * <=
 */
case object Le extends Operator {

  override def toString: String = "<="
  override def hashCode(): Int = (Config.OP_PREFIX + "le").hashCode

  override def getEntropyWeight: Double = 10
}


object Operator {
  val opSet: Set[Operator] = Set(Le, Lt, Ge, Gt, Eq, NEq)
}
