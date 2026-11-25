package com.drwho174.infusioncalc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.ui.isEnglishUi

/** Диалог добавления препаратов в папку: checkbox-список ещё не добавленных. */
@Composable
internal fun AddDrugsToFolderDialog(
    folderName: String,
    drugs: List<Drug>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_drugs_to_folder_title, folderName)) },
        text = {
            if (drugs.isEmpty()) {
                Text(stringResource(R.string.folder_all_drugs_added))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(drugs, key = { it.id }) { drug ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected[drug.id] = !(selected[drug.id] ?: false) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected[drug.id] == true,
                                onCheckedChange = { selected[drug.id] = it },
                            )
                            Text(drug.localizedName(isEnglishUi()))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected.filterValues { it }.keys) },
                enabled = selected.any { it.value },
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
