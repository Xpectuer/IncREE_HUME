package org.dsl.dataStruct


import scala.collection.compat.toMutableMapExtensionMethods
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.language.postfixOps

/*
 *  Implements a standard Union-Find (a.k.a Disjoint Set) data
 *  structure with permissive behavior with respect to
 *  non-existing elements in the structure (Unknown elements are
 *  added as new elements when queried for).
 *
 * See Cormen, Thomas H.; Leiserson, Charles E.; Rivest, Ronald
 * L.; Stein, Clifford (2001), "Chapter 21: Data structures for
 * Disjoint Sets", Introduction to Algorithms (Second ed.), MIT
 * Press, pp. 498–524, ISBN 0-262-03293-7
 *
 * Amortized time for a sequence of m {union, find} operations
 * is O(m * InvAckermann(n)) where n is the number of elements
 * and InvAckermann is the inverse of the Ackermann function.
 *
 * Not thread-safe.
 *
 * **Update: Thread-safe using TrieMap**
 */

class UnionFind[T]() {

  private val parent: mutable.Map[T, T] = new TrieMap[T, T]

  private val rank: mutable.Map[T, Int] = new TrieMap[T, Int]()


  /**
   * Return the parent (representant) of the equivalence class.
   * Uses path compression.
   */
  def find(s: T): T = {
    val ps = parent.getOrElseUpdate(s, {
      s
    })
    if (ps == s) s else {
      val cs = find(ps)
      parent.getOrElseUpdate(s, {
        cs
      })
      cs
    }
  }

  /**
   * Unify equivalence classes of elements.
   * Uses union by rank.
   */
  def union(x: T, y: T): Unit = {
    val cx = find(x)
    val cy = find(y)
    if (cx != cy) {
      val rx = rank.getOrElseUpdate(x, 1)
      val ry = rank.getOrElseUpdate(y, 1)
      if (rx > ry) {
        parent.getOrElseUpdate(cy, {
          cy
        })
        parent(cy) = cx
      }
      else if (rx < ry) {
        parent.getOrElseUpdate(cx, {
          cx
        })
        parent(cx) = cy
      }
      else {
        rank.getOrElseUpdate(cx, {
          1
        })

        rank.updateWith(cx){
          case Some(c) => Some(c + 1)
          case None => Some(1)
        }

        parent.getOrElseUpdate(cy, {
          cy
        })
        parent(cy)= cx
      }
    }
  }

  /**
   * Enumerates the equivalence class of element x
   */
  def equivalenceClass(x: T): Iterable[T] = {
    val px = parent.getOrElseUpdate(x, {
      x
    })
    parent.keys.filter(e => parent.getOrElseUpdate(e, {
      e
    }) == px)
  }


  def roots: Iterable[T] = {
    parent.keys.filter(k => parent.getOrElseUpdate(k, k) == k)
  }


  def isEmpty = parent.isEmpty

  def nonEmpty = !isEmpty

}
