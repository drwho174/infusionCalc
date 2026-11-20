package com.drwho174.infusioncalc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Folder

/**
 * Переименование папки. Из диалога можно также удалить папку.
 * Используется и в настройках папок, и по долгому нажатию на чипс.
 */
@Composable
fun FolderEditDialog(
    folder: Folder,
    existingNames: List<String>,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(folder.id) { mutableStateOf(folder.name) }
    val trimmed = name.trim()
    val otherNames = existingNames.filterNot { it.equals(folder.name, ignoreCase = true) }
    val reserved = trimmed.lowercase() in Folder.RESERVED_NAMES
    val duplicate = trimmed.isNotEmpty() && !reserved && otherNames.any { it.equals(trimmed, ignoreCase = true) }
    val tooLong = trimmed.length > Folder.MAX_NAME_LENGTH
    val valid = trimmed.isNotEmpty() && !duplicate && !reserved && !tooLong

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_folder)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    isError = duplicate || tooLong || reserved,
                    supportingText = {
                        when {
                            reserved -> Text(stringResource(R.string.folder_reserved))
                            duplicate -> Text(stringResource(R.string.folder_exists))
                            tooLong -> Text(stringResource(R.string.folder_name_too_long))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onRename(trimmed) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
