package org.dsl.mining

import com.tdunning.math.stats.{AVLTreeDigest, TDigest}

import java.nio.ByteBuffer
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object TDigestSerialize {
  def serialize(tdigest: TDigest) = {
    val arr = new Array[Byte](tdigest.byteSize)
    tdigest.asBytes(ByteBuffer.wrap(arr))
    mutable.ArrayBuffer.apply(arr:_*)

  }


  def deserialize(arr: ArrayBuffer[Byte]) = {
    AVLTreeDigest.fromBytes(ByteBuffer.wrap(arr.toArray))
  }
}
