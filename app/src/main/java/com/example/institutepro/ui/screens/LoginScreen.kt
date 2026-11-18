package com.example.institutepro.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.institutepro.R
import com.example.institutepro.data.cloud.attemptAutoSignIn
import com.example.institutepro.data.cloud.onGoogleSignInClicked
import com.example.institutepro.ui.components.AppCard
import com.example.institutepro.ui.theme.CobaltDark
import com.example.institutepro.ui.theme.CobaltPrimary
import com.example.institutepro.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    vm: MainViewModel,
    instituteName: String,
    googleUserEmail: String? = null,
    onGoogleAuthSuccess: () -> Unit = {},
    onSignOutGoogle: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(googleUserEmail) {
        if (googleUserEmail == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { },
                onUnauthenticated = { },
                scope = scope
            )
        }
    }

    fun submitLogin() {
        focusManager.clearFocus()
        if (email.isBlank() || password.isBlank()) {
            errorMsg = "Enter both email and password."
        } else {
            vm.signIn(email, password) { err -> errorMsg = err }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_login_hero),
                        contentDescription = "InstitutePro Campus",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(172.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Filled.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = instituteName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Institute Management & Student Learning Portal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Option 1: Email & Password Sign-In
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with Email & Password", style = MaterialTheme.typography.titleLarge, color = CobaltDark)
                        Spacer(Modifier.height(12.dp))
                        if (errorMsg != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = errorMsg!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMsg = null },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("email_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMsg = null },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPw = !showPw }) {
                                    Icon(
                                        if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("password_input")
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { submitLogin() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_button")
                        ) {
                            Text("Sign in", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { showForgot = true },
                            modifier = Modifier.align(Alignment.End).testTag("forgot_password_button")
                        ) {
                            Text("Forgot password?")
                        }
                    }

                    // Option 2: Google Account Sign-In (with Cloud Sync)
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Google Account & Cloud Sync", style = MaterialTheme.typography.titleMedium, color = CobaltDark)
                        Spacer(Modifier.height(4.dp))
                        if (!googleUserEmail.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = CobaltPrimary)
                                        Text(
                                            text = "Connected: $googleUserEmail",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    if (onSignOutGoogle != null) {
                                        TextButton(onClick = onSignOutGoogle) { Text("Switch") }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onGoogleAuthSuccess,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("continue_google_button")
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Continue with Google Account")
                            }
                        } else {
                            Text(
                                "Sign in using your Google account to automatically enable Firebase Cloud Sync & backup.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (googleError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(googleError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    googleError = null
                                    isGoogleSigningIn = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isGoogleSigningIn = false
                                            onGoogleAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isGoogleSigningIn = false
                                            googleError = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isGoogleSigningIn = false
                                        }
                                    )
                                },
                                enabled = !isGoogleSigningIn,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("google_sign_in_button")
                            ) {
                                if (isGoogleSigningIn) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Signing in with Google...")
                                } else {
                                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = CobaltPrimary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }

                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Quick Demo Access", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap a role to explore immediately (demo password: Institute@123)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "super_admin" to "Super Admin",
                                "branch_manager" to "Branch Manager",
                                "teacher" to "Teacher",
                                "finance" to "Finance",
                                "student" to "Student Portal"
                            ).forEach { (role, label) ->
                                FilledTonalButton(
                                    onClick = { vm.quickDemoSignIn(role) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("demo_${role}_button")
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForgot) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text("Request Password Reset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your institute email. Your branch manager or administrator can issue a new temporary password from Staff / Teachers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.showToast("Reset request logged for ${resetEmail.ifBlank { "your account" }}. Contact an administrator.")
                    showForgot = false
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showForgot = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ForceChangePasswordScreen(vm: MainViewModel) {
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppCard(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                Text("Update Temporary Password", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "For security, set a new password (8+ characters) before continuing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                if (err != null) {
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it; err = null },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_pw_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPw,
                    onValueChange = { confirmPw = it; err = null },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_pw_input")
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.weight(1f)) {
                        Text("Sign out")
                    }
                    Button(
                        onClick = {
                            if (newPw.length < 8) err = "Minimum 8 characters required."
                            else if (newPw != confirmPw) err = "Passwords do not match."
                            else vm.changePassword("", newPw) { res ->
                                res.onFailure { err = it.message }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("save_new_pw_button")
                    ) { Text("Update") }
                }
            }
        }
    }
}
