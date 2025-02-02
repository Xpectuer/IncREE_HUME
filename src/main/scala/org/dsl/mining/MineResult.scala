package org.dsl.mining


import com.tdunning.math.stats.TDigest
import org.dsl.dataStruct.Interval
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate.PredicateSet

case class MineResult(result: Iterable[REEWithStat],
                      pruned: Iterable[REEWithStat],
                      samples: Iterable[Sample]) {

  def withP2I(p2iNew:PredicateIndexProvider) = {
    val resultNew = result.map{
      case REEWithStat(ree, supp, conf) =>
        val xNew = PredicateSet.from(ree.X)(p2iNew)
        val p0 = ree.p0
        REEWithStat(REE(xNew, p0), supp,conf)
    }

    //todo: pruned sample
    MineResult(resultNew, pruned, samples)
  }

  private def depthRange(m: Iterable[REE]) = {
    val nl = m.map(p => p.X.size)
    if (nl.nonEmpty) {
      Interval(nl.min.toDouble, nl.max.toDouble)
    } else {
      Interval(-1d, -1d)
    }

  }


  def readable = {
    result.toIndexedSeq.sortBy(e=>(e.ree.X.size,e.toString)).map(_.ree.readable).mkString("\n")
  }




  def ++(other: MineResult): MineResult = {
    MineResult(this.result ++ other.result, this.pruned ++ other.pruned, this.samples ++ other.samples)

  }

  override def toString: String = {
    val resultSorted = result.toIndexedSeq.sortBy(e => (e.ree.X.size,e.toString))

    s"""
       |result SIZE: ${result.size}
       |result depth range: ${depthRange(result.map(_.ree))}
       |
       |pruned SIZE: ${pruned.size}
       |pruned depth range: ${depthRange(pruned.map(_.ree))}
       |
       |sample SIZE: ${samples.size}
       |
       |result:
       | ${resultSorted.mkString("\n")}
       |
       |result readable:
       | ${resultSorted.map(e=>(e.ree.readable, e.supp,e.conf)).mkString("\n")}
       |""".stripMargin
  }

  def toJSON: String = {
    import upickle.default._

    implicit val rw: Writer[MineResult] =
      writer[ujson.Value].comap {
        case MineResult(result, pruned, samples) =>
          ujson.Obj("result" -> result.map(_.toJSON), "pruned" -> pruned.map(_.toJSON), "samples" -> samples.map(_.toJSON))
      }

    write(this)
  }

}

object MineResult {

  def empty:MineResult = {
    MineResult(Iterable(), Iterable(),Iterable())
  }

  val KEY_RESULT = "mine_result"
  def fromJSON(j: String, p2i: PredicateIndexProvider): MineResult = {
    import upickle.default._

    implicit val rw: Reader[MineResult] =
      reader[ujson.Value].map(
        json => {
          val result1 = json("result").arr.map(_.str)
          val pruned1 = json("pruned").arr.map(_.str)
          val samples1 = json("samples").arr.map(_.str)

          val result2 = result1.map(REEWithStat.fromJSON(_, p2i))
          val pruned2 = pruned1.map(REEWithStat.fromJSON(_, p2i))
          val samples2 = samples1.map(Sample.fromJSON(_, p2i))

          MineResult(result2, pruned2, samples2)

        }
      )

    read[MineResult](j)


  }
}

case class MineResultWithSTime(mineResult: MineResult, sampleTime: Double) {

}
