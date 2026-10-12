package com.minima.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minima.chat.core.data.model.AppearanceSetting
import com.minima.chat.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val appearance: StateFlow<AppearanceSetting> = settingsRepository.settings
        .map { it.appearance }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSetting.System)
}
