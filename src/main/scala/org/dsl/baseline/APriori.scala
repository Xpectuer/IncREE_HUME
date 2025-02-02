package org.dsl.baseline

import org.apache.spark.ml.fpm.FPGrowth
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.dsl.emperical.table.TypedColTable
import org.dsl.mining.{CSVRow2, ParameterLambda}
import org.dsl.utils.{$, IndexProvider, Wrappers}

import scala.collection.{Seq, mutable}

object APriori {

  private def compressTable(seq: Seq[Int]) = {

    seq.zipWithIndex.filter(p => p._1 == 1).map(_._2)
  }

  def transactions(sparkSession: SparkSession, df: DataFrame) = {
    import sparkSession.implicits._
    val t = df.map(
      row => {
        val tid = row.getInt(0)
        val values: Seq[Int] = row.toSeq.drop(1).asInstanceOf[Seq[Int]]

        //println(values)

        CRow(tid, values)
      })
    t
  }


  private def collectL1(spark: SparkSession, t: Dataset[CRow]) = {
    import spark.implicits._

    val plis = t.map(
      cRow => {
        val items = cRow.items
        val columnN = items.size
        val tid = cRow.tid
        //println(tid, items)

        val pli = mutable.Map.empty[Int, Set[Int]]

        items.foreach(
          item => {
            pli.getOrElseUpdate(item, Set(tid))
          })

        pli
      })

    def addup(a: PLI, b: PLI): PLI = {
      val c = mutable.Map.empty[Int, Set[Int]]
      b.foreach {
        case (item, tidsB) =>
          val tidsA = a.getOrElseUpdate(item, tidsB)
          a.update(item, tidsA.union(tidsB))
      }

      a
    }


    plis.reduce((a, b) => addup(a, b))
  }

  private type PLI = mutable.Map[Int, Set[Int]]

  def fpGrowth(dataName: String, spark: SparkSession, df: DataFrame,
               param: ParameterLambda, instanceNum: Int): CSVRow2 = {
    param match {
      case p@ParameterLambda(suppOld, suppNew, confOld, confNew, _, _) =>
        import spark.implicits._
        println(p)
        df.show()
        val transactions = df.map {
          row => row.toSeq.asInstanceOf[Seq[Int]].toSet
        }.toDF("items")
        //
        //        transactions.show()

        //        val transactions = spark.createDataset(Seq(
        //          "1 2 5",
        //          "1 2 3 5",
        //          "1 2")
        //        ).map(t => t.split(" ")).toDF("items")
        transactions.show()
        println(s"instanceNum: $instanceNum")

        val fpGrowth = new FPGrowth()
          .setMinSupport(suppOld)
          .setMinConfidence(confOld)
          .setItemsCol("items")
          .setNumPartitions(instanceNum)

        val rowSize = df.count()


        val (model1, timeRules1) = Wrappers.timerWrapperRet {
          val model = fpGrowth.fit(transactions)
          //        val (itemSet1, timeItemSet1) = Wrappers.timerWrapperRet(model1.freqItemsets.collect())
          model.freqItemsets.show()
          model.associationRules.show()
          model
        }

        println(
          s"""
             |BATCH 1:
             |RULE GEN TIME: $timeRules1""".stripMargin)

        val (modelI, timeRulesI) = Wrappers.timerWrapperRet {
          val model = fpGrowth.setMinSupport(suppNew).setMinConfidence(confNew).fit(transactions)
          //        val (itemSet1, timeItemSet1) = Wrappers.timerWrapperRet(model1.freqItemsets.collect())
          model.freqItemsets.show()
          model.associationRules.show()
          model
        }



        println(
          s"""
             |INC:
             |RULE GEN TIME: $timeRulesI""".stripMargin)


        CSVRow2(
          dataName = dataName, numPrediactesTemp = 0,
          numPrediactesConst = 0, rowSize = rowSize.toInt,
          pLambda = param, tempTimeB2 = 0d, constTimeB2 = 0d,
          tempTimeInc = timeRulesI, constTimeInc = 0d,
          numSamples = 0, numREEs = s"${model1.associationRules.count().toInt}", trueRecall = 1, numInstances = instanceNum, sampleSize = 0)
    }


  }

  def batchMine(spark: SparkSession, df: DataFrame, minSupp: Double, supp_threshold: Double, conf_threshold: Double) = {
    val cols = df.columns

    //println(cols.mkString(",\n"))

    val valCols = cols.drop(1)
    val idp = IndexProvider(valCols)

    println("Transforming...")
    val t = transactions(spark, df)

    import spark.implicits._
    val totalSize: Long = df.count() * df.columns.length
    println(s"totalSize: ${totalSize}")
    val min_supp_cnt = minSupp * totalSize
    val supp_cnt = supp_threshold * totalSize

    // get L1
    println(s"Collecting Transactions With Columns ${cols.mkString(" | ")}")
    val L1 = collectL1(spark, t).toSeq.toDS()
    val F1 = L1.filter(p => p._2.size >= min_supp_cnt)
      .map {
        case (item, tids) => Set(item) -> tids
      }.collect()


    val levelBound = 5
    var n = 0


    println("Searching Larger Subset of Item Tuples...")
    val (lattice, time) = Wrappers.timerWrapperRet {
      val lattice = mutable.ArrayBuffer.empty[Array[(Set[Int], Set[Int])]]
      var FP = F1
      while (n <= levelBound) {
        println(s"Apriori Level ${n}")
        val L_P_plus_1 = FP.flatMap {
          case (item, tids) =>
            F1.toSeq.toDS().filter(_._1 != item).map {
              case (item1, tids1) =>
                (item ++ item1) -> tids.intersect(tids1)
            }.collect()
        }

        lattice += FP
        FP = L_P_plus_1.filter(_._2.size >= min_supp_cnt)

        n += 1
      }

      lattice

    }


    val (arms, mtime) = Wrappers.timerWrapperRet {
      (for {i <- lattice.indices;
            xs = lattice(i)
            rhss = F1
            x <- xs
            rhs <- rhss
            supp = x._2.intersect(rhs._2).size
            conf = supp / x._2.size.toDouble
            if supp >= supp_cnt && conf >= conf_threshold && rhs._1 != x._1
            } yield {

        x._1 -> rhs._1
      }).toSet
    }

    println(arms)


    $.WriteResult(s"/apriori/${minSupp}_${supp_threshold}_$conf_threshold.txt",
      s"Apriori TIME: $time\nMining Time: $mtime\n\n${arms.mkString("\n")}")


    //    val L2 = F1.flatMap {
    //      case (item, tids) =>
    //        F1.toSeq.toDS().filter(_._1 != item).map {
    //          case (item1, tids1) =>
    //            Set(item, item1) -> tids.intersect(tids1)
    //        }.collect()
    //    }


    //println(L2.take(5).mkString(",\n"))



    println(lattice.map(_.length).mkString("\n\n"))
  }


  def initAMineArg(spark: SparkSession,
                   table: TypedColTable,
                   supp_threshold: Double,
                   conf_threshold: Double) = {

    AMineArg(spark, table, supp_threshold, conf_threshold)

  }

  def initAMineArg(spark: SparkSession,
                   table: TypedColTable) = {

    AMineArg(spark, table, 0d, 0d)

  }


}

// Associated Rule Mine Arguments
case class AMineArg(spark: SparkSession, pliSet: TypedColTable, supp_threshold: Double, conf_threshold: Double)
