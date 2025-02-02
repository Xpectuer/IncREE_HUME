package org.dsl.mining


case class CSVRow(timeBatch: Double, incTime: Double, param: ParameterLambda, faster: Double, numberNewREEs: String, trueRecall: Double) {

  override def toString: String = {
    param match {
      case ParameterLambda(suppOld, suppNew, confOld, confNew, recall, dist) =>
        s"$timeBatch,$incTime,$suppOld,$confOld,$suppNew,$confNew,$recall,$dist,$faster,$numberNewREEs,$trueRecall"
    }

  }
}


/**
 * Dataset	OldSupp	NewSupp	OldConf	NewConf	Batch1 Time	Pruned set	Sampled Set	Batch2	Inc Time
 *
 * @param dataName
 */
case class CSVRowDebug(dataName: String,
                       oldSupp: Double, newSupp: Double,
                       oldConf: Double, newConf: Double,
                       K: Int, recall: Double,
                       b1Time: Double, prunedN: Long, sampleN: Long,
                       b2Time: Double, incTime: Double) {

  override def toString: String = {
    s"$dataName,$oldSupp,$newSupp,$oldConf,$newConf,$K,$recall,$b1Time,$prunedN,$sampleN,$b2Time,$incTime"
  }
}
