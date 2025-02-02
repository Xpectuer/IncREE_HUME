package org.dsl.dataStruct.evidenceSet

import gnu.trove.map.hash.TObjectLongHashMap
import org.dsl.dataStruct.evidenceSet.HPEvidenceSet.toJSON
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate.PredicateSet

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * High Performance Evidence Set
 */

@SerialVersionUID(114514L)
class HPEvidenceSet() extends IEvidenceSet {


  private val sets: TObjectLongHashMap[PredicateSet] = new TObjectLongHashMap()

  def mergeOne(predicateSet: PredicateSet, count:Long) :this.type = {
    sets.adjustOrPutValue(predicateSet, 0, count)
    this
  }

  def mergeAll(evi: Iterable[(PredicateSet, Long)]): this.type = {
    evi.foreach(e => mergeOne(e._1, e._2)); this
  }


  override def add(predicateSet: PredicateSet): Boolean = this.add(predicateSet, 1)

  override def add(create: PredicateSet, count: Long): Boolean = sets.adjustOrPutValue(create, count, count) == count

  override def getCount(predicateSet: PredicateSet): Long = sets.get(predicateSet)

  def incrementCount(predicateSet: PredicateSet, newEvidences: Long): Unit = {
    sets.adjustOrPutValue(predicateSet, newEvidences, 0)
  }

  override def iterator: Iterator[(PredicateSet, Long)] = sets.keySet().asScala.iterator.map(k => k -> getCount(k))


  def getSetOfPredicateSets: mutable.Set[PredicateSet] = sets.keySet.asScala

  private lazy val _count = sets.values.sum

  override def sum(): Long = _count


  override def isEmpty(): Boolean = sets.isEmpty

  override def hashCode: Int = {
    val prime: Int = 31
    var result: Int = 1
    result = prime * result + (if (sets == null) 0
    else sets.hashCode)
    result
  }

  def remove(key: PredicateSet): Long = {
    sets.remove(key)
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: HPEvidenceSet =>
        if (this eq other) {
          true
        } else {
          sets equals other.sets
        }
      case _ => false
    }
  }

  def jsonify: String = {
    toJSON(this)
  }


}

object HPEvidenceSet {
  def apply(): HPEvidenceSet = new HPEvidenceSet()

  def from(iterable: Iterable[(PredicateSet, Long)]) = {
    val hp = HPEvidenceSet()
    iterable.foreach(p => hp.add(p._1, p._2))
    hp
  }

  case class ProtoEvidenceSet(predSets: List[Array[Long]], counts: List[Long])

  def toJSON(evidenceSet: HPEvidenceSet): String = {
    val m = evidenceSet.toIndexedSeq
    val bsets = m.map(p => p._1.getBitSet.toBitMask)
    import upickle.default._


    val counts = m.map(_._2)
    val proto = ProtoEvidenceSet(bsets.toList, counts.toList)

    implicit val protoEviRW: ReadWriter[ProtoEvidenceSet] = macroRW[ProtoEvidenceSet]

    val json = write(proto)
    "0xdeadbeef" + json
  }

  def fromJSON(json: String)(p2i: PredicateIndexProvider): HPEvidenceSet = {
    assert(json.take(10) == "0xdeadbeef")
    val jsonWithoutMagic = json.drop(10)
    import upickle.default._

    implicit val protoEviRW: ReadWriter[ProtoEvidenceSet] = macroRW[ProtoEvidenceSet]
    val protoEvidenceSet: ProtoEvidenceSet = read[ProtoEvidenceSet](jsonWithoutMagic)

    val evi = HPEvidenceSet()
    protoEvidenceSet match {
      case ProtoEvidenceSet(predSets, counts) =>
        predSets.zip(counts).foreach {

          case (predSetBArray, count) =>
            val predSet = PredicateSet.from(predSetBArray)(p2i)
            evi.add(predSet, count)
        }
    }


    evi

  }
}

