import com.tdunning.math.stats.TDigest
import org.dsl.dataStruct.evidenceSet.HPEvidenceSet
import org.dsl.emperical.table.TypedColTable
import org.dsl.mining._
import org.dsl.reasoning.predicate.PredicateSet
import org.dsl.utils._
import org.scalatest.Tag
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.concurrent.TrieMap
import scala.util.Random
import scala.collection.{immutable, mutable}
import org.dsl.utils.Common._

class REETest extends AnyFunSuite {
  val logger: HUMELogger = HUMELogger(getClass.getName)


  test("debug_diff") {
    val K = Config.SAMPLE_DIST
    val fileDiffRaw = "out/diff.hckpt"
    val fileSampleRaw = Config.RAW_RESULT_PATH + s"/inspection.csv_1.0E-6_0.95_${K}.hckpt"
    val diffJson = $.readFile(fileDiffRaw)
    val fileSampleJson = $.readFile(fileSampleRaw)

    val relPath = "datasets/inspection.csv"
    val miner: REEMiner = initMiner(relPath)
    val p2i = miner.getP2I
    // val rand = new Random(114514)


    val diffSet = MineResult.fromJSON(diffJson, p2i).result.toSet
    val samples = MineResultWithTime.fromJSON(fileSampleJson, p2i).mineResult.samples.toSet

    println(diffSet)

    val sampleRHSMap = samples.groupBy(_.ree.p0).map(p => p._1 -> p._2.map(e => e.ree.X -> e.t))
    val dRHSMap = diffSet.groupBy(_.ree.p0).map(p => p._1 -> p._2.map(e => e.ree.X))


    for ((rhs, dxs) <- dRHSMap) {
      val ss = sampleRHSMap.getOrElse(rhs, Set.empty[(PredicateSet, TDigest)])
      for (dx <- dxs) {
        miner.hasPredecessor(K, mutable.Map.empty ++= ss, dx, p2i) match {
          case Some((p, _)) =>
            println(s"${dx}->${p2i.get(rhs).get} has predecessor ${p}")
          case None =>
            println(s"${dx} NO PREDECESSOR")
        }
      }
    }


  }


  test("REE_3w_0.01", Tag("REE_3w_0.01")) {
    testByDataSetWithTHRESHOLD("datasets/incremental/airport/airport_original.csv", supp = 0.01d)
  }

  test("REE_3w_0.001", Tag("REE_3w_0.001")) {
    testByDataSetWithTHRESHOLD("datasets/incremental/airport/airport_original.csv", supp = 0.001d)
  }

  test("REE_3w_1e-4", Tag("REE_3w_1e-4")) {
    testByDataSetWithTHRESHOLD("datasets/incremental/airport/airport_original.csv", supp = 1e-4d)
  }

  test("REE_3w_0.01_conf_0.9", Tag("REE_3w_0.01_conf_0.9")) {
    testByDataSetWithTHRESHOLD("datasets/incremental/airport/airport_original.csv", supp = 0.01d, conf = 0.9d)
  }

  test("REE_3w_0.01_conf_0.99", Tag("REE_3w_0.01_conf_0.99")) {
    testByDataSetWithTHRESHOLD("datasets/incremental/airport/airport_original.csv", supp = 0.01d, conf = 0.99d)
  }


  /**
   * supp up
   */
  test("REE_3w_supp0.01->0.1_conf_0.99", Tag("REE_3w_0.01->0.1_conf_0.99")) {
    testIncByDataSetWithTHRESHOLD(
      "datasets/incremental/airport/airport_original.csv",
      oldSupp = 0.01d, newSupp = 0.1d,
      oldConf = 0.99d, newConf = 0.99d)
  }


  /**
   * supp down
   */
  test("REE_3w_supp0.01->0.001_conf_0.90", Tag("REE_3w_0.01->0.001_conf_0.90")) {
    testIncByDataSetWithTHRESHOLD(
      "datasets/incremental/airport/airport_original.csv",
      oldSupp = 0.01d, newSupp = 0.001d,
      oldConf = 0.90d, newConf = 0.90d)
  }


  /**
   * supp down
   */
  test("REE_3w_supp0.1->0.01_conf_0.90", Tag("REE_3w_0.1->0.01_conf_0.90")) {
    testIncByDataSetWithTHRESHOLD(
      "datasets/incremental/airport/airport_original.csv",
      oldSupp = 0.1d, newSupp = 0.01d,
      oldConf = 0.90d, newConf = 0.90d)
  }

  private def buildEvidence(relpath: String): (REEMiner, Double) = {
    val (miner, time) = Wrappers.timerWrapperRet(initMiner(relpath))
    (miner, time)
  }


  /**
   * debug
   */

  test("REE_inspection_debug", Tag("REE_inspection_debug")) {

    testIncByDataSetWithTHRESHOLD(
      "datasets/inspection.csv",
      oldSupp = 1e-5d, newSupp = 1e-4d,
      oldConf = 0.95d, newConf = 0.85d)
  }


  test("REE_hospital_debug", Tag("REE_hospital_debug")) {

    testIncByDataSetWithTHRESHOLD(
      "datasets/hospital.csv",
      oldSupp = 1e-5d, newSupp = 1e-4d,
      oldConf = 0.95d, newConf = 0.85d)

    // println(s"merged: ${Config.sample_merged_n}\nnew:${Config.sample_new_n}")
  }


  test("REE_hospital_exp3", Tag("REE_hospital_exp3")) {

    //    testIncByDataSetWithTHRESHOLD(
    //      "datasets/hospital.csv",
    //      oldSupp = 1e-4d, newSupp = 1e-5d,
    //      oldConf = 0.95d, newConf = 0.85d)

    $.banner

    val varyKPath = "in/exp3-vary-k.csv"
    val varyK = $.readParam(varyKPath)
    testMultipleParameterIncVsBatch("datasets/hospital.csv", varyKPath, varyK)

    val varyRecallPath = "in/exp3-vary-recall.csv"
    val varyRecall = $.readParam(varyRecallPath)
    testMultipleParameterIncVsBatch("datasets/hospital.csv", varyRecallPath, varyRecall)
    println("Done")
  }


  test("REE_aminer_exp3", Tag("REE_aminer_exp3")) {

    //    testIncByDataSetWithTHRESHOLD(
    //      "datasets/hospital.csv",
    //      oldSupp = 1e-4d, newSupp = 1e-5d,
    //      oldConf = 0.95d, newConf = 0.85d)

    $.banner

    val varyKPath = "in/exp3-vary-k.csv"
    val varyK = $.readParam(varyKPath)
    testMultipleParameterIncVsBatch("datasets/hospital.csv", varyKPath, varyK)

    val varyRecallPath = "in/exp3-vary-recall.csv"
    val varyRecall = $.readParam(varyRecallPath)
    testMultipleParameterIncVsBatch("datasets/hospital.csv", varyRecallPath, varyRecall)
    println("Done")
    //println(s"merged: ${Config.sample_merged_n}\nnew:${Config.sample_new_n}")
  }


  test("REE_inspection_exp3", Tag("REE_inspection_exp3")) {

    //    testIncByDataSetWithTHRESHOLD(
    //      "datasets/hospital.csv",
    //      oldSupp = 1e-4d, newSupp = 1e-5d,
    //      oldConf = 0.95d, newConf = 0.85d)

    $.banner

    val varyKPath = "in/exp3-vary-k.csv"
    val varyK = $.readParam(varyKPath)
    testMultipleParameterIncVsBatch("datasets/inspection.csv", varyKPath, varyK)

    val varyRecallPath = "in/exp3-vary-recall.csv"
    val varyRecall = $.readParam(varyRecallPath)
    testMultipleParameterIncVsBatch("datasets/inspection.csv", varyRecallPath, varyRecall)
    println("Done.")
    //pri
  }

  test("REE_adult_debug", Tag("REE_hospital_debug")) {

    testIncByDataSetWithTHRESHOLD(
      "datasets/adult.csv",
      oldSupp = 1e-5d, newSupp = 1e-5d,
      oldConf = 0.85d, newConf = 0.95d)

    // println(s"merged: ${Config.sample_merged_n}\nnew:${Config.sample_new_n}")
  }

  test("REE_ncvoter_exp3", Tag("REE_ncvoter_exp3")) {

    //    testIncByDataSetWithTHRESHOLD(
    //      "datasets/hospital.csv",
    //      oldSupp = 1e-4d, newSupp = 1e-5d,
    //      oldConf = 0.95d, newConf = 0.85d)

    $.banner

    val dataPath = "datasets/ncvoter.csv"
    val varyKPath = "in/exp3-vary-k.csv"
    val varyK = $.readParam(varyKPath)
    testMultipleParameterIncVsBatch(dataPath, varyKPath, varyK)

    val varyRecallPath = "in/exp3-vary-recall.csv"
    val varyRecall = $.readParam(varyRecallPath)
    testMultipleParameterIncVsBatch(dataPath, varyRecallPath, varyRecall)
    println("Done.")
    //pri
  }


  test("REE_ncvoter_debug", Tag("REE_ncvoter_debug")) {

    testIncByDataSetWithTHRESHOLD(
      "datasets/ncvoter.csv",
      oldSupp = 1e-6d, newSupp = 1e-5d,
      oldConf = 0.95d, newConf = 0.85d)
  }


  test("REE_airport_debug", Tag("REE_airport_debug")) {

    testIncByDataSetWithTHRESHOLD(
      "datasets/airport.csv",
      oldSupp = 1e-6d, newSupp = 1e-6d,
      oldConf = 0.99d, newConf = 0.7d)
  }

  test("REE_AMiner_debug", Tag("REE_AMiner_debug")) {

    testIncByDataSetWithTHRESHOLD(
      "datasets/AMiner_Author_20%.csv",
      oldSupp = 1e-5d, newSupp = 1e-4d,
      oldConf = 0.95d, newConf = 0.85d)
  }



  //  test("REE_3w_debug", Tag("REE_3w_debug")) {
  //        testIncByDataSetWithTHRESHOLD(
  //          "datasets/incremental/airport/airport_original.csv",
  //          oldSupp = 0.1d, newSupp = 0.01d,
  //          oldConf = 0.65d, newConf = 0.50d)
  //      }


  test("json") {
    val relPath = "datasets/hospital.csv"
    val miner: REEMiner = initMiner(relPath)
    val p2i = miner.getP2I

    logger.info(miner.info())
    val ree = miner.generateOneREE
    val reeWS = REEWithStat(ree, 100, 1d)

    val j = reeWS.toJSON
    val reeNew = REEWithStat.fromJSON(j, p2i)

    println(j)
    assert(reeNew == reeWS)

    val tdigest = TDigest.createAvlTreeDigest(100)
    tdigest.add(2.0);
    tdigest.add(3.0)
    val reeWithTDigest = REEWithT[TDigest](ree, tdigest)

    val jT = REEWithT.toJSON(reeWithTDigest)
    //println(jT)
    val newREEWithTD = REEWithT.fromJSON(jT, p2i)

    assert(newREEWithTD.ree == reeWithTDigest.ree)

    assert(newREEWithTD.t.size() == reeWithTDigest.t.size())
    assert(newREEWithTD.t.cdf(0.2) == reeWithTDigest.t.cdf(0.2))
    assert(newREEWithTD.t.cdf(0.3) == reeWithTDigest.t.cdf(0.3))
    assert(newREEWithTD.t.cdf(2) == reeWithTDigest.t.cdf(2))

    val mineResult = MineResult(Iterable(reeWS), Iterable(reeWS), Iterable(reeWithTDigest))
    val mrJ = mineResult.toJSON
    val mineResultNew = MineResult.fromJSON(mrJ, p2i)

    println(mineResultNew)

    val mineResultWithTime = MineResultWithTime(mineResult, 10110.123, 1145.14)
    val mineResultWithTimeJson = mineResultWithTime.toJSON
    val mineResultWithTimeNew = MineResultWithTime.fromJSON(mineResultWithTimeJson, p2i)

    println(mineResultWithTimeNew)

  }

  test("pg") {
    //    val absPath = Config.addProjPath("datasets/airport.csv")
    //    val (tables, _) = Wrappers.timerWrapperRet($.LoadDataSet(Vector(absPath)))
    //    val pspace = PredSpace(tables.head)
    //    logger.info(s"pspace size: ${pspace.map(_._2.size).sum}")
    //    logger.info(s"pspace: ${pspace.mkString("\n")}")

    //val (evi, time) = buildEvidence("datasets/inspection.csv")
    //logger.info(evi.getEvidenceSet.map(_._1.size))

    val td = TDigest.createMergingDigest(100)
    td.add(10.0)
    td.add(5.0)
    td.add(123.32)
    td.add(0.23)
    td.add(0.15)


    println((td.getMin, td.getMax, td.size(), td.cdf(5)))

    val t = TrieMap.empty[Int, TDigest]
    val tv = t.getOrElseUpdate(1, {
      TDigest.createAvlTreeDigest(100)
    })
    tv.add(10)
    tv.add(5)
    tv.add(123.32)
    tv.add(0.23)

    (0 to 1000).foreach {
      e => tv.add(e)
    }

    println((tv.getMin, tv.getMax))

    val tvv = t.getOrElse(1, TDigest.createAvlTreeDigest(100))
    println((tvv.getMin, tvv.getMax, tvv.size()))


  }

  test("predecessor", Tag("predecessor")) {
    val relPath = "datasets/hospital.csv"
    val miner: REEMiner = initMiner(relPath)
    val p2i = miner.getP2I
    val rand = new Random(114514)

    logger.info(miner.info())
    val ree = miner.generateOneREE(5)
    val reeWS = REEWithStat(ree, 100, 1d)


    println(ree.X.size)
    println(ree)

    reeWS match {
      case REEWithStat(ree, supp, conf) =>
        val state = State(ree.X, ree.X, supp, conf, ree.X.size)

        println(ree)

        val idx = rand.nextInt(p2i.size)
        val expr = p2i.getObject(idx)
        println(s"rand idx $idx")

        val XNew = if (ree.X.size > 0) {
          (ree.X :- ree.X.head) :+ expr

        } else {
          ree.X
        }

        val ps = REEMiner.getPredecessors(5, ree.X)
        println(ps)

        val stateNew = State(XNew, XNew, 100, 0.88, XNew.size)
        println(s"stateNew: $stateNew")

        val states = mutable.Map(state -> (), stateNew -> ())

        val samples = miner.sampleLevelByPredecessor(states, p2i)

        println(samples.map(p => p._1 -> p._2.size()))


    }


  }

  def testBuildEvidences(path: String) = {
    val params = $.readParam(path)


    val directoryPath = Paths.get("datasets")


    val banned = Set("airport.csv", "tax_1000w.csv")
    //        val banned = Set.empty[String]


    logger.debug(params)

    logger.info(s"Preprocessing Build Evidences.... Banned: [${banned.mkString("|")}]")
    Files.walkFileTree(directoryPath, new SimpleFileVisitor[Path] {

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val filename = file.getFileName.toString

        if (file.toString.toLowerCase.endsWith(".csv") && !banned.contains(filename)) {
          val path = file.toAbsolutePath.toString
          val (_, time) = buildEvidence(path)
          $.WriteResult(s"evibuild_time_${file.getFileName}.txt", s"${file.getFileName}:$time (s)")
          logger.info(s"Build Evidence Done. $path")
        }

        FileVisitResult.CONTINUE
      }
    })
  }


  def testMultiFromParameters(path: String): Unit = {
    val params = $.readParam(path)


    val directoryPath = Paths.get("datasets")


    val banned = Set("airport.csv", "tax_1000w.csv")
    //        val banned = Set.empty[String]


    logger.debug(params)

    logger.info(s"Preprocessing Build Evidences.... Banned: [${banned.mkString("|")}]")
    Files.walkFileTree(directoryPath, new SimpleFileVisitor[Path] {

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val filename = file.getFileName.toString

        if (file.toString.toLowerCase.endsWith(".csv") && !banned.contains(filename)) {
          val path = file.toAbsolutePath.toString
          val (_, time) = buildEvidence(path)
          $.WriteResult(s"evibuild_time_${file.getFileName}.txt", s"${file.getFileName}:$time (s)")
          logger.info(s"Build Evidence Done. $path")
        }

        FileVisitResult.CONTINUE
      }
    })

    logger.info("Preprocessing Build Evidences Done! ")


    Files.walkFileTree(directoryPath, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val filename = file.getFileName.toString
        if (file.toString.toLowerCase.endsWith(".csv") && !banned.contains(filename)) {
          val path = file.toAbsolutePath.toString
          testMultipleParameterIncVsBatch(path, path, params)

        }
        FileVisitResult.CONTINUE
      }
    })

    ()

  }

  /**
   * CSV time perf
   */
  test("REE_3w_multi_conf", Tag("REE_3w_multi_conf")) {

    testMultiFromParameters("in/conf_in_var.csv")

  }

  test("build_evidence") {

    val list =
      List(
        "datasets/adult.csv",
        //"datasets/airport.csv",
        "datasets/hospital.csv",
        "datasets/inspection.csv",
        "datasets/ncvoter.csv",
        "datasets/dblp/AMiner_Author.csv",
        "datasets/tax_1000w.csv"
      )


    for (d <- list) buildEvidence(d)
  }


  test("REE_3w_multi_supp", Tag("REE_3w_multi_supp")) {
    testMultiFromParameters("in/supp_in_var.csv")
  }


  test("REE_rate_conf", Tag("REE_3w_rate_conf")) {
    testMultiFromParameters("in/conf_rate_var.csv")
  }


  test("Neighbour_GET") {
    val relPath = "datasets/hospital.csv"
    val conf = 0.0d
    val supp = 0.0d
    val miner = initMiner(relPath)

    logger.info(miner.info())
    miner.generateOneREE match {
      case ree@REE(x, rhs) =>
        logger.info(s"generated: ${ree}")

        val (rnb, overlap) = miner.getNeighbourUnitTest(x, Config.SAMPLE_DIST, rhs, supp, conf)
        // get neighbour OK
        assert(rnb.size == (30 - x.size) /*lhs size*/ * x.size)

        logger.info(s"OVERLAPPING:$overlap\nNEIGHBOUR SIZE:${rnb.size}\nGET NEIGHBOUR FOR $x -> $rhs:\n${rnb.mkString("\n")}")
        rnb.foreach(xp => assert((x dist xp._1) <= Config.SAMPLE_DIST))
        assert(REEMiner.minimize[Int](Map(ree.X -> 0, (x :+ rhs) -> 0)).size == 1)
    }
  }

  test("evidence research ") {
    val relPath = "datasets/airport.csv"
    val miner: REEMiner = initMiner(relPath)
    val p2i = miner.getP2I

    val evi = $.GetJsonIndexOrElseBuild("airport.csv.evi", p2i, {
      HPEvidenceSet()
    })

    val x = evi.map {
      case (_, count) => count
    }.toIndexedSeq.sorted

    val lessThanK = x.filter(_ < 120L)
    val greaterThanK = x.filter(_ >= 120L)
    println("lessThanK Frac", lessThanK.size / x.size.toDouble)
    println("lessThanK AVERAGE:", lessThanK.sum / lessThanK.size)
    println("greaterThanK AVG:", greaterThanK.sum / greaterThanK.size)

    def stat(coll: Iterable[Long]) = {

      import breeze.linalg._
      import breeze.plot._


      val max = coll.max
      val min = coll.min
      val size = coll.size
      val avg = coll.map(_ / coll.size.toDouble).sum


      print(
        s"""
           |size: ${size}
           |max: ${max}
           |min: ${min}
           |avg: ${avg}
           |""".stripMargin)

      //p += plot(x, x ^:^ 2.0)
      //p += plot(x, x ^:^ 3.0, '.')

      val td = TDigest.createAvlTreeDigest(200)


      coll.foreach(e => td.add(e))

      def welldefCDF(td: TDigest, ele: Long) = {
        val cdf = td.cdf(ele)
        if (cdf < 0) 0 else cdf
      }

      val f = Figure()
      val p = f.subplot(0)

      val step = 100
      val cdfs = (0L to max by step).map(e => welldefCDF(td, e))
      println(
        s"""
           |td min: ${td.cdf(td.getMin)}
           |td max ${td.cdf(td.getMax)}
           |
           |""".stripMargin)

      val l = ((max - 0) / step).toInt + 1
      val xx = linspace(min, max, l)


      println((xx.size, cdfs.size))
      //assert(cdfs.size == xx.size)


      p += plot(xx, cdfs)
      p.xlabel = "Count"
      p.ylabel = "Prob. of Count"


      f.saveas("freq_evi.pdf", dpi = 400)

    }


  }

  test("REE_const_recovery") {
    //    val relpath = "datasets/hospital.csv"
    //    val miner = initMiner(relpath)
    //    val (supp, conf) = (1e-6, 0.75)
    //    val rb = doMineBatch(miner, "hospital.csv", supp = supp, conf = conf, 666)
    //    val mineResult = miner.recoverConstants(ConstRecovArg(miner.getDataset, rb.mineResult, supp, conf))
    //    println(mineResult.result.size, mineResult.result.mkString("\n"))
    //    println(mineResult.result.size, mineResult.result.map(_.ree.readable).mkString("\n"))
    //

  }

}
