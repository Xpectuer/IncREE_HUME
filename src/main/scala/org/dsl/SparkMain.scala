package org.dsl

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.dsl.emperical.table.TypedColTable
import org.dsl.mining._
import org.dsl.pb.ProgressBar
import org.dsl.reasoning.predicate.PredicateSet
import org.dsl.utils._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object SparkMain {

  val logger: HUMELogger = HUMELogger(getClass.getPackage.getName)

  def varyInstances(dataName: String, paramName: String, mineArg: MineArg, parameterList: ParameterLambda, worker_instances: Int) = {
    parameterList match {
      case p@ParameterLambda(suppOld, suppNew, confOld, confNew, recall, dist) =>
        val rawResultFileName = s"${dataName}_${suppOld}_${confOld}_${dist}_${worker_instances}.hckpt"
        val rb1WithTime = $.GetResultJsonOrElseMine(rawResultFileName, mineArg.p2i, {
          val mineArgOld = mineArg.updateThreshold(suppOld, confOld)
          val (rb1, timeB1) = Wrappers.timerWrapperRet(PMiners.batchMine(mineArgOld))
          //if (Config.ENABLE_DEBUG)
          $.WriteResult(s"${dataName}_${p}_rb1.txt", s"TIME:$timeB1\n$rb1")

          MineResultWithTime(rb1, timeB1, -1)
        })

        val csv = CSVRow(rb1WithTime.totalTime, -1, p, faster = -1d, numberNewREEs = "-1", trueRecall = -1d)
        val csvData = Iterable(csv)

        $.WriteResult(s"${dataName}_${paramName}_${worker_instances}_benchmark.csv", "timeB2,timeI,oldSupp,oldConf,newSupp,newConf,recall,K,faster(%),reeDelta,trueRecall\n" + csvData.mkString("\n"))


    }


  }

  /**
   * TODO: refactor: merge run multi func with run one
   * Exp-1
   */
  private def varyParamTask(dataPath: String, paramName: String, mineArg: MineArg, parameterList: Iterable[ParameterLambda], worker_instances: Int, table: TypedColTable): Unit = {
    val pb = new ProgressBar(parameterList.size)
    val table = $.LoadDataSet(dataPath).getOrElse(???)
    // lambda -> time
    val dataName = dataPath.split("/").last

    def write(csvData: Iterable[CSVRow2]) = {
      val oHeader = CSVRow2.getHeader
      val enable_sampling = if(Config.ENABLE_SAMPLING) "sampling_yes" else "sampling_no"
      $.WriteResult(s"${dataName}_${paramName}_${worker_instances}${decideFlag}_${enable_sampling}_benchmark.csv", oHeader + csvData.mkString("\n"))
    }


    val ckpt = ArrayBuffer.empty[CSVRow2]
    val csvData = parameterList.map {
      case p@ParameterLambda(_, _, _, _, recall, dist) =>
        // update with new supp, new conf, recall and radius
        val mineArgWithNewRecall = mineArg.updateRecallAndRadius(recall, dist)
        pb += 1
        val row = runBatchVSInc(dataName, mineArgWithNewRecall, p, table)
        ckpt.+=(row)
        write(ckpt)
        row
    }

    write(csvData)
  }


  private def getTrueRecall(rb2Con: MineResult, rIncCon: MineResult): Double = {
    if (rb2Con.result.isEmpty) return 1.0

    /** chx: Update true recall calculation. */
    val groupInc = rIncCon.result.map(_.ree).groupBy(_.p0).mapValues(_.map(_.X))
    val groupRb2 = rb2Con.result.map(_.ree).groupBy(_.p0).mapValues(_.map(_.X))
    val retrieved = rb2Con.result.toSet.intersect(rIncCon.result.toSet).size

    1.0 * retrieved / rb2Con.result.size
  }


  private def runBatchVSInc(filename: String, mineArg: MineArg, param: ParameterLambda, table: TypedColTable): CSVRow2 = {
    require(!filename.contains("/"))
    require(param.suppOld <= 0.1 && param.suppNew <= 0.1 && param.confOld >= 0.3 && param.confNew >= 0.3)
    param match {
      case p@ParameterLambda(suppOld, suppNew, confOld, confNew, recall, dist) =>
        val mineArgB1 = mineArg.updateRecallAndRadius(recall, dist).updateThreshold(suppOld, confOld)
        val rawResultFileName = s"${filename}_${suppOld}_${confOld}_${dist}.hckpt"
        $.CleanResultJson(rawResultFileName)


        val MineResultWithTime(rb1, timeB1, _) = $.GetResultJsonOrElseMine(rawResultFileName, mineArg.p2i, {
          val ENABLE_SAMPLING_OLD = Config.ENABLE_SAMPLING
          Config.ENABLE_SAMPLING = true
          val (rb1, timeB1) = Wrappers.timerWrapperRet(PMiners.batchMine(mineArgB1))
          Config.ENABLE_SAMPLING = ENABLE_SAMPLING_OLD
          MineResultWithTime(rb1, timeB1, -1)
        })

        logger.info(
          s"""
             |🎒BATCH 1 $p DONE.
             |📚Parameter: $p
             |⏰TIME: $timeB1
             |🌲SIZE:${rb1.result.size}
             |""".stripMargin)

        $.CleanResultJson(rawResultFileName)

        val instanceNum = mineArg.instanceNum
        $.WriteResult(s"batch1_${filename}_${param}_${instanceNum}.txt", s"TIME:$timeB1\n$rb1")
        //$.WriteResult(s"batch1_prune_${filename}_${param}_${instanceNum}.txt", s"TIME:$timeB1\n${rb1.pruned.toIndexedSeq.sortBy(_.ree.X.size).mkString("\n")}")

        val mineArgInc = mineArg.updateThreshold(suppNew, confNew)

        val (rInc, timeInc) = if (Config.enable_incremental) {
          Wrappers.timerWrapperRet {
            //Config.ENABLE_SAMPLING = true
            val r = PMiners.incMine(rb1, mineArgInc, p.suppOld, p.confOld)
            //Config.ENABLE_SAMPLING = false
            r
          }
        } else {
          (MineResult.empty, -1d)
        }

        $.WriteResult(s"inc_${filename}_${param}_$instanceNum.txt",
          s"TIME:$timeInc(s)\n$rInc")
        logger.info(
          s"""
             |➕INC $p DONE.
             |⏰TIME: $timeInc
             |📚Parameter: $p
             |🌲SIZE: ${rInc.result.size}
             |""".stripMargin)


        val mineArgB2 = mineArg.updateThreshold(suppNew, confNew)
        val rawResultFileName2 = s"${filename}_${suppNew}_${confNew}_$dist.hckpt"

        $.CleanResultJson(rawResultFileName2)
        val MineResultWithTime(rb2, timeB21, _) = {
          if (Config.enable_batch2) {
            val ENABLE_SAMPLING_OLD = Config.ENABLE_SAMPLING
            Config.ENABLE_SAMPLING = false
            val (rb2Temp, timeB2Temp) = Wrappers.timerWrapperRet(PMiners.batchMine(mineArgB2))
            Config.ENABLE_SAMPLING = ENABLE_SAMPLING_OLD
            val r = MineResultWithTime(rb2Temp, timeB2Temp, -1)
            $.WriteResult(s"batch2_${filename}_${param}_${instanceNum}.txt", s"TIME:${timeB2Temp}(s)\n${rb2Temp}")
            r

          } else {
            MineResultWithTime(MineResult.empty, -1d, -1d)
          }
        }

        val timeB2 = if (Config.enable_batch2) {
          timeB21
        } else {
          -1d
        }


        logger.info(
          s"""
             |🎒BATCH 2 $p DONE.
             |📚Parameter: $p
             |⏰TIME: $timeB2
             |🌲SIZE: ${rb2.result.size}
             |""".stripMargin)

        $.CleanResultJson(rawResultFileName2)
        val trueRecall = getTrueRecall(rb2, rInc)
        val diff = rb2.result.toSet diff rInc.result.toSet
        $.WriteResult("diff.txt", diff.mkString("\n"))


        if (Config.SANITY_CHECK) {
          debug(rInc: MineResult, rb2: MineResult, rb1: MineResult, mineArgInc: MineArg)
          if (Config.ENABLE_DEBUG_SINGLE_RHS) assert(rb2.result.groupBy(e => e.ree.p0).size <= 1, s"Not Single RHS ${rb2.result.groupBy(_.ree.p0)}")

          /** Result validation */
          assert(trueRecall >= mineArgInc.recall,
            s"True recall ${trueRecall} lower than recall bound ${mineArgInc.recall}")
          assert(timeInc < timeB2, s"Incremental ${p} (${timeInc}) slower than batch miner (${timeB2}).")
        }

        // sample size in (MB)
        val sample_byte_size_single = 4 * 8 + 8 * 2
        val sampleSize: Double = 1.0 * rb1.result.size * sample_byte_size_single / (1 << 20)

        logger.info(s"⚠️True Recall⚠️ $trueRecall")
        logger.debug("Writing Debug Info...")


        val flag = decideFlag
        CSVRow2(dataName = filename + flag, numPrediactesTemp = mineArg.p2i.size, numPrediactesConst = 0, rowSize = table.rowNum,
          pLambda = p, tempTimeB2 = timeB2, constTimeB2 = 0, tempTimeInc = timeInc, constTimeInc = 0,
          numSamples = rb1.samples.size, numREEs = s"rb2=${rb2.result.size}(rees)/inc=${rInc.result.size}(rees)", trueRecall = trueRecall, numInstances = mineArg.instanceNum, sampleSize = sampleSize)
    }
  }

  private def decideFlag = {
    var s = ""
    if (Config.enable_batch2) s += "_batch2"
    if (Config.enable_incremental) s += "_inc"
    s
  }


  def debug(incResult: MineResult, rb2Result: MineResult, rb1Result: MineResult, mineArg: MineArg): Unit = {
    val relevantRees = rb2Result.result.filter(ree => mineArg.rhsSpace.toSet.contains(ree.ree.p0)).toSet
    val diff = relevantRees.diff(incResult.result.toSet)
    for (d <- diff) {
      findCoveringSample(d.ree, rb1Result, mineArg)
    }
  }

  /** chx: Debug conf decrease imperfect recall issue.
   * Input:
   *      - x: a REE in batch result but no in inc. result.
   */
  private def findCoveringSample(x: REE, rb1Result: MineResult, mineArg: MineArg) = {

    val rhs = x.p0
    val samples = rb1Result.samples.filter(s => s.rhs == rhs)
    val radius = mineArg.K
    val m = mutable.Map.empty[PredicateSet, Sample] ++ samples.flatMap(s => s.predecessors.map(p => p -> s))
    /** Look for the corresponding sample using the same procecdure in inc. miner. */
    val hasPredecessor: Option[Sample] = {
      import PSampler.getSample

      getSample(radius, x.X, m)
    }

    hasPredecessor match {
      case Some(predSample) => {
        /** check if X is covered by the sample. */
        logger.info(x, predSample)
      }
      case None => logger.info(s"NOT FOUND: ${x}")
    }
  }


  def main(args: Array[String]): Unit = {

    require(!Config.ENABLE_SAMPLING)
    $.banner
    println(s"arg length: ${args.length}")

    // Create a builder for the OParser
    import scopt.OParser

    val parser = CmdOpt.getParser

    // Parse the command-line arguments
    OParser.parse(parser, args, CmdOpt()) match {
      case Some(config) =>
        // If parsing is successful, use the config

        Config.OUTDIR = Config.getOUTDIR(config.out)
        Config.USE_CKPT = config.use_ckpt
        Config.TOPK_RATE = config.topNRate
        Config.EXPAND_CHUNK_SIZE = config.expandChunkSize
        if (config.scalability) {
          Config.USE_CKPT = false
          Config.enable_incremental = false
          Config.enable_batch2 = false
        }

//        if (config.incBaseline) {
//          Config.INC_BASELINE = true
//        }

        val miner = config.miner
        if (miner == "batch") {
          Config.enable_incremental = false
          Config.enable_batch2 = true
        } else if (miner == "inc") {
          Config.enable_incremental = true
          Config.enable_batch2 = false
        } else if (miner == "all") {
          Config.enable_incremental = true
          Config.enable_batch2 = true
        } else {
          assert(assertion = false, "--miner(-m) should be in [inc, batch, all]")
        }

        if(config.levelwiseSampling) {
          Config.ENABLE_SAMPLING = true
        }

        println(config)

        // vary paramTask
        val dataPath = config.dataset
        val paramRelPath = config.param
        val workerInstances = config.instanceNum

        logger.info(s"aaaParam:${paramRelPath}")

        println(
          s"""
             |🌶OutputDIR: ${config.out}"
             |
             |⚠️CRITICAL CONFs(Config.scala):
             |👁ENABLE_DEBUG_SINGLE_RHS=${Config.ENABLE_DEBUG_SINGLE_RHS}
             |✂️ENABLE_TOPK=${Config.ENABLE_TOPK}
             |✂️ENABLE_TOPK_OVERLAP=${Config.ENABLE_TOPK_OVERLAP}
             |❤️‍🩹SANITY_CHECK=${Config.SANITY_CHECK}
             |✂️CONF_OPT=${Config.ENABLE_MIN_CONF_FILTER_OPT}
             |✂️TOPN=${Config.TOPK_RATE}
             |🦴EXPAND_CHUNK_SIZE=${Config.EXPAND_CHUNK_SIZE}
             |📢ENABLE_CKPT=${Config.USE_CKPT}
             |ENABLE_INC=${Config.enable_incremental}
             |ENABLE_BATCH=${Config.enable_batch2}
             |""".stripMargin)

        // init spark
        //    val logFile = "./README.md" // Should be some file on your system
        val sparkConf = new SparkConf()
          //          .set("spark.driver.extraJavaOptions", "-XX:+UseG1GC")
          //          .set("spark.executor.extraJavaOptions", "-XX:+UseG1GC")

          .set("spark.driver.memory", "16g")
          .set("spark.executor.memory", "8g")
          .set("spark.executor.core", "1")
          .set("spark.executor.instances", workerInstances.toString)

        val spark = SparkSession.builder
          .appName("Inc Mining Experiment Application")
          .master("yarn")
          .config(sparkConf)
          .getOrCreate()

        val instanceNum = spark.sparkContext.getConf.getAll.toMap
          .getOrElse("spark.executor.instances", "Not Found")
        println("⚠️True Instance Num：⚠️", instanceNum)

        val tables = $.LoadDataSet(Vector(dataPath))
        assert(tables.nonEmpty)

        logger.info("⏰ InitMine Arg...")
        val mineArg = PMiners.initMineArg(spark, db = tables, workerInstances)
        logger.info("👌🏻 Done InitMine Arg...")

        val filePath = dataPath
        val paramName = paramRelPath.split('/').last
        val params = $.readParam(paramRelPath)
        assert(params.nonEmpty)

        varyParamTask(filePath, paramName, mineArg, params, workerInstances, tables.head)
        spark.stop()

      case _ =>
      // If parsing fails, an error message will be displayed

    }


  }


}
