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

    private val _connectionState = MutableStateFlow<TdApi.ConnectionState>(TdApi.ConnectionStateConnecting())
    val connectionState = _connectionState.asStateFlow()

    private val _chats = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _pinnedMessage = MutableStateFlow<TdApi.Message?>(null)
    val pinnedMessage = _pinnedMessage.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent = _isLoadingContent.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadedFiles = _downloadedFiles.asStateFlow()

    private val _users = MutableStateFlow<Map<Long, TdApi.User>>(emptyMap())
    val users = _users.asStateFlow()

    private val _darkMode = MutableStateFlow(settingsManager.getDarkMode())
    val darkMode = _darkMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(settingsManager.getColorTheme())
    val colorTheme = _colorTheme.asStateFlow()

    private val _chatBackground = MutableStateFlow(settingsManager.getChatBackground())
    val chatBackground = _chatBackground.asStateFlow()

    private val _downloadPath = MutableStateFlow(settingsManager.getDownloadPath())
    val downloadPath = _downloadPath.asStateFlow()

    private val _videoPlayer = MutableStateFlow(settingsManager.getVideoPlayer())
    val videoPlayer = _videoPlayer.asStateFlow()

    private val _cloudDriveChatId = MutableStateFlow(settingsManager.getCloudDriveChatId())
    val cloudDriveChatId = _cloudDriveChatId.asStateFlow()

    private val _cloudDriveMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val cloudDriveMessages = _cloudDriveMessages.asStateFlow()

    private val _fileStatus = MutableStateFlow<Map<Int, TdApi.File>>(emptyMap())
    val fileStatus = _fileStatus.asStateFlow()

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
            is TdApi.UpdateChatLastMessage -> onChatLastMessageUpdated(result.chatId, result.lastMessage)
            is TdApi.UpdateChatPosition -> onChatPositionUpdated(result.chatId, result.position)
            is TdApi.UpdateConnectionState -> onConnectionStateUpdated(result.state)
        }
    }

    private fun onConnectionStateUpdated(state: TdApi.ConnectionState) {
        _connectionState.value = state
    }

    private val downloadingFiles = mutableMapOf<Int, String>()

    private fun onNewMessage(message: TdApi.Message) {
        viewModelScope.launch {
            if (_messages.value.isNotEmpty() && _messages.value.first().chatId == message.chatId) {
                _messages.value = listOf(message) + _messages.value
                requestThumbnails(message)
                fetchSenderInfo(message.senderId)
            }
            if (message.chatId == _cloudDriveChatId.value) {
                // If it's a new file or folder initialization, refresh the drive
                if (message.content is TdApi.MessageDocument || (message.content is TdApi.MessageText && (message.content as TdApi.MessageText).text.text.startsWith("/"))) {
                    loadCloudDriveMessages()
                }
            }
        }
    }

    private fun onMessageContentUpdated(chatId: Long, messageId: Long, content: TdApi.MessageContent) {
        viewModelScope.launch {
            val currentMessages = _messages.value.toMutableList()
            val index = currentMessages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                currentMessages[index] = currentMessages[index].apply { this.content = content }
                _messages.value = currentMessages
            }
            if (chatId == _cloudDriveChatId.value) {
                loadCloudDriveMessages()
            }
        }
    }

    private fun onChatLastMessageUpdated(chatId: Long, lastMessage: TdApi.Message?) {
        viewModelScope.launch {
            client?.send(TdApi.GetChat(chatId)) { result ->
                if (result is TdApi.Chat) {
                    updateChatInList(result)
                }
            }
        }
    }

    private fun onChatPositionUpdated(chatId: Long, position: TdApi.ChatPosition) {
        viewModelScope.launch {
            client?.send(TdApi.GetChat(chatId)) { result ->
                if (result is TdApi.Chat) {
                    updateChatInList(result)
                }
            }
        }
    }

    private fun updateChatInList(chat: TdApi.Chat) {
        viewModelScope.launch {
            val currentChats = _chats.value.toMutableList()
            val index = currentChats.indexOfFirst { it.id == chat.id }
            if (index != -1) {
                currentChats.removeAt(index)
            }
            currentChats.add(0, chat)
            _chats.value = currentChats.distinctBy { it.id }
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

    fun updateChatBackground(bg: String) {
        _chatBackground.value = bg
        settingsManager.saveChatBackground(bg)
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
        viewModelScope.launch {
            val currentStatus = _fileStatus.value.toMutableMap()
            currentStatus[file.id] = file
            _fileStatus.value = currentStatus
        }

        if (file.local.isDownloadingActive || file.remote.isUploadingActive) {
            val progress = if (file.expectedSize > 0) {
                val currentSize = if (file.local.isDownloadingActive) file.local.downloadedSize else file.remote.uploadedSize
                (currentSize.toDouble() / file.expectedSize * 100).toInt()
            } else 0

            downloadingFiles[file.id]?.let { fileName ->
                DownloadService.updateProgress(getApplication(), fileName, progress)
            }
        }

        if (file.local.isDownloadingCompleted) {
            val fileName = downloadingFiles.remove(file.id)
            viewModelScope.launch {
                val current = _downloadedFiles.value.toMutableMap()
                current[file.id] = file.local.path
                _downloadedFiles.value = current

                _downloadPath.value?.let { customPath ->
                    try {
                        val src = File(file.local.path)
                        val destDir = File(customPath)
                        if (!destDir.exists()) destDir.mkdirs()
                        val destFile = File(destDir, src.name)
                        src.copyTo(destFile, overwrite = true)
                    } catch (e: Exception) {
                        Log.e("Telegram", "Failed to copy to custom path", e)
                    }
                }
            }
        }
    }

    fun downloadFile(fileId: Int, fileName: String) {
        downloadingFiles[fileId] = fileName
        DownloadService.start(getApplication(), fileName)
        client?.send(TdApi.DownloadFile(fileId, 1, 0, 0, false)) { }
    }

    fun cancelDownload(fileId: Int) {
        client?.send(TdApi.CancelDownloadFile(fileId, false)) { }
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
                    if (_cloudDriveChatId.value != 0L) {
                        loadCloudDriveMessages()
                    }
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
                                val type = chat.type
                                if (type is TdApi.ChatTypePrivate) {
                                    fetchSenderInfo(TdApi.MessageSenderUser(type.userId))
                                }
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
                                    _chats.value = chatList
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

    fun sendMessage(chatId: Long, text: String, onComplete: (() -> Unit)? = null) {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, false)
        client?.send(TdApi.SendMessage(chatId, null, null, null, null, content)) { result ->
            if (result is TdApi.Error) {
                Log.e("Telegram", "Failed to send message: ${result.message}")
            } else {
                onComplete?.invoke()
            }
        }
    }

    fun sendDocument(chatId: Long, filePath: String, caption: String = "") {
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(filePath),
            null,
            false,
            TdApi.FormattedText(caption, emptyArray())
        )
        client?.send(TdApi.SendMessage(chatId, null, null, null, null, content)) { result ->
            if (result is TdApi.Message) {
                val doc = result.content as? TdApi.MessageDocument
                doc?.document?.document?.id?.let { fileId ->
                    downloadingFiles[fileId] = doc.document.fileName
                }
            }
            if (result is TdApi.Error) {
                Log.e("Telegram", "Failed to upload document: ${result.message}")
            } else {
                viewModelScope.launch { loadCloudDriveMessages() }
            }
        }
    }

    fun loadChatHistory(chatId: Long, fromMessageId: Long = 0L) {
        if (_isLoadingContent.value && fromMessageId != 0L) return
        if (fromMessageId == 0L) {
            _messages.value = emptyList()
            _pinnedMessage.value = null
            _isLoadingContent.value = true
            client?.send(TdApi.SearchChatMessages(chatId, null, "", null, 0L, 0, 1, TdApi.SearchMessagesFilterPinned())) { result ->
                if (result is TdApi.FoundChatMessages && result.messages.isNotEmpty()) {
                    viewModelScope.launch { _pinnedMessage.value = result.messages[0] }
                }
            }
        } else {
            _isLoadingContent.value = true
        }
        client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)) { result ->
            if (result is TdApi.Messages) {
                viewModelScope.launch {
                    val newMessages = result.messages.toList()
                    if (fromMessageId == 0L) _messages.value = newMessages
                    else {
                        val existingIds = _messages.value.map { it.id }.toSet()
                        _messages.value = _messages.value + newMessages.filter { it.id !in existingIds }
                    }
                    _isLoadingContent.value = false
                    newMessages.forEach { msg ->
                        requestThumbnails(msg)
                        fetchSenderInfo(msg.senderId)
                    }
                }
            } else {
                viewModelScope.launch { _isLoadingContent.value = false }
            }
        }
    }

    private fun fetchSenderInfo(senderId: TdApi.MessageSender?) {
        if (senderId is TdApi.MessageSenderUser) {
            if (_users.value.containsKey(senderId.userId)) return
            client?.send(TdApi.GetUser(senderId.userId)) { result ->
                if (result is TdApi.User) {
                    viewModelScope.launch {
                        val current = _users.value.toMutableMap()
                        current[result.id] = result
                        _users.value = current
                        result.profilePhoto?.small?.let { file ->
                            client?.send(TdApi.DownloadFile(file.id, 1, 0, 0, false)) { }
                        }
                    }
                }
            }
        }
    }

    private fun requestThumbnails(message: TdApi.Message) {
        val fileIds = mutableListOf<Int>()
        when (val content = message.content) {
            is TdApi.MessagePhoto -> {
                content.photo.sizes.lastOrNull()?.photo?.id?.let { fileIds.add(it) }
                content.photo.sizes.firstOrNull()?.photo?.id?.let { fileIds.add(it) }
            }
            is TdApi.MessageVideo -> content.video.thumbnail?.file?.id?.let { fileIds.add(it) }
            is TdApi.MessageDocument -> content.document.thumbnail?.file?.id?.let { fileIds.add(it) }
        }
        fileIds.forEach { id -> client?.send(TdApi.DownloadFile(id, 1, 0, 0, false)) { } }
    }

    fun loadSender(userId: Long) {
        fetchSenderInfo(TdApi.MessageSenderUser(userId))
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
            } else viewModelScope.launch { _isLoadingContent.value = false }
        }
    }

    fun updateProfile(firstName: String, lastName: String) { client?.send(TdApi.SetName(firstName, lastName)) { fetchMe() } }
    fun updateUsername(username: String) { client?.send(TdApi.SetUsername(username)) { fetchMe() } }
    fun updateBio(bio: String) { client?.send(TdApi.SetBio(bio)) { fetchMe() } }
    fun deleteProfilePhoto(photoId: Long) { client?.send(TdApi.DeleteProfilePhoto(photoId)) { fetchMe() } }

    fun deleteCloudDriveFile(messageId: Long) {
        client?.send(TdApi.DeleteMessages(_cloudDriveChatId.value, longArrayOf(messageId), true)) {
            viewModelScope.launch { loadCloudDriveMessages() }
        }
    }

    fun renameCloudDriveFolder(fid: String, newName: String) {
        val chatId = _cloudDriveChatId.value
        val metaMessage = _cloudDriveMessages.value.find { msg ->
            val text = (msg.content as? TdApi.MessageText)?.text?.text ?: ""
            text.startsWith(fid)
        }

        if (metaMessage != null) {
            val newText = "$fid $newName"
            client?.send(TdApi.EditMessageText(chatId, metaMessage.id, null, TdApi.InputMessageText(TdApi.FormattedText(newText, emptyArray()), null, false))) {
                viewModelScope.launch { loadCloudDriveMessages() }
            }
        } else {
            // Fallback for legacy folders (renaming all files)
            val legacyPrefix = if (fid.startsWith("/")) fid else "/$fid/"
            val messagesToUpdate = _cloudDriveMessages.value.filter { msg ->
                val caption = when (val content = msg.content) {
                    is TdApi.MessageDocument -> content.caption.text
                    is TdApi.MessageText -> content.text.text
                    else -> ""
                }
                caption.startsWith(legacyPrefix)
            }
            if (messagesToUpdate.isEmpty()) return
            var remaining = messagesToUpdate.size
            messagesToUpdate.forEach { msg ->
                val oldCaption = when (val content = msg.content) {
                    is TdApi.MessageDocument -> content.caption.text
                    is TdApi.MessageText -> content.text.text
                    else -> ""
                }
                val newCaption = oldCaption.replaceFirst(legacyPrefix, "/$newName/")
                val handler = Client.ResultHandler {
                    synchronized(this) {
                        remaining--
                        if (remaining == 0) viewModelScope.launch { loadCloudDriveMessages() }
                    }
                }
                when (val content = msg.content) {
                    is TdApi.MessageDocument -> client?.send(TdApi.EditMessageCaption(chatId, msg.id, null, TdApi.FormattedText(newCaption, emptyArray()), false), handler)
                    is TdApi.MessageText -> client?.send(TdApi.EditMessageText(chatId, msg.id, null, TdApi.InputMessageText(TdApi.FormattedText(newCaption, emptyArray()), null, false)), handler)
                }
            }
        }
    }

    fun deleteCloudDriveFolder(fid: String) {
        val chatId = _cloudDriveChatId.value
        val messagesToDelete = _cloudDriveMessages.value.filter { msg ->
            val caption = when (val content = msg.content) {
                is TdApi.MessageDocument -> content.caption.text
                is TdApi.MessageText -> content.text.text
                else -> ""
            }
            caption.startsWith(fid)
        }.map { it.id }.toLongArray()

        if (messagesToDelete.isNotEmpty()) {
            client?.send(TdApi.DeleteMessages(chatId, messagesToDelete, true)) {
                viewModelScope.launch { loadCloudDriveMessages() }
            }
        }
    }

    fun updateDownloadPath(path: String) { _downloadPath.value = path; settingsManager.saveDownloadPath(path) }
    fun updateVideoPlayer(player: String) { _videoPlayer.value = player; settingsManager.saveVideoPlayer(player) }

    fun setCloudDriveChatId(chatId: Long) {
        _cloudDriveChatId.value = chatId
        settingsManager.saveCloudDriveChatId(chatId)
        if (chatId != 0L) loadCloudDriveMessages()
    }

    fun loadCloudDriveMessages() {
        val chatId = _cloudDriveChatId.value
        if (chatId == 0L) {
            _cloudDriveMessages.value = emptyList()
            return
        }
        _isLoadingContent.value = true
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, 500, false)) { result ->
            if (result is TdApi.Messages) {
                viewModelScope.launch {
                    _cloudDriveMessages.value = result.messages.filter {
                        it.content is TdApi.MessageDocument || (it.content is TdApi.MessageText && (it.content as TdApi.MessageText).text.text.startsWith("/"))
                    }
                    _isLoadingContent.value = false
                    result.messages.forEach { requestThumbnails(it) }
                }
            } else viewModelScope.launch { _isLoadingContent.value = false }
        }
    }

    fun uploadFileToDrive(uri: android.net.Uri, folderPath: String) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                val tempFile = File(context.cacheDir, fileName)
                tempFile.outputStream().use { inputStream.copyTo(it) }
                sendDocument(_cloudDriveChatId.value, tempFile.absolutePath, caption = "$folderPath$fileName")
            } catch (e: Exception) { Log.e("Telegram", "Upload failed", e) }
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }
}
