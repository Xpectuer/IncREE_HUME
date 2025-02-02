package org.dsl.emperical

import org.dsl.dataStruct.Statistics
import org.dsl.emperical.Database.logger
import org.dsl.emperical.table.{ColTable, RowTable}

object JournalOps {
  /**
   * Merges the D and ΔD, (D,ΔD) => (ΔD, D+ΔD)
   *
   * @param spset
   * @param dbOriginal
   * @param tableOrigin
   * @param journal
   * @param stat
   * @return incr, incr + origin
   */
  def MergeJournal(dbOriginal: Database,
                   delta: DeltaDatabase, stat: Statistics): (Database, Statistics) = {
    logger.debug(s"Merge DB $dbOriginal with delta ${delta}")
    mergeJournal(dbOriginal, delta, stat)
  }


  private def mergeJournal(dbOriginal: Database,
                           delta: DeltaDatabase, stat: Statistics): (Database, Statistics) = {


    def dropStat(stat: Statistics, neg: Seq[Int]): Unit = stat.dropFromIdxSeq(neg)


    val (addDeltaDB, delLogsDB) = delta.toPosDBAndDelJournal


    // D+ΔD
    val db_plus_delta: Database = Database(dbOriginal.getDB.map {
      case (schm, table) => schm -> {
        table match {
          case rowTable: RowTable =>
            addDeltaDB.getDB.get(schm) match {
              case Some(table) =>
                table match {
                  case r: RowTable => rowTable.merge(r)
                  case c: ColTable => c
                }

              case None => rowTable
            }

          case colTable:ColTable =>
            addDeltaDB.getDB.get(schm) match {
              case Some(table) =>
                val n = table match {
                  case r: RowTable => colTable.merge(r.transpose)
                  case c:ColTable => c
                }
                n

              case None => colTable
            }
        }
      }
    })

    // clean statistics
    delLogsDB.foreach {
      case (_, log) => dropStat(stat, log)
    }

    val cleanedStats = stat

    (db_plus_delta, cleanedStats)

  }
}
