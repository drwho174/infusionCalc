package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.convertAmountUnit
import com.drwho174.infusioncalc.engine.CalcResult
import com.drwho174.infusioncalc.engine.InfusionEngine
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.displayLabel
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.rememberAppContainer
import com.drwho174.infusioncalc.ui.schemeLabel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    drugId: String,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onEditDrug: () -> Unit,
) {
    val container = rememberAppContainer()
    val weight by container.weightStore.weight.collectAsState()
    var drug by remember { mutableStateOf<Drug?>(null) }

    LaunchedEffect(drugId) {
        drug = container.drugRepository.getDrug(drugId)
    }

    val current = drug
    if (current == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.dose)) },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                    },
                )
            },
        ) { innerPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.loading))
            }
        }
        return
    }

    CalculatorContent(
        drug = current,
        weight = weight,
        onBack = onBack,
        onOpenCard = onOpenCard,
        onEditDrug = onEditDrug,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorContent(
    drug: Drug,
    weight: BigDecimal?,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onEditDrug: () -> Unit,
) {
    var scheme by remember(drug.id, drug.revision) { mutableStateOf(drug.defaultScheme) }
    var preset by remember(drug.id, drug.revision) { mutableStateOf(drug.defaultPreset) }
    var doseText by rememberSaveable(drug.id) { mutableStateOf("") }
    var rateText by rememberSaveable(drug.id) { mutableStateOf("") }
    var source by rememberSaveable(drug.id) { mutableStateOf("dose") }

    val schemeUnit = scheme?.amountUnit
    val concentration = preset?.concentrationIn(schemeUnit)

    val unitMismatch = preset?.amountUnit?.let { presetUnit ->
        schemeUnit?.let { convertAmountUnit(BigDecimal.ONE, presetUnit, it) == null }
    } ?: false

    val engineScheme = scheme?.toEngine()

    val computed = remember(doseText, rateText, source, concentration, weight, engineScheme) {
        if (concentration == null || engineScheme == null) {
            null
        } else if (source == "dose") {
            doseText.toBigDecimalOrNull()?.let { d ->
                (InfusionEngine.rate(d, engineScheme, weight, concentration) as? CalcResult.Success)
                    ?.value?.let { "rate" to it }
            }
        } else {
            rateText.toBigDecimalOrNull()?.let { r ->
                (InfusionEngine.dose(r, engineScheme, weight, concentration) as? CalcResult.Success)
                    ?.value?.let { "dose" to it }
            }
        }
    }

    val doseValue: BigDecimal? = if (source == "dose") {
        doseText.toBigDecimalOrNull()
    } else {
        computed?.takeIf { it.first == "dose" }?.second
    }
    val displayDose = if (source == "dose") {
        doseText
    } else {
        computed?.takeIf { it.first == "dose" }?.second
            ?.let { InfusionEngine.formatDoseDisplay(it) }?.display() ?: ""
    }
    val displayRate = if (source == "rate") {
        rateText
    } else {
        computed?.takeIf { it.first == "rate" }?.second
            ?.let { InfusionEngine.formatRate(it) }?.display() ?: ""
    }

    val resultValue = computed?.let {
        if (it.first == "dose") InfusionEngine.formatDoseDisplay(it.second) else InfusionEngine.formatRate(it.second)
    }
    val resultUnit = when {
        computed == null -> ""
        computed.first == "rate" -> "мл/ч"
        else -> scheme?.let { schemeLabel(it.amountUnit, it.perKg, it.perTime) } ?: ""
    }

    val doseMax = scheme?.doseMax
    val doseMin = scheme?.doseMin
    val aboveRange = doseValue != null && doseMax != null && doseValue > doseMax
    val belowRange = doseValue != null && doseMin != null && doseValue < doseMin
    val unitLabel = scheme?.let { schemeLabel(it.amountUnit, it.perKg, it.perTime) } ?: ""
    val rangeText = if (doseMin != null || doseMax != null) {
        stringResource(R.string.range_format, doseMin.display(), doseMax.display(), unitLabel)
    } else {
        stringResource(R.string.no_range)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(drug.localizedName(isEnglishUi()), maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = onOpenCard) { Text(stringResource(R.string.card)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.result_label), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = resultValue?.display() ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = " $resultUnit",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text = when {
                            unitMismatch -> stringResource(R.string.status_unit_mismatch)
                            concentration == null -> stringResource(R.string.status_no_concentration)
                            resultValue == null -> stringResource(R.string.status_enter)
                            aboveRange -> stringResource(R.string.status_above)
                            belowRange -> stringResource(R.string.status_below)
                            doseMin == null && doseMax == null -> stringResource(R.string.status_no_range)
                            else -> stringResource(R.string.status_in_range)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    preset?.let {
                        Text(it.displayLabel(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (aboveRange) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.warning_above_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(rangeText, style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(R.string.warning_check),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.concentration), style = MaterialTheme.typography.labelLarge)
            if (drug.presets.isEmpty()) {
                Text(
                    stringResource(R.string.no_presets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    drug.presets.forEach { item ->
                        FilterChip(
                            selected = preset?.id == item.id,
                            onClick = { preset = item },
                            label = { Text(item.displayLabel()) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = displayDose,
                onValueChange = { raw ->
                    source = "dose"
                    doseText = normalizeNumber(raw)
                },
                label = { Text(stringResource(R.string.dose)) },
                suffix = { Text(unitLabel) },
                supportingText = { Text(rangeText) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = displayRate,
                onValueChange = { raw ->
                    source = "rate"
                    rateText = normalizeNumber(raw)
                },
                label = { Text(stringResource(R.string.rate)) },
                suffix = { Text("мл/ч") },
                supportingText = {
                    val hint = when {
                        computed == null -> ""
                        computed.first == "rate" -> stringResource(R.string.computed_from_dose)
                        else -> stringResource(R.string.computed_from_rate)
                    }
                    Text(hint)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    R.string.weight_info,
                    weight?.display() ?: stringResource(R.string.weight_not_set),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (scheme?.perKg == true && weight == null) {
                Text(
                    text = stringResource(R.string.weight_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = onEditDrug) { Text(stringResource(R.string.edit_drug)) }
        }
    }
}

private fun normalizeNumber(raw: String): String {
    val sb = StringBuilder()
    var separator = false
    for (ch in raw) {
        when {
            ch.isDigit() -> sb.append(ch)
            (ch == '.' || ch == ',') && !separator && sb.isNotEmpty() -> {
                separator = true
                sb.append('.')
            }
        }
    }
    return sb.toString()
}
