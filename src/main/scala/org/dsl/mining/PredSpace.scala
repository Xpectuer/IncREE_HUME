package org.dsl.mining

import org.dsl.emperical.Table
import org.dsl.emperical.table.TypedColTable
import org.dsl.emperical.table.TypedColTable.{isComparable, isJoinable}
import org.dsl.exception.HumeException
import org.dsl.reasoning.predicate.HumeType.{HFloat, HInt, HLong, HString}
import org.dsl.reasoning.predicate.NodePlaceholder.ConstantPLACEHOLDER
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate._
import org.dsl.utils.{$, Config, HUMELogger, IndexProvider}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.asScalaSetConverter


object PredSpace {


  def from(ps: Iterable[Expression]): PredSpace = {
    ps.map {
      case t: TCalc =>
        t.getOp -> t.asInstanceOf[Expression]
      case _ => ???
    }.foldLeft(mutable.Map.empty[Operator, IndexedSeq[Expression]]) {
      case (coll, (op, expr)) =>
        val exprs = coll.getOrElseUpdate(op, mutable.IndexedSeq.empty[Expression])
        coll.update(op, exprs :+ expr)
        coll
    }.toMap

  }

  type PredSpace = Map[Operator, Iterable[Expression]]


  def getP2I(predSpace: PredSpace): PredicateIndexProvider = {
    // map to predicate list
    val predicateAsList = predSpace.values.flatten.toIndexedSeq.sortBy(_.toString)
    logger.debug(s"total predicate num: ${predicateAsList.size}")
    IndexProvider[Expression](predicateAsList)
  }


  def getP2I(predicates: Iterable[Expression]): PredicateIndexProvider = {
    // map to predicate list
    val predicateAsList = predicates.toIndexedSeq.sortBy(_.toString)
    logger.debug(s"total predicate num: ${predicateAsList.size}")
    IndexProvider[Expression](predicateAsList)
  }

  private case class ColumnPairWithFlags(c1: TypedColumnAtom, c2: TypedColumnAtom, joinable: Boolean, comparable: Boolean)

  private def getConstantPred(table: TypedColTable): Iterable[Expression] = {
    // 1. enumerate 2. filter 3. output

    val totalRowCount = table.rowNum
    val constantFilterThreshCnt = Config.CONSTANT_FILTER_RATE * totalRowCount
    val cols = table.getColumns

    val cpreds = cols.flatMap {
      col =>
        // convert to str
        val values = col.getValueSet
        // use parallel for large value set
        val t = values.entrySet().asScala
          .withFilter(_.getCount >= constantFilterThreshCnt)
          .withFilter(e => e.getElement.toString != "")
          .map {
            e =>
              val v = ConstantAtom(e.getElement.toString)
              TypedConstantBin(Eq, TupleAtom("t0"), col.getTypedColumnAtom, v)
          }
        t
    }

    cpreds
  }

  def apply(table: TypedColTable): PredSpace = {

    val cPairs =
      for (i <- 0 until table.colNum;
           j <- i until table.colNum) yield (table.getColumnAtom(i), table.getColumnAtom(j))

    //    logger.info(cPairs.mkString("\n"))

    val filteredCPairs = cPairs.map {
      case (c1, c2) => ColumnPairWithFlags(c1, c2, joinable = isJoinable(table, c1, c2), comparable = isComparable(table, c1, c2))
    }.withFilter {
      case ColumnPairWithFlags(_, _, joinable, comparable) => joinable || comparable
    }
    //    logger.info(filteredCPairs.mkString("\n"))

    val t0 = TupleAtom("t0")
    val t1 = TupleAtom("t1")

    val ps: Iterable[Expression] = filteredCPairs.flatMap {
      case ColumnPairWithFlags(c1, c2, joinable, comparable) =>
        val set = for (op <- Operator.opSet) yield {
          op match {
            case Eq | NEq if joinable => Some(TypedTupleBin(op, t0, t1, c1, c2))
            case Gt | Ge | Lt | Le if comparable => Some(TypedTupleBin(op, t0, t1, c1, c2))
            case _ => None
          }
        }
        set.withFilter(_.isDefined).map(_.get)
    }.toSet

    // todo: ADD constant predicates with filters
    val cs: Iterable[Expression] = getConstantPred(table)

    logger.info(s"Enumerate Predicate Space with Size=${cs.size + ps.size}")
    (ps ++ cs).map {
      case t: TCalc => t
      case _ => ???
    }.groupBy(_.getOp)
  }


  private def cartesian[T](list1: List[T], list2: List[T]): List[(T, T)] =
    for (x <- list1; y <- list2) yield (x, y)

  def getAttrs(table: Table): List[ColumnAtom] = {
    table.getHeader.toList
  }

  /**
   * Preconditions Space
   *
   * @param head1
   * @param head2
   * @return
   */
  def permutePrecondition(head1: List[ColumnAtom], head2: List[ColumnAtom]): Iterable[Expression] = {
    val ops: Set[Operator] = Set(Eq, Lt, Gt, NEq, Ge, Le)
    // val ops: Set[Operator] = Set(Eq)
    permutePredicate(head1, head2, ops)(Config.CONSTANT)
  }

  /**
   * Conclusions Space
   *
   * for experiment, only t0.A == t1.B is considered
   *
   * @param head1
   * @param head2
   * @return
   */
  def permuteConclusion(head1: List[ColumnAtom], head2: List[ColumnAtom]): Iterable[Expression] = {
    val ops: Set[Operator] = Set(Eq)
    permuteBinary(head1, head2, ops)
  }

  private def permuteTypedBinary(head1: List[ColumnAtom], head2: List[ColumnAtom], operators: Set[Operator]): Iterable[Expression] = {
    val tupleBins = permuteBinary(head1, head2, operators)

    def isConsist(tbin: TypedTupleBin) = {
      val tcol1 = tbin.col1
      val tcol2 = tbin.col2
      val op = tbin.op

      val numerical = Set(HLong, HInt, HFloat)

      val h1 = tcol1.htype
      val h2 = tcol2.htype

      op match {
        case Eq | NEq => (h1 == HString && h2 == HString) || (numerical.contains(h1) && numerical.contains(h2))
        case Lt | Gt | Le | Ge => tcol1.htype != HString && tcol2.htype != HString && tcol1.htype == tcol2.htype
      }
    }

    tupleBins.map {
      case t: TupleBin => t.toTyped
    }.filter(isConsist)

  }

  private def permuteBinary(head1: List[ColumnAtom], head2: List[ColumnAtom], operators: Set[Operator]): Iterable[Expression] = {
    val frag3 = ListBuffer.empty[Expression]
    val h1Xh2 = cartesian(head1, head2)
    for ((attr1, attr2) <- h1Xh2) {
      for (op <- operators) {
        // todo: tuple  binder e.g. airport(t0)=>t0
        val r = TupleBin(op, TupleAtom("t0"), attr1, TupleAtom("t1"), attr2)
        frag3.+=(r)
      }
    }

    frag3.toList
  }

  private def permuteConstTemplate(head: List[ColumnAtom], operators: Set[Operator]): Iterable[Expression] = {
    val frag2 = ListBuffer.empty[Expression]
    for (attr <- head) {
      for (op <- operators) {
        // todo: tuple  binder e.g. airport(t0)=>t0
        val r = ConstantBin(op, TupleAtom("t1"), attr, ConstantPLACEHOLDER)
        frag2.+=(r)
      }
    }

    frag2.toList
  }

  private def permutePredicate(head1: List[ColumnAtom], head2: List[ColumnAtom], operators: Set[Operator])(constant: Boolean): Iterable[Expression] = {
    val frag1 = if (constant) {
      permuteConstTemplate(head1, operators)
    } else {
      List.empty
    }
    val frag2 = if (constant) {
      permuteConstTemplate(head2, operators)
    } else {
      List.empty
    }

    val frag3 = permuteTypedBinary(head1, head2, operators)

    frag1 ++ frag2 ++ frag3
  }


  val logger: HUMELogger = HUMELogger(getClass.getPackage.getName)

  def main(args: Array[String]): Unit = {

    // dataset: airport
    //    val path = Config.addProjPath("datasets/incremental/airport/airport_original.test.csv")


    //    val tableopt = $.LoadDataSet("datasets/hospital.csv")
    val tableopt = $.LoadDataSet("datasets/ncvoter.csv")

    logger.info(s"Data Loading done .")
    // test size of predicate

    tableopt match {
      case Some(table) =>
        val space = apply(table)
        println("SIZE:", space.values.map(l => l.size).sum)
        val r = space.toIndexedSeq.map(_._2).foldLeft(mutable.ArrayBuffer.empty[Expression])(_ ++ _)

        println(r.mkString("\n"))

      case _ => ???
    }


  }

  private case class PredSpaceException(msg: String) extends HumeException {
    override def getMsg: String = msg
  }

}


