package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.drwho174.infusioncalc.domain.WeightStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

data class WeightUiState(
    val text: String = "",
    val valid: Boolean = false,
)

/** Ввод веса пациента; значение сохраняется в [WeightStore] только по кнопке «Продолжить». */
class WeightViewModel(
    private val weightStore: WeightStore,
) : ViewModel() {
    private val _state = MutableStateFlow(WeightUiState())
    val state: StateFlow<WeightUiState> = _state.asStateFlow()

    fun onTextChange(raw: String) {
        val text = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val parsed = text.toBigDecimalOrNull()
        val valid = parsed != null && parsed > BigDecimal.ZERO && parsed <= MAX_WEIGHT
        _state.value = WeightUiState(text = text, valid = valid)
    }

    fun commit() {
        val weight = _state.value.text.toBigDecimalOrNull() ?: return
        weightStore.set(weight)
    }

    private companion object {
        val MAX_WEIGHT: BigDecimal = BigDecimal("300")
    }
}
