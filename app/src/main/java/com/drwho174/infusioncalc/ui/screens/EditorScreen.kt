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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Verification
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import com.drwho174.infusioncalc.ui.amountUnitLabel
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import com.drwho174.infusioncalc.ui.rememberAppContainer
import kotlinx.coroutines.launch
import java.math.MathContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    drugId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    val existing = remember { mutableStateOf<Drug?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(drugId) {
        if (drugId != null) {
            existing.value = container.drugRepository.getDrug(drugId)
        }
    }

    var name by rememberSaveable { mutableStateOf("") }
    var unitName by rememberSaveable { mutableStateOf(AmountUnit.MCG.name) }
    var perKg by rememberSaveable { mutableStateOf(false) }
    var timeName by rememberSaveable { mutableStateOf(TimeBase.PER_MINUTE.name) }
    var doseMin by rememberSaveable { mutableStateOf("") }
    var doseMax by rememberSaveable { mutableStateOf("") }
    var mass by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf("") }
    var unitMenu by remember { mutableStateOf(false) }
    var timeMenu by remember { mutableStateOf(false) }
    var massUnitName by rememberSaveable { mutableStateOf(AmountUnit.MCG.name) }
    var massUnitMenu by remember { mutableStateOf(false) }

    LaunchedEffect(existing.value) {
        val d = existing.value ?: return@LaunchedEffect
        if (prefilled) return@LaunchedEffect
        name = d.nameRu
        d.defaultScheme?.let { s ->
            unitName = s.amountUnit.name
            perKg = s.perKg
            timeName = s.perTime.name
            doseMin = s.doseMin?.toPlainString() ?: ""
            doseMax = s.doseMax?.toPlainString() ?: ""
        }
        d.defaultPreset?.let { p ->
            mass = p.amount?.toPlainString() ?: ""
            volume = p.volumeMl?.toPlainString() ?: ""
            p.amountUnit?.let { massUnitName = it.name }
        }
        prefilled = true
    }

    val amountUnit = AmountUnit.valueOf(unitName)
    val massUnit = AmountUnit.valueOf(massUnitName)
    val timeBase = TimeBase.valueOf(timeName)
    val massValue = mass.toBigDecimalOrNull()
    val volumeValue = volume.toBigDecimalOrNull()
    val concentration = if (massValue != null && volumeValue != null && volumeValue.signum() > 0) {
        massValue.divide(volumeValue, MathContext(34))
    } else {
        null
    }
    val valid = name.isNotBlank() &&
        massValue != null && massValue.signum() > 0 &&
        volumeValue != null && volumeValue.signum() > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (drugId == null) R.string.editor_new else R.string.editor_edit),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.units), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { unitMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(amountUnitLabel(amountUnit))
            }
            DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                AmountUnit.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(amountUnitLabel(unit)) },
                        onClick = {
                            unitName = unit.name
                            unitMenu = false
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = perKg, onCheckedChange = { perKg = it })
                Text(
                    stringResource(R.string.per_kg),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(stringResource(R.string.time_base), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { timeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (timeBase == TimeBase.PER_MINUTE) R.string.per_minute else R.string.per_hour,
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
                            timeName = base.name
                            timeMenu = false
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = doseMin,
                    onValueChange = { doseMin = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.range_from)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = doseMax,
                    onValueChange = { doseMax = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.range_to)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.concentration_preset), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mass,
                    onValueChange = { mass = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.mass)) },
                    suffix = { Text(amountUnitLabel(massUnit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = normalizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.volume)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = { massUnitMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.mass_unit) + ": " + amountUnitLabel(massUnit))
            }
            DropdownMenu(expanded = massUnitMenu, onDismissRequest = { massUnitMenu = false }) {
                AmountUnit.entries.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(amountUnitLabel(candidate)) },
                        onClick = {
                            massUnitName = candidate.name
                            massUnitMenu = false
                        },
                    )
                }
            }
            Text(
                text = if (concentration != null) {
                    stringResource(
                        R.string.concentration_preview,
                        concentration.display(),
                        amountUnitLabel(massUnit),
                    )
                } else {
                    stringResource(R.string.concentration_empty)
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val id = existing.value?.id ?: UUID.randomUUID().toString()
                    val schemeId = UUID.randomUUID().toString()
                    val presetId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val drug = Drug(
                        id = id,
                        nameRu = name.trim(),
                        isBuiltIn = false,
                        verification = Verification.USER,
                        revision = (existing.value?.revision ?: 0) + 1,
                        updatedAt = now,
                        defaultSchemeId = schemeId,
                        schemes = listOf(
                            DoseScheme(
                                id = schemeId,
                                drugId = id,
                                amountUnit = amountUnit,
                                perKg = perKg,
                                perTime = timeBase,
                                doseMin = doseMin.toBigDecimalOrNull(),
                                doseMax = doseMax.toBigDecimalOrNull(),
                                schemeOrder = 0,
                            ),
                        ),
                        presets = listOf(
                            ConcentrationPreset(
                                id = presetId,
                                drugId = id,
                                mode = ConcentrationMode.MASS_VOLUME,
                                amount = massValue,
                                amountUnit = massUnit,
                                volumeMl = volumeValue,
                                isDefault = true,
                            ),
                        ),
                    )
                    prefilled = true
                    scope.launch {
                        container.drugRepository.save(drug)
                        onSaved()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
