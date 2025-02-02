package org.dsl.emperical.pli


class NumericalTpIDsIndexer(value2TupleIDMap: Map[Int,IndexedSeq[Int]], sortedValues: IndexedSeq[Int]) extends ITPIDSIndexer{
  override def getValues = sortedValues
  override def getTupleIDsFromValue(value: Int) = {
    value2TupleIDMap.get(value)
  }

  // binary search
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
    val sm = value2TupleIDMap.map {
      case (k,v) => s"$k:$v"
    }.mkString(",\n")

    val sValues = sortedValues.mkString(",")

    sm + "\n" + sValues + "\n"
  }
}

object NumericalTpIDsIndexer{
  def apply(clusters: IndexedSeq[IndexedSeq[Int]], values: Array[Int]): NumericalTpIDsIndexer = {
    val value2TupleIDMap = (for(ids <- clusters) yield {
      val value = values(ids.head)
      value -> ids
    }).toMap

    val sortedValues = for(ids <- clusters) yield {
      val value = values(ids.head)
      value
    }

    new NumericalTpIDsIndexer(value2TupleIDMap, sortedValues)
  }
}
