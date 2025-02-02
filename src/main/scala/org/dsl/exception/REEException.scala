package org.dsl.exception

case class REEException(msg: String) extends HumeException  {
  override def getMsg: String = "REE got exception: " + msg
}
