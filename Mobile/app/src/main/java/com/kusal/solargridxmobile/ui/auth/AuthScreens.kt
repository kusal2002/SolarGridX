package com.kusal.solargridxmobile.ui.auth

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
import com.kusal.solargridxmobile.data.repository.AccountPendingApprovalException
import com.kusal.solargridxmobile.data.repository.AuthRepository

val SolarGreen = Color(0xFF15803D)
val SolarGreenLight = Color(0xFFDCFCE7)
val AuthTextPrimary = Color(0xFF0F172A)
val AuthTextSecondary = Color(0xFF475569)
val AuthInputBackground = Color(0xFFFFFFFF)

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onAccountPending: (email: String, password: String) -> Unit = { _, _ -> }
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WbSunny,
            contentDescription = "SolarGridX",
            tint = SolarGreen,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SolarGridX",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = SolarGreen
        )

        Text(
            text = "Prosumer Energy Management",
            fontSize = 14.sp,
            color = AuthTextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

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
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description, tint = AuthTextSecondary)
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
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
                    val result = authRepository.login(email, password)
                    isLoading = false
                    result.onSuccess {
                        onLoginSuccess()
                    }.onFailure { error ->
                        if (error is AccountPendingApprovalException || error.message?.contains("pending", ignoreCase = true) == true) {
                            onAccountPending(email, password)
                        } else {
                            errorMessage = error.message ?: "Login failed."
                        }
                    }
                }
            },
            enabled = !isLoading,
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
                Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("Don't have an account? Register as Prosumer", color = SolarGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegisterSuccess: (email: String, password: String, nic: String, name: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var nic by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
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

    var showSuccessDialog by remember { mutableStateOf(false) }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onRegisterSuccess(email, password, nic, name)
            },
            title = {
                Text(
                    text = "Registration Submitted",
                    fontWeight = FontWeight.Bold,
                    color = SolarGreen
                )
            },
            text = {
                Text(
                    text = "Your account has been created successfully!\n\nAs per system policy, your account is in 'Pending' status and requires Backoffice administrator approval before you can log in.",
                    fontSize = 14.sp,
                    color = AuthTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onRegisterSuccess(email, password, nic, name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SolarGreen)
                ) {
                    Text("View Approval Status", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateToLogin()
                    }
                ) {
                    Text("Go to Login", color = AuthTextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Create Prosumer Account",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SolarGreen
        )

        Text(
            text = "Register to book and reserve solar energy slots",
            fontSize = 13.sp,
            color = AuthTextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

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
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMessage = null },
            label = { Text("Full Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email Address") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password (min 8 chars)") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description, tint = AuthTextSecondary)
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
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
                    val result = authRepository.register(nic, name, email, password)
                    isLoading = false
                    result.onSuccess {
                        showSuccessDialog = true
                    }.onFailure {
                        errorMessage = it.message ?: "Registration failed."
                    }
                }
            },
            enabled = !isLoading,
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
                Text("Register", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already registered? Log In", color = SolarGreen, fontWeight = FontWeight.SemiBold)
        }
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
                .background(Color(0xFFFEF3C7), shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Pending Approval",
                tint = Color(0xFFD97706),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Approval Pending",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AuthTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Your Prosumer account is under review",
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
                            text = "PENDING REVIEW",
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
                    text = "1. An administrator reviews your prosumer registration.\n2. Once approved, your account will be activated.\n3. Click 'Check Status Now' below to log in once activated.",
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
                        if (error is AccountPendingApprovalException || error.message?.contains("pending", ignoreCase = true) == true) {
                            isError = false
                            statusMessage = "Account is still pending administrator approval. Please check again later."
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
                Text("Check Status Now", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Back to Log In", color = SolarGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}
