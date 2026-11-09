package com.drwho174.infusioncalc.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.ui.amountUnitLabel
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.displayLabel
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import com.drwho174.infusioncalc.ui.schemeLabel
import com.drwho174.infusioncalc.ui.viewmodel.DrugCardViewModel
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugCardScreen(
    viewModel: DrugCardViewModel,
    onBack: () -> Unit,
    onCalculate: () -> Unit,
) {
    val drug by viewModel.drug.collectAsStateWithLifecycle()
    var dialogOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ConcentrationPreset?>(null) }

    val current = drug
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (current?.isBuiltIn == true) {
                                R.string.card_title_catalog
                            } else {
                                R.string.card_title_mine
                            },
                        ),
                    )
                },
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
        if (current == null) {
            Text(
                stringResource(R.string.loading),
                modifier = Modifier.padding(innerPadding).padding(16.dp),
            )
            return@Scaffold
        }
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(current.localizedName(isEnglishUi()), style = MaterialTheme.typography.headlineSmall)
            if (current.route.isNotBlank()) {
                Text(current.route, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            SectionCard(title = stringResource(R.string.section_dosing)) {
                current.schemes.forEach { scheme ->
                    Text(
                        text =
                            schemeLabel(scheme.amountUnit, scheme.perKg, scheme.perTime) +
                                (scheme.indication?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (scheme.doseMin != null || scheme.doseMax != null) {
                        Text(
                            stringResource(
                                R.string.range_format,
                                scheme.doseMin.display(),
                                scheme.doseMax.display(),
                                schemeLabel(scheme.amountUnit, scheme.perKg, scheme.perTime),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = stringResource(R.string.section_concentrations)) {
                if (current.presets.isEmpty()) {
                    Text(stringResource(R.string.no_presets), style = MaterialTheme.typography.bodySmall)
                }
                current.presets.forEach { preset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preset.displayLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            editTarget = preset
                            dialogOpen = true
                        }) {
                            Text(stringResource(R.string.edit))
                        }
                        if (current.presets.size > 1) {
                            TextButton(onClick = {
                                viewModel.savePresets(current.presets.filterNot { it.id == preset.id })
                            }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    editTarget = null
                    dialogOpen = true
                }) {
                    Text(stringResource(R.string.add_concentration))
                }
            }

            if (!current.notes.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                SectionCard(title = stringResource(R.string.section_notes)) {
                    Text(current.notes.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onCalculate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.to_calc))
            }
        }
    }

    if (dialogOpen && current != null) {
        PresetEditorDialog(
            initial = editTarget,
            onDismiss = { dialogOpen = false },
            onSave = { amount, unit, volume ->
                val base =
                    editTarget ?: ConcentrationPreset(
                        id = UUID.randomUUID().toString(),
                        drugId = current.id,
                        mode = ConcentrationMode.MASS_VOLUME,
                    )
                val preset =
                    base.copy(
                        mode = ConcentrationMode.MASS_VOLUME,
                        amount = amount,
                        amountUnit = unit,
                        volumeMl = volume,
                    )
                val existing = current.presets
                val updated =
                    if (editTarget == null) {
                        existing + preset
                    } else {
                        existing.map { if (it.id == preset.id) preset else it }
                    }
                viewModel.savePresets(updated)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun PresetEditorDialog(
    initial: ConcentrationPreset?,
    onDismiss: () -> Unit,
    onSave: (BigDecimal, AmountUnit, BigDecimal) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(initial?.amount?.toPlainString() ?: "") }
    var volume by rememberSaveable { mutableStateOf(initial?.volumeMl?.toPlainString() ?: "") }
    var unitName by rememberSaveable { mutableStateOf((initial?.amountUnit ?: AmountUnit.MG).name) }
    var unitMenu by remember { mutableStateOf(false) }

    val unit = AmountUnit.valueOf(unitName)
    val amountValue = amount.toBigDecimalOrNull()
    val volumeValue = volume.toBigDecimalOrNull()
    val valid =
        amountValue != null &&
            amountValue.signum() > 0 &&
            volumeValue != null &&
            volumeValue.signum() > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.add_concentration else R.string.edit_concentration,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.mass)) },
                    suffix = { Text(amountUnitLabel(unit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { unitMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(amountUnitLabel(unit))
                }
                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                    AmountUnit.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(amountUnitLabel(candidate)) },
                            onClick = {
                                unitName = candidate.name
                                unitMenu = false
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.volume)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(amountValue, unit, volumeValue) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
