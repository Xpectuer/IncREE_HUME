package org.dsl.utils

import scopt.OParser

case class CmdOpt(dataset: String = "datasets/hospital.csv",
                  param: String = "in/exp-test.csv",
                  out: String = "out",
                  instanceNum: Int = 20,
                  topNRate: Double = 0.9,
                  use_ckpt: Boolean = false,
                  scalability: Boolean = false,
                  expandChunkSize: Int = -1,
//                  incBaseline: Boolean = false,
                  miner: String = "inc",
                  levelwiseSampling: Boolean = false
                 )

object CmdOpt {
  def getParser: OParser[Unit, CmdOpt] = {
    import scopt.OParser
    val builder = OParser.builder[CmdOpt]
    val parser = {
      import builder._
      OParser.sequence(
        programName("HUME"),
        head("HUME", "1.0.0"),

        // Define the -o option for output
        opt[String]('o', "out")
          .valueName("<output>")
          .action((x, c) => c.copy(out = x))
          .text("Output file or directory"),

        // Define the -b option for use_ckpt
        opt[Unit]('c', "use-ckpt")
          .action((_, c) => c.copy(use_ckpt = true))
          .text("Use Checkpoint"),

        opt[String]('d', "dataset")
          .required()
          .valueName("<dataset>")
          .action((x, c) => c.copy(dataset = x))
          .text("Input Dataset Path"),

        opt[String]('p', "param")
          .required()
          .valueName("<parameter file path>")
          .action((x, c) => c.copy(param = x))
          .text("Parameter file"),

        opt[String]('n', "workers")
          .valueName("<orkers num>")
          .action((x, c) => c.copy(instanceNum = x.toInt))
          .text("Workers Number"),

        opt[String]('t', "topn")
          .valueName("<top-N candidate predicates>")
          .action((x, c) => c.copy(topNRate = x.toDouble))
          .text("Top-N Candidate Predicates Rate. Make big dataset feasible"),
        opt[Unit]('s', "scalability")
          .valueName("<scale mode: no incremental>")
          .action((_, c) => c.copy(scalability = true))
          .text("Only Run Batch Mode For Scalability Research"),
        opt[String]('e', "expand-chunkn")
          .valueName("<expand chunk size>")
          .action((x, c) => c.copy(expandChunkSize = x.toInt))
          .text("Expand Chunk Size For Scalability Research"),
//        opt[Unit]('r', "inc-baseline")
//          .valueName("<inc baseline>")
//          .action((_, c) => c.copy(incBaseline = true))
//          .text("Enable Incremental Miner Baseline."),
        opt[String]('m', "miner")
          .valueName("<miner name>")
          .action((m, c) => c.copy(miner = m))
          .text("Enable Incremental Miner Baseline."),

        opt[Unit]('l', "levelwise sampling")
          .valueName("<enable levelwise sampling>")
          .action((_, c) => c.copy(levelwiseSampling = true))
          .text("Enable Incremental Miner Baseline."),
      )
    }

    parser
  }
}