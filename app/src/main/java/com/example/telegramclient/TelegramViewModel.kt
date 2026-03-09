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
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.EnterCredentials)
    val authState = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _videos = MutableStateFlow<List<TdApi.Message>>(emptyList())
    val videos = _videos.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent = _isLoadingContent.asStateFlow()

    private var client: Client? = null
    private var appId: Int = 0
    private var apiHash: String = ""
    private val appDir: String = application.filesDir.absolutePath + "/tdlib"

    init {
        File(appDir).mkdirs()
    }

    fun initializeClient(id: String, hash: String) {
        try {
            this.appId = id.toInt()
            this.apiHash = hash
            _authState.value = AuthState.Loading
            createClient()
        } catch (e: NumberFormatException) {
            _authState.value = AuthState.Error("Invalid App ID")
        }
    }

    private fun createClient() {
        client = Client.create(
            { result -> 
                if (result is TdApi.UpdateAuthorizationState) {
                    onAuthStateUpdated(result.authorizationState)
                }
            },
            { e -> Log.e("Telegram", "Error: ${e.localizedMessage}") },
            { e -> Log.e("Telegram", "Error: ${e.localizedMessage}") }
        )
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
                is TdApi.AuthorizationStateClosed -> _authState.value = AuthState.EnterCredentials
                else -> {}
            }
        }
    }

    fun submitPhone(phone: String) {
        _authState.value = AuthState.Loading
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result is TdApi.Error) viewModelScope.launch { _authState.value = AuthState.EnterPhone }
        }
    }

    fun submitCode(code: String) {
        _authState.value = AuthState.Loading
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) viewModelScope.launch { _authState.value = AuthState.EnterCode }
        }
    }

    private fun fetchMe() {
        client?.send(TdApi.GetMe()) { result ->
            if (result is TdApi.User) viewModelScope.launch { _authState.value = AuthState.LoggedIn(result) }
        }
    }

    fun loadChats() {
        _isLoadingContent.value = true
        // Get up to 100 chats
        client?.send(TdApi.GetChats(null, 100)) { result ->
            if (result is TdApi.Chats) {
                val chatList = mutableListOf<TdApi.Chat>()
                var count = 0
                result.chatIds.forEach { chatId ->
                    client?.send(TdApi.GetChat(chatId)) { chat ->
                        if (chat is TdApi.Chat) {
                            synchronized(chatList) { chatList.add(chat) }
                        }
                        count++
                        if (count == result.chatIds.size) {
                            viewModelScope.launch {
                                _chats.value = chatList.filter { 
                                    it.type is TdApi.ChatTypeSupergroup || it.type is TdApi.ChatTypeBasicGroup 
                                }
                                _isLoadingContent.value = false
                            }
                        }
                    }
                }
            } else {
                viewModelScope.launch { _isLoadingContent.value = false }
            }
        }
    }

    fun loadVideos(chatId: Long) {
        _videos.value = emptyList()
        _isLoadingContent.value = true
        // Search for videos in the chat
        client?.send(TdApi.SearchChatMessages(chatId, "", null, 0, 0, 100, TdApi.SearchMessagesFilterVideo(), 0)) { result ->
            if (result is TdApi.Messages) {
                viewModelScope.launch {
                    _videos.value = result.messages.toList()
                    _isLoadingContent.value = false
                }
            } else {
                viewModelScope.launch { _isLoadingContent.value = false }
            }
        }
    }
}
