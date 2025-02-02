package org.dsl.utils

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * All instances of index provider should be Pre-processed
 *
 * @param mapping val => index
 * @param objects index => value
 * @tparam T type of any value you want to compress into integers
 */
@SerialVersionUID(823L)
class IndexProvider[T: ClassTag](private val mapping: Map[T, Int],
                                 private val objects: Array[T]) extends Serializable {

  def getMapping = mapping

  def getObjects: Iterable[T] = objects.toIndexedSeq

  def get(e: T): Option[Int] = mapping.get(e)

  def getOrElse(e: T, f: => Int): Int = mapping.get(e) match {
    case Some(v) => v
    case None => f
  }

  def getObject(idx: Int): T = {
    objects(idx)
  }


  def size: Int = objects.length

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: IndexProvider[T] =>
        this.size == other.size &&
          other.objects.head == this.objects.head &&
          this.objects.last == other.objects.last
      case _ => false
    }
  }


}

object IndexProvider {
  def getSorted[T: ClassTag](provider: IndexProvider[T])(by: (T, T) => Boolean): IndexProvider[T] = {
    val (sortedObjects, time) = Wrappers.timerWrapperRet(provider.objects.sortWith(by))
    //logger.info(s"sort with $time")

    IndexProvider(sortedObjects)
  }

  def Nil: IndexProvider[_] = {
    new IndexProvider[Any](Map(), Array())
  }

  def apply[T: ClassTag](values: Iterable[T]): IndexProvider[T] = {
    val mt_mappings = mutable.Map.empty[T, Int]
    val mt_values = mutable.ArrayBuffer.empty[T]

    var nextIndex = 0


    for (e <- values) {
      mt_mappings.get(e) match {
        case Some(i) =>
        case None =>
          mt_values += (e)
          mt_mappings.update(e, nextIndex)
          nextIndex = nextIndex + 1
      }

    }

    new IndexProvider[T](mt_mappings.toMap, mt_values.toArray)
  }
}




