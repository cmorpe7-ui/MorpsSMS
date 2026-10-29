package com.realme.spamblocker.sms.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.realme.spamblocker.sms.data.local.entity.BlockedNumberEntity
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.data.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MessagesViewModel(val repository: SmsRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Multi-select / Batch Mode State
    private val _selectedThreads = MutableStateFlow<Set<String>>(emptySet())
    val selectedThreads: StateFlow<Set<String>> = _selectedThreads.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedThreads
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Inbox messages automatically filtered if user is typing in search
    val inboxMessages: StateFlow<List<MessageEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.inboxMessages
            } else {
                repository.searchInbox(query.trim())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val spamMessages: StateFlow<List<MessageEntity>> = repository.spamMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val starredMessages: StateFlow<List<MessageEntity>> = repository.starredMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = repository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val spamCount: StateFlow<Int> = repository.spamCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedNumbers: StateFlow<List<BlockedNumberEntity>> = repository.getBlockedNumbers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearching(active: Boolean) {
        _isSearching.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun toggleSelectThread(sender: String) {
        val current = _selectedThreads.value.toMutableSet()
        if (current.contains(sender)) {
            current.remove(sender)
        } else {
            current.add(sender)
        }
        _selectedThreads.value = current
    }

    fun selectAll(senders: List<String>) {
        _selectedThreads.value = senders.toSet()
    }

    fun clearSelection() {
        _selectedThreads.value = emptySet()
    }

    fun deleteSelectedThreads() {
        val toDelete = _selectedThreads.value.toList()
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMultipleThreads(toDelete)
            clearSelection()
        }
    }

    fun markSelectedAsSpam(sampleMessages: Map<String, String>) {
        val toBlock = _selectedThreads.value.toList()
        val samples = toBlock.mapNotNull { sampleMessages[it] }
        viewModelScope.launch(Dispatchers.IO) {
            repository.markMultipleAsSpam(toBlock, samples)
            clearSelection()
        }
    }

    fun markSelectedAsNotSpam(messages: List<MessageEntity>) {
        val toRestore = _selectedThreads.value.toList()
        viewModelScope.launch(Dispatchers.IO) {
            for (sender in toRestore) {
                val sample = messages.firstOrNull { it.sender == sender }
                if (sample != null) {
                    repository.markAsNotSpam(sample)
                }
            }
            clearSelection()
        }
    }

    fun togglePin(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.togglePin(message.sender, !message.isPinned)
        }
    }

    fun toggleStar(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleStar(message.id, !message.isStarred)
        }
    }

    fun blockNumber(number: String, reason: String = "Manual Block") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.blockNumber(number, reason)
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.unblockNumber(number)
        }
    }

    fun syncExistingSms(context: Context, forceFullSync: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncExistingSms(context, forceFullSync)
        }
    }

    fun markAsSpam(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAsSpam(message)
        }
    }

    fun markAsNotSpam(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAsNotSpam(message)
        }
    }

    fun deleteAndMarkSpam(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAndMarkSpam(message)
        }
    }

    fun markAsUnread(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAsUnread(message.id)
        }
    }

    fun toggleReadStatus(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleReadStatus(message.id, !message.isRead)
        }
    }

    fun deleteThread(sender: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteThread(sender)
        }
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessage(message)
        }
    }

    fun simulateIncomingSms(sender: String, body: String, isContact: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveIncomingMessage(sender, null, body, isContact)
        }
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    suspend fun importBackupJson(json: String): Boolean = repository.importBackupJson(json)

    suspend fun getAllMessages(): List<MessageEntity> = repository.getAllMessages()
}

class MessagesViewModelFactory(private val repository: SmsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessagesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessagesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
