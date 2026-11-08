package com.drwho174.infusioncalc.data.seed

import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.first

/**
 * Досевает недостающие встроенные препараты, не трогая пользовательские карточки.
 * Встроенный препарат, отредактированный пользователем, сохранён копией с другим id,
 * поэтому исходный id остаётся свободным для сида.
 */
class CatalogSeeder(
    private val repository: DrugRepository,
) {
    suspend fun seedMissing() {
        val existingBuiltInIds =
            repository
                .observeDrugs()
                .first()
                .filter { it.isBuiltIn }
                .map { it.id }
                .toSet()
        CatalogSeed
            .drugs()
            .filter { it.id !in existingBuiltInIds }
            .forEach { repository.save(it) }
    }
}
