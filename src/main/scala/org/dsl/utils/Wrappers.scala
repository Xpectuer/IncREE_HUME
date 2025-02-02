package org.dsl.utils

import org.dsl.pb.ProgressBar

object Wrappers {

  private case class RetryException() extends Throwable

  def retry[T](f: => T, condition: T => Boolean): Unit = {
    try {
      val pass = condition(f)
      if (!pass) {
        throw RetryException()
      }
    } catch {
      case _: RetryException => retry(f, condition)
      case _: Throwable => ???
    }
  }

  def progressBarWrapper(foreach: => Unit, pb: ProgressBar) = {
    pb += 1
    foreach
  }

  def progressBarWrapperRet[R](foreach: => R, pb: ProgressBar) = {
    pb += 1
    foreach
  }

  private def identity[A](x: A) = x

  def timerWrapper0(f: => Unit): Double = {
    val t1 = System.nanoTime()
    f
    (System.nanoTime - t1) / 1e9d
  }

  def timerWrapperRet[R](f: => R): (R, Double) = {
    val t1 = System.nanoTime()
    val r = f
    val time = (System.nanoTime - t1) / 1e9d
    (r, time)
  }


}
