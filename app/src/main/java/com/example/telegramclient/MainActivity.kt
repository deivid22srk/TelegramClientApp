package com.example.telegramclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    private val viewModel: TelegramViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Explicitly provide LocalLifecycleOwner to prevent the "not present" crash
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TelegramApp(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun TelegramApp(viewModel: TelegramViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        is AuthState.Initial, AuthState.EnterCredentials -> CredentialsScreen(viewModel)
        is AuthState.Loading -> LoadingScreen()
        is AuthState.EnterPhone -> PhoneScreen(viewModel)
        is AuthState.EnterCode -> CodeScreen(viewModel)
        is AuthState.EnterPassword -> ErrorScreen("Password login not supported in this demo") { }
        is AuthState.LoggedIn -> LoggedInScreen(state.user)
        is AuthState.Error -> ErrorScreen(state.message) { }
    }
}

@Composable
fun CredentialsScreen(viewModel: TelegramViewModel) {
    var appId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Telegram Setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = appId,
            onValueChange = { appId = it },
            label = { Text("App ID") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiHash,
            onValueChange = { apiHash = it },
            label = { Text("API Hash") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.initializeClient(appId, apiHash) },
            enabled = appId.isNotEmpty() && apiHash.isNotEmpty()
        ) {
            Text("Initialize")
        }
    }
}

@Composable
fun PhoneScreen(viewModel: TelegramViewModel) {
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Phone Number", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (with code)") },
            placeholder = { Text("+123456789") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.submitPhone(phone) }) {
            Text("Send Code")
        }
    }
}

@Composable
fun CodeScreen(viewModel: TelegramViewModel) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Verification Code", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Code") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.submitCode(code) }) {
            Text("Verify")
        }
    }
}

@Composable
fun LoggedInScreen(user: org.drinkless.tdlib.TdApi.User) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Welcome, ${user.firstName} ${user.lastName}",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
