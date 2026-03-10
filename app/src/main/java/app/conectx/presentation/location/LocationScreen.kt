package app.conectx.presentation.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.conectx.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    squadId: String,
    onBack: () -> Unit,
    viewModel: LocationViewModel = hiltViewModel()
) {
    val section by viewModel.section.collectAsState()
    val row by viewModel.row.collectAsState()
    val seat by viewModel.seat.collectAsState()
    val note by viewModel.note.collectAsState()
    val shared by viewModel.shared.collectAsState()
    val memberLocations by viewModel.memberLocations.collectAsState()

    // Auto-dismiss the "shared" confirmation after 2 seconds
    LaunchedEffect(shared) {
        if (shared) {
            delay(2_000)
            viewModel.dismissSharedConfirmation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.location_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── My Location card ────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.location_my_location),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = section,
                            onValueChange = viewModel::updateSection,
                            label = { Text(stringResource(R.string.location_section_required)) },
                            placeholder = { Text(stringResource(R.string.location_section_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = row,
                                onValueChange = viewModel::updateRow,
                                label = { Text(stringResource(R.string.location_row)) },
                                placeholder = { Text(stringResource(R.string.location_row_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = seat,
                                onValueChange = viewModel::updateSeat,
                                label = { Text(stringResource(R.string.location_seat)) },
                                placeholder = { Text(stringResource(R.string.location_seat_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = note,
                            onValueChange = viewModel::updateNote,
                            label = { Text(stringResource(R.string.location_note)) },
                            placeholder = { Text(stringResource(R.string.location_note_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = viewModel::shareLocation,
                            enabled = section.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (shared) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.location_shared))
                            } else {
                                Text(stringResource(R.string.location_share))
                            }
                        }
                    }
                }
            }

            // ── Squad Members section ───────────────────────────
            if (memberLocations.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.location_squad_members),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(memberLocations, key = { it.authorId }) { member ->
                    MemberLocationCard(member)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MemberLocationCard(member: MemberLocation) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = member.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // Battery indicator
                if (member.ping.battery >= 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryFull,
                            contentDescription = stringResource(R.string.location_battery),
                            tint = batteryColor(member.ping.battery),
                            modifier = Modifier.height(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${member.ping.battery}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Location details
            val locationParts = buildList {
                add(stringResource(R.string.location_section_short, member.ping.section))
                member.ping.row?.let { add(stringResource(R.string.location_row_short, it)) }
                member.ping.seat?.let { add(stringResource(R.string.location_seat_short, it)) }
            }
            Text(
                text = locationParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge
            )

            member.ping.note?.let { noteText ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(member.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun batteryColor(level: Int) = when {
    level > 50 -> MaterialTheme.colorScheme.primary
    level > 20 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
