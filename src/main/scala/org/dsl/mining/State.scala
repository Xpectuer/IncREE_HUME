package org.dsl.mining

import org.dsl.dataStruct.evidenceSet.HPEvidenceSet.ProtoEvidenceSet
import org.dsl.reasoning.predicate.PredicateSet

import java.util.Objects

case class State(selected: PredicateSet, rest: PredicateSet,
                 supp: Long, conf: Double, level: Int)  {


  def isSubSetOf(other: State): Boolean = {
    this.selected.isSubsetOf(other.selected)
  }
  override def hashCode(): Int = {
    Objects.hashCode(selected)
  }

  override def equals(other: Any): Boolean = {
    other match {
      case State(s, _, _, _, _) => this.selected == s
      case _ => false
    }
  }

  private case class ProtoState(selectedRaw: Array[Long],
                                selectedRepr: Array[Int],
                                Nselected: Int,
                                restRaw: Array[Long],
                                restRepr: Array[Int],
                                Nrest: Int,
                                supp:Long,
                                conf:Double)

  def toJson:String = {
    import upickle.default._

    val selectedRaw = selected.getBitSet.toBitMask
    val selectedRepr = selected.getBitSet.toArray
    val restRaw = rest.getBitSet.toBitMask
    val restRepr = rest.getBitSet.toArray

    val proto = ProtoState(
      selectedRaw = selectedRaw,
      selectedRepr = selectedRepr,
      Nselected = selectedRepr.length,
      restRaw = restRaw,
      restRepr = restRepr,
      Nrest = restRepr.length,
      supp = supp,
      conf = conf
    )

    implicit val protoStateRW: ReadWriter[ProtoState] = macroRW[ProtoState]

    val json = write(proto)

    json
  }

  override def toString: String = {
    s"""|
        |selected=$selected,
        |rest=$rest,
        |supp=$supp,
        |conf=$conf,
        |level=$level
        |""".stripMargin
  }

}

//object State {
//
//  def unapply(arg: State): Option[(PredicateSet, PredicateSet, Long, Double, Int)] = Some((arg.getSelected, arg.getRest, arg.getSuppCnt, arg.getConf, arg.getLevel))
//
//  def apply(selected: PredicateSet, rest: PredicateSet, supp: Long, conf: Double, level: Int): State = new State(selected, rest, supp, conf, level)
//
//
//}