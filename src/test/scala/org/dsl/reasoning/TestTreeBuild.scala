//package org.dsl.reasoning
//
//import org.dsl.dataStruct.Counter
//import org.dsl.emperical.pli.Bucket
//import org.dsl.emperical.table.RowTable
//import org.dsl.emperical.{Database, DatabaseOps}
//import org.dsl.reasoning.TestRuleInterpreter.{logger, showInitInfo}
//import org.dsl.reasoning.predTree.CompactPred
//import org.dsl.reasoning.predicate.{BigAndExpr, Expression, Imply, TCalc, TableInstanceAtom}
//import org.dsl.utils.{$, Wrappers}
//
//import java.nio.file.{Path, Paths}
//import scala.collection.mutable
//
//object TestTreeBuild {
//  def TestTreeBuildEvalFull(dataFile: String, ruleFile: String): Unit = {
//    TestTreeBuildEvalRate(dataFile, ruleFile, 1)
//  }
//
//  def flatExpr(expr: Expression): List[Expression] = {
//    expr match {
//      case Imply(lhs, rhs) => flatExpr(lhs) ++ flatExpr(rhs)
//      case BigAndExpr(l) => l.map(e => e.asInstanceOf[TCalc])
//      case _ => List(expr.asInstanceOf[TCalc])
//    }
//  }
//
//
//  private def countInterp(expression: Expression, counter: Counter, countable: Boolean): Unit = {
//    expression match {
//      case Imply(lhs, rhs) =>
//        countInterp(lhs, counter, countable = true)
//        countInterp(rhs, counter, countable = true)
//      case BigAndExpr(l) => l.foreach(e => countInterp(e, counter, countable))
//      case _ => if (countable) counter.add1(expression)
//    }
//  }
//
//  def TestTreeBuild(dataFile: String, ruleFile: String): CompactPred.TreeNode = {
//    TestTreeBuildEvalRate(dataFile, ruleFile, 0)
//  }
//
//
//  def TestTreeBuildEvalRate(dataFile: String, ruleFile: String, rate: Double): CompactPred.TreeNode = {
//
//    showInitInfo()
//
//    logger.info("Tree Build Test Starts")
//    //val ruleFile = Config.PROJ_PATH + "/rules/labeled_data_400/airports/train/rules.txt"
//    val ruleTable = RuleTable(ruleFile)
//    val sourceList = ruleTable.getRules.toList
//    //val dataFile = Config.PROJ_PATH + "/datasets/airport.test.csv.bak"
//
//    val tableName= $.GetDSName(dataFile)
//    val db = $.LoadDataSet(tableName, dataFile)
//    val rowTable = db.get(TableInstanceAtom(tableName)).asInstanceOf[RowTable]
//
//    logger.info("Transposing...")
//    val (transDB, time) = Wrappers.timerWrapperRet(DatabaseOps.BatchTranspose(db))
//    logger.info(s"Transpose Done in $time (s)")
//    val (sample, _) = DatabaseOps.SplitIntoTwoDB(transDB, rate)
//
//    val bucket = Bucket.from(rowTable)
//
//    val cnt = Counter.empty
//    // collect counter
//    //    val list = doTest(sourceList, db, cnt)
//    //    doHandleResult(list)
//
//    val exprList = sourceList.map(s => RuleParser.parse(s).get)
//
//    exprList.foreach(e => countInterp(e, cnt, countable = false))
//    logger.info(s"Total Nodes: ${cnt.totalSize}")
//
//    val es = exprList.map(flatExpr)
//
//    def splitPrelConcl(e: List[_ <: Expression]) = {
//      val conclusion = e.last
//      val preliminary = e.dropRight(1)
//      (preliminary, conclusion)
//    }
//
//    val pcPairs = es.map(splitPrelConcl)
//    val root = CompactPred.BuildTree(bucket)(pcPairs, cnt)
//    logger.debug(s"Build Tree Complete: tree size ${CompactPred.size}")
//    CompactPred.EvalNodePartial1(root, sample)
//    CompactPred.ReportTree(root)
//
//    root
//  }
//
//
//  // todo: refactor uniform db eval function
//  def TestTreeIncrRate(dataPath: String, ruleFile: String,
//                       sampleRate: Double, incrRate: Double): Unit = {
//
//    showInitInfo()
//    val db = $.LoadDataSet("airports", dataPath)
//    // 1. D X D
//    // 2. ΔD X (D + ΔD)
//    val (rest, incr) = DatabaseOps.SplitIntoTwoDB(db, sampleRate * (1 - incrRate))
//
//    //  DB(root) = rest
//    val root = TestTreeBuildEvalRate(dataPath, ruleFile, sampleRate * (1 - incrRate))
//
//    val incrCol = DatabaseOps.BatchTranspose(incr)
//
//    CompactPred.EvalNodePartial2(root, incrCol, rest)
//
//  }
//
//  def TestBucketTree(dataPath: String, ruleFile: String): Unit = {
//    showInitInfo()
//
//    logger.info("Tree Build Test Starts")
//    //val ruleFile = Config.PROJ_PATH + "/rules/labeled_data_400/airports/train/rules.txt"
//    val ruleTable = RuleTable(ruleFile)
//    val sourceList = ruleTable.getRules.toList
//    //val dataFile = Config.PROJ_PATH + "/datasets/airport.test.csv.bak"
//
//
//    val rowTable = RowTable("airports", dataPath)
//
//
//    logger.info("Building Bucket...")
//    val bucket = Bucket.from(rowTable)
//    logger.debug(s"Bucket Build Done... $bucket")
//    val schmTableMap = TableInstanceAtom("airports") -> bucket
//    val m = Map(schmTableMap)
//    val db = Database(mutable.Map.from(m))
//
//    val cnt = Counter.empty
//
//    // collect counter
//    //    val list = doTest(sourceList, db, cnt)
//    //    doHandleResult(list)
//
//    val exprList = sourceList.map(s => RuleParser.parse(s).get)
//
//    exprList.foreach(e => countInterp(e, cnt, countable = false))
//    logger.info(s"Total Nodes: ${cnt.totalSize}")
//
//
//    val es = exprList.map(flatExpr)
//
//
//    def splitPrelConcl(e: List[_ <: Expression]) = {
//      val conclusion = e.last
//      val preliminary = e.dropRight(1)
//      (preliminary, conclusion)
//    }
//
//    val pcPairs = es.map(splitPrelConcl)
//    val root = CompactPred.BuildTree(bucket)(pcPairs, cnt)
//    //CompactPred.EvalNodePartial1(root, db)
//    CompactPred.ReportTree(root)
//
//  }
//
//
//  // todo
//  def TestBucketEntropySort(dataPath: String, ruleFile: String): Unit = {
//    showInitInfo()
//
//
//    val ruleTable = RuleTable(ruleFile)
//    val sourceList = ruleTable.getRules.toList
//    val dataFile = dataPath
//
//    val rowTable: RowTable = RowTable("airports", dataFile)
//
//    logger.info("Building Bucket")
//    val bucket = Bucket.from(rowTable)
//
//
//    val data = bucket.getData
//
//    val m = data.keys.map {
//      col => col -> bucket.getEntropy(col)
//    }
//
//    logger.info(s"Entropy ${bucket.getName}: $m")
//
//    val exprList = sourceList.map(r => RuleParser.parse(r).get)
//
//
//    val es = exprList.map(flatExpr)
//
//    // stat atom number
//    val atomMap = mutable.Map.empty[Expression, Int]
//
//    for (ls <- es; e <- ls) {
//      val count = atomMap.getOrElseUpdate(e,0)
//      atomMap.update(e, count + 1)
//    }
//
//    logger.info(s"stat atom number: $atomMap")
//
//    // ---------------------
//    def splitPrelConcl(e: List[_ <: Expression]) = {
//      val conclusion = e.last
//      val preliminary = e.dropRight(1)
//      (preliminary, conclusion)
//    }
//
//    val pcPairs = es.map(splitPrelConcl)
//
//    for (pair <- pcPairs) {
//      val X = pair._1
//      logger.info(s" before: $X")
//      val sortedX = X.sortWith(CompactPred.exprEntropy(bucket))
//
//      val p = sortedX.map {
//        case t: TCalc => (t, CompactPred.tcalcEntropy(bucket, t))
//        case _ => throw new Exception()
//      }
//
//      logger.info(s" after: $p")
//    }
//
//  }
//
//
//}
