package org.dsl.emperical.pli

class StringTpIDsIndexer(value2TpIDsMap: Map[Int, IndexedSeq[Int]],
                         sortedValues: IndexedSeq[Int]) extends ITPIDSIndexer {
  override def getValues: Iterable[Int] = sortedValues

  override def getTupleIDsFromValue(value: Int): Option[IndexedSeq[Int]] = value2TpIDsMap.get(value)

  override def getIndexForValueThatIsLessThan(value: Int): Int = {
    var start = 0
    var end = sortedValues.size - 1

    var ans = -1
    while (start <= end) {
      val mid = (start + end) / 2
      // Move to right side if target is
      // greater.
      if (sortedValues(mid) >= value) start = mid + 1
      else {
        ans = mid
        end = mid - 1
      }
    }

    ans
  }

  override def toString: String = {
    val sm = value2TpIDsMap.map {
      case (k, v) => s"$k:$v"
    }.mkString(",\n")

    val sValues = sortedValues.mkString(",")

    sm + "\n" + sValues + "\n"
  }

}

object StringTpIDsIndexer {
  def apply(clusters: IndexedSeq[IndexedSeq[Int]], values: Array[Int]): StringTpIDsIndexer = {
    val value2TupleIDMap = (for (ids <- clusters) yield {
      val value = values(ids.head)
      value -> ids
    }).toMap

    val sortedValues = for (ids <- clusters) yield {
      val value = values(ids.head)
      value
    }

    new StringTpIDsIndexer(value2TupleIDMap, sortedValues)
  }
}
