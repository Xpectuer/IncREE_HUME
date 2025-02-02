package org.dsl.utils

object TestReporter {

  def reportTestTime(logger: HUMELogger, name: String, time: Double): Unit = {
    // val precision2time =  BigDecimal(time).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    logger.info(s"$name done in: " + "%.2f (sec)".format(time))
  }

}
