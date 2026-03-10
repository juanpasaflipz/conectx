package app.conectx.presentation.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.conectx.R

@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAlreadyActivated by viewModel.isAlreadyActivated.collectAsState()

    // If already activated, skip straight to squads
    LaunchedEffect(isAlreadyActivated) {
        if (isAlreadyActivated == true) {
            onActivated()
        }
    }

    // Don't show UI while checking activation status
    if (isAlreadyActivated == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.activation_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Username field
        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::updateUsername,
            label = { Text(stringResource(R.string.activation_username_hint)) },
            placeholder = { Text(stringResource(R.string.activation_username_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Activation code field
        OutlinedTextField(
            value = uiState.code,
            onValueChange = viewModel::updateCode,
            label = { Text(stringResource(R.string.activation_hint)) },
            placeholder = { Text(stringResource(R.string.activation_code_placeholder)) },
            singleLine = true,
            isError = uiState.error != null || uiState.errorRes != null,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { viewModel.activate(onActivated) }
            )
        )

        // Error message
        val errorText = uiState.error ?: uiState.errorRes?.let { stringResource(it) }
        if (errorText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.activate(onActivated) },
            enabled = !uiState.isLoading && uiState.code.isNotBlank() && uiState.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.activation_button))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Pricing info
        Text(
            text = stringResource(R.string.activation_pricing_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}
