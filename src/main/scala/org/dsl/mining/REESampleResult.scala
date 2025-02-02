package org.dsl.mining

import org.dsl.dataStruct.Interval
import org.dsl.reasoning.predicate.Expression

case class REESampleResult(r:Iterable[(REE, Interval[Double])])