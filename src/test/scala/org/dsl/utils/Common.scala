package org.dsl.utils

import org.dsl.emperical.table.TypedColTable
import org.dsl.mining._

import java.nio.file._
import scala.collection.{immutable, mutable}

object Common {


  val logger: HUMELogger = HUMELogger(getClass.getName)


  def correlationAnalysis(miner:REEMiner) = {
    val allSpace = miner.getP2I.getObjects.toIndexedSeq

    allSpace.map{
      e=>
    }
  }


  def testIncByDataSetWithTHRESHOLD(relpath: String,
                                    oldSupp: Double = 0.8d, oldConf: Double = 0.5d,
                                    newSupp: Double, newConf: Double): Unit = {

    val dataSetName = relpath.split("/").last
    val miner = initMiner(relpath)
    // ============ batch ==================

    val (rb, _) = doMineBatch(miner, dataSetName, oldSupp, oldConf, 1)

    // =============== batch2 ================
    val enable_batch2 = true

    val (_, rb2) = if (enable_batch2) {
      doMineBatch(miner, dataSetName, newSupp, newConf, 2)
    } else {
      (MineResult.empty, MineResult.empty)
    }

    // ================ inc ==================
    if (Config.enable_incremental) {

      (rb, rb2) match {
        case (MineResultWithTime(rb, b1Time, _), MineResultWithTime(rb2, b2Time, _)) =>

          val (rIncTemp, timeIncTemp) =
            Wrappers.timerWrapperRet(
              miner.incMine(rb, oldSupp, oldConf, newSupp, newConf))

          logger.info(s"INC TEMPLATE SIZE: ${rIncTemp.result.size}")


          val (rInc, timeIncCon) =
            Wrappers.timerWrapperRet {
              val c = ConstRecovArg(miner.getDataset, rIncTemp, newSupp, newConf, miner.getP2I)
              miner.recoverConstants(c) match {
                case MineResult(result, p, s) =>
                  MineResult(REEMiner.minimizeREEStat(result), p, s)
              }
            }


          val timeInc = timeIncTemp + timeIncCon

          logger.info(s"Incremental Mining USING TIME: $timeInc")
          val fileNameInc = s"inc_dataset=${dataSetName}_oldsupp=${oldSupp}_oldconf=${oldConf}_newsupp=${newSupp}_newconf=${newConf}_recall=${Config.recall}_K=${Config.SAMPLE_DIST}.txt"
          logger.info(s"Writing Inc Result To: $fileNameInc")
          $.WriteResult(fileNameInc, s"TIME:$timeInc\n$rInc")
          $.WriteResult("[inc_readable]" + fileNameInc, rInc.readable)
          $.WriteResult("[INC_TEMP]" + fileNameInc, rIncTemp.readable)

          // =========================== INC MINER ==============================
          val (nInc, nBatch) = (rInc.result.size, rb2.result.size)
          val doDiff = nInc != nBatch
          if (doDiff && enable_batch2) {
            val fileNameDiff = s"diff_dataset=${dataSetName}_oldsupp=${oldSupp}_oldconf=${oldConf}_newsupp=${newSupp}_newconf=$newConf.txt"

            val diff = if (nInc > nBatch) {
              //assert(rb2.result.keySet.subsetOf(rInc.result.keySet))
              rInc.result.toSet diff rb2.result.toSet
            } else {
              //assert(rInc.result.keySet.subsetOf(rb2.result.keySet))
              rb2.result.toSet diff rInc.result.toSet
            }


            val fileDiffRaw = "diff.hckpt"

            val diffResStr = s"DIFF SIZE: ${diff.size}\nDIFF:\n ${diff.map(_.ree.readable).mkString("\n")}"
            logger.info("Writing DIFF to $fileNameDiff")
            $.WriteResult(fileNameDiff, diffResStr)
            $.WriteResult(fileDiffRaw, MineResult(diff, Iterable(), Iterable()).toJSON)
          }

          val csvRow = CSVRowDebug(dataName = dataSetName,
            oldSupp = oldSupp, newSupp = newSupp, oldConf = oldConf, newConf = newConf, K = Config.SAMPLE_DIST, recall = Config.recall,
            b1Time = b1Time, prunedN = rb.pruned.size, sampleN = rb.samples.size, b2Time = b2Time, incTime = timeInc)

          logger.debug("Writing Debug Info...")
          $.WriteDebugDataRow(csvRow)

          if (Config.recall == 1) {
            assert(rInc.result.size == rb2.result.size * Config.recall)
          } else {
            assert(rInc.result.size >= rb2.result.size * Config.recall)
          }


      }


    }
  }


  def testByDataSetWithTHRESHOLD(relpath: String, supp: Double = 0.8d, conf: Double = 0.5d): Unit = {
    val dataSetName = relpath.split("/").last

    val absPath = Config.addProjPath(relpath)
    val (tables: immutable.Iterable[TypedColTable], timeData) = Wrappers.timerWrapperRet($.LoadDataSet(Vector(absPath)))

    logger.info(s"PLI Building Takes $timeData(s) to Complete.")


    // todo: cross table
    val table = tables.head
    logger.info(s"Data Size: ${table.rowNum}")


    val miner = REEMiner(tables)

    // todo: OOM
    // >>> scipy.special.binom(31,15)
    //300540195.0


    val (rST, time) = Wrappers.timerWrapperRet(miner.batchMine(supp, conf))
    logger.info(s"Mining USING TIME: $time")

    val r = rST.mineResult

    val resultREEs = r.result
    val resultStr = s"RESULT: ${resultREEs.mkString("\n")}"
    val resultSize = resultREEs.size

    logger.info(resultStr)
    logger.info(s"RESULT SIZE: $resultSize")


    $.WriteResult(s"dataset=${dataSetName}_supp=${supp}_conf=$conf.txt",
      resultStr + "\n" + resultSize)
  }


  def isAbsPath(path: String): Boolean = path.startsWith(FileSystems.getDefault.getSeparator)

  def initMiner(path: String): REEMiner = {

    logger.info(s" Building PLI...")

    val absPath =
      if (isAbsPath(path)) {
        path
      } else {
        Config.addProjPath(path)
      }

    val (tables, timeData) = Wrappers.timerWrapperRet($.LoadDataSet(Vector(absPath)))


    // todo: cross table
    val table = tables.head
    logger.info(s"Data Size: ${table.rowNum}")

    val miner = REEMiner(tables)
    logger.info(s"PLI Building Takes $timeData(s) to Complete.")
    miner
  }


  /**
   *
   * @param dataPath     relative path to dataset
   * @param suppPairList (old, new)
   * @param confPairList (old, new)
   */
  def testMultipleParameterIncVsBatch(dataPath: String, paramPath: String, params: Iterable[ParameterLambda]): Unit = {

    val dataSetName = dataPath.split("/").last
    val miner = initMiner(dataPath)

    val paramName = paramPath.split('/').last

    val resSucc = mutable.ArrayBuffer.empty[CSVRow]

    var N = 0

    // (lambda parameter configuration) -> (mineResult, time)
    // todo: read From JSON
    val resMemo = mutable.Map.empty[(Double, Double), (MineResultWithSTime, Double)]
    val res = params.map {
      case p@ParameterLambda(oldSupp, newSupp, oldConf, newConf, recall, dist) =>
        N = N + 1

        // todo: make it not global
        Config.recall = recall
        Config.SAMPLE_DIST = dist

        logger.info(s"I am Doing TEST $N, $oldSupp->$newSupp | $oldConf->$newConf | ${Config.recall} | ${Config.SAMPLE_DIST}")

        //logger.info(s"P2I SIZE: ${miner.getP2I.getObjects.size}")
        val rawResultFileName1 = s"${dataSetName}_${oldSupp}_${oldConf}_${Config.SAMPLE_DIST}.hckpt"

        val resultWithTime1 = doMineBatch(miner, dataSetName, oldSupp, oldConf, 1) match {
          case (rTemp, _) => rTemp
        }

        val rawResultFileName2 = s"${dataSetName}_${newSupp}_${newConf}_${Config.SAMPLE_DIST}.hckpt"

        val (resultWithTime2, resultWithtime2Con) = doMineBatch(miner, dataSetName, newSupp, newConf, 2) match {
          case (rTemp, rCon) =>
            val filename = s"${miner.getDataset.getName}=$oldSupp->${newSupp}_conf=$oldConf->$newConf.txt"
            logger.info(s"Writing $filename...")
            writeResult("[batch2con]" + filename, rCon.mineResult, rCon.totalTime, "BATCH2")
            (rTemp, rCon)
        }


        $.CleanResultJson(rawResultFileName1)
        $.CleanResultJson(rawResultFileName2)

        val (rb1, rb2) = (resultWithTime1.mineResult, resultWithTime2.mineResult)
        val rb2Con = resultWithtime2Con.mineResult
        if (Config.enable_incremental) {
          val (rIncTemp, timeIncTemp) =
            Wrappers.timerWrapperRet(
              miner.incMine(rb1, oldSupp, oldConf, newSupp, newConf))


          val (rInc, timeIncCon) = Wrappers.timerWrapperRet {
            val c = ConstRecovArg(miner.getDataset, rIncTemp, newSupp, newConf, miner.getP2I)
            miner.recoverConstants(c)
          }


          val timeInc = timeIncCon + timeIncTemp

          logger.info(s"[INC] Constant Recovery Time :$timeIncCon")
          logger.debug(s"Find Smaller Time :${miner.smallerTime}")

          val timeBatch1 = resultWithTime1.totalTime
          val timeBatch2 = resultWithTime2.totalTime

          /**
           * Test
           */
          if (rInc.result.size < rb2.result.size * recall) {
            val filename = s"debug_supp=$oldSupp->${newSupp}_conf=$oldConf->$newConf.txt"
            writeResult("[batch1]" + filename, rb1, timeBatch1, "BATCH1")
            writeResult("[batch2]" + filename, rb2Con, timeBatch2, "BATCH2")
            writeResult("[inc]" + filename, rInc, timeInc, "INC")
            $.WriteResult("[inc_readable]" + filename, rInc.result.map(_.ree.readable).mkString("\n"))

            $.WriteResult(
              s"${dataSetName}_${paramName}_benchmark.csv",
              "timeB2,timeI,oldSupp,oldConf,newSupp,newConf,recall,K,faster(%),sample_num,true_recall\n"
                + resSucc.mkString("\n"))
            //fail("RESULT SIZE NOT CORRECT!!!")
          }

          val faster = (timeBatch2 / timeInc) * 100.0d
          val samplesSize = s"${rb1.samples.size}"
          val trueRecall = rInc.result.size.toDouble / rb2Con.result.size

          val row = CSVRow(timeBatch2, timeInc, p, faster, samplesSize, trueRecall)


          resSucc.+=(row)
          row
        }

    }

    $.WriteResult(s"${dataSetName}_${paramName}_benchmark.csv", "timeB2,timeI,oldSupp,oldConf,newSupp,newConf,recall,K,faster(%),sample_num,true_recall\n" + res.mkString("\n"))
  }

  def writeResult(filename: String, mineResult: MineResult, time: Double, desc: String): Unit = {


    val batchResStr =
      s"""DESCRIPTION: $desc
         |TIME: $time\n PRUNED SIZE:
         |$mineResult
          """.stripMargin('|')

    logger.info(s"Writing Batch Result To: $filename")

    $.WriteResult(filename, batchResStr)


  }


  /**
   * !!! DO NOT WRAP WITH $.GetResultJsonOrElseMine !!!
   *
   * @param miner
   * @param dataSetName
   * @param supp
   * @param conf
   * @param time
   * @return temp result * concrete result for inc miner
   */
  def doMineBatch(miner: REEMiner, dataSetName: String, supp: Double, conf: Double, time: Int) = {

    logger.info(s"Batch Mining TIME: $time")
    val fileNameBatch = s"batch${time}_dataset=${dataSetName}_supp=${supp}_conf=${conf}_recall=${Config.recall}_K=${Config.SAMPLE_DIST}.txt"

    val rawResultFileName = s"${dataSetName}_${supp}_${conf}_${Config.SAMPLE_DIST}.hckpt"
    logger.info(s"Finding $rawResultFileName")
    val resultWithTime = $.GetResultJsonOrElseMine(rawResultFileName, miner.getP2I, {
      val (rbST, timeBatch) = Wrappers.timerWrapperRet(
        miner.batchMine(supp, conf)
      )
      MineResultWithTime(rbST.mineResult, timeBatch, rbST.sampleTime)
    })


    resultWithTime match {
      case MineResultWithTime(rbTemp, tempTime, sampleTime) =>


        val writeBatch = true
        val rb = rbTemp

        val (resultREEs, _) = (rb.result, rb.pruned)

        val constRecov = ConstRecovArg(miner.getDataset, rbTemp, supp, conf, miner.getP2I)
        lazy val (rCon, constTime) = {
          Wrappers.timerWrapperRet(miner.recoverConstants(constRecov))
        }

        val rb1 = if (time == 1) rbTemp else rCon

        val totalTime = if (time > 1) tempTime + constTime else tempTime
        if (writeBatch) {
          writeResult(fileNameBatch, rb1, totalTime, "BATCHMINE")
          $.WriteResult("[readable]" + fileNameBatch, rb1.readable)
        }

        logger.info(s"Batch TIME: $totalTime")
        logger.info(s"Batch TEMP TIME: $tempTime")
        logger.info(s"Sample Time: $sampleTime")

        logger.info(s"Batch RESULT SIZE ${rb.result.size}")
        logger.info(s"Batch PRUNED SIZE ${rb.pruned.size}")
        logger.info(s"resultREEs.size:${resultREEs.size}")

        $.CleanResultJson(rawResultFileName)

        if (time == 1) {
          (resultWithTime, resultWithTime)
        } else {
          $.WriteResult("[temp_readble]" + fileNameBatch, rbTemp.readable)
          logger.info(s"BATCH${time} TEMPLATE RESULT SIZE:${rbTemp.result.size}")
          (resultWithTime, MineResultWithTime(rb1, totalTime, sampleTime))
        }

    }


  }

}
