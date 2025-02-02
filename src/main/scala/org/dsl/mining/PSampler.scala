package org.dsl.mining


import com.tdunning.math.stats.TDigest
import org.dsl.reasoning.predicate.{Expression, PredicateSet}
import org.dsl.mining.PMiners.{getConfFromSupp, getSuppCnt}
import org.apache.spark.util.CollectionAccumulator
import org.dsl.mining.REEWithStat.{KEY_CONF, KEY_REE, KEY_SUPP}
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.immutable.ParSet

case class PSampler() {

  val samples: mutable.ArrayBuffer[Sample] = ArrayBuffer.empty[Sample]
  val predToSampleMapping: mutable.Map[PredicateSet, Int] = mutable.Map[PredicateSet, Int]()

  def lookupSample(X: PredicateSet): Option[Sample] = {
    predToSampleMapping.get(X) match {
      case Some(i) =>
        assert(i < samples.size)
        Some(samples(i))
      case None => None
    }
  }

  def getSample(X: PredicateSet, radius: Int): Option[Sample] = {
    import PSampler._
    val predecessors = getPredecessors(radius, X)
    for (pred <- predecessors) {
      val lookup = lookupSample(pred)
      if (lookup.isDefined) return lookup
    }
    None

  }

  def addNewSample(predecessors: Set[PredicateSet], newSample: Sample): Unit = {

    val newIndex = samples.size
    samples += newSample
    for (p <- predecessors) {
      predToSampleMapping += (p -> newIndex)
    }
  }

  def addPredecessorOrNewSample(K: Int,
                                in: (REE, Double), mineArg: MineArg): Unit
  = {
    val (ree, conf) = in
    val p2i = mineArg.p2i
    // val predecessors: Iterable[PredicateSet] = getPredecessors(K, pred)
    import PSampler._
    this.getSample(ree.X, K) match {
      case Some(sp) =>
        assert(sp.rhs == ree.p0)
        sp.digest.add(conf)
      case None =>
        val predecessors: Set[PredicateSet] = getPredecessors(K, ree.X)
        val newDigest = TDigest.createAvlTreeDigest(100)
        newDigest.add(conf)
        val newSample = Sample(predecessors, ree.p0, newDigest, p2i)
        addNewSample(predecessors, newSample)
    }
  }

  def getSamples: Seq[Sample] = samples.toIndexedSeq
}


object PSampler {

  def toPSamples(samples: Iterable[REEWithT[TDigest]], radius: Int): Iterable[Sample] = {
    //    samples.map {
    //      case REEWithT(ree, t) =>
    //        ree match {
    //          case REE(x, rhs) =>
    //            val predcessors = getPredecessors(radius = radius, x)
    //            Sample(predcessors, rhs, t)
    //        }
    //    }
    ???
  }

  // val samples: mutable.Map[PredicateSet, Sample] = mutable.Map.empty[PredicateSet, Sample]

  def addPredecessorOrNewSample(K: Int, sampled: mutable.Map[PredicateSet, Sample],
                                in: (REE, Double), mineArg: MineArg): mutable.Map[PredicateSet, Sample]
  = {
    val (ree, conf) = in
    // val predecessors: Iterable[PredicateSet] = getPredecessors(K, pred)
    val p2i = mineArg.p2i
    getSample(K, ree.X, sampled) match {
      case Some(sp) => {
        assert(sp.rhs == ree.p0)
        sp.digest.add(conf)
      }
      case None =>
        val predecessors: Set[PredicateSet] = getPredecessors(K, ree.X)
        val newDigest = TDigest.createAvlTreeDigest(100)
        newDigest.add(conf)
        val newSample = Sample(predecessors, ree.p0, newDigest, p2i)
        /** What should be the key here? */
        val newKey: PredicateSet = ???
        sampled += (newKey -> newSample)
    }
    sampled
  }

  def getSample(radius: Int, X: PredicateSet, sampled: mutable.Map[PredicateSet, Sample]): Option[Sample] = {
    val predecessors = getPredecessors(radius, X)
    val hittingPreds = predecessors.intersect(sampled.keySet)
    if (hittingPreds.isEmpty) {
      None
    }
    else {
      val key = hittingPreds.head
      Some(sampled(key))
    }
  }

  def getPredecessors(radius: Int, X: PredicateSet): Set[PredicateSet] = {

    val r = X.map(p => X :- p).toSeq
    r.take(radius).toSet
  }


  def expandSamplesCDF(rhs: Expression,
                       samples: Set[Sample],
                       result: Iterable[REEWithStat],
                       mineArg: MineArg, timer: CollectionAccumulator[(Long, Long)]): ParSet[(REE, Stat)] = mineArg match {
    case MineArg(_, _, lhsSpace, _,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, _) =>

      val successors = if (recall == 1.0) {
        val highConfSamples = samples.filter(_.getMax >= conf_threshold)
        highConfSamples.flatMap(_.getSuccessors(lhsSpace.toSet))
      }
      else {
        /** todo:  implement cases for recall < 1.0 */
        ???
      }

      val REEWithStat = successors.par.map(ree => {
        val supp = PMiners.getSuppCnt(ree, eviSet)
        val conf = PMiners.getConfFromSupp(ree, eviSet, supp)
        (ree, Stat(supp, conf))
      })

      val validREEs = REEWithStat.filter {
        case (_, Stat(supp, conf)) => supp >= supp_threshold && conf >= conf_threshold
      }
      validREEs
  }

  def expandSamplesCDFWithTopK(rhs: Expression,
                               samples: Set[Sample],
                               result: Iterable[REEWithStat],
                               mineArg: MineArg, p: ParameterLambda) = mineArg match {
    case MineArg(_, _, lhsSpace, _,
    supp_threshold, conf_threshold,
    p2i, eviSet, fullEviSize, recall, dist, _) =>

      val old_conf_threshold = p.confOld
      val new_conf_threshold = p.confNew

      /** Organize samples by levels */
      val levelWiseSamples = samples.groupBy(s => s.predecessors.head.size)

      /** Expand samples and simulate topK mining */
      var oldTopK = mutable.Map[Int, Set[REEWithStat]]()
      var results = Set[REEWithStat]()
      var lastLevelTopKSuccessors = Set[REEWithStat]()

      for ((level, levelSamples) <- levelWiseSamples) {
        /** step1. recover all REEs at level i from samples. */
        val recoveredREEWithStats: Set[REEWithStat] = ???

        /** results at level i under new parameter */
        val candidateREES: Set[REEWithStat] = recoveredREEWithStats ++ lastLevelTopKSuccessors
        results ++= candidateREES.filter(
          rs => rs.supp >= supp_threshold && rs.conf >= conf_threshold
        )

        /** step2. From the recovered REEs, simulate topK REEs with old and new parameters. */


        /** step3. Mine only the diff in the topK REEs. */
        val newDiffTopK = candidateREES.filter(e =>
          old_conf_threshold <= e.conf && e.conf < new_conf_threshold)

        lastLevelTopKSuccessors ++= newDiffTopK.flatMap {
          reeWithStat: REEWithStat => {
            /** todo: generate all its successor here. */
            ???
          }
        }

      }

      val successors = if (recall == 1.0) {
        val highConfSamples = samples.filter(_.getMax >= conf_threshold)
        highConfSamples.flatMap(_.getSuccessors(lhsSpace.toSet))
      }
      else {
        /** todo:  implement cases for recall < 1.0 */
        ???
      }

      val REEWithStat = successors.par.map(ree => {
        val supp = getSuppCnt(ree, eviSet)
        val conf = getConfFromSupp(ree, eviSet, supp)
        (ree, Stat(supp, conf))
      })

      val validREEs = REEWithStat.filter {
        case (_, Stat(supp, conf)) => supp >= supp_threshold && conf >= conf_threshold
      }
      validREEs
  }

  def pickTopK(reeWithStats: Set[REEWithStat]): Set[REEWithStat] = ???


}


case class Sample(predecessors: Set[PredicateSet], rhs: Expression, digest: TDigest, p2i: PredicateIndexProvider) {
  def cdf(conf: Double): Double = {
    digest.cdf(conf)
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case otherS: Sample =>
        otherS.predecessors == this.predecessors && this.rhs == otherS.rhs
      case _ => false
    }
  }


  def getMax: Double = digest.getMax

  def getSuccessors(lhsSpace: Set[Expression]): Set[REE] = {
    predecessors.flatMap {
      X: PredicateSet =>
        lhsSpace.flatMap {
          p: Expression =>
            if (X.contains(p)) None else {
              Some(REE(X :+ p, rhs))
            }
        }
    }
  }


  def toJSON: String = {
    import Sample._
    import upickle.default._
    implicit val rw: Writer[Sample] =
      writer[ujson.Value].comap {
        case Sample(precs, rhs, digest, _) =>
          val rees = precs.map(p => REE(p, rhs))
          val rhsId = p2i.getOrElse(rhs, ???)
          ujson.Obj(KEY_PREDECESSORS -> rees.map(_.toJSON), KEY_RHS -> rhsId, KEY_CDF -> TDigestSerialize.serialize(digest))
      }

    write(this)
  }
}

object Sample {
  val KEY_PREDECESSORS = "predecessors"
  val KEY_RHS = "rhs"
  val KEY_CDF = "cdf"

  def fromJSON(j: String, p2i: PredicateIndexProvider) = {
    import upickle.default._

    implicit val rw: Reader[Sample] =
      reader[ujson.Value].map(
        json => {
          val predecessors = json.obj(KEY_PREDECESSORS).arr.map(raw => REE.fromJSON(raw.str, p2i).X).toSet
          val rhsId = json.obj(KEY_RHS).num.toInt
          val rhs = p2i.getObject(rhsId)
          val cdf = TDigestSerialize.deserialize(json.obj(KEY_CDF).arr.map(_.num.toByte))

          Sample(predecessors, rhs, cdf, p2i)
        }
      )

    read[Sample](j)

  }
}

