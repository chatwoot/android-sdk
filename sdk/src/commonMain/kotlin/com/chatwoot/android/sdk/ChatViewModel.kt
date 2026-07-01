package com.chatwoot.android.sdk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwoot.android.sdk.data.ChatRepository
import com.chatwoot.android.sdk.data.ChatUiState
import com.chatwoot.android.sdk.data.PickedFile
import com.chatwoot.android.sdk.net.defaultHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class ChatViewModel : ViewModel() {
    private val client = defaultHttpClient()
    private val repository = ChatRepository(Chatwoot.config, client)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        repository.state
            .onEach { _state.value = it }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            runCatching { repository.connect(this) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Could not connect to Chatwoot",
                    )
                }
        }
    }

    fun send(content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.send(text) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Message failed to send")
                }
        }
    }

    fun sendAttachment(file: PickedFile) {
        viewModelScope.launch {
            runCatching { repository.sendAttachment(file) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Attachment failed to send")
                }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        client.close()
    }
}
