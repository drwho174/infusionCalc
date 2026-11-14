package com.drwho174.infusioncalc.testutil

import com.drwho174.infusioncalc.data.session.TciSessionSnapshot
import com.drwho174.infusioncalc.data.session.TciSessionStore
import java.math.BigDecimal

/** Минимальное хранилище сессии TCI для UI-тестов. */
class FakeTciSessionStore(
    private var snapshot: TciSessionSnapshot? = null,
    private val disclaimerHidden: Boolean = false,
) : TciSessionStore {
    override suspend fun load(): TciSessionSnapshot? = snapshot

    override suspend fun save(snapshot: TciSessionSnapshot) {
        this.snapshot = snapshot
    }

    override suspend fun clear() {
        snapshot = null
    }

    override suspend fun isDisclaimerHidden(): Boolean = disclaimerHidden

    override suspend fun setDisclaimerHidden(hidden: Boolean) = Unit

    override suspend fun loadBolusRateMlPerHour(): BigDecimal? = null

    override suspend fun saveBolusRateMlPerHour(value: BigDecimal) = Unit

    override suspend fun loadTableStepMinutes(): Int? = null

    override suspend fun saveTableStepMinutes(value: Int) = Unit

    override suspend fun loadWarningLeadMinutes(): Int? = null

    override suspend fun saveWarningLeadMinutes(value: Int) = Unit
}
