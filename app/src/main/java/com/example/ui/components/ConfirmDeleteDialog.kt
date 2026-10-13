package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.designsystem.ConfirmDialog

/** Destructive confirmation used by card and deck deletion. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = title,
        body = body,
        confirmLabel = stringResource(R.string.delete),
        dismissLabel = stringResource(R.string.cancel),
        onConfirm = { onConfirm(); onDismiss() },
        onDismiss = onDismiss,
        destructive = true
    )
}
