package org.dsl.mining

import org.dsl.mining.REEMiner.EvidencePairList
import org.dsl.reasoning.predicate.PredicateSet

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

trait BFSArgs
case class LevelArg[Idx](oldS: State, newS: State,
                         visited: mutable.Map[PredicateSet, Unit],
                         eviSet: EvidencePairList,
                         fullEviSize: Long,
                         result: mutable.Map[PredicateSet, Idx],
                         supp_threshold: Double, conf_threshold: Double) extends BFSArgs

case class ResArg[Idx](nextLevel: Option[State],
                       res: Option[(PredicateSet, Idx)],
                       pruned: Option[(PredicateSet, Idx)]) extends BFSArgs

case class CollectionArg[Idx](nextLevel: mutable.Map[State, Unit],
                              resultLevel: mutable.Map[PredicateSet, Idx],
                              prunedLevel: mutable.Map[PredicateSet, Idx],
                              sampledLevel: mutable.Map[PredicateSet, Idx]) extends BFSArgs

object CollectionArg {
  def empty[Idx]: CollectionArg[Idx] = {
    CollectionArg(mutable.Map.empty[State, Unit],
      mutable.Map.empty[PredicateSet, Idx],
      mutable.Map.empty[PredicateSet, Idx],
      mutable.Map.empty[PredicateSet, Idx])
  }

  def emptyPar[Idx]: CollectionArg[Idx] = {
    CollectionArg(TrieMap.empty[State, Unit],
      TrieMap.empty[PredicateSet, Idx],
      TrieMap.empty[PredicateSet, Idx],
      TrieMap.empty[PredicateSet, Idx])
  }

  def withQueue[Idx](level: mutable.Map[State, Unit]): CollectionArg[Idx] = {
    CollectionArg(level,
      TrieMap.empty[PredicateSet, Idx],
      TrieMap.empty[PredicateSet, Idx],
      TrieMap.empty[PredicateSet, Idx])
  }


}