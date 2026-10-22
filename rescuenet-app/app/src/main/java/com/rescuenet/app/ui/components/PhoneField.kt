package com.rescuenet.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.rescuenet.app.data.model.PhoneNumbers
import com.rescuenet.app.data.viewmodel.ContactInfoViewModel

/**
 * "My phone number" — saved as you type, used for every help request and "I'm safe" update so
 * the Command Center can call you back. Optional: leaving it blank never blocks a report.
 */
@Composable
fun MyPhoneField(
    modifier: Modifier = Modifier,
    viewModel: ContactInfoViewModel = hiltViewModel(),
) {
    val saved by viewModel.myPhone.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }

    // Fill the field once, from whatever was saved earlier (after that, the user is in control).
    LaunchedEffect(saved) {
        val current = saved
        if (!seeded && current != null) {
            text = current
            seeded = true
        }
    }

    val invalid = text.isNotBlank() && PhoneNumbers.normalize(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            viewModel.savePhone(it)
        },
        label = { Text("My phone number (optional)") },
        supportingText = {
            Text(
                if (invalid) "Enter a valid number, for example +92 300 1234567"
                else "Shared with rescuers when you ask for help, so they can call you back."
            )
        },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = modifier.fillMaxWidth(),
    )
}
