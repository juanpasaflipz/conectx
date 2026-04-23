package app.conectx.presentation.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.conectx.R

/**
 * Full-screen prominent disclosure shown on first launch, before any runtime
 * permission requests. Required by Google Play policy for apps that request
 * sensitive permissions (location, Bluetooth, nearby devices).
 */
@Composable
fun ProminentDisclosure(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val privacyUrl = stringResource(R.string.privacy_policy_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.disclosure_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.disclosure_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bluetooth & WiFi
        DisclosureSection(
            title = stringResource(R.string.disclosure_bt_wifi_title),
            body = stringResource(R.string.disclosure_bt_wifi_body)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Location
        DisclosureSection(
            title = stringResource(R.string.disclosure_location_title),
            body = stringResource(R.string.disclosure_location_body)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Messages & data
        DisclosureSection(
            title = stringResource(R.string.disclosure_data_title),
            body = stringResource(R.string.disclosure_data_body)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy policy link
        TextButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.disclosure_privacy_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Accept button
        Button(
            onClick = onAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.disclosure_accept),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun DisclosureSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
