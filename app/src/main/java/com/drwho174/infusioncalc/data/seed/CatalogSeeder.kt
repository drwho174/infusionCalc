package com.drwho174.infusioncalc.data.seed

import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.first

/**
 * Досевает недостающие встроенные препараты и обновляет устаревшие (по [CatalogSeed.CATALOG_VERSION]),
 * не трогая пользовательские карточки. Встроенный препарат, отредактированный пользователем,
 * сохранён копией с другим id, поэтому исходный id остаётся свободным для сида.
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
            if (current == null || current.catalogVersion < seed.catalogVersion) {
                repository.save(seed)
            }
        }
    }
}
