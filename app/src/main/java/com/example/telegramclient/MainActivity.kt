package com.example.telegramclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {
    private val viewModel: TelegramViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    var selectedChatId by remember { mutableStateOf<Long?>(null) }

    if (selectedChatId != null) {
        VideosScreen(viewModel, selectedChatId!!) { selectedChatId = null }
    } else {
        when (val state = authState) {
            is AuthState.Initial, AuthState.EnterCredentials -> CredentialsScreen(viewModel)
            is AuthState.Loading -> LoadingScreen()
            is AuthState.EnterPhone -> PhoneScreen(viewModel)
            is AuthState.EnterCode -> CodeScreen(viewModel)
            is AuthState.EnterPassword -> ErrorScreen("Password required") { }
            is AuthState.LoggedIn -> GroupsScreen(viewModel) { selectedChatId = it }
            is AuthState.Error -> ErrorScreen(state.message) { }
        }
    }
}

@Composable
fun GroupsScreen(viewModel: TelegramViewModel, onGroupClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("Your Groups") })
        if (isLoading && chats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(chats) { chat ->
                    ListItem(
                        headlineContent = { Text(chat.title) },
                        modifier = Modifier.clickable { 
                            viewModel.loadVideos(chat.id)
                            onGroupClick(chat.id) 
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun VideosScreen(viewModel: TelegramViewModel, chatId: Long, onBack: () -> Unit) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Videos") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentAlignment = null)
                }
            }
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(videos) { message ->
                    val video = (message.content as? TdApi.MessageVideo)?.video
                    ListItem(
                        headlineContent = { Text(video?.fileName ?: "Unnamed Video") },
                        supportingContent = { Text("${(video?.duration ?: 0) / 60} min") }
                    )
                    HorizontalDivider()
                }
                if (videos.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No videos found in this group")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CredentialsScreen(viewModel: TelegramViewModel) {
    var appId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Telegram Setup", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("App ID") })
        OutlinedTextField(value = apiHash, onValueChange = { apiHash = it }, label = { Text("API Hash") })
        Button(onClick = { viewModel.initializeClient(appId, apiHash) }, enabled = appId.isNotEmpty() && apiHash.isNotEmpty()) { Text("Initialize") }
    }
}

@Composable
fun PhoneScreen(viewModel: TelegramViewModel) {
    var phone by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Phone Number", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("+123456789") })
        Button(onClick = { viewModel.submitPhone(phone) }) { Text("Send Code") }
    }
}

@Composable
fun CodeScreen(viewModel: TelegramViewModel) {
    var code by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Verification Code", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code") })
        Button(onClick = { viewModel.submitCode(code) }) { Text("Verify") }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Text(message)
    }
}
