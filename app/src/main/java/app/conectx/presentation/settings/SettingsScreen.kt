package app.conectx.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.conectx.BuildConfig
import app.conectx.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val username by viewModel.username.collectAsState()
    val passType by viewModel.passType.collectAsState()
    val passExpiry by viewModel.passExpiry.collectAsState()
    val isFreeUser by viewModel.isFreeUser.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Account card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_account),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsRow(stringResource(R.string.settings_user), username ?: "—")

                    val passLabel = when (passType) {
                        "mundial" -> stringResource(R.string.settings_pass_mundial)
                        "partido" -> stringResource(R.string.settings_pass_partido)
                        "free" -> stringResource(R.string.settings_pass_free)
                        else -> "—"
                    }
                    SettingsRow(stringResource(R.string.settings_pass), passLabel)

                    val expiryLabel = if (passExpiry != null && passExpiry!! > 0) {
                        SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))
                            .format(Date(passExpiry!!))
                    } else {
                        "—"
                    }
                    SettingsRow(stringResource(R.string.settings_expires), expiryLabel)

                    if (isFreeUser) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onUpgrade,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_upgrade))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ThemeModeSelector(
                        selected = themeMode,
                        onSelect = { viewModel.setThemeMode(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                    Text(
                        text = stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val url = context.getString(R.string.privacy_policy_url)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_policy_link),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=app.conectx")))
                            } catch (_: android.content.ActivityNotFoundException) {
                                val url = context.getString(R.string.play_store_url)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.settings_rate_app),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { viewModel.signOut(onBack) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "system" to stringResource(R.string.settings_theme_system),
        "light" to stringResource(R.string.settings_theme_light),
        "dark" to stringResource(R.string.settings_theme_dark)
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            OutlinedButton(
                onClick = { onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
