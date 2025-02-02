package org.dsl.utils

import java.time.LocalDateTime

class HUMELogger(private val clazzName: String) extends Serializable {

  def template = s"${LocalDateTime.now()} [HUME LOGGING] "

  def debug(args :Object*): Unit = {
    if(Config.ENABLE_DEBUG) {
      println(s"$template [🔧DEBUG🔧] $clazzName === ${args.map(e => e.toString).mkString(",")}")
    }
  }

  def debug(args: Int): Unit = {
    if (Config.ENABLE_DEBUG) {
      println(s"$template [🔧DEBUG🔧] $clazzName === $args")
    }
  }

  def info(args :Object*): Unit = {
    println(s"$template [INFO] $clazzName === ${args.map(e => e.toString).mkString(",")}")
  }

  def error(args: Object*): Unit = {
    System.err.println(s"$template [❌ERROR❌] $clazzName === ${args.map(e => e.toString).mkString(",")}")
  }

  def profile(args: Object*): Unit = {
    if(Config.enable_profiling) println(s"$template [PROFILE] $clazzName === ${args.map(e => e.toString).mkString(",")}")
  }

  def fatal(args: Object*): Nothing = {
    error(args)
    throw new Exception
  }



}

object HUMELogger {
  def apply(clazz: String): HUMELogger = {
    new HUMELogger(clazz)
  }
}
