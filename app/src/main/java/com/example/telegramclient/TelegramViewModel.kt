package com.example.telegramclient

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

sealed class AuthState {
    object Initial : AuthState()
    object EnterCredentials : AuthState()
    object Loading : AuthState()
    object EnterPhone : AuthState()
    object EnterCode : AuthState()
    object EnterPassword : AuthState()
    data class LoggedIn(val user: TdApi.User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class TelegramViewModel(application: Application) : AndroidViewModel(application) {
    
    private val settingsManager = SettingsManager(application)
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent = _isLoadingContent.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadedFiles = _downloadedFiles.asStateFlow()

    private val _darkMode = MutableStateFlow(settingsManager.getDarkMode())
    val darkMode = _darkMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(settingsManager.getColorTheme())
    val colorTheme = _colorTheme.asStateFlow()

    val isPlaybackActive = MutableStateFlow(false)

    var client: Client? = null
    private var appId: Int = 0
    private var apiHash: String = ""
    private val appDir: String = application.filesDir.absolutePath + "/tdlib"

    private val handler = Client.ResultHandler { result ->
        when (result) {
            is TdApi.UpdateAuthorizationState -> onAuthStateUpdated(result.authorizationState)
            is TdApi.UpdateFile -> onUpdateFile(result.file)
            is TdApi.UpdateNewMessage -> onNewMessage(result.message)
            is TdApi.UpdateMessageContent -> onMessageContentUpdated(result.chatId, result.messageId, result.newContent)
        }
    }

    private fun onNewMessage(message: TdApi.Message) {
        viewModelScope.launch {
            if (_messages.value.isNotEmpty() && _messages.value.first().chatId == message.chatId) {
                _messages.value = listOf(message) + _messages.value
                requestThumbnails(message)
            }
            // Update chat list if needed
            val currentChats = _chats.value.toMutableList()
            val index = currentChats.indexOfFirst { it.id == message.chatId }
            if (index != -1) {
                val chat = currentChats[index]
                chat.lastMessage = message
                if (!message.isOutgoing) chat.unreadCount++
                currentChats.removeAt(index)
                currentChats.add(0, chat)
                _chats.value = currentChats
            }
        }
    }

    private fun onMessageContentUpdated(chatId: Long, messageId: Long, content: TdApi.MessageContent) {
        viewModelScope.launch {
            val currentMessages = _messages.value.toMutableList()
            val index = currentMessages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val oldMsg = currentMessages[index]
                oldMsg.content = content
                _messages.value = currentMessages
            }
        }
    }

    init {
        File(appDir).mkdirs()
        checkSavedCredentials()
    }

    private fun checkSavedCredentials() {
        val savedId = settingsManager.getAppId()
        val savedHash = settingsManager.getApiHash()
        if (savedId != null && savedHash != null) {
            initializeClient(savedId, savedHash)
        } else {
            _authState.value = AuthState.EnterCredentials
        }
    }

    fun updateDarkMode(mode: Int) {
        _darkMode.value = mode
        settingsManager.saveDarkMode(mode)
    }

    fun updateColorTheme(theme: String) {
        _colorTheme.value = theme
        settingsManager.saveColorTheme(theme)
    }

    fun initializeClient(id: String, hash: String) {
        try {
            this.appId = id.toInt()
            this.apiHash = hash
            settingsManager.saveCredentials(id, hash)
            _authState.value = AuthState.Loading
            createClient()
        } catch (e: NumberFormatException) {
            _authState.value = AuthState.Error("Invalid App ID")
        }
    }

    private fun createClient() {
        if (client != null) return
        client = Client.create(
            handler,
            { e -> Log.e("Telegram", "Error: ${e.localizedMessage}") },
            { e -> Log.e("Telegram", "Error: ${e.localizedMessage}") }
        )
    }

    private fun onUpdateFile(file: TdApi.File) {
        if (file.local.isDownloadingCompleted) {
            viewModelScope.launch {
                val current = _downloadedFiles.value.toMutableMap()
                current[file.id] = file.local.path
                _downloadedFiles.value = current
            }
        }
    }

    private fun onAuthStateUpdated(state: TdApi.AuthorizationState) {
        viewModelScope.launch {
            when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    client?.send(TdApi.SetTdlibParameters(
                        false, appDir, appDir + "/files", ByteArray(0),
                        true, true, true, true, appId, apiHash,
                        "en", "Android", "Example", "1.0"
                    )) { }
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthState.EnterPhone
                is TdApi.AuthorizationStateWaitCode -> _authState.value = AuthState.EnterCode
                is TdApi.AuthorizationStateWaitPassword -> _authState.value = AuthState.EnterPassword
                is TdApi.AuthorizationStateReady -> {
                    fetchMe()
                    loadChats()
                }
                is TdApi.AuthorizationStateClosed -> {
                    client = null
                    _authState.value = AuthState.EnterCredentials
                }
                else -> {}
            }
        }
    }

    fun submitPhone(phone: String) {
        _authState.value = AuthState.Loading
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result is TdApi.Error) viewModelScope.launch { _authState.value = AuthState.Error(result.message) }
        }
    }

    fun submitCode(code: String) {
        _authState.value = AuthState.Loading
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) viewModelScope.launch { _authState.value = AuthState.Error(result.message) }
        }
    }

    private fun fetchMe() {
        client?.send(TdApi.GetMe()) { result ->
            if (result is TdApi.User) {
                viewModelScope.launch {
                    _authState.value = AuthState.LoggedIn(result)
                    // Request user avatar download
                    result.profilePhoto?.small?.let { file ->
                        if (!file.local.isDownloadingCompleted) {
                            client?.send(TdApi.DownloadFile(file.id, 1, 0, 0, false)) { }
                        } else {
                            onUpdateFile(file)
                        }
                    }
                }
            }
        }
    }

    fun loadChats() {
        _isLoadingContent.value = true
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), 100)) { result ->
            if (result is TdApi.Chats) {
                val chatIds = result.chatIds
                val chatList = mutableListOf<TdApi.Chat>()
                if (chatIds.isEmpty()) {
                    viewModelScope.launch { _isLoadingContent.value = false }
                } else {
                    var count = 0
                    chatIds.forEach { chatId ->
                        client?.send(TdApi.GetChat(chatId)) { chat ->
                            if (chat is TdApi.Chat) {
                                synchronized(chatList) { chatList.add(chat) }
                                // Request avatar download
                                chat.photo?.small?.let { file ->
                                    if (!file.local.isDownloadingCompleted) {
                                        client?.send(TdApi.DownloadFile(file.id, 1, 0, 0, false)) { }
                                    } else {
                                        onUpdateFile(file)
                                    }
                                }
                            }
                            count++
                            if (count == chatIds.size) {
                                viewModelScope.launch {
                                    _chats.value = chatList.filter {
                                        it.type is TdApi.ChatTypeSupergroup || it.type is TdApi.ChatTypeBasicGroup
                                    }
                                    _isLoadingContent.value = false
                                }
                            }
                        }
                    }
                }
            } else {
                viewModelScope.launch { _isLoadingContent.value = false }
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, false)
        client?.send(TdApi.SendMessage(chatId, null, null, null, null, content)) { result ->
            if (result is TdApi.Error) {
                Log.e("Telegram", "Failed to send message: ${result.message}")
            }
        }
    }

    fun loadChatHistory(chatId: Long) {
        _messages.value = emptyList()
        _isLoadingContent.value = true
        
        // p0: chatId, p1: fromMessageId, p2: offset, p3: limit, p4: onlyLocal
        client?.send(TdApi.GetChatHistory(chatId, 0L, 0, 50, false)) { result ->
            if (result is TdApi.Messages) {
                viewModelScope.launch {
                    _messages.value = result.messages.toList()
                    _isLoadingContent.value = false
                    // Request download for thumbnails in the loaded messages
                    result.messages.forEach { msg ->
                        requestThumbnails(msg)
                    }
                }
            } else {
                viewModelScope.launch { _isLoadingContent.value = false }
            }
        }
    }

    private fun requestThumbnails(message: TdApi.Message) {
        val fileId = when (val content = message.content) {
            is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo?.id
            is TdApi.MessageVideo -> content.video.thumbnail?.file?.id
            is TdApi.MessageDocument -> content.document.thumbnail?.file?.id
            else -> null
        }

        fileId?.let { id ->
            client?.send(TdApi.DownloadFile(id, 1, 0, 0, false)) { }
        }
    }

    fun loadVideos(chatId: Long) {
        _messages.value = emptyList()
        _isLoadingContent.value = true

        val filter = TdApi.SearchMessagesFilterVideo()
        client?.send(TdApi.SearchChatMessages(chatId, null, "", null, 0L, 0, 100, filter)) { result ->
            if (result is TdApi.FoundChatMessages) {
                viewModelScope.launch {
                    _messages.value = result.messages.toList()
                    _isLoadingContent.value = false
                    result.messages.forEach { requestThumbnails(it) }
                }
            } else {
                viewModelScope.launch { _isLoadingContent.value = false }
            }
        }
    }

    fun updateProfile(firstName: String, lastName: String) {
        client?.send(TdApi.SetName(firstName, lastName)) { fetchMe() }
    }

    fun updateUsername(username: String) {
        client?.send(TdApi.SetUsername(username)) { fetchMe() }
    }

    fun updateBio(bio: String) {
        client?.send(TdApi.SetBio(bio)) { fetchMe() }
    }

    fun deleteProfilePhoto(photoId: Long) {
        client?.send(TdApi.DeleteProfilePhoto(photoId)) { fetchMe() }
    }
}
