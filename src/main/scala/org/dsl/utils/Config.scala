package org.dsl.utils

import org.dsl.reasoning.predicate._

import java.nio.file.{Files, Paths}

object Config {

  def nproc: Int = (Runtime.getRuntime.availableProcessors * 0.8).toInt

  val enable_profiling = false
  var enable_incremental = true

  def getOUTDIR(path: String): String = {
    val out = path
    $.findFileOrElse(out,
      path => Files.createDirectories(path),
      _ => ())
    if (Config.ENABLE_CLEAN_OUTDIR) $.CleanDir(out)
    out
  }

  var OUTDIR = "out"

  val CACHE_PATH: String = addProjPath("/index")

  //  val RAW_RESULT_PATH = addProjPath("/out_raw")
  val RAW_RESULT_PATH: String = $.getTempDir("hume_out_raw")

  def EVIDENCE_IDX_NAME(dataset: String) = s"$dataset.evi"

  // operator prefix for hashing
  val OP_PREFIX: String = "hume$op$"

  lazy val PROJ_PATH: String = Paths.get(".").toAbsolutePath.toString

  def addProjPath(path: String): String =
    Paths.get(PROJ_PATH, path).toString

  val CONSTANT = false

  val REE_RHS: Set[Operator] = Set(Eq)
  val REE_LHS: Set[Operator] = Set(Lt, Gt, Eq, NEq, Le, Ge)
  //val REE_LHS: Set[Operator] = Set(Eq, NEq)

  // prune 1: deepest search level
    val levelUpperBound = 5
//  val levelUpperBound = 3

  // prune 2: if 2 co
  val confLowerBound = 0.3d

  val AWSAccessKey = ""

  val AWSAccessSecretKey = ""

  val AWSS3BucketName = "reeminer-datasets"

  // Not suitable for large dataset!!!
  val ENABLE_DEBUG: Boolean = false

  val CONSTANT_FILTER_RATE = 0.8
//      val CONSTANT_FILTER_RATE = 0.1

  var SAMPLE_DIST = 5

  var recall = 1d

  val WILDCARD = "$WILDCARD$"

  val BFS_LEVEL_CHUNK_SIZE = 4

  val MIN_CONF = 0.3

  val MIN_SUPP = 1e-7

  val INSTANCE_NUM_KEY = "spark.executor.instances"

  val TOPK = 30

  var TOPK_RATE = 1.0

  val ENABLE_MIN_CONF_FILTER_OPT = false

  val ENABLE_DEBUG_SINGLE_RHS = false
  val DEBUG_RHS_INDEX = 2
  val ENABLE_TOPK_OVERLAP = false
  val ENABLE_TOPK = false

  val SANITY_CHECK = false
  var USE_CKPT = false

  val FRAG_SIZE: Int = 5000
  val CHUNK_SIZE: Int = 5000 * 1000

  val ENABLE_CLEAN_OUTDIR = false

  var EXPAND_CHUNK_SIZE = 100

  var enable_batch2 = true

  var ENABLE_SAMPLING = false
}

