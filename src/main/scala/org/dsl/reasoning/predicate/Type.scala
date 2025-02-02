package org.dsl.reasoning.predicate


import org.dsl.reasoning.predicate.HumeType.{HInt,HLong,HString,HFloat}
import org.dsl.exception.ParserException


object Type {

  def parseTypedColumn(columnWithType: ColumnAtom): TypedColumnAtom = {
    val columnWithType1 = columnWithType.getValue
    val htypePattern = "\\(.*\\)".r
    val s = htypePattern.findFirstIn(columnWithType1) match {
      case Some(s) => s // kill parenthesis
      case None => throw ParserException("No Type Annotation in Column. Please Check the dataset!!! ")
    }

    val htype = s match {
      case "(Integer)" => HInt
      case "(String)" => HString
      case "(Long)" => HLong
      case "(Float)" => HFloat
    }

    TypedColumnAtom(columnWithType1, htype)
  }

}
