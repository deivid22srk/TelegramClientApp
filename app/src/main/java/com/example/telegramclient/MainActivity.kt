package com.example.telegramclient

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {
    private val viewModel: TelegramViewModel by viewModels()
    private val isInPipMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TelegramApp(viewModel, isInPipMode.value)
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        if (viewModel.isPlaybackActive.value) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

@Composable
fun TelegramApp(viewModel: TelegramViewModel, isInPip: Boolean) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedVideoFileId by remember { mutableStateOf<Int?>(null) }

    if (selectedVideoFileId != null) {
        VideoPlayerScreen(viewModel, selectedVideoFileId!!, isInPip) {
            selectedVideoFileId = null
            viewModel.isPlaybackActive.value = false
        }
    } else if (selectedChatId != null) {
        VideosScreen(viewModel, selectedChatId!!, 
            onBack = { selectedChatId = null },
            onVideoClick = { selectedVideoFileId = it }
        )
    } else {
        when (val state = authState) {
            is AuthState.Initial -> LoadingScreen()
            is AuthState.EnterCredentials -> CredentialsScreen(viewModel)
            is AuthState.Loading -> LoadingScreen()
            is AuthState.EnterPhone -> PhoneScreen(viewModel)
            is AuthState.EnterCode -> CodeScreen(viewModel)
            is AuthState.EnterPassword -> ErrorScreen("Password required") { }
            is AuthState.LoggedIn -> GroupsScreen(viewModel) { selectedChatId = it }
            is AuthState.Error -> ErrorScreen(state.message) { 
                // Basic way to reset
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(viewModel: TelegramViewModel, onGroupClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Groups", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading && chats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chats) { chat ->
                    val avatarPath = chat.photo?.small?.id?.let { downloadedFiles[it] }

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.loadVideos(chat.id)
                            onGroupClick(chat.id)
                        },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (avatarPath != null) {
                                AsyncImage(
                                    model = avatarPath,
                                    contentDescription = "Group Avatar",
                                    modifier = Modifier.size(50.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(50.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(chat.title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (chat.unreadCount > 0) {
                                    Text("${chat.unreadCount} unread messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(viewModel: TelegramViewModel, chatId: Long, onBack: () -> Unit, onVideoClick: (Int) -> Unit) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Videos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(videos) { message ->
                    val video = when (val content = message.content) {
                        is TdApi.MessageVideo -> content.video
                        is TdApi.MessageDocument -> {
                            if (content.document.mimeType.startsWith("video/")) content.document else null
                        }
                        else -> null
                    }

                    if (video != null) {
                        val fileName = if (video is TdApi.Video) video.fileName else (video as TdApi.Document).fileName
                        val fileId = if (video is TdApi.Video) video.video.id else (video as TdApi.Document).document.id

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onVideoClick(fileId) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = fileName ?: "Unnamed Video",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(viewModel: TelegramViewModel, fileId: Int, isInPip: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val exoPlayer = remember {
        viewModel.isPlaybackActive.value = true
        val factory = TdLibDataSourceFactory(viewModel.client!!, fileId)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(factory))
            .build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri("tdlib://file/$fileId")
                    .setMimeType("video/mp4") // Defaulting to mp4 for TDLib streaming
                    .build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            if (!isInPip) {
                TopAppBar(
                    title = { Text("Video Player", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Resize", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isInPip) PaddingValues(0.dp) else padding),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = !isInPip
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = {
                    it.resizeMode = resizeMode
                    it.useController = !isInPip
                },
                modifier = Modifier.fillMaxSize()
            )
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
        Button(onClick = onRetry) { Text("Retry") }
    }
}
