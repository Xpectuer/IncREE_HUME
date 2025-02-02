package org.dsl.dataStruct

case class Interval[T](begin: T, end:T)  {
  override def toString: String = {
    s"($begin,$end)"
  }
}
