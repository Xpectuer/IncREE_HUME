package org.dsl.mining


import com.tdunning.math.stats.TDigest
import org.dsl.dataStruct.Interval
import org.dsl.mining.REEWithStat.{KEY_CONF, KEY_REE, KEY_SUPP}
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate.{Expression, PredicateSet}

import java.util.Objects

case class REE(X: PredicateSet, p0: Expression) {
  override def toString: String =
    X + " -> " + X.getId(p0)

  def readable: String = {
    X.readable() + " -> " + p0
  }

  def toJSON: String = {
    val xBitMask = X.getBitSet.toBitMask
    val rhs = X.getId(p0)
    val proto = ProtoREE(X = xBitMask, r = rhs)


    import upickle.default._
    implicit val rw: ReadWriter[ProtoREE] = macroRW

    write(proto)
  }

}

object REE {
  def fromJSON(json: String, p2i: PredicateIndexProvider): REE = {
    import upickle.default._
    implicit val rw: ReadWriter[ProtoREE] = macroRW

    read[ProtoREE](json) match {
      case p: ProtoREE =>
        //println("PROTO:", p)
        p.toREE(p2i)
    }

  }
}

case class REEWithStat(ree: REE, supp: Long = -1L, conf: Double = -1.0d) {

  def toState: State = {
    val rest = ree.X ^ PMiners._allOnePredSet(ree.X.getP2I)
    ???
    State(ree.X, rest, supp, conf, ree.X.size)
  }

  override def toString: String = {
    s"$ree | supp=$supp | conf=$conf"
  }

  override def equals(other: Any): Boolean = other match {
    case o: REEWithStat => this.ree.equals(o.ree)
    case _ => false
  }

  override def hashCode(): Int = Objects.hashCode(ree)

  def toJSON: String = {
    import upickle.default._

    implicit val rw: Writer[REEWithStat] =
      writer[ujson.Value].comap(
        reeWithStat => ujson.Obj(KEY_REE -> reeWithStat.ree.toJSON, KEY_SUPP -> reeWithStat.supp, KEY_CONF -> reeWithStat.conf))

    write(this)

  }

}

object REEWithStat {

  val KEY_REE = "ree"
  val KEY_SUPP = "supp"
  val KEY_CONF = "conf"

  def fromJSON(json: String, p2i: PredicateIndexProvider): REEWithStat = {
    import upickle.default._

    implicit val rw: Reader[REEWithStat] =
      reader[ujson.Value].map(
        json => {
          val ree = REE.fromJSON(json.obj(KEY_REE).str, p2i)

          // todo: why there is a str for long data ??? is upickle impl?
          val supp = json.obj(KEY_SUPP).str.toLong
          val conf = json.obj(KEY_CONF).num
          REEWithStat(ree, supp, conf)
        }
      )

    read[REEWithStat](json)

  }
}

case class REEWithInterval(ree: REE, intr: Interval[Double]) {
  override def toString: String = {
    s"$ree | conf_range:$intr"
  }
}

case class ProtoREE(X: Array[Long], r: Int) {
  def toREE(p2i: PredicateIndexProvider): REE = {
    val x = PredicateSet.from(X)(p2i)
    val rhs = p2i.getObject(r)

    REE(x, rhs)
  }
}


case class REEWithT[T](ree: REE, t: T) {
  override def toString: String = {
    s"${ree} | T:${t}"
  }
}

object REEWithT {

  private val KEY_REE = "ree"
  private val KEY_TDIGEST = "tdigest"


  def toJSON(reeWithTdigest: REEWithT[TDigest]): String = {
    import upickle.default._

    implicit val rw: Writer[REEWithT[TDigest]] =
      writer[ujson.Value].comap {
        case REEWithT(ree, tdigest) =>
          val byteArray = TDigestSerialize.serialize(tdigest)
          ujson.Obj(KEY_REE -> ree.toJSON, KEY_TDIGEST -> byteArray)
      }


    write(reeWithTdigest)
  }


  def fromJSON(j: String, p2i: PredicateIndexProvider): REEWithT[TDigest] = {
    import upickle.default._

    implicit val rw: Reader[REEWithT[TDigest]] =
      reader[ujson.Value].map(
        json => {
          val ree = REE.fromJSON(json.obj(KEY_REE).str, p2i)

          // todo: why there is a str for long data ??? is upickle impl?
          val tdigestRaw = json.obj(KEY_TDIGEST).arr.map(_.num.toByte)
          val tdigest = TDigestSerialize.deserialize(tdigestRaw)

          REEWithT[TDigest](ree, tdigest)
        }
      )

    read[REEWithT[TDigest]](j)

  }


}


