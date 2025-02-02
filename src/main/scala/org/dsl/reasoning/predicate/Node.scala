package org.dsl.reasoning.predicate

import org.dsl.emperical.table.TypedColTable
import org.dsl.reasoning.predicate.HumeType.{HFloat, HInt, HLong, HString, HumeType}
import org.dsl.utils.{Config, IndexProvider}

import java.util.Objects
import scala.collection.mutable

trait Node

class Expression() extends Node

case class Imply(lhs: Expression, rhs: TCalc) extends Expression {

  override def hashCode(): Int = Objects.hashCode(lhs, rhs)
}

case class AndExpr(lhs: Expression, rhs: Expression) extends Expression {
  override def hashCode(): Int = Objects.hash(lhs, rhs)
}

case class BigAndExpr(l: List[Expression]) extends Expression {
  override def hashCode(): Int = l.hashCode()
}

case object NilExpr extends Expression

trait TCalc extends Expression {
  def getOp: Operator

  def withOpposite: Expression
}

case class Membership(scheme: TableInstanceAtom, tuple: TupleAtom) extends TCalc {
  override def hashCode(): Int = Objects.hash(scheme, tuple)

  override def getOp: Operator = ???

  override def withOpposite: TCalc = ???
}


case class TupleBin(op: Operator, t1: TupleAtom, col1: ColumnAtom, t2: TupleAtom, col2: ColumnAtom) extends TCalc {

  def withOpposite = {
    op match {
      case Eq => TupleBin(NEq, t1: TupleAtom, col1: ColumnAtom, t2: TupleAtom, col2: ColumnAtom)
      case NEq => TupleBin(Eq, t1: TupleAtom, col1: ColumnAtom, t2: TupleAtom, col2: ColumnAtom)
      case _ => ???
    }
  }

  override def hashCode(): Int =
    Objects.hash(op, t1, col1, t2, col2)

  private def commutativeEquals(bin1: TupleBin, bin2: TupleBin) = {
    // commutative of = and !=
    val tt0 = bin1.op.equals(bin2.op)
    val tt1 = bin1.t1.equals(bin2.t1)
    val tt2 = bin1.t2.equals(bin2.t2)
    val tt3_case1 = bin1.col1.equals(bin2.col1) && bin1.col2.equals(bin2.col2)
    val tt3_case2 = bin1.col1.equals(bin2.col2) && bin1.col2.equals(bin2.col1)

    tt0 && tt1 && tt2 && (tt3_case1 || tt3_case2)
  }

  override def equals(obj: Any): Boolean =
    obj match {
      case other: TupleBin =>
        this.op match {
          case Eq | NEq => commutativeEquals(this, other)
          case _ => super.equals(other)
        }
      case _ =>
        false
    }

  def toTyped: TypedTupleBin = {
    val tCol1 = Type.parseTypedColumn(col1)
    val tCol2 = Type.parseTypedColumn(col2)
    TypedTupleBin(op, t1, t2, tCol1, tCol2)
  }

  override def getOp: Operator = op
}

case class TypedConstantBin(op: Operator, t0: TupleAtom, col: TypedColumnAtom,
                            const: ConstantAtom) extends TCalc {
  def eval(table: TypedColTable): mutable.BitSet = {

    val pli = table.getPli(col)
    (op, col.htype) match {
      case (Eq, HString) =>
        val provider = table.getStrProvider
        val idxVal = provider.getOrElse(const.getValue, -1)
        val idxs = pli.get(idxVal).getOrElse(mutable.BitSet.empty)
        mutable.BitSet.empty ++= idxs
      case _ => mutable.BitSet.empty
    }
  }


  def withOpposite = {
    op match {
      case Eq => TypedConstantBin(NEq, t0: TupleAtom, col: TypedColumnAtom, const: ConstantAtom)
      case NEq => TypedConstantBin(NEq, t0: TupleAtom, col: TypedColumnAtom, const: ConstantAtom)
      case _ => ???
    }
  }

  def withConst(constNew: ConstantAtom) = {
    TypedConstantBin(op, t0, col, constNew)
  }

  override def hashCode(): Int = {
    Objects.hash(op, col, const)
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: TypedConstantBin =>
        other.op == this.op &&
          other.const == this.const &&
          other.col == this.col
      case _ => false
    }

  }

  override def getOp: Operator = op

  override def toString: String = {
    s"[${col} ${op} ${const}]"
  }
}


case class TypedTupleBin(op: Operator, t0: TupleAtom, t1: TupleAtom, col1: TypedColumnAtom,
                         col2: TypedColumnAtom) extends TCalc {

  override def withOpposite: TypedTupleBin = {
    op match {
      case Eq => TypedTupleBin(NEq, t0: TupleAtom, t1: TupleAtom, col1: TypedColumnAtom, col2: TypedColumnAtom)
      case NEq => TypedTupleBin(Eq, t0: TupleAtom, t1: TupleAtom, col1: TypedColumnAtom, col2: TypedColumnAtom)
      case _ => ???
    }
  }

  def eval(data: TypedColTable): mutable.BitSet = {
    assert(col1.htype == col2.htype)
    //println(s"Eval: $this")
    val totalRowCount = data.rowNum
    val constantFilterThreshCnt = Config.CONSTANT_FILTER_RATE * totalRowCount

    val pli1 = data.getPli(col1)
    val pli2 = data.getPli(col2)
    // todo: optimize for specific operators
    //println(colValues1.getValues.size, colValues2.getValues.size)
    mutable.BitSet.empty ++= pli1.getValues.flatMap {
      v1 =>
        pli2.getValues.grouped(Config.nproc * 4).toIndexedSeq.par.flatMap {
          chunk =>
            chunk.flatMap {
              v2 =>
                val idxsOpt1 = pli1.get(v1)
                val idxsOpt2 = pli2.get(v2)
                (idxsOpt1, idxsOpt2) match {
                  case (Some(idxs1), Some(idxs2)) =>
                    op match {
                      case Eq =>
                        if (v1 == v2) {
                          mutable.BitSet.empty ++= (idxs1 ++ idxs2)
                        } else {
                          mutable.BitSet.empty
                        }
                      case NEq =>
                        if (v1 != v2) {
                          mutable.BitSet.empty ++= (idxs1 ++ idxs2)
                        } else {
                          mutable.BitSet.empty
                        }
                      case _ => ???
                    }
                  case _ => ???
                }
            }
        }
    }


  }

  override def toString: String = {
    s"[$col1 $op $col2]"
  }

  override def hashCode(): Int = {
    op match {
      case Eq | NEq => Objects.hash(op)
      case _ => Objects.hash(op, col1, col2)
    }

  }

  private def commutativeEquals(bin1: TypedTupleBin, bin2: TypedTupleBin) = {
    // typed commutative of = and !=
    val tt0 = bin1.op.equals(bin2.op)


    val tt3_case1 = bin1.col1.equals(bin2.col1) && bin1.col2.equals(bin2.col2)
    val tt3_case2 = bin1.col1.equals(bin2.col2) && bin1.col2.equals(bin2.col1)

    tt0 && (tt3_case1 || tt3_case2)
  }

  private def eq(other: TypedTupleBin) = {
    this.op == other.op &&
      this.col1 == other.col1 &&
      this.col2 == other.col2

  }

  override def equals(obj: Any): Boolean =
    obj match {
      case other: TypedTupleBin =>
        this.op match {
          case Eq | NEq => commutativeEquals(this, other)
          case _ => eq(other)
        }
      case _ =>
        false
    }

  override def getOp: Operator = op
}

case class ConstantBin(op: Operator, t: TupleAtom, col: ColumnAtom, constant: ConstantAtom) extends TCalc {
  override def hashCode(): Int = Objects.hash(op, t, col, constant)

  def toTyped: TypedConstantBin = {
    val tCol = Type.parseTypedColumn(col)
    TypedConstantBin(op, t, tCol, constant)
  }

  override def getOp: Operator = op

  override def withOpposite: TCalc = ???
}


/**
 * For Journal Deletion
 */
sealed trait Where extends Expression

case class Condition(op: Operator,
                     schemeAtom: TableInstanceAtom,
                     columnAtom: ColumnAtom,
                     constantAtom: ConstantAtom) extends Where

trait Atom {
  def getValue: String
}

case class TableInstanceAtom(value: String) extends Atom {

  override def getValue: String = value

  override def hashCode: Int = value.hashCode

  def ==(other: TableInstanceAtom): Boolean = other.hashCode == this.hashCode
}

case class TupleAtom(value: String) extends Atom {
  override def getValue: String = value

  override def hashCode(): Int = (value + "t").hashCode

  override def equals(obj: Any): Boolean = {
    obj match {
      case tupleAtom: TupleAtom =>
        this.value == tupleAtom.value
      case _ =>
        false
    }

  }
}

case class ColumnAtom(value: String) extends Atom {
  override def getValue: String = value

  override def hashCode(): Int = (value + "col").hashCode
}

case class TypedColumnAtom(colname: String, htype: HumeType) extends Atom {
  override def getValue: String = colname

  override def hashCode(): Int = (colname + "col" + htype + "type").hashCode

  override def equals(obj: Any): Boolean = {
    obj match {
      case t: TypedColumnAtom =>
        this.colname == t.colname && this.htype == t.htype
      case _ =>
        false
    }

  }

  def toUnTyped = ColumnAtom(colname)

  override def toString: String = s"($colname)"
}


case class ConstantAtom(value: String) extends Atom {
  override def getValue: String = value

  override def hashCode(): Int = (value + "const").hashCode
}

object NodePlaceholder {
  val ConstantPLACEHOLDER = ConstantAtom("$PLACEHOLDER$")
}

