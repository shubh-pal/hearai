package com.hearai.app.ui.screens.apikey

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hearai.app.R
import com.hearai.app.data.model.KeyValidationStatus

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

/** §6.2 Onboarding — API Key Setup. */
@Composable
fun ApiKeySetupScreen(
    onContinue: () -> Unit,
    viewModel: ApiKeySetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Your Gemini API Key", style = MaterialTheme.typography.headlineSmall)
        Text(
            "You need a Gemini API key from Google AI Studio to use HearAI.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        OutlinedButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_URL)))
        }) {
            Text("Open Google AI Studio")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.keyInput,
            onValueChange = viewModel::onKeyInputChanged,
            label = { Text("Paste your API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Row {
            Button(onClick = viewModel::validate, enabled = state.keyInput.isNotBlank() && !state.isValidating) {
                Text("Validate")
            }
            if (state.isValidating) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).height(24.dp))
            }
        }

        when (state.validationStatus) {
            KeyValidationStatus.VALID -> Text(
                "Key validated ✓",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            KeyValidationStatus.INVALID -> Text(
                state.errorMessage ?: "This key couldn't be validated.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> if (state.errorMessage != null) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // §5 disclosure — surfaced here, before mic permission is requested, not buried in Settings.
        Text(
            stringResource(R.string.privacy_disclosure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Checkbox(checked = state.disclosureAcknowledged, onCheckedChange = viewModel::onDisclosureAcknowledgedChanged)
            Text("I understand", modifier = Modifier.padding(start = 4.dp, top = 12.dp))
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onContinue, enabled = state.canContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        Text(
            "Your key is stored securely on this device and never shared or logged.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
