package org.dsl.exception

case class EmpericalException(msg: String) extends HumeException {
  override def getMsg: String = msg
}
