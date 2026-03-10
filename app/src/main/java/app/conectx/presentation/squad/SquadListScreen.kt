package app.conectx.presentation.squad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.conectx.R
import app.conectx.domain.model.Squad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadListScreen(
    onSquadSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: SquadListViewModel = hiltViewModel()
) {
    val squads by viewModel.squads.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.squads_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    OutlinedButton(onClick = { viewModel.showJoinDialog() }) {
                        Text(stringResource(R.string.squads_join))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.squads_create))
            }
        }
    ) { padding ->
        if (squads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.squads_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(squads, key = { it.id }) { squad ->
                    SquadCard(
                        squad = squad,
                        onClick = { onSquadSelected(squad.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────

    if (uiState.showCreateDialog) {
        CreateSquadDialog(
            createdSquad = uiState.createdSquad,
            onCreateClicked = { name -> viewModel.createSquad(name) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }

    if (uiState.showJoinDialog) {
        JoinSquadDialog(
            showError = uiState.joinError,
            onJoinClicked = { code -> viewModel.joinSquad(code) },
            onDismiss = { viewModel.dismissDialogs() }
        )
    }

    if (uiState.showSquadLimitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialogs() },
            title = { Text(stringResource(R.string.squad_limit_title)) },
            text = { Text(stringResource(R.string.squad_limit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissDialogs()
                    onUpgrade()
                }) {
                    Text(stringResource(R.string.upgrade_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialogs() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.showMemberLimitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialogs() },
            title = { Text(stringResource(R.string.squad_limit_title)) },
            text = { Text(stringResource(R.string.member_limit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissDialogs()
                    onUpgrade()
                }) {
                    Text(stringResource(R.string.upgrade_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialogs() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SquadCard(squad: Squad, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = squad.name,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = squad.inviteCode,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.squads_member_count, squad.memberIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun CreateSquadDialog(
    createdSquad: Squad?,
    onCreateClicked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.squads_create_title)) },
        text = {
            Column {
                if (createdSquad != null) {
                    Text(stringResource(R.string.squads_created_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = createdSquad.inviteCode,
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.squads_create_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (createdSquad != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            } else {
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreateClicked(name.trim()) },
                    enabled = name.isNotBlank()
                ) {
                    Text(stringResource(R.string.squads_create_button))
                }
            }
        },
        dismissButton = {
            if (createdSquad == null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun JoinSquadDialog(
    showError: Boolean,
    onJoinClicked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.squads_join_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(7) },
                    label = { Text(stringResource(R.string.squads_join_code_hint)) },
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.squads_join_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (code.isNotBlank()) onJoinClicked(code.trim()) },
                enabled = code.isNotBlank()
            ) {
                Text(stringResource(R.string.squads_join_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
