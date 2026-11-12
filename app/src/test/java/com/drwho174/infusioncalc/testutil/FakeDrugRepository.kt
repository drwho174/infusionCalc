package com.drwho174.infusioncalc.testutil

import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Простой in-memory репозиторий для тестов ViewModel. */
class FakeDrugRepository(
    initial: List<Drug> = emptyList(),
) : DrugRepository {
    private val drugs = MutableStateFlow(initial)

    override fun observeDrugs(): Flow<List<Drug>> = drugs

    override fun observeDrug(id: String): Flow<Drug?> = drugs.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getDrug(id: String): Drug? = drugs.value.firstOrNull { it.id == id }

    override suspend fun save(drug: Drug) {
        drugs.value = drugs.value.filterNot { it.id == drug.id } + drug
    }

    override suspend fun delete(id: String) {
        drugs.value = drugs.value.filterNot { it.id == id }
    }
}
