package com.drwho174.infusioncalc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.ui.amountUnitLabel
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import java.math.BigDecimal

/** Диалог добавления/изменения концентрации: масса, единица массы, объём. */
@Suppress("LongMethod")
@Composable
fun ConcentrationPresetDialog(
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
                onClick = {
                    val amount = amountValue
                    val volume = volumeValue
                    if (amount != null && volume != null) {
                        onSave(amount, unit, volume)
                    }
                },
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
