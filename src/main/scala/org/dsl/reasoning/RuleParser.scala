package org.dsl.reasoning

import org.dsl.exception.ParserException
import org.dsl.reasoning.RuleParser.tCalc
import org.dsl.reasoning.predicate.{AndExpr, BigAndExpr, ColumnAtom, ConstantAtom, ConstantBin, Eq, Expression, Gt, Imply, Lt, Membership, NEq, NilExpr, Operator, TCalc, TableInstanceAtom, TupleAtom, TupleBin}
import org.dsl.utils.Config

import scala.io.Source
import scala.language.postfixOps
import scala.util.Try
import scala.util.parsing.combinator._

object RuleParser extends RegexParsers {
  // Define the token types
  // ======================================================

  private def date: Parser[String] = """([0-9]+/[0-9]+/[0-9]+)""".r

  private def general: Parser[String] = "GENERAL"

  private def dateMarked: Parser[String] = date ~ general ^^ {
    case date ~ _ => s"$date"
  }

  private def constant: Parser[String] = dateMarked | litNumber | litStr

  private def litNumber: Parser[String] = """([+\-])?[0-9]+(\.[0-9]+)?""".r

  private def litStr: Parser[String] = """'?[A-Za-z0-9- ]+'?""".r


  def schema: Parser[String] = """[a-zA-Z0-9_]+""".r

  private def tuple: Parser[String] = """t[0-9]+""".r

  def column: Parser[String] = """([a-zA-Z0-9]+_[a-zA-Z0-9]+)|([a-zA-Z0-9]+)""".r

  // def quantifier: Parser[String] = "∀" | "∃"
  private def imply: Parser[String] = "(->)".r

  private def wedge: Parser[String] = """(\^)|⋀|/\\""".r

  private def comp: Parser[String] = """(==)|(<=)|(>=)|(!=)|(=)""".r

  private def dot: Parser[String] = "."

  private def openParen: Parser[String] = "("

  private def closeParen: Parser[String] = ")"

  private def comma: Parser[String] = ","


  // Define the grammar rules for RULE expressions
  private def rules: Parser[_ <: Expression] = TCalcs ~ imply ~ tCalc ^^ {
    case lhs ~ _ ~ rhs =>
      // println("rules")
      // println(s"$lhs -> $rhs, ${args.mkString(",")}")
      // s"$lhs -> $rhs, ${args.mkString(",")}"
      //      val support = args.head
      //      val confidence = args.tail.head.toDouble
      //      val concise = args.tail.tail.head.toDouble
      Imply(lhs, rhs)
  }

  //  private def matchAnd(car: TCalc, cdr: List[TCalc]): Expression = {
  //
  //  }
  def consAnd(l: List[Expression]): Expression = {
    l match {
      case Nil => NilExpr
      case car :: Nil => car
      case car :: cdr => AndExpr(car, consAnd(cdr))
    }
  }

  private def TCalcs: Parser[Expression] =
    tCalc ~ rep(wedge ~> tCalc) ^^ {
      case car ~ Nil => car
      case car ~ cdr => BigAndExpr(car::cdr)
    }

  //  private def TCalcsRHS: Parser[String] = {
  //    //println("rhs")
  //    TCalcs
  //  }
  private def tCalc: Parser[TCalc] = {
    // println("tCalc")
    Member | TTupleBin | TConstantBin //| ML
  }

  private def Member: Parser[Membership] = schema ~ openParen ~ tuple ~ closeParen ^^ {
    case schema ~ _ ~ t ~ _ => {
      // println("Member:", s"$schema($t)")
      Membership(TableInstanceAtom(schema), TupleAtom(t))
    }
  }

  private def matchOperator(s: String): Operator = {
    s match {
      case "==" => Eq
      case "=" => Eq
      case ">=" => Gt
      case "<=" => Lt
      case "!=" => NEq
      case _ => throw ParserException("[ERROR]Cannot Recognize Operator!!!")
    }
  }


  private def TConstantBin: Parser[ConstantBin] = tuple ~ dot ~ column ~ comp ~ constant ^^ {
    case t ~ _ ~ c ~ comp ~ rhs => {
      val op = matchOperator(comp)
      val rhs1 = rhs.stripPrefix("\'").stripSuffix("\'").trim
      predicate.ConstantBin(op, TupleAtom(t), ColumnAtom(c), ConstantAtom(rhs1))
    }
  }

  private def TTupleBin: Parser[TupleBin] = tuple ~ dot ~ column ~ comp ~ tuple ~ dot ~ column ^^ {
    case t1 ~ _ ~ col1 ~ comp ~ t2 ~ _ ~ col2 => {
      //println("TEq:", s"$t1.$col1 $comp $t2.$col2")
      val op = matchOperator(comp)
      predicate.TupleBin(op, TupleAtom(t1), ColumnAtom(col1), TupleAtom(t2), ColumnAtom(col2))
    }
  }

  def ML = ???


  def parse(input: String): Try[Expression] = parseAll(rules, input) match {
    case Success(matched, _) => scala.util.Success(matched)
    case Failure(msg, remaining) => scala.util.Failure(new Exception("Parser failed: " + msg + " remaining: " + remaining.source.toString.drop(remaining.offset)))
    case Error(msg, _) => scala.util.Failure(new Exception(msg))
  }
}

object Main extends App {
  val input = "ncvoter(t0) ⋀ ncvoter(t1) ⋀ t0.city_id3 == t1.city_id3 ⋀ t0.county_id == t1.county_id ⋀ t0.election_phase == 11/04/2008 GENERAL  ->  t0.city == t1.city, 245102508,0.817871983742513,0.3333333333333333"
  val result = RuleParser.parse(input)
  println(result)

    val filename= Config.PROJ_PATH + "/rules/labeled_data_400/airports/train/rules.txt"
    var head = true
    for (line <- Source.fromFile(filename).getLines) {
      if(!head) {
        println(RuleParser.parse(line))
      }
      head = false
    }



}
