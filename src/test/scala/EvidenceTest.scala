import org.dsl.dataStruct.evidenceSet.builders.{EvidenceSetBuilder, SplitReconsEviBuilder}
import org.dsl.emperical.table.TypedColTable
import org.dsl.mining.PredSpace
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.utils.{$, HUMELogger, IndexProvider, Wrappers}
import org.scalatest.Tag
import org.scalatest.funsuite.AnyFunSuite

class EvidenceTest extends AnyFunSuite {
  val logger: HUMELogger = HUMELogger(getClass.getName)

  test("Evidence1", Tag("Evidence1")) {
    //val path = $.addProjPath("datasets/incremental/airport/airport_original.csv")
    val path = $.addProjPath("datasets/incremental/airport/airport_original.test.csv")

    logger.info(path)
    val raw = $.LoadTable(path)
    val size = raw.rowNum
    //logger.info(raw)

    val (typedColTable, time) = Wrappers.timerWrapperRet(TypedColTable.from(raw))


    println(s"It takes $time (s) to data processing.")
    val fragSize = SplitReconsEviBuilder.FRAG_SIZE

    val predSpace = PredSpace(typedColTable)
    val p2i: PredicateIndexProvider = PredSpace.getP2I(predSpace)

    val evb = EvidenceSetBuilder(typedColTable, predSpace, fragSize)(p2i)


    val eviSet = SplitReconsEviBuilder.buildFullEvi(evb, typedColTable.size)
    //logger.debug(s"${eviSet.mkString(",\n")} SIZE=${eviSet.size}")
  }


  test("Evidence1 Benchmark Airport FULL", Tag("Evidence Airport FULL")) {
    //val path = $.addProjPath("datasets/incremental/airport/airport_original.csv")
    //val path = $.addProjPath("datasets/incremental/airport/airport_original.test.csv")

    val path = $.addProjPath("datasets/incremental/airport/airport_original.csv")
    logger.info(path)
    val raw = $.LoadTable(path)
    val size = raw.rowNum
    //logger.info(raw)

    val (typedColTable, time) = Wrappers.timerWrapperRet(TypedColTable.from(raw))


    val fragSize = SplitReconsEviBuilder.FRAG_SIZE


    val predSpace = PredSpace(typedColTable)
    implicit val p2i: PredicateIndexProvider = PredSpace.getP2I(predSpace)
    logger.debug(s"Predicate Space SIZE ${predSpace.size}")
    val evb = EvidenceSetBuilder(typedColTable, predSpace, fragSize)(p2i)
    println(s"It takes $time (s) to data processing.")
    logger.debug(s"$evb")


    logger.debug("maskMap: ",
      evb.maskMap.mkString(",\n"),
      s"SIZE:${evb.maskMap.size}")


    logger.debug(s"TYPEDCOLTABLE SIZE: ${typedColTable.rowNum}")
    val eviSet = SplitReconsEviBuilder.buildFullEvi(evb, typedColTable.rowNum)
    logger.debug(s"${eviSet.mkString(",\n")} SIZE=${eviSet.size}")
  }
}
