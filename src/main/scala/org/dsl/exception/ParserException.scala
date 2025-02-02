package org.dsl.exception

case class ParserException(msg: String) extends HumeException {
  override def getMsg: String = "Parser Failed with: " + msg
}
