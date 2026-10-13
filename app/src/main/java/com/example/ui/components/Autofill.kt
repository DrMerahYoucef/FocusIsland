package com.example.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * Lets the phone's password manager fill this field (email, password, name).
 * shortcut: Compose 1.7 only has this experimental autofill API; on Compose >= 1.8 replace it with
 * Modifier.semantics { contentType = ContentType.EmailAddress } (also enables "save password").
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofillEmail(onFill: (String) -> Unit) = autofill(listOf(AutofillType.EmailAddress, AutofillType.Username), onFill)
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofillPassword(onFill: (String) -> Unit) = autofill(listOf(AutofillType.Password), onFill)
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofillName(onFill: (String) -> Unit) = autofill(listOf(AutofillType.PersonFullName), onFill)

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.autofill(types: List<AutofillType>, onFill: (String) -> Unit): Modifier = composed {
    val node = remember { AutofillNode(autofillTypes = types, onFill = onFill) }
    val autofill = LocalAutofill.current
    LocalAutofillTree.current += node
    this
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { state ->
            autofill?.run { if (state.isFocused) requestAutofillForNode(node) else cancelAutofillForNode(node) }
        }
}
