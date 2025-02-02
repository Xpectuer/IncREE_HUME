//package org.dsl.reasoning.predTree
//
//import org.dsl.dataStruct.support.{PairSupportSet, Universe}
//import org.dsl.dataStruct.{Counter, Env, Statistics}
//import org.dsl.reasoning.predicate._
//import org.dsl.utils._
//
//import java.util.Objects
//import scala.annotation.tailrec
//import scala.collection.mutable
//
//object CompactPred {
//
//  type ExprStat = (Expression, Statistics)
//
//  var size = 0
//
//  private def consNil: ExprStat = (NilExpr, Statistics.empty)
//
//  private def consNilStat(expr: Expression): ExprStat = (expr, Statistics.empty)
//
//  private val logger = HUMELogger(getClass.getName)
//
//  sealed trait TreeNode {
//    def ==(other: TreeNode): Boolean
//
//    def getExpr: Expression
//
//    def getStat: Statistics
//
//    def setStat(stat: Statistics): Unit
//  }
//
//
//  private case class Leaf(pair: ExprStat, parent: TreeNode) extends TreeNode {
//    override def setStat(stat: Statistics): Unit = Statistics.update(pair._2, stat)
//
//    override def ==(other: TreeNode): Boolean = equals(other)
//
//    override def hashCode(): Int = Objects.hash("L", getExpr)
//
//    override def toString: String = "Leaf: " + pair.toString
//
//    override def getExpr: Expression = pair._1
//
//    override def getStat: Statistics = pair._2
//
//    override def equals(obj: Any): Boolean = obj match {
//      case Leaf(v, paren) => pair._1 == v._1 && paren == parent
//      case _ => false
//    }
//  }
//
//  private case class Branch(pair: ExprStat, children: mutable.Map[TreeNode, TreeNode], parent: TreeNode) extends TreeNode {
//
//    override def setStat(stat: Statistics): Unit = Statistics.update(pair._2, stat)
//
//    override def ==(other: TreeNode): Boolean = {
//      other match {
//        case Branch(_, _, _) => this equals other
//        case _ => false
//      }
//    }
//
//    override def hashCode(): Int = Objects.hash("B", getExpr)
//
//    override def toString: String = "Branch: " + pair.toString
//
//    override def equals(obj: Any): Boolean = obj match {
//      case Branch(p, _, _) => p._1 equals this.getExpr
//      case _ => false
//    }
//
//    override def getExpr: Expression = pair._1
//
//    override def getStat: Statistics = pair._2
//  }
//
//  private case object NilNode extends TreeNode {
//    override def setStat(stat: Statistics): Unit = ???
//
//    override def ==(other: TreeNode): Boolean = false
//
//    override def getExpr: Expression = NilExpr
//
//    override def getStat: Statistics = Statistics.empty
//
//    override def hashCode(): Int = "NILNODE".hashCode()
//  }
//
//  def empty: TreeNode = NilNode
//
//  // Branch on the top
//
//  private def identity(n: TreeNode) = n -> n
//
//  private final def inspect(f: (TreeNode, Int) => Unit)(treeNode: TreeNode, cnt: Int): Unit = {
//    val queue: mutable.Queue[TreeNode] = mutable.Queue.empty
//    queue.enqueue(treeNode)
//    while (queue.nonEmpty) {
//      val e = queue.dequeue()
//      e match {
//        case Branch(_, chld, _) =>
//          f(e, cnt)
//          chld.keys.foreach(c => inspect(f)(chld(c), cnt + 1))
//        case Leaf(_, _) => f(e, cnt)
//        case NilNode => logger.info("NilNode")
//      }
//    }
//  }
//
//  def ReportTree(tree: TreeNode): Unit = {
//    logger.info(s"SIZE: $size")
//    reportTree(tree)
//  }
//
//  private def reportTree(treeNode: TreeNode): Unit =
//    inspect((e, cnt) => {
//      logger.info(s"Level:$cnt || ($e)")
//    })(treeNode, 0)
//
//    def tcalcEntropy(bucket: Bucket,calc: TCalc): Double = {
//      calc match {
//        case _: Membership => Double.MaxValue
//        case TupleBin(op, _, col1, _, col2) =>
//          val weight = op.getEntropyWeight
//          val entro1 = bucket.getEntropy(col1)
//          val entro2 = bucket.getEntropy(col2)
//
//          weight * ((entro1 + entro2) / 2)
//
//        case ConstantBin(op, _, col, const) =>
//          val weight = op.getEntropyWeight
//          // info quantity
//          val infoQ = bucket.getInfoQ(col, const)
//
//          weight * infoQ
//
//      }
//    }
//  def exprEntropy(bucket: Bucket)(a: Expression, b: Expression): Boolean = {
//
//    a match {
//      case t1: TCalc =>
//        b match {
//          case t2: TCalc =>
//            val ea = tcalcEntropy(bucket, t1)
//            val eb = tcalcEntropy(bucket,t2)
//            ea > eb
//        }
//    }
//  }
//
//
//  final def BuildTree(bucket:Bucket)(sources: Seq[(Seq[_<:Expression], _ <: Expression)],
//                      counter: Counter): TreeNode = {
//    logger.debug("Building Tree Start...")
//    def exprCount(a: Expression, b: Expression) = {
//      val ca = counter.lookup(a).getOrElse(-1)
//      val cb = counter.lookup(b).getOrElse(-1)
//      ca > cb
//    }
//
//
//    // todo: entropy sorting
//    // interp Expression and extract column
//
//    // dummy
//    val root = Branch(consNil, mutable.Map.empty, NilNode)
//
//    var totalTime = 0.0
//    for (s <- sources) {
//      val preliminary = s._1
//      val conclusion = s._2
//
//      val p = if (Config.TREE_GREEDY) {
//        val (pp, time) = Wrappers
//          .timerWrapperRet(preliminary sortWith exprEntropy(bucket))
//          // .timerWrapperRet(preliminary.sortWith(exprCount))
//        totalTime = totalTime + time
//        pp
//
//      } else {
//        preliminary
//      }
//
//      buildTree(root, p :+ conclusion)
//    }
//
//    if (Config.TREE_GREEDY) {
//      logger.info(s"Sort takes $totalTime (s)")
//    }
//
//    root
//  }
//
//  def EvalNodeFull(node: TreeNode, db1: Table, db2: Table): Unit = {
//    val env = Env.empty
//    evalNodeFull(node, db1, db2, env)
//  }
//
//
//  private def evalNodeFull(node: TreeNode, table1: Table, table2: Table, env: Env): Unit = {
//    val expr = node.getExpr
//    val stat = node.getStat
//
//    expr match {
//      case Membership(tableInstance, tuple) =>
//        env.update(tuple, tableInstance)
//
//      case TupleBin(op, t1, col1, t2, col2) =>
//        op match {
//          case Eq =>
//
//            val schm1 = env.lookup(t1)
//            val schm2 = env.lookup(t2)
//
//            val stat1 = DatabaseOps.QueryMatchFull1(schm1, table1, schm2, table2, col1, col2)(stat)
//            stat.update(stat1)
//          // node.setStat(stat1)
//          case _ => ???
//        }
//
//      case ConstantBin(op, t, col, const) =>
//        op match {
//          case Eq =>
//            val schm = env.lookup(t)
//            val stat1 = DatabaseOps.QueryMatchConstFull(schm, table1, col, const)(stat)
//            stat.update(stat1)
//          // node.setStat(stat1)
//          case _ => ???
//        }
//
//      case NilExpr => ()
//
//      case _ => throw PredTreeException("Unexcepted Expression IN Pred Tree!!!")
//    }
//
//    // drill down
//    node match {
//      case Leaf(pair, _) => logger.debug(s"LEAF::${pair._2.support}")
//      case NilNode => ()
//      case Branch(_, children, _) =>
//        // todo: makes it prettier
//        // todo: confidence for rule fuzzing
//        // val conf = stat.setConfidence()
//        // logger.debug(s"Confidence: $conf")
//
//        children.values.foreach(c => evalNodeFull(c, table1, table2, env))
//    }
//  }
//
//  def JSONifyTree(node: TreeNode): Unit = {
//
//  }
//
//
//  private def interpOneFull(expression: Expression, db1: Database, db2: Database, env: Env, stat: Statistics): Statistics = {
//    logger.debug(s"Fully Interpreting: $expression")
//    expression match {
//      case Membership(schm, tuple) =>
//        env.update(tuple, schm)
//        Statistics.empty
//      case TupleBin(op, t1, col1, t2, col2) =>
//        op match {
//          case Eq =>
//            val table1 = db1.getFromEnvOrElse(t1, env, Table.Nil)
//            val table2 = db2.getFromEnvOrElse(t2, env, Table.Nil)
//            val schm1 = env.lookup(t1)
//            val schm2 = env.lookup(t2)
//            val stat1 = DatabaseOps.QueryMatchFull1(schm1, table1, schm2, table2, col1, col2)(stat)
//            logger.debug(s"With Support Set Size: ${stat1.spset.size}")
//            stat1
//          case _ => ???
//        }
//
//      case ConstantBin(op, t, col, const) =>
//        op match {
//          case Eq =>
//            val table1 = db1.getFromEnvOrElse(t, env, Table.Nil)
//            val schm = env.lookup(t)
//            val stat1 = DatabaseOps.QueryMatchConstFull(schm, table1, col, const)(stat)
//            if (db1 != db2) {
//              val table2 = db2.getFromEnvOrElse(t, env,Table.Nil)
//              val stat2 = DatabaseOps.QueryMatchConstFull(schm, table2, col, const)(stat)
//              logger.debug(s"With Support Set Size: ${stat2.spset.size}")
//              stat2
//            } else {
//              logger.debug(s"With Support Set Size: ${stat1.spset.size}")
//              stat1
//            }
//
//          case _ => ???
//        }
//
//      case NilExpr => Statistics.empty
//
//      case _ => throw PredTreeException("Unexcepted Expression IN Pred Tree!!!")
//    }
//  }
//
//  private def interpOnePartial(supportSet: PairSupportSet)(expression: Expression, db1: Database, db2: Database, env: Env, stat: Statistics): Statistics = {
//    logger.debug(s"Partially Interpreting: $expression")
//    logger.debug(s"With Support Size ${supportSet.size}")
//    expression match {
//      case Membership(schm, tuple) =>
//        env.update(tuple, schm)
//        Statistics.empty
//
//      case TupleBin(op, t1, col1, t2, col2) =>
//        op match {
//          case Eq=>
//            val table1 = db1.getFromEnvOrElse(t1, env, Table.Nil)
//            val table2 = db2.getFromEnvOrElse(t2, env, Table.Nil)
//            val stat1 = DatabaseOps.QueryMatchPartial1(supportSet)(table1, table2, col1, col2)(stat)
//            stat1
//          case _ => ???
//        }
//
//      case ConstantBin(op, t, col, const) =>
//        op match {
//          case Eq=>
//            val table1 = db1.getFromEnvOrElse(t, env, Table.Nil)
//            val stat1 = DatabaseOps.QueryMatchConstPartial(supportSet)(table1, col, const)(stat)
//
//            if (db1 != db2) {
//              val table2 = db2.getFromEnvOrElse(t, env,Table.Nil)
//              val stat2 = DatabaseOps.QueryMatchConstPartial(supportSet)(table2, col, const)(stat)
//              stat2
//            } else {
//              stat1
//            }
//
//          case _ => ???
//        }
//
//      case NilExpr => Statistics.empty
//
//      case _ => throw PredTreeException("Unexcepted Expression IN Pred Tree!!!")
//    }
//  }
//
//  def EvalNodePartial1(node: TreeNode, db: Database): Unit = {
//    val env = Env.empty
//    evalNodePartial(node, db, db, env)
//  }
//
//  def EvalNodePartial2(node: TreeNode, db1: Database, db2: Database): Unit = {
//    val env = Env.empty
//    evalNodePartial(node, db1, db2, env)
//  }
//
//
//  private def evalNodePartial(node: TreeNode, db1: Database, db2: Database, env: Env): Unit = {
//    node match {
//      case Branch(pair, children, parent) =>
//        val expr = pair._1
//        val stat = pair._2
//        parent.getStat.spset match {
//
//          case p: PairSupportSet =>
//            val stat1 = interpOnePartial(p)(expr, db1, db2, env, stat)
//            // todo: detroy spset
//            stat.update(stat1)
//            parent.getStat.clearSpSet
//
//          case _: Universe =>
//            val stat1 = interpOneFull(expr, db1, db2, env, stat)
//            stat.update(stat1)
//            parent.getStat.clearSpSet
//        }
//
//        children.values.foreach(child =>
//          evalNodePartial(child, db1, db2, env))
//
//      case Leaf(pair, parent) =>
//        val expr = pair._1
//        val stat = pair._2
//        parent.getStat.spset match {
//          case p: PairSupportSet =>
//            val stat1 = interpOnePartial(p)(expr, db1, db2, env, stat)
//            stat.update(stat1)
//
//            // No Children here
//            parent.getStat.clearSpSet
//            stat.clearSpSet
//
//          case _: Universe =>
//            val stat1 = interpOneFull(expr, db1, db2, env, stat)
//            stat.update(stat1)
//        }
//
//
//    }
//
//  }
//
//
//  private final def buildTree(root: TreeNode, source: Seq[_ <: Expression]): Unit = {
//    @tailrec
//    def build(tree: TreeNode, source: Seq[_ <: Expression]): Unit = {
//      size = size + 1
//      tree match {
//        case Branch(_, chld, _) =>
//          source match {
//            case car :: Nil =>
//              val p = consNilStat(car)
//              val leaf = Leaf(p, tree)
//              chld.update(leaf, leaf)
//
//            case car :: cdr =>
//              val p = consNilStat(car)
//              val branch = Branch(p, mutable.Map.empty, tree)
//              chld.get(branch) match {
//                case Some(t) =>
//                  // skip
//                  assert(t == branch && t == t)
//                  size = size - 1
//                  build(t, cdr)
//                case None =>
//                  chld.update(branch, branch)
//                  build(branch, cdr)
//              }
//
//          }
//        case Leaf(_, parent) =>
//          parent match {
//            case Branch(_, chld, _) =>
//              source match {
//                case car :: Nil => {
//                  val p = consNilStat(car)
//                  val leaf = Leaf(p, tree)
//                  chld.update(leaf, leaf)
//                }
//                case car :: cdr =>
//                  val p = consNilStat(car)
//                  val branch = Branch(p, mutable.Map.empty, tree)
//                  chld.get(branch) match {
//                    case Some(t) =>
//                      // skip
//                      size = size - 1
//                      build(t, cdr)
//                    case None =>
//                      chld.update(branch, branch)
//                      build(branch, cdr)
//                  }
//              }
//            case Leaf(_, _) => {
//              throw PredTreeException("Leaf as parent of Leaf!!!")
//            }
//            case NilNode => logger.debug(s"Nil Node Found")
//          }
//        case NilNode => logger.debug(s"Nil Node Found")
//      }
//    }
//
//
//    build(root, source)
//
//  }
//
//
//}
//
//
//
