package org.dsl.utils

import org.dsl.mining.PredSpace.logger

object Profile {
  var visitedNodeNumTotal = 0L
  var visitedNodeNumTotal1 = 0L
  var visitedNodeNumTotal2 = 0L

  def flushVisitedNodesNum = {
    logger.info(s"VISITED ${Profile.visitedNodeNumTotal}")
    Profile.visitedNodeNumTotal = 0L
  }
}
