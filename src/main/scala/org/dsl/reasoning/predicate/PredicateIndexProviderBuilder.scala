package org.dsl.reasoning.predicate

import org.dsl.utils.IndexProvider

object PredicateIndexProviderBuilder {

  // todo: consistency check for dataset name and version (for JSON serialization's sake)

  type PredicateIndexProvider = IndexProvider[Expression]

  def empty: IndexProvider[Expression] = IndexProvider[Expression](List())
}
