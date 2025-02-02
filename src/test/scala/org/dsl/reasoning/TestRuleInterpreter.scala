//package org.dsl.reasoning
//
//import org.dsl.dataStruct.{Counter, Statistics}
//import org.dsl.emperical.pli.Bucket
//import org.dsl.emperical.table.RowTable
//import org.dsl.exception.{InterpException, ParserException}
//import org.dsl.reasoning.vanilla.RuleInterpreter
//import org.dsl.reasoning.predicate.{Expression, TableInstanceAtom}
//import org.dsl.utils.{Config, HUMELogger, Sampling, Wrappers}
//
//import scala.collection.mutable
//import scala.language.postfixOps
//import scala.sys.exit
//import scala.util.{Failure, Success, Try}
//
//object TestRuleInterpreter {
//  val logger: HUMELogger = HUMELogger(getClass.getName)
//
//  type IncrRestPair = (RowTable, RowTable)
//
//
//  private def doInterpAll(ruleList: List[String],
//                          db: Database, cnt: Counter): List[Try[InterpResult]] = {
//
//    val exprList = ruleList.map(source => RuleParser.parse(source))
//    val statList = exprList.map {
//      case Success(expr) =>
//        if (Config.PRED_TREE) {
//          RuleInterpreter.InterpWithCounter(expr, db, cnt)
//        } else {
//          RuleInterpreter.Interp(expr, db)
//        }
//      case Failure(err) =>
//        logger.info(err.getMessage, "\n")
//        exit(-1)
//    }
//
//    statList
//  }
//
//  private def doInterpAllIncr(ruleStatList: List[(Expression, Statistics)], db: Database,
//                              delta: DeltaDatabase): List[Try[InterpResult]] = {
//
//    ruleStatList.map {
//      case (expr, stat) =>
//        val newStat = RuleInterpreter.InterpIncremental(expr, db, delta, stat)
//        Wrappers.tryStatWrapperId1(newStat)
//    }
//  }
//
//  private def doInterpOne(source: String, db: Database): Try[InterpResult] = {
//
//    val tryExpr = RuleParser.parse(source)
//
//    val tryResult = tryExpr match {
//      case Success(expr) =>
//        logger.debug(expr)
//        RuleInterpreter.Interp(expr, db)
//      case Failure(err) =>
//        logger.info(err.getMessage, "\n")
//        exit(-1)
//    }
//    tryResult
//
//  }
//
//  private def doHandleResult(tryResult: Try[InterpResult]): Unit = {
//    logger.info("Success Interpret Rules")
//    tryResult match {
//      case Success(v) => println(s",\t ${
//        v.stat match {
//          case Left(s) => s
//          case Right(_) => "Unit"
//        }
//      }")
//      case Failure(e) => e match {
//        case ParserException(msg) => println(msg)
//        case InterpException(msg) => println(msg)
//        case _ => e.printStackTrace(); scala.sys.exit(-1)
//      }
//    }
//  }
//
//
//  private def doHandleResult(statList: List[Try[InterpResult]]): Unit = {
//    logger.info("Success Interpret Rules")
//    statList.zipWithIndex.foreach {
//      case (fst, idx) =>
//        fst match {
//          case Success(v) => println(s"Rule($idx),\t ${
//            v.stat match {
//              case Left(s) => s
//              case Right(_) => "Unit"
//            }
//          }")
//          case Failure(e) => e match {
//            case ParserException(msg) => logger.info(s"$idx, $msg")
//            case InterpException(msg) => logger.info(s"$idx, $msg")
//            case _ => e.printStackTrace(); scala.sys.exit(-1)
//          }
//        }
//
//    }
//  }
//
//
//  def TestSingleREE(source: String, dataFile: String): Unit = {
//
//    logger.info("Single Ree Test Starts")
//
//
//    // val source = "airports(t0) ^ airports(t1) ^ t0.keywords == t1.keywords ^ t0.ident == '00A' ->  t0.scheduled_service == t1.scheduled_service"
//    logger.info("REE: ", source)
//
//    // val dataFile = Config.PROJ_PATH + "/datasets/airport.test.csv"
//
//    val colTable = RowTable("airports", dataFile).transpose
//    val map = TableInstanceAtom("airports") -> colTable
//    val db = Database(mutable.Map.from(Map(map)))
//
//
//    val tryResult = doInterpOne(source, db)
//    doHandleResult(tryResult)
//
//  }
//
//  def showInitInfo(): Unit = {
//    val heapSize = Runtime.getRuntime.maxMemory()
//    val heapSizeMega = heapSize >> 20
//    logger.info(s"Heap Size: ${heapSizeMega} MB")
//  }
//
//
//  // rate =  |\delta D| / |D|
//  // (incr, rest)
//  private def splitRowtable(rowTable: RowTable, rate: Double): IncrRestPair = {
//    val incrSize: Int = (rate * rowTable.rowNum).toInt
//    val incrName: String = rowTable.getName + "_incr"
//
//    val restSize = rowTable.rowNum - incrSize
//    val restName: String = rowTable.getName + "_rest"
//
//    val (incr, rest) = Sampling.sampleSplitVanilla[List[(String, String)]](rowTable.getData, incrSize)
//    // logger.debug(incr)
//
//    (RowTable(incrName, incr), RowTable(restName, rest))
//  }
//
//  def TestAirport(dataFile: String, ruleFile: String): Unit = {
//    TestAirportRate(dataFile, ruleFile, 1)
//  }
//
//  /**
//   * base data rows: 10%
//   *
//   * @param dataFile
//   * @param ruleFile
//   * @param incrRate incrmental rate
//   */
//  def TestAirportRate(dataFile: String, ruleFile: String, incrRate: Double): Unit = {
//    val ruleTable = RuleTable(ruleFile)
//    val sourceList = ruleTable.getRules.toList
//
//
//    val rowTable = RowTable("airports", dataFile)
//
//    val (incrTable, _) = splitRowtable(rowTable, incrRate)
//
//    logger.debug("Transposing...")
//    val colTable = incrTable.transpose
//    val map = TableInstanceAtom("airports") -> colTable
//    val m = Map(map)
//    val db = Database(mutable.Map.from(m))
//    val cnt = Counter.empty
//
//    val statList = doInterpAll(sourceList, db, cnt)
//    doHandleResult(statList)
//
//    if (Config.PRED_TREE) {
//      logger.debug(s"cnt=$cnt")
//    }
//  }
//
//
//  def TestBucket(dataPath: String, ruleFile: String): Unit = {
//    showInitInfo()
//
//    val ruleTable = RuleTable(ruleFile)
//    val sourceList = ruleTable.getRules.toList
//    val dataFile = dataPath
//
//    val rowTable : RowTable = RowTable("airports", dataFile)
//
//    logger.info("Building Bucket")
//    val bucket = Bucket.from(rowTable)
//
//    logger.debug(s"Bucket: $bucket")
//
//    val m = Map(TableInstanceAtom("airports") -> bucket)
//    val db = Database(mutable.Map.from(m))
//
//    val cnt = Counter.empty
//    val statList = doInterpAll(sourceList, db, cnt)
//
//    doHandleResult(statList)
//  }
//
//
//  def TestIncremental(dataPath: String, ruleFile: String,
//                      sampleRate: Double, incrRate: Double): Unit = {
//    showInitInfo()
//
//    // init
//    logger.info("Tree Build Test Starts")
//    val ruleTable = RuleTable(ruleFile)
//    val sourceList = ruleTable.getRules.toList
//    val dataFile = dataPath
//
//
//    val rowTable : RowTable = RowTable("airports", dataFile)
//
//    val m  = Map(TableInstanceAtom("airports") -> rowTable)
//    val fullDB = Database(mutable.Map.from(m))
//
//    logger.info("Splitting...")
//    // split into 5000 rows (for testing)
//    val (sampleDB, _) = SplitIntoTwoDB(fullDB, sampleRate)
//
//    // analogy incremental experiment
//    val (incr, rest) = SplitIntoTwoDB(sampleDB, incrRate)
//    val colRest = DatabaseOps.BatchTranspose(rest)
//
//
//    // val map = SchemeAtom("airports") -> colTable
//    // val db = Database(Map(map))
//    // val cnt = Counter.empty
//    // val exprList = sourceList.map(s => RuleParser.parse(s).getOrElse(logger.error(s"Parse Error")))
//
//    // 1. D X D
//    // 2. ΔD X (D + ΔD)
//    def baseline(rules: List[String], dbIncr: Database, dbOrigin: Database): Unit = {
//      val cnt = Counter.empty
//      val originResults: List[Try[InterpResult]] = doInterpAll(rules, dbOrigin, cnt)
//
//      val pairList = originResults.map {
//        case Success(v) =>
//          val stat = v.stat match {
//            case Left(res) =>
//              logger.info(s"Original Statistics: $res")
//              res
//            case Right(_) => ???
//          }
//          (v.expr, stat)
//
//        case Failure(e) =>
//          logger.error(s"$e")
//          exit(-1)
//      }
//
//      // Incremental Start
//      val delta = dbIncr.toDeltaDB
//      val m = dbIncr.mapExtract {
//        case (schm, table) =>
//          val rightCol = table match {
//            case RowTable(_, _) => table.asInstanceOf[RowTable].transpose
//            case _ => table
//          }
//          schm -> rightCol
//      }
//      val transOriginalDB = Database(mutable.Map.from(m))
//
//      val (resList,time) = Wrappers.timerWrapperRet {
//        doInterpAllIncr(pairList, transOriginalDB, delta)
//      }
//
//      logger.info(s"Incremental Airport done in: $time (s)")
//
//      doHandleResult(resList)
//    }
//
//
//    baseline(sourceList, incr, colRest)
//
//  }
//
//
//}
//
