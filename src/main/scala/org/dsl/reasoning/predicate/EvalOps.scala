package org.dsl.reasoning.predicate

trait EvalOps[T] {
  def eq(t:T, s:T): Option[(Int, Int)]

  def neq(t:T, s:T): Option[(Int, Int)]

}
