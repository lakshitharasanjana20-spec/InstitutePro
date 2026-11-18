package com.example.institutepro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.institutepro.data.cloud.onGoogleSignInClicked
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.components.*
import com.example.institutepro.ui.theme.*
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.CloudSyncViewModel
import com.example.institutepro.ui.viewmodel.CloudUiState
import com.google.firebase.auth.FirebaseUser

@Composable
fun ConnectGoogleCloudScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(CobaltDark, CobaltPrimary, OceanBlue)))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill("Local Session Active", Color.White)
                    Text(
                        text = "Connect Google Account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "You signed in with Email & Password. Connect a Google account anytime to enable Cloud Firestore sync & backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("Firebase Cloud Firestore Sync", style = MaterialTheme.typography.titleMedium, color = CobaltDark)
            Spacer(Modifier.height(6.dp))
            Text(
                "Cloud sync stores your students, receipts, attendance summaries, and audit logs privately under your Google account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (errorMsg != null) {
                Spacer(Modifier.height(10.dp))
                Text(errorMsg!!, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    errorMsg = null
                    isSigningIn = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = { isSigningIn = false },
                        onAuthError = { msg -> isSigningIn = false; errorMsg = msg },
                        scope = scope,
                        onAuthCancelled = { isSigningIn = false }
                    )
                },
                enabled = !isSigningIn,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("connect_google_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
fun CloudSyncScreen(
    firebaseUser: FirebaseUser,
    cloudVm: CloudSyncViewModel,
    snapshot: AppDataSnapshot,
    onSignOutGoogle: () -> Unit
) {
    val profileState by cloudVm.profileState.collectAsStateWithLifecycle()
    val studentsState by cloudVm.studentsState.collectAsStateWithLifecycle()
    val paymentsState by cloudVm.paymentsState.collectAsStateWithLifecycle()
    val attendanceState by cloudVm.attendanceState.collectAsStateWithLifecycle()
    val activityState by cloudVm.activityState.collectAsStateWithLifecycle()
    val isSyncing by cloudVm.isSyncing.collectAsStateWithLifecycle()
    val feedback by cloudVm.syncFeedback.collectAsStateWithLifecycle()

    val studentCount = (studentsState as? CloudUiState.Success)?.data?.size ?: 0
    val paymentCount = (paymentsState as? CloudUiState.Success)?.data?.size ?: 0
    val attendanceCount = (attendanceState as? CloudUiState.Success)?.data?.size ?: 0
    val activityCount = (activityState as? CloudUiState.Success)?.data?.size ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(CobaltDark, CobaltPrimary, OceanBlue)))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill("Firebase Connected", Color.White)
                        Text(
                            text = firebaseUser.displayName ?: "Google Account",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = firebaseUser.email ?: firebaseUser.uid,
                            style = MaterialTheme.typography.labelMedium,
                            color = CobaltContainer
                        )
                        val roleLabel = (profileState as? CloudUiState.Success)?.data?.activeRole
                            ?.let { DomainRules.ROLE_LABELS[it] ?: it } ?: "Syncing profile..."
                        Text(
                            text = "Cloud Role: $roleLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        if (!feedback.isNullOrBlank()) {
            item {
                Surface(
                    color = CobaltContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(feedback!!, color = CobaltDark, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { cloudVm.clearFeedback() }) { Text("Dismiss") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { cloudVm.syncWorkspaceSnapshot(snapshot) },
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("sync_workspace_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Workspace Now")
                    }
                }
                OutlinedButton(
                    onClick = onSignOutGoogle,
                    modifier = Modifier.height(50.dp).testTag("google_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sign Out")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        label = "Cloud Students",
                        value = studentCount.toString(),
                        subtitle = statusText(studentsState),
                        icon = Icons.Filled.School,
                        accent = CobaltPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "Cloud Receipts",
                        value = paymentCount.toString(),
                        subtitle = statusText(paymentsState),
                        icon = Icons.Filled.ReceiptLong,
                        accent = OceanBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        label = "Attendance Logs",
                        value = attendanceCount.toString(),
                        subtitle = statusText(attendanceState),
                        icon = Icons.Filled.FactCheck,
                        accent = CobaltMid,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "Activity Audits",
                        value = activityCount.toString(),
                        subtitle = statusText(activityState),
                        icon = Icons.Filled.History,
                        accent = IndigoBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when (val st = studentsState) {
            is CloudUiState.Loading -> item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Connecting to Cloud Firestore...")
                    }
                }
            }
            is CloudUiState.Error -> item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Firestore Notice", style = MaterialTheme.typography.titleMedium, color = CrimsonAlert)
                    Text(st.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is CloudUiState.Success -> {
                item {
                    Text("Synced Students in Cloud (${st.data.size})", style = MaterialTheme.typography.titleMedium, color = CobaltDark, fontWeight = FontWeight.Bold)
                }
                if (st.data.isEmpty()) {
                    item {
                        EmptyStateCard("No cloud records yet", "Tap Sync Workspace Now above to back up your institute data to Firebase.")
                    }
                } else {
                    items(st.data.take(10), key = { it.id }) { cs ->
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cs.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text("${cs.studentCode} · ${cs.branchName} · ${cs.gender}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(cs.status, statusColorFor(cs.status))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> statusText(state: CloudUiState<T>): String = when (state) {
    is CloudUiState.Loading -> "Loading..."
    is CloudUiState.Success -> "Live in Firestore"
    is CloudUiState.Error -> "Sync error"
}
