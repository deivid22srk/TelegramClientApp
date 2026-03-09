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

// Define UI States
sealed class AuthState {
    object Initial : AuthState()
    object EnterCredentials : AuthState() // App ID, Hash
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

    private var client: Client? = null
    
    // User credentials
    private var appId: Int = 0
    private var apiHash: String = ""
    private var phoneNumber: String = ""

    // Path for TDLib database
    private val appDir: String = application.filesDir.absolutePath + "/tdlib"

    init {
        // Prepare directory
        File(appDir).mkdirs()
    }

    // Step 1: Initialize Client with App ID / Hash
    fun initializeClient(id: String, hash: String) {
        try {
            this.appId = id.toInt()
            this.apiHash = hash
            _authState.value = AuthState.Loading
            
            createClient()
        } catch (e: NumberFormatException) {
            _authState.value = AuthState.Error("Invalid App ID (must be number)")
        }
    }

    private fun createClient() {
        // Create new client. We use the raw Client class for control, 
        // relying on the library to provide the native link.
        client = Client.create(
            { result -> 
                // Handle updates
                if (result is TdApi.UpdateAuthorizationState) {
                    onAuthStateUpdated(result.authorizationState)
                }
            },
            { e -> Log.e("Telegram", "Exception: ${e.localizedMessage}") },
            { e -> Log.e("Telegram", "Exception: ${e.localizedMessage}") }
        )
    }

    private fun onAuthStateUpdated(state: TdApi.AuthorizationState) {
        viewModelScope.launch {
            when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    val parameters = TdApi.TdlibParameters()
                    parameters.databaseDirectory = appDir
                    parameters.useMessageDatabase = true
                    parameters.useSecretChats = true
                    parameters.apiId = appId
                    parameters.apiHash = apiHash
                    parameters.systemLanguageCode = "en"
                    parameters.deviceModel = "Android"
                    parameters.systemVersion = "Example"
                    parameters.applicationVersion = "1.0"
                    
                    client?.send(TdApi.SetTdlibParameters(parameters)) { }
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    _authState.value = AuthState.EnterPhone
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    _authState.value = AuthState.EnterCode
                }
                is TdApi.AuthorizationStateWaitPassword -> {
                    _authState.value = AuthState.EnterPassword
                }
                is TdApi.AuthorizationStateReady -> {
                    fetchMe()
                }
                is TdApi.AuthorizationStateClosed -> {
                    _authState.value = AuthState.EnterCredentials
                }
                else -> {
                    // Ignore other states
                }
            }
        }
    }

    fun submitPhone(phone: String) {
        phoneNumber = phone
        _authState.value = AuthState.Loading
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
                // Revert to phone input?
                viewModelScope.launch { _authState.value = AuthState.EnterPhone }
            }
        }
    }

    fun submitCode(code: String) {
        _authState.value = AuthState.Loading
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
                viewModelScope.launch { _authState.value = AuthState.EnterCode }
            }
        }
    }

    private fun fetchMe() {
        client?.send(TdApi.GetMe()) { result ->
            viewModelScope.launch {
                if (result is TdApi.User) {
                    _authState.value = AuthState.LoggedIn(result)
                } else {
                    _authState.value = AuthState.Error("Failed to get user info")
                }
            }
        }
    }
}
