package org.dsl.mining

trait BFSOps[V, R, CC] extends Serializable {
  def process(vertex:V): R

  def merge(cc: CC, r:R) : CC

  def mergeAll(in: CC, out:CC) : CC

}
