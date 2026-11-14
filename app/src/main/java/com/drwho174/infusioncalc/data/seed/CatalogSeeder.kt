package com.drwho174.infusioncalc.data.seed

import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.first

/**
 * Досевает недостающие встроенные препараты и обновляет устаревшие (по [CatalogSeed.CATALOG_VERSION]),
 * не трогая отредактированные пользователем ([Drug.revision] > 1). Правка встроенного
 * сохраняется на месте, поэтому отдельной копии в списке не появляется.
 */
class CatalogSeeder(
    private val repository: DrugRepository,
) {
    suspend fun seedMissing() {
        val existingBuiltIns =
            repository
                .observeDrugs()
                .first()
                .filter { it.isBuiltIn }
                .associateBy { it.id }
        CatalogSeed.drugs().forEach { seed ->
            val current = existingBuiltIns[seed.id]
            val editedByUser = current != null && current.revision > 1
            if (current == null || (!editedByUser && current.catalogVersion < seed.catalogVersion)) {
                repository.save(seed)
            }
        }
    }
}
