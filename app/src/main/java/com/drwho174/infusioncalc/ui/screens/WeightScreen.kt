package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.ui.rememberAppContainer
import java.math.BigDecimal

@Composable
fun WeightScreen(onContinue: () -> Unit) {
    val container = rememberAppContainer()
    var text by rememberSaveable { mutableStateOf("") }
    val weight = remember(text) { text.toBigDecimalOrNull() }
    val valid = weight != null && weight > BigDecimal.ZERO && weight <= BigDecimal("300")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.weight_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
            },
            label = { Text(stringResource(R.string.weight_label)) },
            supportingText = {
                Text(
                    if (text.isNotEmpty() && !valid) {
                        stringResource(R.string.weight_invalid)
                    } else {
                        stringResource(R.string.weight_hint)
                    },
                )
            },
            isError = text.isNotEmpty() && !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                container.weightStore.set(weight)
                onContinue()
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.weight_continue))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.weight_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
