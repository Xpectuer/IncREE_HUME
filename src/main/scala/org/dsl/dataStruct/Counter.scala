package org.dsl.dataStruct

import org.dsl.reasoning.predicate.Expression
import scala.collection.mutable

// Counter For Greedy Algorith (Expr -> Weight[integer count])
class Counter(private val counter: mutable.Map[Expression, Int]) {

  lazy val totalSize: Int = counter.values.sum
  private def update(k: Expression, v: Int): Unit = {
    counter.update(k,v)
  }
  def lookup(k: Expression): Option[Int] = {
    counter.get(k) match {
      case Some(k) => Some(k)
      case None =>
        this.update(k, 0)
        None
    }
  }

  def add1(k: Expression): Unit = {
    lookup(k) match {
      case Some(old) =>
        val newNum = old + 1
        update(k, newNum)

      case None =>
        update(k, 1)
    }
  }

  override def toString: String = {
    s"Counter:" +
    s"preview: ${counter.take(5).mkString(",\n")}" +
    "\n" +
      s"size: ${counter.size}"
  }


}

object Counter {
  def apply(cnt: mutable.Map[Expression, Int]): Counter = {
    new Counter(cnt)
  }

  def empty : Counter = apply(mutable.Map.empty[Expression, Int])
}