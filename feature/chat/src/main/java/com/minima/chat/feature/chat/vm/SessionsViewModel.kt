package com.minima.chat.feature.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minima.chat.core.data.model.Session
import com.minima.chat.core.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val activeFlow = sessionRepository.observeSessions()
    private val archivedFlow = sessionRepository.observeArchived()

    val archivedCount: StateFlow<Int> = archivedFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sessions: StateFlow<List<Session>> =
        combine(activeFlow, archivedFlow, _query, _showArchived) { a, arch, q, showArch ->
            val base = if (showArch) a + arch else a
            val needle = q.trim().lowercase()
            if (needle.isEmpty()) base
            else base.filter { it.title.lowercase().contains(needle) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(v: String) {
        _query.value = v
    }

    fun toggleShowArchived() {
        _showArchived.value = !_showArchived.value
    }

    fun create(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val s = sessionRepository.create()
            onCreated(s.id)
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { sessionRepository.rename(id, title) }
    }

    fun delete(id: String) {
        viewModelScope.launch { sessionRepository.delete(id) }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { sessionRepository.setPinned(id, pinned) }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch { sessionRepository.setArchived(id, archived) }
    }
}
