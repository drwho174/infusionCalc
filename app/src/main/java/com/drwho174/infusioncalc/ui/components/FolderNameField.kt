package com.drwho174.infusioncalc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Folder

/** Поле «Название папки» + кнопка «Создать» с валидацией. */
@Composable
fun FolderNameField(
    name: String,
    onNameChange: (String) -> Unit,
    existingNames: List<String>,
    onCreate: () -> Unit,
) {
    val trimmed = name.trim()
    val duplicate = trimmed.isNotEmpty() && existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val tooLong = trimmed.length > Folder.MAX_NAME_LENGTH
    val canCreate = trimmed.isNotEmpty() && !duplicate && !tooLong

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.folder_name)) },
            isError = duplicate || tooLong,
            supportingText = {
                when {
                    duplicate -> Text(stringResource(R.string.folder_exists))
                    tooLong -> Text(stringResource(R.string.folder_name_too_long))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onCreate, enabled = canCreate) {
            Text(stringResource(R.string.create))
        }
    }
}
