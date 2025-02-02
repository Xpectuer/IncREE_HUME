package org.dsl.mining

import org.dsl.reasoning.predicate.{Expression, PredicateSet}


trait Openable[A] {
  def open(a: A): Iterable[A]
}
object Statistics {

  private def dist(a: PredicateSet, b: PredicateSet) :Int = {
    assert(a.size == b.size)
    a.size - (a & b).size
  }
  def mutateSample(sample: REEWithStat, xs: Iterable[REEWithStat], K: Int) = {
    for (x <- xs if dist(sample.ree.X, x.ree.X) <= K) yield x
  }

  def groupedByK(reeWithConf: Map[REE, Double], K: Int) = {
    val XS = reeWithConf.toIndexedSeq.map(e => REEWithStat(e._1,-1,e._2))
    val n = XS.length
    val sampleRange = mutateSample(XS.head, XS, K)
    sampleRange
  }

  def groupREE(resultREEs: Map[REE, Double], K: Int) = {
    // resultREEs: ree -> confidence

    val groupdByRHS = resultREEs.groupBy{case (ree, conf) => ree.p0}

    // levelwise group
    val groupedByRHS_LEVEL: Map[Expression, Map[Int, Map[REE, Double]]] = groupdByRHS.map{
      case (rhs, ree) => rhs -> ree.groupBy(_._1.X.size)
    }

    val out = groupedByRHS_LEVEL.map{
      case (rhs, levels) =>
        rhs.toString -> (levels.map {
          case (_, reeWithConf) => groupedByK(reeWithConf, K)
        })
    }

    out

  }

  def vertexCover2Approx(nodeList: Iterable[PredicateSet])
                        (implicit openablePredSet: Openable[PredicateSet]) : Iterable[PredicateSet] = {

    ???
  }
}
