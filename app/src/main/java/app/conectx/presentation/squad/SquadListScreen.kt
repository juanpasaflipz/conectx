package app.conectx.presentation.squad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.conectx.R
import app.conectx.domain.model.Squad
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadListScreen(
    onSquadSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onUpgrade: () -> Unit,
    onConversationsClick: () -> Unit = {},
    viewModel: SquadListViewModel = hiltViewModel()
) {
    val squads by viewModel.squads.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var shareSquad by remember { mutableStateOf<Squad?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.squads_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onConversationsClick) {
                        Icon(Icons.Default.Email, contentDescription = stringResource(R.string.conversations_title))
                    }
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
                EmptySquadsState(
                    onCreateClick = { viewModel.showCreateDialog() },
                    onJoinClick = { viewModel.showJoinDialog() }
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
                        onClick = { onSquadSelected(squad.id) },
                        onShowCode = { shareSquad = squad }
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
            onDismiss = { viewModel.dismissDialogs() },
            onCopyCode = { squad -> copySquadCode(context, squad.inviteCode) },
            onShareCode = { squad -> shareSquadInvite(context, squad) }
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

    shareSquad?.let { squad ->
        SquadCodeDialog(
            squad = squad,
            onDismiss = { shareSquad = null },
            onCopyCode = { copySquadCode(context, squad.inviteCode) },
            onShareCode = { shareSquadInvite(context, squad) }
        )
    }
}

@Composable
private fun EmptySquadsState(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.squads_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onJoinClick) {
                Text(stringResource(R.string.squads_join))
            }
            OutlinedButton(onClick = onCreateClick) {
                Text(stringResource(R.string.squads_create))
            }
        }
    }
}

@Composable
private fun SquadCard(
    squad: Squad,
    onClick: () -> Unit,
    onShowCode: () -> Unit
) {
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.squads_code_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = squad.inviteCode,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.squads_member_count, squad.memberIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                TextButton(onClick = onShowCode) {
                    Text(stringResource(R.string.squads_show_code))
                }
            }
        }
    }
}

@Composable
private fun CreateSquadDialog(
    createdSquad: Squad?,
    onCreateClicked: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopyCode: (Squad) -> Unit,
    onShareCode: (Squad) -> Unit
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
                    SquadQrCode(code = createdSquad.inviteCode)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = createdSquad.inviteCode,
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { onCopyCode(createdSquad) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.squads_copy_code_button)
                            )
                        }
                        IconButton(onClick = { onShareCode(createdSquad) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.squads_share_code)
                            )
                        }
                    }
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
private fun SquadCodeDialog(
    squad: Squad,
    onDismiss: () -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.squads_show_code_title, squad.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.squads_code_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SquadQrCode(code = squad.inviteCode)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = squad.inviteCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.squads_share_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCopyCode) {
                    Text(stringResource(R.string.squads_copy_code_button))
                }
                TextButton(onClick = onShareCode) {
                    Text(stringResource(R.string.squads_share_code))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun SquadQrCode(code: String) {
    val bitmap = remember(code) { generateQrBitmap(code) }
    bitmap?.let {
        val frameShape = RoundedCornerShape(24.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = frameShape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = frameShape
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.squads_qr_code),
                    modifier = Modifier.size(220.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.squads_qr_hint_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.squads_qr_hint_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
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

private fun copySquadCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Conectx squad code", code))
    Toast.makeText(context, context.getString(R.string.squads_copy_code), Toast.LENGTH_SHORT).show()
}

private fun shareSquadInvite(context: Context, squad: Squad) {
    val shareText = context.getString(R.string.squads_share_message, squad.name, squad.inviteCode)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, shareText)

    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.squads_share_code))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun generateQrBitmap(content: String, size: Int = 768): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
