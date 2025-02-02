package org.dsl.reasoning.predTree

case class PredTreeException(msg:String) extends Exception{
  override def getMessage: String = "Error in Pred Tree: " + msg
}
