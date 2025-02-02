package org.dsl.baseline

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.dsl.utils.{$, Wrappers}

object WordCountMain {


  def main(args: Array[String]): Unit = {

    $.bannerApriori()

    assert(args.length == 2)
    val inputPath = args(0)
    val workerInstances = args(1)


    val sparkConf = new SparkConf()
      //      .set("spark.driver.extraJavaOptions", "-XX:+UseG1GC")
      //      .set("spark.executor.extraJavaOptions", "-XX:+UseG1GC")
      .set("spark.debug.maxToStringFields", "100")
      .set("spark.driver.memory", "3g")
      .set("spark.executor.memory", "2g")
      .set("spark.executor.core", "1")
      .set("spark.executor.instances", workerInstances)

    val spark = SparkSession.builder
      .appName("WordCount")
      .master("yarn")
      .config(sparkConf)
      .getOrCreate()

    val sc = spark.sparkContext
    // Read input text file
    val inputFile = inputPath
    //val input = sc.textFile(inputFile, workerInstances.toInt)
    val s = Range.BigDecimal.inclusive(1, 1e6,1).map(_.toString + " units")
    val input = sc.parallelize(s)

    val (wordCounts, time) = Wrappers.timerWrapperRet {
      // Perform word count
      val words = input.flatMap(line => line.split("\\W+"))
      val wordCounts = words.map(word => (word, 1)) //.reduceByKey(_ + _)
      wordCounts.fold(("total", 0))((acc, x) => ("total", acc._2 + x._2))

    }

    println(wordCounts)
    println(s"WORD COUNT N=${workerInstances} TIME:${time}")

    // Save the result to an output file
    //    val outputFile = args(1)
    //    wordCounts.saveAsTextFile(outputFile)
  }

}
