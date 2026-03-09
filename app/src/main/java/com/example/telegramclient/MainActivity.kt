package com.example.telegramclient

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
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
    private val isFullscreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TelegramApp(viewModel, isInPipMode.value, isFullscreen.value,
                            onFullscreenToggle = { isFullscreen.value = !isFullscreen.value },
                            onPipRequest = { enterPip() }
                        )
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
        if (isInPictureInPictureMode) {
            isFullscreen.value = false
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramApp(viewModel: TelegramViewModel, isInPip: Boolean, isFullscreen: Boolean, onFullscreenToggle: () -> Unit, onPipRequest: () -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedVideoFileId by remember { mutableStateOf<Int?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }

    if (selectedVideoFileId != null) {
        VideoPlayerScreen(viewModel, selectedVideoFileId!!, isInPip, isFullscreen,
            onFullscreenToggle = onFullscreenToggle,
            onPipRequest = onPipRequest,
            onBack = {
                selectedVideoFileId = null
                viewModel.isPlaybackActive.value = false
            }
        )
    } else if (selectedChatId != null) {
        ChatScreen(viewModel, selectedChatId!!,
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
            is AuthState.LoggedIn -> {
                LoggedInMainScreen(viewModel, currentTab, onTabChange = { currentTab = it },
                    onGroupClick = { selectedChatId = it })
            }
            is AuthState.Error -> ErrorScreen(state.message) { 
                // Basic way to reset
            }
        }
    }
}

@Composable
fun GroupsScreen(viewModel: TelegramViewModel, onGroupClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    if (isLoading && chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(chats, key = { it.id }) { chat ->
                val avatarPath = chat.photo?.small?.id?.let { downloadedFiles[it] }

                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            viewModel.loadChatHistory(chat.id)
                            onGroupClick(chat.id)
                        },
                    leadingContent = {
                        if (avatarPath != null) {
                            AsyncImage(
                                model = avatarPath,
                                contentDescription = "Group Avatar",
                                modifier = Modifier.size(56.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(56.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        chat.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    },
                    headlineContent = {
                        Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    },
                    supportingContent = {
                        val lastMsgText = when (val content = chat.lastMessage?.content) {
                            is TdApi.MessageText -> content.text.text
                            is TdApi.MessageVideo -> "🎥 Vídeo"
                            is TdApi.MessagePhoto -> "🖼️ Foto"
                            is TdApi.MessageAnimation -> "GIF"
                            is TdApi.MessageAudio -> "🎵 Áudio"
                            is TdApi.MessageVoiceNote -> "🎤 Mensagem de voz"
                            is TdApi.MessageDocument -> "📄 Documento"
                            else -> "Mensagem"
                        }
                        Text(
                            lastMsgText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chat.lastMessage?.let { lastMsg ->
                                Text(
                                    text = android.text.format.DateFormat.format("HH:mm", lastMsg.date.toLong() * 1000).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (chat.unreadCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${chat.unreadCount}", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggedInMainScreen(viewModel: TelegramViewModel, currentTab: Int, onTabChange: (Int) -> Unit, onGroupClick: (Long) -> Unit) {
    Scaffold(
        topBar = {
            val title = if (currentTab == 0) "Conversas" else "Meu Perfil"
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    selected = currentTab == 0,
                    onClick = { onTabChange(0) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = currentTab == 1,
                    onClick = { onTabChange(1) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (currentTab == 0) {
                GroupsScreen(viewModel, onGroupClick)
            } else {
                SettingsScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: TelegramViewModel, chatId: Long, onBack: () -> Unit, onVideoClick: (Int) -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    var onlyVideos by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val chat = viewModel.chats.collectAsStateWithLifecycle().value.find { it.id == chatId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatarPath = chat?.photo?.small?.id?.let { downloadedFiles[it] }
                        if (avatarPath != null) {
                            AsyncImage(
                                model = avatarPath,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column {
                            Text(chat?.title ?: "Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (chat != null && chat.unreadCount > 0) {
                                Text("${chat.unreadCount} mensagens não lidas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    FilterChip(
                        selected = onlyVideos,
                        onClick = {
                            onlyVideos = !onlyVideos
                            if (onlyVideos) viewModel.loadVideos(chatId) else viewModel.loadChatHistory(chatId)
                        },
                        label = { Text("Vídeos") },
                        leadingIcon = if (onlyVideos) {
                            { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Mensagem") },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(chatId, messageText)
                                messageText = ""
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading && messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(message, downloadedFiles, onVideoClick)
                }

                if (messages.isEmpty() && !isLoading) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhuma mensagem encontrada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: TdApi.Message, downloadedFiles: Map<Int, String>, onVideoClick: (Int) -> Unit) {
    val isOutgoing = message.isOutgoing

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                when (val content = message.content) {
                    is TdApi.MessageText -> {
                        Text(content.text.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    is TdApi.MessageVideo -> {
                        VideoMessageContent(content.video, downloadedFiles, onVideoClick)
                    }
                    is TdApi.MessagePhoto -> {
                        PhotoMessageContent(content.photo, downloadedFiles)
                    }
                    is TdApi.MessageDocument -> {
                        if (content.document.mimeType.startsWith("video/")) {
                            VideoMessageContent(content.document, downloadedFiles, onVideoClick)
                        } else {
                            Text("📄 ${content.document.fileName}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        Text("Mensagem não suportada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Text(
                    text = android.text.format.DateFormat.format("HH:mm", message.date.toLong() * 1000).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun VideoMessageContent(video: Any, downloadedFiles: Map<Int, String>, onVideoClick: (Int) -> Unit) {
    val (fileId, thumbnail, fileName) = when (video) {
        is TdApi.Video -> Triple(video.video.id, video.thumbnail, video.fileName)
        is TdApi.Document -> Triple(video.document.id, video.thumbnail, video.fileName)
        else -> Triple(0, null, "")
    }

    val thumbPath = thumbnail?.file?.id?.let { downloadedFiles[it] }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onVideoClick(fileId) },
        contentAlignment = Alignment.Center
    ) {
        if (thumbPath != null) {
            AsyncImage(
                model = thumbPath,
                contentDescription = fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)))
        }

        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.padding(8.dp))
        }

        if (video is TdApi.Video) {
            Text(
                text = "${video.duration}s",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PhotoMessageContent(photo: TdApi.Photo, downloadedFiles: Map<Int, String>) {
    val photoPath = photo.sizes.lastOrNull()?.photo?.id?.let { downloadedFiles[it] }

    if (photoPath != null) {
        AsyncImage(
            model = photoPath,
            contentDescription = "Photo",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: TelegramViewModel,
    fileId: Int,
    isInPip: Boolean,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onPipRequest: () -> Unit,
    onBack: () -> Unit
) {
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
            if (!isInPip && !isFullscreen) {
                TopAppBar(
                    title = { Text("Video Player", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onPipRequest) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                        }
                        IconButton(onClick = onFullscreenToggle) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White
                            )
                        }
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
        val actualPadding = if (isInPip || isFullscreen) PaddingValues(0.dp) else padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(actualPadding),
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Configuração TDLib", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Insira suas credenciais do Telegram obtidas em my.telegram.org",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = appId,
                onValueChange = { appId = it },
                label = { Text("App ID") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = apiHash,
                onValueChange = { apiHash = it },
                label = { Text("API Hash") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.initializeClient(appId, apiHash) },
                enabled = appId.isNotEmpty() && apiHash.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Inicializar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PhoneScreen(viewModel: TelegramViewModel) {
    var phone by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Seu Telefone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Por favor, confirme o código do seu país e insira seu número de telefone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Número de Telefone") },
                placeholder = { Text("+55 11 99999-9999") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submitPhone(phone) },
                enabled = phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enviar Código", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CodeScreen(viewModel: TelegramViewModel) {
    var code by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Verificação", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Enviamos um código para o seu Telegram em outro dispositivo ou via SMS.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de Verificação") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submitCode(code) },
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Verificar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Carregando...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Text(message)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun SettingsScreen(viewModel: TelegramViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()

    val user = (authState as? AuthState.LoggedIn)?.user
    if (user != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val avatarPath = user.profilePhoto?.small?.id?.let { downloadedFiles[it] }

            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                if (avatarPath != null) {
                    AsyncImage(
                        model = avatarPath,
                        contentDescription = "Foto de Perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.firstName.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "${user.firstName} ${user.lastName}".trim(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (user.phoneNumber != null) "+${user.phoneNumber}" else "Telefone não disponível",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileInfoItem(Icons.Default.Person, "Nome de usuário", user.usernames?.activeUsernames?.firstOrNull() ?: "Não definido")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ProfileInfoItem(Icons.Default.Settings, "Configurações da Conta", "Privacidade, Notificações...")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { /* Implement logout if needed */ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Sair da Conta", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
