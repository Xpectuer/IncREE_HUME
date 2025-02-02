//package org.dsl.reasoning.vanilla
//
//import org.dsl.dataStruct.support.{ISupportSet, PairSupportSet, Universe}
//import org.dsl.dataStruct.{Counter, Env, Statistics}
//import org.dsl.emperical.{Database, DatabaseOps, DeltaDatabase, JournalOps, Table}
//import org.dsl.exception.InterpException
//import org.dsl.reasoning._
//import org.dsl.reasoning.predicate.{BigAndExpr, ColumnAtom, ConstantAtom, ConstantBin, Eq, Expression, Imply, Membership, TupleAtom, TupleBin}
//import org.dsl.utils.Wrappers.{tryStatWrapperId2, tryUnitWrapper1}
//import org.dsl.utils._
//
//import scala.util.Try
//
//
//// baseline code for rule evaluations
//object RuleInterpreter {
//  private val logger = HUMELogger(getClass.getName)
//
//  // TODO: 2 db eval
//  def Interp(expr: Expression, db: Database): Try[InterpResult] = {
//    val env: Env = Env(Map.empty)
//    val counter: Counter = Counter.empty
//    val stats: Statistics = Statistics.empty
//    val res = interp(expr, db, db)(env, counter)(stats)
//    res
//  }
//
//  // Returns counter
//  def InterpWithCounter(expr: Expression, database: Database, counter: Counter): Try[InterpResult] = {
//    val env: Env = Env(Map.empty)
//    val stats: Statistics = Statistics.empty
//    val res = interp(expr, database, database)(env, counter)(stats)
//    res
//  }
//
//
//
//
//  def InterpIncremental(expr: Expression,
//                        dbOrigin: Database, dbIncr: DeltaDatabase,
//                        stat: Statistics): Statistics = {
//    val env: Env = Env.empty
//    val cnt: Counter = Counter.empty
//
//    val (merged, prunedStat) = JournalOps.MergeJournal(dbOrigin, dbIncr, stat)
//    val (dbI, _) = dbIncr.toPosDBAndDelJournal
//    val colDbI = DatabaseOps.BatchTranspose(dbI)
//
//    val res = interp(expr, merged, colDbI)(env, cnt)(prunedStat)
//
//    (res.get.stat.left).getOrElse(throw InterpException("Statistics not collected!!!"))
//  }
//
//
//
//
//  private def queryMatch(t1: TupleAtom, col1: ColumnAtom,
//                         t2: TupleAtom, col2: ColumnAtom,
//                         db1: Database, db2: Database)(env: Env)(stat: Statistics): Statistics = {
//    // todo: refactor into Database
//    val schm1 = env.lookup(t1)
//    val schm2 = env.lookup(t2)
//    // TODO: 2 db
//    val table1 = db1.getFromEnvOrElse(t1, env,  Table.Nil)
//    val table2 = db2.getFromEnvOrElse(t2, env,  Table.Nil)
//
//
//    var spset: ISupportSet = Universe()
//    // TODO: if stat with existing spset then do partial counting (eval on spset)
//    // TODO: if stat with unit spset then do full counting
//    stat.spset match {
//      case _: Universe =>
//        val newStat = DatabaseOps.QueryMatchFull1(schm1, table1, schm2, table2, col1, col2)(stat)
//        Statistics.update(stat, newStat)
//        newStat
//      case p: PairSupportSet =>
//        val newStat = DatabaseOps.QueryMatchPartial1(p)(table1, table2, col1, col2)(stat)
//        Statistics.update(stat, newStat)
//        newStat
//    }
//  }
//
//  private def interpTupleBin(tbin: TupleBin, db1: Database, db2: Database)
//                            (env: Env, stats: Statistics) = {
//    //TODO: 2 db
//    tbin match {
//      case TupleBin(op, t1, col1, t2, col2) =>
//        op match {
//          case Eq =>
//            // todo: some rule may match different columns
//            if (col1.value == col2.value) {
//              val res = queryMatch(t1, col1, t2, col2, db1, db2)(env)(stats)
//              res
//            } else {
//              throw InterpException("Mismatched Columns in TupleBin")
//            } // statistic support set: |spset(p,D)|
//          case _ => throw InterpException("Cannot Recognize Operator.")
//        }
//    }
//  }
//
//  private def queryConstantMatch(t: TupleAtom, col: ColumnAtom,
//                                 const: ConstantAtom, db1: Database, db2: Database)
//                                (env: Env)(stat: Statistics)= {
//
//
//    // todo: 1 t -> 1 scheme
//    val schm = env.lookup(t)
//    val table1 = db1.getOrElse(schm, Table.Nil)
//    val table2 = db2.getOrElse(schm, Table.Nil)
//
//    stat.spset match {
//      case _:Universe =>
//        val newStat = DatabaseOps.QueryMatchConstFull(schm, table1, col, const)(stat)
//        Statistics.update(stat, newStat)
//        if(db1 != db2) {
//          val newStatMore = DatabaseOps.QueryMatchConstFull(schm, table2, col, const)(newStat)
//          newStatMore
//        } else {
//          newStat
//        }
//
//      case p:PairSupportSet =>
//        val newStat = DatabaseOps.QueryMatchConstPartial(p)(table1, col, const)(stat)
//        Statistics.update(stat, newStat)
//        logger.debug("stat:", stat, "new stat", newStat)
//        if (db1 != db2) {
//          val newStatMore = DatabaseOps.QueryMatchConstPartial(p)(table2, col, const)(newStat)
//          newStatMore
//        } else {
//          newStat
//        }
//    }
//  }
//
//  private def interpConstantBin(constantBin: ConstantBin, db1: Database, db2: Database)
//                               (env: Env, stats: Statistics) =
//  // TODO: 2 db eval
//    constantBin match {
//      case ConstantBin(op, t, col, const) =>
//        op match {
//          case Eq =>
//            val res = queryConstantMatch(t, col, const, db1, db2)(env)(stats)
//            res
//          case _ => throw InterpException("Cannot Recognize Operator.")
//        }
//    }
//
//  private def throwStatNotFound = throw InterpException("Statistics Cannot Find!")
//
//  private def interpImply(lhs: Expression, rhs: Expression, db1: Database, db2: Database)
//                         (env: Env, cnt: Counter)(stats: Statistics) = {
//
//    // stat changes
//    interp(lhs, db1, db2)(env, cnt: Counter)(stats)
//
//    val oldSup = stats.support
//    // stat changes
//    val res = interp(rhs, db1, db2)(env, cnt: Counter)(stats)
//    val newSup = stats.support
//
//
//    val newStat = res.getOrElse(throwStatNotFound).stat match {
//      case Left(s) => s.setConfidence(newSup, oldSup);s
//      case Right(_) => throwStatNotFound
//    }
//
//    newStat
//
//
////    tryStatWrapper1[Try[InterpResult]](res) {
////      case Success(res) => res.stat match {
////        case Left(s) => s.setConfidence(newSup, oldSup); s
////        case Right(_) => throw InterpException("Get Unit while calculating confidence")
////      }
////      case Failure(err) => throw err
////    }
//  }
//
//  /**
//   * core interpreter function
//   *
//   * @param expr  expression to evaluate
//   * @param db1   database as input 1
//   * @param db2   database as input 2
//   * @param env   environment of scheme->data
//   * @param cnt   compact
//   * @param stats statistics for resulting and intermediate results
//   * @return
//   */
//  private def interp(expr: Expression, db1: Database, db2: Database)
//                    (env: Env, cnt: Counter)(stats: Statistics): Try[InterpResult] = {
//    expr match {
//      case Imply(lhs, rhs) =>
//        val newStat = interpImply(lhs, rhs, db1, db2)(env, cnt)(stats)
//        tryStatWrapperId2(expr, newStat)
//
//      case BigAndExpr(l) =>
//        Wrappers.tryUnitWrapper1[List[_ <: Expression]](
//          l => l.foreach(e => interp(e, db1, db2)(env, cnt)(stats)))(l)
//
//      case Membership(scheme, tuple) =>
//        val mem = expr.asInstanceOf[Membership]
//        if (Config.PRED_TREE) {
//          cnt.add1(mem)
//        }
//        tryUnitWrapper1[Env](env =>
//          Right(env.update(tuple, scheme)))(env)
//
//      case TupleBin(_, _, _, _, _) =>
//        val tupleBin = expr.asInstanceOf[TupleBin]
//        // count for optimization
//        if (Config.PRED_TREE) {
//          cnt.add1(tupleBin)
//        }
//        val newStat = interpTupleBin(tupleBin, db1, db2)(env, stats)
//        tryStatWrapperId2(tupleBin, newStat)
//
//      case ConstantBin(_, _, _, _) =>
//        val constantBin = expr.asInstanceOf[ConstantBin]
//        if (Config.PRED_TREE) {
//          cnt.add1(constantBin)
//        }
//        val  newStat = interpConstantBin(constantBin, db1, db2)(env, stats)
//        tryStatWrapperId2(constantBin, newStat)
//    }
//  }
//
//}
//
//
