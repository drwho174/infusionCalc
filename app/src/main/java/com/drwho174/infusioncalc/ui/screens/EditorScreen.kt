package com.drwho174.infusioncalc.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.drwho174.infusioncalc.engine.TimeBase
import com.drwho174.infusioncalc.ui.amountUnitLabel
import com.drwho174.infusioncalc.ui.components.ConcentrationPresetDialog
import com.drwho174.infusioncalc.ui.displayLabel
import com.drwho174.infusioncalc.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onCalculate: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var unitMenu by remember { mutableStateOf(false) }
    var timeMenu by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ConcentrationPreset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.editor_edit else R.string.editor_new))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.isLoaded) {
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.name)) },
                isError = state.duplicateName,
                supportingText =
                    if (state.duplicateName) {
                        ({ Text(stringResource(R.string.duplicate_name)) })
                    } else {
                        null
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.units), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { unitMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(amountUnitLabel(state.unit))
            }
            DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                AmountUnit.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(amountUnitLabel(unit)) },
                        onClick = {
                            viewModel.onUnitChange(unit)
                            unitMenu = false
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.perKg, onCheckedChange = viewModel::onPerKgChange)
                Text(
                    stringResource(R.string.per_kg),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(stringResource(R.string.time_base), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { timeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (state.timeBase == TimeBase.PER_MINUTE) R.string.per_minute else R.string.per_hour,
                    ),
                )
            }
            DropdownMenu(expanded = timeMenu, onDismissRequest = { timeMenu = false }) {
                TimeBase.entries.forEach { base ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (base == TimeBase.PER_MINUTE) R.string.per_minute else R.string.per_hour,
                                ),
                            )
                        },
                        onClick = {
                            viewModel.onTimeBaseChange(base)
                            timeMenu = false
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.doseMin,
                    onValueChange = viewModel::onDoseMinChange,
                    label = { Text(stringResource(R.string.range_from)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.doseMax,
                    onValueChange = viewModel::onDoseMaxChange,
                    label = { Text(stringResource(R.string.range_to)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.section_concentrations), style = MaterialTheme.typography.labelLarge)
            if (state.presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_presets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.presets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.displayLabel().replace(" = ", "\n= "),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (preset.mode == ConcentrationMode.MASS_VOLUME) {
                        TextButton(
                            onClick = {
                                editTarget = preset
                                dialogOpen = true
                            },
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                    }
                    if (state.presets.size > 1) {
                        TextButton(onClick = { viewModel.deletePreset(preset.id) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    editTarget = null
                    dialogOpen = true
                },
            ) {
                Text(stringResource(R.string.add_concentration))
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.section_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = state.valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.save))
            }
            if (state.isEditing) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.save(onCalculate) },
                    enabled = state.valid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.to_calc))
                }
            }
        }
    }

    if (dialogOpen) {
        val current = editTarget
        ConcentrationPresetDialog(
            initial = current,
            onDismiss = { dialogOpen = false },
            onSave = { amount, unit, volume ->
                if (current == null) {
                    viewModel.addPreset(amount, unit, volume)
                } else {
                    viewModel.updatePreset(current.id, amount, unit, volume)
                }
                dialogOpen = false
            },
        )
    }
}
