package com.drwho174.infusioncalc.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

/**
 * Вес пациента задаётся один раз при запуске и действует для всех расчётов в сессии.
 * Хранится только в памяти (FR-WEIGHT-01, v1.1) и не сохраняется на устройстве.
 */
class WeightStore {
    private val _weight = MutableStateFlow<BigDecimal?>(null)
    val weight: StateFlow<BigDecimal?> = _weight.asStateFlow()

    fun set(value: BigDecimal?) {
        _weight.value = value?.takeIf { it.signum() > 0 }
    }

    fun clear() {
        _weight.value = null
    }
}
