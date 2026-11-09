package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.displayLabel
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.schemeLabel
import com.drwho174.infusioncalc.ui.viewmodel.CalculatorUiState
import com.drwho174.infusioncalc.ui.viewmodel.CalculatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onEditDrug: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.isLoaded || state.drug == null) {
        LoadingScaffold(onBack)
        return
    }

    CalculatorContent(
        state = state,
        viewModel = viewModel,
        onBack = onBack,
        onOpenCard = onOpenCard,
        onEditDrug = onEditDrug,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorContent(
    state: CalculatorUiState,
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onOpenCard: () -> Unit,
    onEditDrug: () -> Unit,
) {
    val drug = state.drug ?: return
    val scheme = state.scheme
    val preset = state.preset
    val unitLabel = scheme?.let { schemeLabel(it.amountUnit, it.perKg, it.perTime) } ?: ""
    val resultUnit =
        when {
            state.resultValue == null -> ""
            state.resultIsRate -> stringResource(R.string.rate_unit)
            else -> unitLabel
        }
    val doseMin = scheme?.doseMin
    val doseMax = scheme?.doseMax
    val rangeText =
        if (doseMin != null || doseMax != null) {
            stringResource(R.string.range_format, doseMin.display(), doseMax.display(), unitLabel)
        } else {
            stringResource(R.string.no_range)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(drug.localizedName(isEnglishUi()), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCard) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.card),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.result_label), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = state.resultValue?.display() ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            text = " $resultUnit",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text =
                            when {
                                state.unitMismatch -> stringResource(R.string.status_unit_mismatch)
                                state.concentration == null -> stringResource(R.string.status_no_concentration)
                                state.resultValue == null -> stringResource(R.string.status_enter)
                                state.aboveRange -> stringResource(R.string.status_above)
                                state.belowRange -> stringResource(R.string.status_below)
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

            if (state.aboveRange) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors =
                        CardDefaults.cardColors(
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    drug.presets.forEach { item ->
                        FilterChip(
                            selected = preset?.id == item.id,
                            onClick = { viewModel.onPresetSelected(item) },
                            label = { Text(item.displayLabel()) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.displayDose,
                onValueChange = viewModel::onDoseChange,
                label = { Text(stringResource(R.string.dose)) },
                suffix = { Text(unitLabel) },
                supportingText = { Text(rangeText) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.displayRate,
                onValueChange = viewModel::onRateChange,
                label = { Text(stringResource(R.string.rate)) },
                suffix = { Text(stringResource(R.string.rate_unit)) },
                supportingText = {
                    val hint =
                        when {
                            state.resultValue == null -> ""
                            state.resultIsRate -> stringResource(R.string.computed_from_dose)
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
                text =
                    stringResource(
                        R.string.weight_info,
                        state.weight?.display() ?: stringResource(R.string.weight_not_set),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (scheme?.perKg == true && state.weight == null) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingScaffold(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dose)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.loading))
        }
    }
}
