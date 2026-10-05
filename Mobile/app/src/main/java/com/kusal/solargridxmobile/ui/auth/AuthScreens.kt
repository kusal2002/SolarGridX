package com.kusal.solargridxmobile.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.kusal.solargridxmobile.R
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kusal.solargridxmobile.data.repository.AccountInactiveException
import com.kusal.solargridxmobile.data.repository.AccountPendingApprovalException
import com.kusal.solargridxmobile.data.repository.AuthRepository

val SolarGreen = Color(0xFF065F46)
val SolarGreenLight = Color(0xFFD1FAE5)
val AuthTextPrimary = Color(0xFF0F172A)
val AuthTextSecondary = Color(0xFF475569)
val AuthInputBackground = Color(0xFFFFFFFF)

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onAccountPending: (email: String, password: String) -> Unit = { _, _ -> },
    onAccountInactive: (email: String, reason: String) -> Unit = { _, _ -> }
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AuthTextPrimary,
        unfocusedTextColor = AuthTextPrimary,
        focusedContainerColor = AuthInputBackground,
        unfocusedContainerColor = AuthInputBackground,
        focusedLabelColor = SolarGreen,
        unfocusedLabelColor = AuthTextSecondary,
        focusedBorderColor = SolarGreen,
        unfocusedBorderColor = Color(0xFFCBD5E1),
        focusedLeadingIconColor = SolarGreen,
        unfocusedLeadingIconColor = AuthTextSecondary
    )

    AuthScaffold(scrollState = scrollState) {
        AuthHeading("Welcome back", "Sign in to manage your solar energy,\nbookings, and grid connections.")
        Spacer(Modifier.height(28.dp))
        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description, tint = AuthTextSecondary)
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter both email and password."
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = authRepository.login(email.trim(), password)
                    isLoading = false
                    result.onSuccess {
                        onLoginSuccess()
                    }.onFailure { error ->
                        when {
                            error is AccountPendingApprovalException ||
                            error is AccountInactiveException ||
                            error.message?.contains("pending", ignoreCase = true) == true ||
                            error.message?.contains("inactive", ignoreCase = true) == true ||
                            error.message?.contains("deactivat", ignoreCase = true) == true -> {
                                onAccountPending(email, password)
                            }
                            else -> {
                                errorMessage = error.message ?: "Login failed."
                            }
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SolarGreen,
                disabledContainerColor = SolarGreen.copy(alpha = 0.85f),
                contentColor = Color.White,
                disabledContentColor = Color.White
            )
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Logging in...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("New to SolarGridX? Create an account", color = SolarGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegisterSuccess: (email: String, password: String, nic: String, name: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var nic by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AuthTextPrimary,
        unfocusedTextColor = AuthTextPrimary,
        focusedContainerColor = AuthInputBackground,
        unfocusedContainerColor = AuthInputBackground,
        focusedLabelColor = SolarGreen,
        unfocusedLabelColor = AuthTextSecondary,
        focusedBorderColor = SolarGreen,
        unfocusedBorderColor = Color(0xFFCBD5E1),
        focusedLeadingIconColor = SolarGreen,
        unfocusedLeadingIconColor = AuthTextSecondary
    )

    AuthScaffold(scrollState = scrollState) {
        AuthHeading("Join the energy community", "Create your prosumer account and\nmake more of your solar energy.")
        Spacer(Modifier.height(24.dp))
        Surface(color = SolarGreenLight, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = SolarGreen)
                Text("Your account will be reviewed before you can sign in.", fontSize = 13.sp, color = SolarGreen)
            }
        }
        Spacer(Modifier.height(18.dp))
        errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedTextField(
            value = nic,
            onValueChange = { nic = it; errorMessage = null },
            label = { Text("NIC Number") },
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMessage = null },
            label = { Text("Full Name") },
            leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password (min 8 chars)") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description, tint = AuthTextSecondary)
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = {
                if (nic.isBlank() || name.isBlank() || email.isBlank() || password.length < 8) {
                    errorMessage = "Please fill in all fields (password must be at least 8 chars)."
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = authRepository.register(nic.trim(), name.trim(), email.trim(), password)
                    isLoading = false
                    result.onSuccess {
                        onRegisterSuccess(email.trim(), password, nic.trim(), name.trim())
                    }.onFailure {
                        errorMessage = it.message ?: "Registration failed."
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SolarGreen,
                disabledContainerColor = SolarGreen.copy(alpha = 0.85f),
                contentColor = Color.White,
                disabledContentColor = Color.White
            )
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Creating Account...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Text("Create account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already registered? Log In", color = SolarGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AuthScaffold(scrollState: androidx.compose.foundation.ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFECFDF5), Color(0xFFF8FAFC))))
            .safeDrawingPadding().imePadding().verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.solargridx_mark), "SolarGridX logo", Modifier.size(52.dp))
            Column {
                Text("SolarGridX", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SolarGreen)
                Text("SOLAR ENERGY. CONNECTED.", fontSize = 10.sp, letterSpacing = 1.sp, color = AuthTextSecondary)
            }
        }
        Spacer(Modifier.height(28.dp))
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 3.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
        Spacer(Modifier.height(24.dp))
        Text("A brighter future starts with your energy.", color = AuthTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun AuthHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AuthTextPrimary)
        Text(subtitle, fontSize = 14.sp, color = AuthTextSecondary, lineHeight = 21.sp)
    }
}

@Composable
fun PendingApprovalScreen(
    email: String,
    password: String = "",
    nic: String = "",
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var isChecking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    AuthScaffold(scrollState = scrollState) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFFFEF3C7), shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.HourglassTop,
                contentDescription = "Pending Approval",
                tint = Color(0xFFD97706),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pending admin approval",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AuthTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Registration received. Your account is awaiting review.",
            fontSize = 14.sp,
            color = AuthTextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Status", fontSize = 12.sp, color = AuthTextSecondary)
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "PENDING APPROVAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (email.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Email", fontSize = 12.sp, color = AuthTextSecondary)
                        Text(email, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AuthTextPrimary)
                    }
                }

                if (nic.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("NIC", fontSize = 12.sp, color = AuthTextSecondary)
                        Text(nic, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AuthTextPrimary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Role", fontSize = 12.sp, color = AuthTextSecondary)
                    Text("Prosumer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SolarGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "What happens next?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SolarGreen
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. An administrator reviews your prosumer registration.\n2. Once approved, your account will be activated.\n3. Tap 'Check approval status' below to sign in after approval.",
                    fontSize = 12.sp,
                    color = AuthTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else Color(0xFFFEF3C7)
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
                Text(
                    text = msg,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFB45309),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    onNavigateToLogin()
                    return@Button
                }
                isChecking = true
                statusMessage = null
                scope.launch {
                    val result = authRepository.login(email, password)
                    isChecking = false
                    result.onSuccess {
                        onLoginSuccess()
                    }.onFailure { error ->
                        if (error is AccountPendingApprovalException ||
                            error is AccountInactiveException ||
                            error.message?.contains("pending", ignoreCase = true) == true ||
                            error.message?.contains("inactive", ignoreCase = true) == true ||
                            error.message?.contains("deactivat", ignoreCase = true) == true) {
                            isError = false
                            statusMessage = "Your account is awaiting activation. An administrator must approve and activate it before you can sign in."
                        } else {
                            isError = true
                            statusMessage = error.message ?: "Authentication failed."
                        }
                    }
                }
            },
            enabled = !isChecking,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SolarGreen,
                disabledContainerColor = SolarGreen.copy(alpha = 0.85f),
                contentColor = Color.White,
                disabledContentColor = Color.White
            )
        ) {
            if (isChecking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Checking Approval...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check approval status", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Back to Log In", color = SolarGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AccountInactiveScreen(
    email: String,
    statusMessage: String = "",
    onNavigateToLogin: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFFFEE2E2), shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Account Inactive",
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Account Inactive",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AuthTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Access to the grid platform has been suspended",
            fontSize = 14.sp,
            color = AuthTextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Status", fontSize = 12.sp, color = AuthTextSecondary)
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "DEACTIVATED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (email.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Email", fontSize = 12.sp, color = AuthTextSecondary)
                        Text(email, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AuthTextPrimary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Role", fontSize = 12.sp, color = AuthTextSecondary)
                    Text("Prosumer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AuthTextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Why is my account inactive?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (statusMessage.isNotBlank()) statusMessage else "Your account has been deactivated by a Backoffice administrator or upon request. Login and energy booking privileges are currently disabled for this account.",
                    fontSize = 12.sp,
                    color = AuthTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SolarGreen)
        ) {
            Text("Back to Log In", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
