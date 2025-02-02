package org.dsl.reasoning

import org.dsl.dataStruct.Statistics
import org.dsl.reasoning.predicate.Expression

case class InterpResult(expr: Expression,stat: Either[Statistics, Unit])
