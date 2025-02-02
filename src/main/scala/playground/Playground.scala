package playground

import org.dsl.reasoning.RuleParser.parseAll
import scala.collection.compat._
import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}
import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

class Playground {

}

object TestParser extends RegexParsers {
  // Define the token types
  // TODO: test .* regular
  // def constant: Parser[String] = """.*""".r
  /// def placeholder: Parser[String] = """HOLD""".r

  // def parseTest(input: String): ParseResult[String] = parseAll()


}

case class A(a: Int, s: String) {

}


object Main extends App {
  val pattern: Regex = "[a-zA-Z0-9]*\\.[a-zA-Z0-9]*".r
  pattern.findFirstMatchIn("awesomepassword.abc") match {
    case Some(_) => println("Password OK")
    case None => println("Password must contain a number")
  }

  val pattern1: Regex = "\'[a-zA-Z0-9]*\\.[a-zA-Z0-9]*\'".r
  pattern1.findFirstMatchIn("\'0.000000003\'") match {
    case Some(s) => println("OK", s)
    case None => println("Failed")
  }

  var cache: Map[String, Int] = Map.empty
  cache = cache + ("1" -> 2)
  cache = cache + ("1" -> 3)
  println(cache)


  val mmap: mutable.Map[A, Int] = mutable.Map.empty[A, Int]
  val a = A(1, "a")

  mmap.update(a , 1)
  mmap.update(a , 2)


  println(mmap)

  // Original map
  val originalMap: Map[Int, Map[String, String]] = Map(
    1 -> Map("A" -> "ValueA1", "B" -> "ValueB1"),
    2 -> Map("A" -> "ValueA2", "B" -> "ValueB2"),
    3 -> Map("A" -> "ValueA3", "B" -> "ValueB3"),
  )


  // Transpose the map
  val transposedData: Map[String, Map[Int, String]] =
    originalMap.map(
      row => row._2.map { case (k, v) => k -> Map(row._1 -> v) }).
      flatten.map { case (k, v) => (k, v.head) }.groupBy(_._1).
      map { case (k, v) => k ->
        v.map { case (_, pair) => pair._1 -> pair._2 }.toMap
      }


  println(transposedData)
  //.map { case (k, v) => k -> v.map { case (_, innerMap) => innerMap }.toMap }

  breakable {
    for (i <- 1 to 10) {
      println("for")
      if (i == 111) {
        break
      }
    }
    println("end for")
  }

  
}
