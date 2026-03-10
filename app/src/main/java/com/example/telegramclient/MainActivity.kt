package com.example.telegramclient

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.drinkless.tdlib.TdApi
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

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

    val dynamicColor = colorTheme == "Default" || colorTheme == "Padrão"

    LaunchedEffect(isDark) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        colorTheme == "Oceano" -> if (isDark) {
            darkColorScheme(primary = Color(0xFF80D8FF), secondary = Color(0xFF40C4FF), tertiary = Color(0xFF00B0FF))
        } else {
            lightColorScheme(primary = Color(0xFF0091EA), secondary = Color(0xFF00B0FF), tertiary = Color(0xFF40C4FF))
        }
        colorTheme == "Floresta" -> if (isDark) {
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
    val videoPlayer by viewModel.videoPlayer.collectAsStateWithLifecycle()
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedChatInfoId by remember { mutableStateOf<Long?>(null) }
    var selectedVideoFileId by remember { mutableStateOf<Int?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var isCloudDriveOpen by remember { mutableStateOf(false) }
    var currentSettingsSubScreen by remember { mutableStateOf<String?>(null) }

    if (isEditingProfile) {
        BackHandler { isEditingProfile = false }
        EditProfileScreen(viewModel, onBack = { isEditingProfile = false })
    } else if (currentSettingsSubScreen != null) {
        BackHandler { currentSettingsSubScreen = null }
        when (currentSettingsSubScreen) {
            "Appearance" -> AppearanceSettingsScreen(viewModel, onBack = { currentSettingsSubScreen = null })
            "Downloads" -> DownloadSettingsScreen(viewModel, onBack = { currentSettingsSubScreen = null })
        }
    } else if (selectedVideoFileId != null) {
        BackHandler {
            selectedVideoFileId = null
            viewModel.isPlaybackActive.value = false
        }
        if (videoPlayer == "VLC") {
            VlcPlayerScreen(viewModel, selectedVideoFileId!!, onBack = {
                selectedVideoFileId = null
                viewModel.isPlaybackActive.value = false
            })
        } else {
            VideoPlayerScreen(viewModel, selectedVideoFileId!!, isInPip, isFullscreen,
                onFullscreenToggle = onFullscreenToggle,
                onPipRequest = onPipRequest,
                onBack = {
                    selectedVideoFileId = null
                    viewModel.isPlaybackActive.value = false
                }
            )
        }
    } else if (isCloudDriveOpen) {
        BackHandler { isCloudDriveOpen = false }
        CloudDriveScreen(viewModel, onBack = { isCloudDriveOpen = false })
    } else if (selectedChatId != null) {
        BackHandler { selectedChatId = null }
        ChatScreen(viewModel, selectedChatId!!,
            onBack = { selectedChatId = null },
            onVideoClick = { selectedVideoFileId = it },
            onTitleClick = { selectedChatInfoId = it }
        )
    } else if (selectedChatInfoId != null) {
        BackHandler { selectedChatInfoId = null }
        ChatInfoScreen(viewModel, selectedChatInfoId!!,
            onBack = { selectedChatInfoId = null },
            onMessageClick = {
                selectedChatId = it
                selectedChatInfoId = null
            }
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
                    onGroupClick = { selectedChatInfoId = it },
                    onEditProfile = { isEditingProfile = true },
                    onCloudDrive = { isCloudDriveOpen = true },
                    onSettingsSubScreen = { currentSettingsSubScreen = it })
            }
            is AuthState.Error -> ErrorScreen(state.message) { }
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
fun GroupsScreen(viewModel: TelegramViewModel, onGroupClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("Todos") }
    var searchText by remember { mutableStateOf("") }
    val users by viewModel.users.collectAsStateWithLifecycle()

    val filteredChats by remember(chats, selectedFilter, users, searchText) {
        derivedStateOf {
            val base = when (selectedFilter) {
                "Pessoas" -> chats.filter { (it.type is TdApi.ChatTypePrivate || it.type is TdApi.ChatTypeSecret) &&
                    (users[(it.type as? TdApi.ChatTypePrivate)?.userId]?.type !is TdApi.UserTypeBot) }
                "Bots" -> chats.filter {
                    val type = it.type
                    type is TdApi.ChatTypePrivate && users[type.userId]?.type is TdApi.UserTypeBot
                }
                "Grupos" -> chats.filter { it.type is TdApi.ChatTypeBasicGroup || it.type is TdApi.ChatTypeSupergroup }
                else -> chats
            }
            if (searchText.isEmpty()) base else base.filter { it.title.contains(searchText, ignoreCase = true) }
        }
    }

    if (isLoading && chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Pesquisar conversas...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchText.isNotEmpty()) {
                    { IconButton(onClick = { searchText = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Todos", "Pessoas", "Bots", "Grupos").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    items = filteredChats,
                    key = { it.id },
                    contentType = { "chat" }
                ) { chat ->
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
fun LoggedInMainScreen(viewModel: TelegramViewModel, currentTab: Int, onTabChange: (Int) -> Unit, onGroupClick: (Long) -> Unit, onEditProfile: () -> Unit, onCloudDrive: () -> Unit, onSettingsSubScreen: (String) -> Unit) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            val title = if (currentTab == 0) "Conversas" else "Meu Perfil"
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.ExtraBold)
                        if (currentTab == 0 && connectionState !is TdApi.ConnectionStateReady) {
                            val statusText = when (connectionState) {
                                is TdApi.ConnectionStateConnecting -> "Conectando..."
                                is TdApi.ConnectionStateConnectingToProxy -> "Conectando ao proxy..."
                                is TdApi.ConnectionStateUpdating -> "Atualizando..."
                                is TdApi.ConnectionStateWaitingForNetwork -> "Aguardando rede..."
                                else -> ""
                            }
                            if (statusText.isNotEmpty()) {
                                Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
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
            if (currentTab == 0) GroupsScreen(viewModel, onGroupClick) else SettingsScreen(viewModel, onEditClick = onEditProfile, onCloudDrive = onCloudDrive, onNavigate = onSettingsSubScreen)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: TelegramViewModel, chatId: Long, onBack: () -> Unit, onVideoClick: (Int) -> Unit, onTitleClick: (Long) -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val bgStyle by viewModel.chatBackground.collectAsStateWithLifecycle()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onTitleClick(chatId) }
                    ) {
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
        val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
        val isDark = when (darkMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedChatBackground(style = bgStyle, isDark = isDark)

            if (isLoading && messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
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
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), reverseLayout = true) {
                        itemsIndexed(
                            items = messages,
                            key = { _, it -> it.id },
                            contentType = { _, it -> it.content.constructor }
                        ) { index, message ->
                            val nextMessage = if (index > 0) messages[index - 1] else null
                            val prevMessage = if (index < messages.size - 1) messages[index + 1] else null

                            val isLastInGroup = nextMessage == null || !isSameSender(nextMessage.senderId, message.senderId)
                            val isFirstInGroup = prevMessage == null || !isSameSender(prevMessage.senderId, message.senderId)

                            val currentDate = android.text.format.DateFormat.format("yyyyMMdd", message.date.toLong() * 1000).toString()
                            val prevDate = prevMessage?.let { android.text.format.DateFormat.format("yyyyMMdd", it.date.toLong() * 1000).toString() }

                            if (prevDate != null && currentDate != prevDate) {
                                DateHeader(message.date)
                            } else if (prevMessage == null) {
                                DateHeader(message.date)
                            }

                            ChatMessageItem(viewModel, message, downloadedFiles, users, onVideoClick, isFirstInGroup, isLastInGroup)

                            // Pre-fetch when reaching the last 10 messages
                            if (index >= messages.size - 10 && !isLoading && messages.size >= 50) {
                                LaunchedEffect(messages.last().id) {
                                    viewModel.loadChatHistory(chatId, messages.last().id)
                                }
                            }
                        }
                        if (messages.isEmpty() && !isLoading) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma mensagem encontrada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) } } }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: Int) {
    val dateText = remember(date) {
        val now = java.util.Calendar.getInstance()
        val msgDate = java.util.Calendar.getInstance().apply { timeInMillis = date.toLong() * 1000 }

        if (now.get(java.util.Calendar.DATE) == msgDate.get(java.util.Calendar.DATE)) "Hoje"
        else if (now.get(java.util.Calendar.DATE) - 1 == msgDate.get(java.util.Calendar.DATE)) "Ontem"
        else android.text.format.DateFormat.format("d 'de' MMMM", msgDate.timeInMillis).toString()
    }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(viewModel: TelegramViewModel, chatId: Long, onBack: () -> Unit, onMessageClick: (Long) -> Unit) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val fullInfo by viewModel.selectedChatFullInfo.collectAsStateWithLifecycle()
    val basicGroups by viewModel.basicGroups.collectAsStateWithLifecycle()
    val supergroups by viewModel.supergroups.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val chat = remember(chats, chatId) { chats.find { it.id == chatId } }

    LaunchedEffect(chatId) {
        viewModel.loadChatFullInfo(chatId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dados do Grupo") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Large Avatar
            val avatarPath = chat?.photo?.big?.id?.let { downloadedFiles[it] } ?: chat?.photo?.small?.id?.let { downloadedFiles[it] }
            if (avatarPath != null) {
                AsyncImage(model = avatarPath, contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Surface(modifier = Modifier.size(120.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(chat?.title?.take(1)?.uppercase() ?: "?", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(chat?.title ?: "Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            val membersText = when (val type = chat?.type) {
                is TdApi.ChatTypeBasicGroup -> {
                    val group = basicGroups[type.basicGroupId.toLong()]
                    if (group != null) "${group.memberCount} membros" else "Grupo"
                }
                is TdApi.ChatTypeSupergroup -> {
                    val sg = supergroups[type.supergroupId.toLong()]
                    val suffix = if (type.isChannel) "inscritos" else "membros"
                    if (sg != null) "${sg.memberCount} $suffix" else if (type.isChannel) "Canal" else "Supergrupo"
                }
                is TdApi.ChatTypePrivate -> "Privado"
                else -> ""
            }
            Text(membersText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(24.dp))

            // Action Grid
            val isMuted = chat?.notificationSettings?.muteFor ?: 0 > 0
            InfoActionGrid(
                onMessage = { onMessageClick(chatId) },
                onMute = { chat?.let { viewModel.toggleMute(it) } },
                onLeave = {
                    viewModel.leaveChat(chatId)
                    onBack()
                },
                isMuted = isMuted,
                onMembers = { /* Feature not requested yet */ },
                onMedia = { /* Feature not requested yet */ },
                onSaved = { /* Feature not requested yet */ },
                onFiles = { /* Feature not requested yet */ },
                onLinks = { /* Feature not requested yet */ }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Description and Invite Link
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val currentInfo = fullInfo
                val description = when (currentInfo) {
                    is TdApi.BasicGroupFullInfo -> currentInfo.description
                    is TdApi.SupergroupFullInfo -> currentInfo.description
                    else -> ""
                }
                if (description.isNotEmpty()) {
                    InfoSection(title = "Descrição", content = description)
                }

                val inviteLink = when (currentInfo) {
                    is TdApi.BasicGroupFullInfo -> currentInfo.inviteLink?.inviteLink
                    is TdApi.SupergroupFullInfo -> currentInfo.inviteLink?.inviteLink
                    else -> null
                }
                if (inviteLink != null) {
                    InfoSection(title = "Link de Convite", content = inviteLink, isLink = true)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun InfoSection(title: String, content: String, isLink: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isLink) TextDecoration.Underline else null
            )
        }
    }
}

@Composable
fun InfoActionGrid(onMessage: () -> Unit, onMute: () -> Unit, onLeave: () -> Unit, isMuted: Boolean, onMembers: () -> Unit, onMedia: () -> Unit, onSaved: () -> Unit, onFiles: () -> Unit, onLinks: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoActionButton(Modifier.weight(1f), Icons.AutoMirrored.Filled.Chat, "Mensagem", onMessage)
            InfoActionButton(Modifier.weight(1f), if (isMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, if (isMuted) "Ativar" else "Silenciar", onMute)
            InfoActionButton(Modifier.weight(1f), Icons.Default.ExitToApp, "Sair", onLeave, isError = true)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoActionButton(Modifier.weight(1f), Icons.Default.People, "Membros", onMembers)
            InfoActionButton(Modifier.weight(1f), Icons.Default.PermMedia, "Mídias", onMedia)
            InfoActionButton(Modifier.weight(1f), Icons.Default.Bookmark, "Salvos", onSaved)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoActionButton(Modifier.weight(1f), Icons.Default.Description, "Arquivos", onFiles)
            InfoActionButton(Modifier.weight(1f), Icons.Default.Link, "Links", onLinks)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun InfoActionButton(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, isError: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AnimatedChatBackground(style: String, isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(20000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bg_anim"
    )

    val brush = when (style) {
        "Oceano" -> {
            val colors = if (isDark) {
                listOf(Color(0xFF001F3F), Color(0xFF003366), Color(0xFF001F3F))
            } else {
                listOf(Color(0xFFB3E5FC), Color(0xFFE1F5FE), Color(0xFFB3E5FC))
            }
            Brush.linearGradient(
                colors = colors,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(animValue * 2000f, animValue * 2000f)
            )
        }
        "Floresta" -> {
            val colors = if (isDark) {
                listOf(Color(0xFF0A2F10), Color(0xFF1B4332), Color(0xFF0A2F10))
            } else {
                listOf(Color(0xFFC8E6C9), Color(0xFFE8F5E9), Color(0xFFC8E6C9))
            }
            Brush.linearGradient(
                colors = colors,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(animValue * 2000f, animValue * 2000f)
            )
        }
        else -> {
            val colors = if (isDark) {
                listOf(Color(0xFF0F0F0F), Color(0xFF000000))
            } else {
                listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.surface)
            }
            Brush.verticalGradient(colors)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(brush))
}

@Composable
fun ChatMessageItem(viewModel: TelegramViewModel, message: TdApi.Message, downloadedFiles: Map<Int, String>, users: Map<Long, TdApi.User>, onVideoClick: (Int) -> Unit, isFirstInGroup: Boolean, isLastInGroup: Boolean) {
    val isOutgoing = message.isOutgoing
    val senderId = (message.senderId as? TdApi.MessageSenderUser)?.userId
    val sender = senderId?.let { users[it] }

    if (!isOutgoing && senderId != null && sender == null) {
        LaunchedEffect(senderId) {
            viewModel.loadSender(senderId)
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (isLastInGroup) 4.dp else 1.dp), horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!isOutgoing) {
            Box(modifier = Modifier.size(32.dp)) {
                if (isLastInGroup) {
                    val avatarPath = sender?.profilePhoto?.small?.id?.let { downloadedFiles[it] }
                    if (avatarPath != null) {
                        AsyncImage(model = avatarPath, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Surface(modifier = Modifier.fillMaxSize().clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Text(sender?.firstName?.take(1) ?: "?", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = if (isOutgoing || isFirstInGroup) 20.dp else 4.dp,
                topEnd = if (!isOutgoing || isFirstInGroup) 20.dp else 4.dp,
                bottomStart = if (isOutgoing || isLastInGroup) 20.dp else 4.dp,
                bottomEnd = if (!isOutgoing || isLastInGroup) 20.dp else 4.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp),
            tonalElevation = if (isOutgoing) 2.dp else 0.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isOutgoing && sender != null && isFirstInGroup) {
                    Text(
                        text = "${sender.firstName} ${sender.lastName}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
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
                    is TdApi.MessageAnimation -> {
                        VideoMessageContent(content.animation, downloadedFiles, onVideoClick)
                        if (content.caption.text.isNotEmpty()) {
                            ClickableFormattedText(content.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    is TdApi.MessageAudio -> {
                        AudioMessageContent(viewModel, content.audio, downloadedFiles)
                        if (content.caption.text.isNotEmpty()) {
                            ClickableFormattedText(content.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    is TdApi.MessageVoiceNote -> {
                        VoiceNoteMessageContent(viewModel, content.voiceNote, downloadedFiles)
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
        is TdApi.Video -> Quadruple(video.video.id, video.thumbnail, video.fileName, video.duration)
        is TdApi.Document -> Quadruple(video.document.id, video.thumbnail, video.fileName, 0)
        is TdApi.Animation -> Quadruple(video.animation.id, video.thumbnail, video.fileName, video.duration)
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
fun AudioMessageContent(viewModel: TelegramViewModel, audio: TdApi.Audio, downloadedFiles: Map<Int, String>) {
    val fileStatus by viewModel.fileStatus.collectAsStateWithLifecycle()
    val status = fileStatus[audio.audio.id]
    val isDownloaded = downloadedFiles.containsKey(audio.audio.id)
    val isDownloading = status?.local?.isDownloadingActive == true

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { if (!isDownloaded && !isDownloading) viewModel.downloadFile(audio.audio.id, audio.fileName) }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(audio.title.ifEmpty { audio.fileName }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(audio.performer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (isDownloading) {
                IconButton(onClick = { viewModel.cancelDownload(audio.audio.id) }) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        }
        if (isDownloading && status != null) {
            val progress = status.local.downloadedSize.toFloat() / status.expectedSize
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

fun isSameSender(s1: TdApi.MessageSender, s2: TdApi.MessageSender): Boolean {
    if (s1.constructor != s2.constructor) return false
    return when (s1) {
        is TdApi.MessageSenderUser -> s2 is TdApi.MessageSenderUser && s1.userId == s2.userId
        is TdApi.MessageSenderChat -> s2 is TdApi.MessageSenderChat && s1.chatId == s2.chatId
        else -> false
    }
}

@Composable
fun VoiceNoteMessageContent(viewModel: TelegramViewModel, voiceNoteData: TdApi.VoiceNote, downloadedFiles: Map<Int, String>) {
    val fileId = voiceNoteData.voice.id
    val isDownloaded = downloadedFiles.containsKey(fileId)
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (!isDownloaded) viewModel.downloadFile(fileId, "voice_note.ogg") }) {
            Icon(if (isDownloaded) Icons.Default.PlayArrow else Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text("Mensagem de Voz (${voiceNoteData.duration}s)", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DocumentMessageContent(viewModel: TelegramViewModel, document: TdApi.Document, downloadedFiles: Map<Int, String>) {
    val fileStatus by viewModel.fileStatus.collectAsStateWithLifecycle()
    val status = fileStatus[document.document.id]
    val isDownloaded = downloadedFiles.containsKey(document.document.id)
    val isDownloading = status?.local?.isDownloadingActive == true

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { if (!isDownloaded && !isDownloading) viewModel.downloadFile(document.document.id, document.fileName) }, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDownloaded) Icons.Default.Description else Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(document.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${document.document.expectedSize / 1024} KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (isDownloading) {
                IconButton(onClick = { viewModel.cancelDownload(document.document.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar")
                }
            }
        }
        if (isDownloading && status != null) {
            val progress = status.local.downloadedSize.toFloat() / status.expectedSize
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
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
    var isBuffering by remember { mutableStateOf(false) }

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

    val exoPlayer = remember(fileId) {
        viewModel.isPlaybackActive.value = true
        val factory = TdLibDataSourceFactory(viewModel.client!!, fileId)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(factory)).build().apply {
            val mediaItem = MediaItem.Builder().setUri("tdlib://file/$fileId").build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlayingChanged: Boolean) { isPlaying = isPlayingChanged }
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == androidx.media3.common.Player.STATE_BUFFERING
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        duration = this@apply.duration
                    }
                }
                override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo, newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) {
                    currentPosition = newPosition.positionMs
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("ExoPlayer", "Playback error: ${error.localizedMessage}", error)
                    // Attempt to recover on certain errors
                    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                        this@apply.prepare()
                        this@apply.play()
                    }
                }
            })
        }
    }
    LaunchedEffect(exoPlayer) { while (true) { currentPosition = exoPlayer.currentPosition; kotlinx.coroutines.delay(500) } }
    DisposableEffect(lifecycleOwner) { onDispose { exoPlayer.release() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls }) {
        AndroidView(factory = { PlayerView(context).apply { player = exoPlayer; useController = false; setBackgroundColor(android.graphics.Color.BLACK) } }, update = { it.resizeMode = resizeMode }, modifier = Modifier.fillMaxSize())

        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

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
                    IconButton(onClick = {
                        exoPlayer.seekTo(maxOf(0, currentPosition - 10000))
                    }) { Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(48.dp)) }

                    Surface(
                        onClick = {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            } else {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ) {
                        Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproduzir", modifier = Modifier.padding(16.dp).size(48.dp))
                    }

                    IconButton(onClick = {
                        exoPlayer.seekTo(minOf(duration, currentPosition + 10000))
                    }) { Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(48.dp)) }
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
fun SettingsScreen(viewModel: TelegramViewModel, onEditClick: () -> Unit, onCloudDrive: () -> Unit, onNavigate: (String) -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val user = (authState as? AuthState.LoggedIn)?.user

    if (user != null) {
        SettingsMainScreen(user, downloadedFiles, onEditClick, onCloudDrive, onNavigate)
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
    val chatBackground by viewModel.chatBackground.collectAsStateWithLifecycle()
    val videoPlayer by viewModel.videoPlayer.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aparência") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
            Column {
                Text("Plano de Fundo do Chat", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Padrão", "Oceano", "Floresta").forEach { name ->
                        FilterChip(selected = chatBackground == name, onClick = { viewModel.updateChatBackground(name) }, label = { Text(name) })
                    }
                }
            }
            Column {
                Text("Player de Vídeo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ExoPlayer", "VLC").forEach { name ->
                        FilterChip(selected = videoPlayer == name, onClick = { viewModel.updateVideoPlayer(name) }, label = { Text(name) })
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CloudDriveScreen(viewModel: TelegramViewModel, onBack: () -> Unit) {
    val cloudChatId by viewModel.cloudDriveChatId.collectAsStateWithLifecycle()
    val messages by viewModel.cloudDriveMessages.collectAsStateWithLifecycle()
    val fileStatus by viewModel.fileStatus.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()

    var showChatSelector by remember { mutableStateOf(false) }
    var chatFilterText by remember { mutableStateOf("") }
    val filteredChatsForSelector by remember(chats, chatFilterText) {
        derivedStateOf {
            if (chatFilterText.isEmpty()) chats else chats.filter { it.title.contains(chatFilterText, ignoreCase = true) }
        }
    }
    var selectedMessageForMenu by remember { mutableStateOf<TdApi.Message?>(null) }
    var selectedFolderForMenu by remember { mutableStateOf<String?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var folderRenameValue by remember { mutableStateOf("") }
    var driveSearchText by remember { mutableStateOf("") }

    // Logic to organize files by folders using ID-based prefixes (/fid_xxxx/ Name)
    val folderMapping = remember(messages) {
        messages.filter { it.content is TdApi.MessageText }
            .map { (it.content as TdApi.MessageText).text.text }
            .filter { it.startsWith("/fid_") }
            .associate { line ->
                val parts = line.split(" ", limit = 2)
                val id = parts[0]
                val name = parts.getOrNull(1)?.removePrefix(".init")?.trim() ?: id
                id to name
            }
    }

    val filesByFolder = remember(messages) {
        val map = mutableMapOf<String, MutableList<TdApi.Message>>()
        messages.forEach { msg ->
            val caption = when (val content = msg.content) {
                is TdApi.MessageDocument -> content.caption.text
                is TdApi.MessageText -> content.text.text
                else -> ""
            }
            if (caption.startsWith("/fid_")) {
                val fid = caption.substringBefore("/", "").ifEmpty { caption.substringBefore(" ", "") }.let { if (it.isEmpty()) caption.split("/").getOrNull(1)?.let { s -> "/fid_$s" } else it }
                val actualFid = if (caption.startsWith("/fid_")) caption.substringBefore("/", "/").let { if (it == "") caption.split("/").getOrNull(1)?.let { s -> "/fid_$s" } ?: "/" else it } else "/"
                // Simpler extraction:
                val match = Regex("^(/fid_[^/ ]+)/?").find(caption)
                val folderId = match?.groupValues?.get(1) ?: "/"
                map.getOrPut(folderId) { mutableListOf() }.add(msg)
            } else if (caption.startsWith("/") && !caption.startsWith("/fid_")) {
                // Backwards compatibility for old folder names
                val folder = if (caption.endsWith("/")) caption else caption.substringBeforeLast("/", "/") + "/"
                map.getOrPut(folder) { mutableListOf() }.add(msg)
            } else {
                map.getOrPut("/") { mutableListOf() }.add(msg)
            }
        }
        map
    }

    var currentFolderPath by remember { mutableStateOf("/") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadFileToDrive(it, currentFolderPath) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (currentFolderPath == "/") "Cloud Drive" else currentFolderPath.removePrefix("/"), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { if (currentFolderPath == "/") onBack() else currentFolderPath = "/" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                    actions = { IconButton(onClick = { showChatSelector = true }) { Icon(Icons.Default.Settings, contentDescription = "Configurar Chat") } }
                )
                OutlinedTextField(
                    value = driveSearchText,
                    onValueChange = { driveSearchText = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Pesquisar nos arquivos...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (driveSearchText.isNotEmpty()) {
                        { IconButton(onClick = { driveSearchText = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }
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
                    val rootFolders = filesByFolder.keys.filter { it != "/" }.distinct()
                    items(
                        items = rootFolders,
                        key = { it },
                        contentType = { "folder" }
                    ) { folderKey ->
                        val folderName = folderMapping[folderKey] ?: folderKey.removePrefix("/").removeSuffix("/")
                        FolderItem(
                            name = folderName,
                            onClick = { currentFolderPath = folderKey },
                            onLongClick = {
                                selectedFolderForMenu = folderKey
                                folderRenameValue = folderName
                            }
                        )
                    }
                }

                val displayFiles = if (driveSearchText.isEmpty()) {
                    filesByFolder[currentFolderPath]?.filter { it.content is TdApi.MessageDocument } ?: emptyList()
                } else {
                    messages.filter { it.content is TdApi.MessageDocument }.filter {
                        (it.content as TdApi.MessageDocument).document.fileName.contains(driveSearchText, ignoreCase = true)
                    }
                }

                items(
                    items = displayFiles,
                    key = { it.id },
                    contentType = { "file" }
                ) { msg ->
                    val doc = (msg.content as TdApi.MessageDocument).document
                    val status = fileStatus[doc.document.id]
                    val progress = if (status != null) {
                        if (status.local.isDownloadingActive && status.expectedSize > 0) {
                            status.local.downloadedSize.toFloat() / status.expectedSize
                        } else if (status.remote.isUploadingActive && status.expectedSize > 0) {
                            status.remote.uploadedSize.toFloat() / status.expectedSize
                        } else null
                    } else null

                    FileItem(
                        name = doc.fileName,
                        mimeType = doc.mimeType,
                        progress = progress,
                        onLongClick = { selectedMessageForMenu = msg }
                    )
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
                        OutlinedTextField(
                            value = chatFilterText,
                            onValueChange = { chatFilterText = it },
                            label = { Text("Filtrar chats") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(filteredChatsForSelector) { chat ->
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
                        val fid = "/fid_${System.currentTimeMillis()}/"
                        viewModel.sendMessage(cloudChatId, "$fid $newFolderName")
                    }
                    showNewFolderDialog = false; newFolderName = ""
                }) { Text("Criar") } },
                dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancelar") } }
            )
        }

        if (showRenameFolderDialog && selectedFolderForMenu != null) {
            AlertDialog(
                onDismissRequest = { showRenameFolderDialog = false },
                title = { Text("Renomear Pasta") },
                text = { OutlinedTextField(value = folderRenameValue, onValueChange = { folderRenameValue = it }, label = { Text("Novo nome") }, singleLine = true) },
                confirmButton = { Button(onClick = {
                    viewModel.renameCloudDriveFolder(selectedFolderForMenu!!, folderRenameValue)
                    showRenameFolderDialog = false
                    selectedFolderForMenu = null
                }) { Text("Renomear") } },
                dismissButton = { TextButton(onClick = { showRenameFolderDialog = false }) { Text("Cancelar") } }
            )
        }

        if (selectedMessageForMenu != null) {
            ModalBottomSheet(onDismissRequest = { selectedMessageForMenu = null }) {
                val doc = (selectedMessageForMenu!!.content as TdApi.MessageDocument).document
                ListItem(
                    modifier = Modifier.clickable { viewModel.deleteCloudDriveFile(selectedMessageForMenu!!.id); selectedMessageForMenu = null },
                    headlineContent = { Text("Excluir Arquivo", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
                ListItem(
                    modifier = Modifier.clickable { viewModel.downloadFile(doc.document.id, doc.fileName); selectedMessageForMenu = null },
                    headlineContent = { Text("Baixar Arquivo") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (selectedFolderForMenu != null && !showRenameFolderDialog) {
            ModalBottomSheet(onDismissRequest = { selectedFolderForMenu = null }) {
                ListItem(
                    modifier = Modifier.clickable { showRenameFolderDialog = true },
                    headlineContent = { Text("Renomear Pasta") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        viewModel.deleteCloudDriveFolder(selectedFolderForMenu!!)
                        selectedFolderForMenu = null
                    },
                    headlineContent = { Text("Excluir Pasta", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItem(name: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun VlcPlayerScreen(viewModel: TelegramViewModel, fileId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBuffering by remember { mutableStateOf(true) }
    val libVLC = remember { LibVLC(context) }
    val mediaPlayer = remember {
        MediaPlayer(libVLC).apply {
            setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Buffering -> { if (event.buffering >= 100f) isBuffering = false }
                    MediaPlayer.Event.Playing -> isBuffering = false
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()

    LaunchedEffect(downloadedFiles[fileId]) {
        downloadedFiles[fileId]?.let { path ->
            val media = Media(libVLC, path)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } ?: run {
            viewModel.downloadFile(fileId, "streaming_video")
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(name: String, mimeType: String, progress: Float? = null, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(64.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
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
            if (start >= 0 && end <= formattedText.text.length) {
                when (entity.type) {
                    is TdApi.TextEntityTypeUrl -> {
                        addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), start, end)
                        addStringAnnotation("URL", formattedText.text.substring(start, end), start, end)
                    }
                    is TdApi.TextEntityTypeTextUrl -> {
                        addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline), start, end)
                        addStringAnnotation("URL", (entity.type as TdApi.TextEntityTypeTextUrl).url, start, end)
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
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                context.startActivity(intent)
            }
        }
    )
}
