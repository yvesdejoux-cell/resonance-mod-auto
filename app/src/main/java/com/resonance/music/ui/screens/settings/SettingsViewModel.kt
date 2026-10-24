package com.resonance.music.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.repository.AuthRepository
import com.resonance.music.data.repository.SettingsStore
import com.resonance.music.ui.theme.AppTheme
import com.resonance.music.ui.theme.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App language override, applied via [AppCompatDelegate.setApplicationLocales]. */
enum class AppLanguage(val tag: String?, val displayName: String) {
    SYSTEM(null, "System"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    DUTCH("nl", "Nederlands");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val gaplessPlayback: Boolean = true,
    val scrobbleEnabled: Boolean = true,
    val currentTheme: AppTheme = AppTheme.NEON_PULSE,
    val currentLanguage: AppLanguage = AppLanguage.SYSTEM
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsStore: SettingsStore,
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val creds = authRepository.getCredentials()
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotEmpty() }
            _uiState.value = SettingsUiState(
                serverUrl = creds?.serverUrl ?: "",
                username = creds?.username ?: "",
                gaplessPlayback = settingsStore.gaplessPlayback.first(),
                scrobbleEnabled = settingsStore.scrobbleEnabled.first(),
                currentTheme = themeRepository.currentTheme.first(),
                currentLanguage = AppLanguage.fromTag(currentTag)
            )
        }
    }

    fun setTheme(theme: AppTheme) {
        _uiState.value = _uiState.value.copy(currentTheme = theme)
        viewModelScope.launch { themeRepository.setTheme(theme) }
    }

    /** Applies the chosen app language immediately (AppCompat persists it automatically). */
    fun setLanguage(language: AppLanguage) {
        _uiState.value = _uiState.value.copy(currentLanguage = language)
        val locales = if (language.tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(gaplessPlayback = enabled)
        viewModelScope.launch { settingsStore.setGaplessPlayback(enabled) }
    }

    fun setScrobbleEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(scrobbleEnabled = enabled)
        viewModelScope.launch { settingsStore.setScrobbleEnabled(enabled) }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
