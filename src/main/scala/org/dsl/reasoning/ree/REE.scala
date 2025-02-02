package org.dsl.reasoning.ree

import org.dsl.exception.REEException
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate.{BigAndExpr, Expression, Imply, PredicateSet}

import java.util
import java.util.List
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * example:
 * phi: p1 /\ p2 /\ ... /\ pn -> c
 * <=>
 * BitSet(p1 , p2 , ... , pn , c)
 *
 * @param preconditionsSet bitset of X
 * @param preconditions sequence of X predicates
 * @param conclusionPos id of conclusion
 */
class REE(private val preconditionsSet: PredicateSet,
          private val preconditions: Seq[Expression],

          private val conclusionPos: Int,
          private val conclusion: Expression) {

  private val redundants: mutable.Seq[Expression] = ListBuffer.empty

  // TODO: hashCode and equal
  //
  //  override def hashCode(): Int = {
  //    // final int prime = 31;
  //    var result1 = 0
  //
  //    for (p <- preconditionsSet) {
  //      result1 += Math.max(p.hashCode, p.getSymmetric.hashCode)
  //    }
  //    var result2 = 0
  //    if (getInvT1T2DC != null) {
  //      import scala.collection.JavaConversions._
  //      for (p <- getInvT1T2DC.predicateSet) {
  //        result2 += Math.max(p.hashCode, p.getSymmetric.hashCode)
  //      }
  //    }
  //
  //    Math.max(result1, result2)
  //  }
}

object REE {
  def from(imply: Expression)(implicit p2i: PredicateIndexProvider): REE = {
    imply match {
      case Imply(lhs, rhs) => {

        val predSet = PredicateSet.empty(p2i)
        val X = lhs match {
          case BigAndExpr(l) => l
          case _ => Nil
        }

        val p0 = rhs

        for(p <- X) {
          predSet.addOne(p)
        }

        val conclusionPos = p2i.get(p0) match {
          case Some(i) => i
          case _ => throw REEException("Conclusion Position Not Found !!")
        }

        predSet.addOne(p0)

        new REE(predSet, X, conclusionPos, p0)
      }

      case _ => throw REEException("Unexpected Expression! Only Imply Expression is feasible for REE!")
    }
  }



}