package org.dsl.mining

case class CSVRow2(dataName:String, numPrediactesTemp:Int,numPrediactesConst:Int, rowSize:Int,
                   pLambda:ParameterLambda,
                   tempTimeB2:Double, constTimeB2:Double,
                   tempTimeInc:Double, constTimeInc:Double,
                   numSamples:Int, numREEs:String, trueRecall:Double, numInstances:Int, sampleSize:Double) {
  override def toString: String = {

    val totalB2 = tempTimeB2+constTimeB2
    val totalInc = tempTimeInc+constTimeInc
    val a = s"${dataName},${numPrediactesTemp},${numPrediactesConst},${rowSize},${pLambda.suppOld}, ${pLambda.suppNew},${pLambda.confOld},${pLambda.confNew},${pLambda.K},${pLambda.recall},${tempTimeB2},${constTimeB2},${totalB2},${tempTimeInc},${constTimeInc},${totalInc},${numSamples},${numREEs},${trueRecall},${numInstances},${sampleSize:Double}"
    assert(CSVRow2.getHeader.split(",").length == a.split(",").length)
    a
  }
}

object CSVRow2 {
  def getHeader = "Name,#predicates temp,#predicates const,row size,old support,new support,old confidence,new confidence,radius,recall,Template Mining Time Batch,Constant Recovery Time Batch,Total Time Batch,Template Mining Time Inc,Constant Recovery Time Inc,Total Time Inc,#Samples,#REEs,True Recall,numInstances,sample size(MB)\n"
}
