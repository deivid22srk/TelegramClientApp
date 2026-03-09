package com.example.telegramclient

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                TelegramTheme(viewModel) {
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
fun EditProfileScreen(viewModel: TelegramViewModel, onBack: () -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val user = (authState as? AuthState.LoggedIn)?.user ?: return

    var firstName by remember { mutableStateOf(user.firstName) }
    var lastName by remember { mutableStateOf(user.lastName) }
    var username by remember { mutableStateOf(user.usernames?.activeUsernames?.firstOrNull() ?: "") }
    var bio by remember { mutableStateOf("") }

    LaunchedEffect(user.id) {
        viewModel.client?.send(TdApi.GetUserFullInfo(user.id)) { result ->
            if (result is TdApi.UserFullInfo) {
                bio = result.bio?.text ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.updateProfile(firstName, lastName)
                        viewModel.updateUsername(username)
                        viewModel.updateBio(bio)
                        onBack()
                    }) {
                        Text("Salvar", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (user.profilePhoto != null) {
                Button(
                    onClick = { viewModel.deleteProfilePhoto(user.profilePhoto!!.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text("Remover Foto de Perfil")
                }
            }

            OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Sobrenome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Nome de usuário") }, modifier = Modifier.fillMaxWidth(), prefix = { Text("@") })
            OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

            Text("Você pode adicionar uma bio opcional ao seu perfil.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun TelegramTheme(viewModel: TelegramViewModel, content: @Composable () -> Unit) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    val isDark = when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    LaunchedEffect(isDark) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    val colorScheme = when (colorTheme) {
        "Oceano" -> if (isDark) {
            darkColorScheme(primary = Color(0xFF80D8FF), secondary = Color(0xFF40C4FF), tertiary = Color(0xFF00B0FF))
        } else {
            lightColorScheme(primary = Color(0xFF0091EA), secondary = Color(0xFF00B0FF), tertiary = Color(0xFF40C4FF))
        }
        "Floresta" -> if (isDark) {
            darkColorScheme(primary = Color(0xFFB9F6CA), secondary = Color(0xFF69F0AE), tertiary = Color(0xFF00E676))
        } else {
            lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFF43A047), tertiary = Color(0xFF66BB6A))
        }
        else -> if (isDark) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramApp(viewModel: TelegramViewModel, isInPip: Boolean, isFullscreen: Boolean, onFullscreenToggle: () -> Unit, onPipRequest: () -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedVideoFileId by remember { mutableStateOf<Int?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var isCloudDriveOpen by remember { mutableStateOf(false) }

    if (isEditingProfile) {
        BackHandler { isEditingProfile = false }
        EditProfileScreen(viewModel, onBack = { isEditingProfile = false })
    } else if (selectedVideoFileId != null) {
        BackHandler {
            selectedVideoFileId = null
            viewModel.isPlaybackActive.value = false
        }
        VideoPlayerScreen(viewModel, selectedVideoFileId!!, isInPip, isFullscreen,
            onFullscreenToggle = onFullscreenToggle,
            onPipRequest = onPipRequest,
            onBack = {
                selectedVideoFileId = null
                viewModel.isPlaybackActive.value = false
            }
        )
    } else if (isCloudDriveOpen) {
        BackHandler { isCloudDriveOpen = false }
        CloudDriveScreen(viewModel, onBack = { isCloudDriveOpen = false })
    } else if (selectedChatId != null) {
        BackHandler { selectedChatId = null }
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
                    onGroupClick = { selectedChatId = it },
                    onEditProfile = { isEditingProfile = true },
                    onCloudDrive = { isCloudDriveOpen = true })
            }
            is AuthState.Error -> ErrorScreen(state.message) { }
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
                ChatListItem(
                    chat = chat,
                    avatarPath = chat.photo?.small?.id?.let { downloadedFiles[it] },
                    onClick = {
                        viewModel.loadChatHistory(chat.id)
                        onGroupClick(chat.id)
                    }
                )
            }
        }
    }
}

@Composable
fun ChatListItem(chat: TdApi.Chat, avatarPath: String?, onClick: () -> Unit) {
    val lastMsgText = remember(chat.lastMessage?.content) {
        when (val content = chat.lastMessage?.content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessageVideo -> "🎥 Vídeo"
            is TdApi.MessagePhoto -> "🖼️ Foto"
            is TdApi.MessageAnimation -> "GIF"
            is TdApi.MessageAudio -> "🎵 Áudio"
            is TdApi.MessageVoiceNote -> "🎤 Mensagem de voz"
            is TdApi.MessageDocument -> "📄 Documento"
            else -> "Mensagem"
        }
    }
    val timeText = remember(chat.lastMessage?.date) {
        chat.lastMessage?.let { lastMsg ->
            android.text.format.DateFormat.format("HH:mm", lastMsg.date.toLong() * 1000).toString()
        } ?: ""
    }

    ListItem(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        leadingContent = {
            if (avatarPath != null) {
                AsyncImage(model = avatarPath, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Surface(modifier = Modifier.size(56.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(chat.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        },
        headlineContent = { Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1) },
        supportingContent = {
            Text(lastMsgText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (timeText.isNotEmpty()) {
                    Text(text = timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (chat.unreadCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("${chat.unreadCount}", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggedInMainScreen(viewModel: TelegramViewModel, currentTab: Int, onTabChange: (Int) -> Unit, onGroupClick: (Long) -> Unit, onEditProfile: () -> Unit, onCloudDrive: () -> Unit) {
    Scaffold(
        topBar = {
            val title = if (currentTab == 0) "Conversas" else "Meu Perfil"
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground),
                actions = { if (currentTab == 1) { IconButton(onClick = onEditProfile) { Icon(Icons.Default.Edit, contentDescription = "Editar Perfil") } } }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                NavigationBarItem(icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") }, label = { Text("Chats") }, selected = currentTab == 0, onClick = { onTabChange(0) })
                NavigationBarItem(icon = { Icon(Icons.Default.Person, contentDescription = "Profile") }, label = { Text("Profile") }, selected = currentTab == 1, onClick = { onTabChange(1) })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (currentTab == 0) GroupsScreen(viewModel, onGroupClick) else SettingsScreen(viewModel, onEditClick = onEditProfile, onCloudDrive = onCloudDrive)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: TelegramViewModel, chatId: Long, onBack: () -> Unit, onVideoClick: (Int) -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val pinnedMessage by viewModel.pinnedMessage.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
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
                            AsyncImage(model = avatarPath, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column {
                            Text(chat?.title ?: "Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (chat != null && chat.unreadCount > 0) { Text("${chat.unreadCount} mensagens não lidas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = { FilterChip(selected = onlyVideos, onClick = { onlyVideos = !onlyVideos; if (onlyVideos) viewModel.loadVideos(chatId) else viewModel.loadChatHistory(chatId) }, label = { Text("Vídeos") }, leadingIcon = if (onlyVideos) { { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null, modifier = Modifier.padding(end = 8.dp)) }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = messageText, onValueChange = { messageText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Mensagem") }, maxLines = 5, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(onClick = { if (messageText.isNotBlank()) { viewModel.sendMessage(chatId, messageText); messageText = "" } }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp), modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp)) }
                }
            }
        }
    ) { padding ->
        if (isLoading && messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (pinnedMessage != null) {
                    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 2.dp) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Mensagem Fixada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                val pinnedText = when (val content = pinnedMessage!!.content) {
                                    is TdApi.MessageText -> content.text.text
                                    is TdApi.MessageVideo -> "Vídeo"
                                    is TdApi.MessagePhoto -> "Foto"
                                    else -> "Mídia"
                                }
                                Text(pinnedText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), reverseLayout = true) {
                    items(messages, key = { it.id }) { message ->
                        ChatMessageItem(viewModel, message, downloadedFiles, users, onVideoClick)

                        // Check if it's the last item in the list (the oldest message) to load more
                        if (message.id == messages.lastOrNull()?.id && !isLoading) {
                            LaunchedEffect(message.id) {
                                viewModel.loadChatHistory(chatId, message.id)
                            }
                        }
                    }
                    if (messages.isEmpty() && !isLoading) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma mensagem encontrada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) } } }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(viewModel: TelegramViewModel, message: TdApi.Message, downloadedFiles: Map<Int, String>, users: Map<Long, TdApi.User>, onVideoClick: (Int) -> Unit) {
    val isOutgoing = message.isOutgoing
    val senderId = (message.senderId as? TdApi.MessageSenderUser)?.userId
    val sender = senderId?.let { users[it] }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!isOutgoing) {
            val avatarPath = sender?.profilePhoto?.small?.id?.let { downloadedFiles[it] }
            if (avatarPath != null) {
                AsyncImage(model = avatarPath, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Surface(modifier = Modifier.size(32.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(sender?.firstName?.take(1) ?: "?", style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isOutgoing) 16.dp else 4.dp, bottomEnd = if (isOutgoing) 4.dp else 16.dp), modifier = Modifier.widthIn(max = 280.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!isOutgoing && sender != null) { Text(text = "${sender.firstName} ${sender.lastName}".trim(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp)) }
                when (val content = message.content) {
                    is TdApi.MessageText -> {
                        ClickableFormattedText(content.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    is TdApi.MessageVideo -> {
                        VideoMessageContent(content.video, downloadedFiles, onVideoClick)
                        if (content.caption.text.isNotEmpty()) {
                            ClickableFormattedText(content.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    is TdApi.MessagePhoto -> {
                        PhotoMessageContent(content.photo, downloadedFiles)
                        if (content.caption.text.isNotEmpty()) {
                            ClickableFormattedText(content.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    is TdApi.MessageDocument -> {
                        if (content.document.mimeType.startsWith("video/")) {
                            VideoMessageContent(content.document, downloadedFiles, onVideoClick)
                        } else {
                            DocumentMessageContent(viewModel, content.document, downloadedFiles)
                        }
                        if (content.caption.text.isNotEmpty()) {
                            ClickableFormattedText(content.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    else -> { Text("Mensagem não suportada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                }
                Text(text = android.text.format.DateFormat.format("HH:mm", message.date.toLong() * 1000).toString(), style = MaterialTheme.typography.labelSmall, color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun VideoMessageContent(video: Any, downloadedFiles: Map<Int, String>, onVideoClick: (Int) -> Unit) {
    val (fileId, thumbnail, fileName, duration) = when (video) {
        is TdApi.Video -> Triple(video.video.id, video.thumbnail, video.fileName).let { (a, b, c) -> Quadruple(a, b, c, video.duration) }
        is TdApi.Document -> Triple(video.document.id, video.thumbnail, video.fileName).let { (a, b, c) -> Quadruple(a, b, c, 0) }
        else -> Quadruple(0, null, "", 0)
    }
    val thumbPath = thumbnail?.file?.id?.let { downloadedFiles[it] }
    Card(
        modifier = Modifier.fillMaxWidth().height(240.dp).clickable { onVideoClick(fileId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (thumbPath != null) {
                AsyncImage(model = thumbPath, contentDescription = fileName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Surface(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.padding(12.dp).size(32.dp))
            }
            if (duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(text = "${duration / 60}:${(duration % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
    }
}

data class Quadruple<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun PhotoMessageContent(photo: TdApi.Photo, downloadedFiles: Map<Int, String>) {
    val photoPath = photo.sizes.lastOrNull()?.photo?.id?.let { downloadedFiles[it] }
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (photoPath != null) {
            AsyncImage(model = photoPath, contentDescription = "Photo", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
fun DocumentMessageContent(viewModel: TelegramViewModel, document: TdApi.Document, downloadedFiles: Map<Int, String>) {
    val isDownloaded = downloadedFiles.containsKey(document.document.id)
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).clickable { if (!isDownloaded) viewModel.downloadFile(document.document.id, document.fileName) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (isDownloaded) Icons.Default.Description else Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(document.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${document.document.expectedSize / 1024} KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(viewModel: TelegramViewModel, fileId: Int, isInPip: Boolean, isFullscreen: Boolean, onFullscreenToggle: () -> Unit, onPipRequest: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isFullscreen) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val exoPlayer = remember {
        viewModel.isPlaybackActive.value = true
        val factory = TdLibDataSourceFactory(viewModel.client!!, fileId)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(factory)).build().apply {
            val mediaItem = MediaItem.Builder().setUri("tdlib://file/$fileId").build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlayingChanged: Boolean) { isPlaying = isPlayingChanged }
                override fun onPlaybackStateChanged(state: Int) { if (state == androidx.media3.common.Player.STATE_READY) { duration = this@apply.duration } }
            })
        }
    }
    LaunchedEffect(exoPlayer) { while (true) { currentPosition = exoPlayer.currentPosition; kotlinx.coroutines.delay(500) } }
    DisposableEffect(lifecycleOwner) { onDispose { exoPlayer.release() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls }) {
        AndroidView(factory = { PlayerView(context).apply { player = exoPlayer; useController = false; setBackgroundColor(android.graphics.Color.BLACK) } }, update = { it.resizeMode = resizeMode }, modifier = Modifier.fillMaxSize())
        if (showControls && !isInPip) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
                    Spacer(modifier = Modifier.width(8.dp)); Text("Reproduzindo Vídeo", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onPipRequest) { Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White) }
                    IconButton(onClick = { resizeMode = when (resizeMode) { AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL; AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT } }) { Icon(Icons.Default.AspectRatio, contentDescription = "Redimensionar", tint = Color.White) }
                    IconButton(onClick = onFullscreenToggle) { Icon(imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Tela Cheia", tint = Color.White) }
                }
                Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(onClick = { exoPlayer.seekTo(currentPosition - 10000) }) { Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(48.dp)) }
                    Surface(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), contentColor = Color.White) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproduzir", modifier = Modifier.padding(16.dp).size(48.dp)) }
                    IconButton(onClick = { exoPlayer.seekTo(currentPosition + 10000) }) { Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(48.dp)) }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp).align(Alignment.BottomCenter)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelSmall); Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    Slider(value = currentPosition.toFloat(), onValueChange = { exoPlayer.seekTo(it.toLong()) }, valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f), colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

fun formatTime(milliseconds: Long): String { val totalSeconds = milliseconds / 1000; val minutes = totalSeconds / 60; val seconds = totalSeconds % 60; return "%02d:%02d".format(minutes, seconds) }
@Composable
fun CredentialsScreen(viewModel: TelegramViewModel) {
    var appId by remember { mutableStateOf("") }; var apiHash by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary); Text("Configuração TDLib", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Insira suas credenciais do Telegram obtidas em my.telegram.org", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("App ID") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)); OutlinedTextField(value = apiHash, onValueChange = { apiHash = it }, label = { Text("API Hash") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)); Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { viewModel.initializeClient(appId, apiHash) }, enabled = appId.isNotEmpty() && apiHash.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Inicializar", fontWeight = FontWeight.Bold) }
        }
    }
}
@Composable
fun PhoneScreen(viewModel: TelegramViewModel) {
    var phone by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Seu Telefone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Por favor, confirme o código do seu país e insira seu número de telefone.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Número de Telefone") }, placeholder = { Text("+55 11 99999-9999") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)); Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { viewModel.submitPhone(phone) }, enabled = phone.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Enviar Código", fontWeight = FontWeight.Bold) }
        }
    }
}
@Composable
fun CodeScreen(viewModel: TelegramViewModel) {
    var code by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Verificação", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Enviamos um código para o seu Telegram em outro dispositivo ou via SMS.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código de Verificação") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)); Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { viewModel.submitCode(code) }, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text("Verificar", fontWeight = FontWeight.Bold) }
        }
    }
}
@Composable
fun LoadingScreen() { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(strokeWidth = 3.dp); Spacer(modifier = Modifier.height(16.dp)); Text("Carregando...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) } } }
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) { Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error); Text(message); Button(onClick = onRetry) { Text("Retry") } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TelegramViewModel, onEditClick: () -> Unit, onCloudDrive: () -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val user = (authState as? AuthState.LoggedIn)?.user

    var currentSubScreen by remember { mutableStateOf("Main") }

    if (user != null) {
        when (currentSubScreen) {
            "Main" -> SettingsMainScreen(user, downloadedFiles, onEditClick, onCloudDrive, onNavigate = { currentSubScreen = it })
            "Appearance" -> AppearanceSettingsScreen(viewModel, onBack = { currentSubScreen = "Main" })
            "Downloads" -> DownloadSettingsScreen(viewModel, onBack = { currentSubScreen = "Main" })
        }
    }
}

@Composable
fun SettingsMainScreen(user: TdApi.User, downloadedFiles: Map<Int, String>, onEditClick: () -> Unit, onCloudDrive: () -> Unit, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val avatarPath = user.profilePhoto?.small?.id?.let { downloadedFiles[it] }
        Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp) { if (avatarPath != null) { AsyncImage(model = avatarPath, contentDescription = "Foto de Perfil", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } else { Box(contentAlignment = Alignment.Center) { Text(text = user.firstName.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) } } }
        Spacer(modifier = Modifier.height(24.dp)); Text(text = "${user.firstName} ${user.lastName}".trim(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(text = if (user.phoneNumber != null) "+${user.phoneNumber}" else "Telefone não disponível", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(48.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsNavigationItem(Icons.Default.Settings, "Aparência", "Tema, modo escuro e cores") { onNavigate("Appearance") }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                SettingsNavigationItem(Icons.Default.Download, "Downloads", "Pasta de destino e progresso") { onNavigate("Downloads") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileInfoItem(Icons.Default.Person, "Nome de usuário", user.usernames?.activeUsernames?.firstOrNull() ?: "Não definido")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth().clickable { onCloudDrive() }) {
                    ProfileInfoItem(Icons.Default.Cloud, "Cloud Drive", "Meus arquivos, pastas e backup")
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { /* Logout implementation */ },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        ) {
            Text("Sair da Conta", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsNavigationItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(viewModel: TelegramViewModel, onBack: () -> Unit) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aparência") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("Modo Escuro", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sistema", "Claro", "Escuro").forEachIndexed { index, name ->
                        FilterChip(selected = darkMode == index, onClick = { viewModel.updateDarkMode(index) }, label = { Text(name) })
                    }
                }
            }
            Column {
                Text("Tema de Cores", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Padrão", "Oceano", "Floresta").forEach { name ->
                        FilterChip(selected = colorTheme == name, onClick = { viewModel.updateColorTheme(name) }, label = { Text(name) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(viewModel: TelegramViewModel, onBack: () -> Unit) {
    val downloadPath by viewModel.downloadPath.collectAsStateWithLifecycle()
    var showPathInput by remember { mutableStateOf(false) }
    var tempPath by remember { mutableStateOf(downloadPath ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Configurações de Armazenamento", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            ListItem(
                modifier = Modifier.clickable { showPathInput = true },
                headlineContent = { Text("Pasta de Downloads") },
                supportingContent = { Text(downloadPath ?: "Padrão do Sistema") },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) }
            )

            if (showPathInput) {
                AlertDialog(
                    onDismissRequest = { showPathInput = false },
                    title = { Text("Definir Pasta de Downloads") },
                    text = {
                        OutlinedTextField(value = tempPath, onValueChange = { tempPath = it }, label = { Text("Caminho absoluto") }, placeholder = { Text("/storage/emulated/0/Download") })
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.updateDownloadPath(tempPath); showPathInput = false }) { Text("Salvar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPathInput = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp))
        Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline); Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDriveScreen(viewModel: TelegramViewModel, onBack: () -> Unit) {
    val cloudChatId by viewModel.cloudDriveChatId.collectAsStateWithLifecycle()
    val messages by viewModel.cloudDriveMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    var showChatSelector by remember { mutableStateOf(false) }

    // Logic to organize files by folders (using captions as metadata for folder paths)
    val filesByFolder = remember(messages) {
        val map = mutableMapOf<String, MutableList<TdApi.Message>>()
        messages.forEach { msg ->
            val caption = (msg.content as? TdApi.MessageDocument)?.caption?.text ?: ""
            val folder = if (caption.startsWith("/")) caption.substringBeforeLast("/", "/") else "/"
            map.getOrPut(folder) { mutableListOf() }.add(msg)
        }
        map
    }

    var currentFolderPath by remember { mutableStateOf("/") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { /* In a real app, convert URI to path and upload */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentFolderPath == "/") "Cloud Drive" else currentFolderPath.removePrefix("/"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (currentFolderPath == "/") onBack() else currentFolderPath = "/" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = { IconButton(onClick = { showChatSelector = true }) { Icon(Icons.Default.Settings, contentDescription = "Configurar Chat") } }
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallFloatingActionButton(onClick = { showNewFolderDialog = true }, containerColor = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Default.Folder, contentDescription = "Nova Pasta") }
                FloatingActionButton(onClick = { filePicker.launch("*/*") }) { Icon(Icons.Default.Add, contentDescription = "Adicionar Arquivo") }
            }
        }
    ) { padding ->
        if (cloudChatId == 0L) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Cloud, modifier = Modifier.size(64.dp), contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text("Nenhum chat selecionado para o Cloud Drive", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    Button(onClick = { showChatSelector = true }, modifier = Modifier.padding(top = 16.dp)) { Text("Selecionar Chat") }
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentFolderPath == "/") {
                    items(filesByFolder.keys.filter { it != "/" }.toList()) { folder ->
                        FolderItem(name = folder.removePrefix("/"), onClick = { currentFolderPath = folder })
                    }
                }
                items(filesByFolder[currentFolderPath] ?: emptyList()) { msg ->
                    val doc = (msg.content as TdApi.MessageDocument).document
                    FileItem(name = doc.fileName, mimeType = doc.mimeType)
                }
            }
        }

        if (showChatSelector) {
            AlertDialog(
                onDismissRequest = { showChatSelector = false },
                title = { Text("Selecionar Chat para Cloud Drive") },
                text = {
                    Column {
                        Text("Escolha um grupo para armazenar seus arquivos. Recomendamos criar um grupo privado apenas com você.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(chats) { chat ->
                                ListItem(
                                    modifier = Modifier.clickable { viewModel.setCloudDriveChatId(chat.id); showChatSelector = false },
                                    headlineContent = { Text(chat.title) },
                                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) }
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showChatSelector = false }) { Text("Fechar") } }
            )
        }

        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Nova Pasta") },
                text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Nome da pasta") }, singleLine = true) },
                confirmButton = { Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.sendMessage(cloudChatId, "/$newFolderName/ .init")
                    }
                    showNewFolderDialog = false; newFolderName = ""
                }) { Text("Criar") } },
                dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
fun FolderItem(name: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun FileItem(name: String, mimeType: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun ClickableFormattedText(formattedText: TdApi.FormattedText, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val annotatedString = buildAnnotatedString {
        append(formattedText.text)
        formattedText.entities.forEach { entity ->
            val start = entity.offset
            val end = entity.offset + entity.length
            if (start < formattedText.text.length && end <= formattedText.text.length) {
                when (val type = entity.type) {
                    is TdApi.TextEntityTypeUrl -> {
                        addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), start, end)
                        addStringAnnotation("URL", formattedText.text.substring(start, end), start, end)
                    }
                    is TdApi.TextEntityTypeTextUrl -> {
                        addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), start, end)
                        addStringAnnotation("URL", type.url, start, end)
                    }
                    is TdApi.TextEntityTypeMention -> {
                        addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold), start, end)
                    }
                }
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = style.copy(color = LocalContentColor.current),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(annotation.item))
                context.startActivity(intent)
            }
        }
    )
}
