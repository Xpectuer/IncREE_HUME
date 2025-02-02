package org.dsl.baseline

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.dsl.SparkMain.logger
import org.dsl.mining.CSVRow2
import org.dsl.pb.ProgressBar
import org.dsl.utils.{$, CmdOpt, Config}

import scala.collection.mutable.ArrayBuffer

object AprioriMain {



  def main(args: Array[String]): Unit = {

    $.bannerApriori()


    //    val logFile = "./README.md" // Should be some file on your system
    val sparkConf = new SparkConf()
      .set("spark.driver.extraJavaOptions", "-XX:+UseG1GC")
      .set("spark.executor.extraJavaOptions", "-XX:+UseG1GC")
      .set("spark.driver.memory", "32g")
      .set("spark.executor.memory", "32g")

    val spark = SparkSession.builder
      .appName("Apriori Inc Mining Experiment Application")
      .master("local[*]")
      .config(sparkConf)
      .getOrCreate()


    import scopt.OParser
    // Create a builder for the OParser
    val parser = CmdOpt.getParser

    // Parse the command-line arguments
    OParser.parse(parser, args, CmdOpt()) match {
      case Some(config) =>
        // If parsing is successful, use the config
        println(s"Output: ${config.out}")
        println(s"Use checkpoint: ${config.use_ckpt}")

        Config.OUTDIR = Config.getOUTDIR(config.out)
        Config.USE_CKPT = config.use_ckpt
        Config.TOPK_RATE = config.topNRate

        // vary paramTask
        val dataPath = config.dataset
        val paramRelPath = config.param
        val workerInstances = config.instanceNum


        val params = $.readParam(paramRelPath)
        assert(params.nonEmpty)


        val instanceNum = workerInstances
        val pb = new ProgressBar(params.size)
        logger.info(s"Reading Dataset ${dataPath}...")

        val df = spark.read.option("header", "true")
                        .option("inferSchema", "true").csv(dataPath)

        val csvRows = ArrayBuffer.empty[CSVRow2]
        for (p <- params) {
          pb += 1
          /** Update supports.
          *   Input support is the fraction of |D|&^2.
          *   For single tuple association rules, the support parameter should take a square root. */
          val p_updated = p.copy(suppOld = math.sqrt(p.suppOld), suppNew = math.sqrt(p.suppNew))
          val csvRow = APriori.fpGrowth(dataPath, spark, df, p_updated, instanceNum)
          csvRows.+=(csvRow)
          val fileOut = s"apriori_${dataPath.split("/").last}_${paramRelPath.split("/").last}.csv"
          $.WriteResult(fileOut, s"${CSVRow2.getHeader}\n${csvRows.mkString("\n")}")
        }


      case _ =>
      // If parsing fails, an error message will be displayed

    }
  }

}
