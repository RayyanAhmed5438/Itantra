    }

    fun setSelectedLanguage(languageCode: String) {
        if (languageCode !in setOf("hi", "en")) return
        if (_uiState.value.languageLoadingCode != null) return
        if (_uiState.value.selectedLanguageCode == languageCode) return

        val previousLanguageCode = _uiState.value.selectedLanguageCode
        val wasContinuousCallMode =
            !_uiState.value.pttEnabled && _uiState.value.pttContinuousSession

        viewModelScope.launch {
            _uiState.update { it.copy(languageLoadingCode = languageCode) }

            try {
                if (wasContinuousCallMode) {
                    runCatching { pttController.stopContinuous() }
                }

                speechLanguagePreferences.setSelectedLanguageCode(languageCode)
                routingSpeechToText.onSelectedLanguageChanged(languageCode)

                // This indicator is tied to actual outgoing STT readiness.
                // It clears only after the new native STT model has finished
                // loading successfully. TTS warming is intentionally separate.
                routingSpeechToText.preloadSelectedLanguage()

                _uiState.update {
                    it.copy(
                        selectedLanguageCode = languageCode,
                        selectedLanguage = displayLanguageName(languageCode),
                        languageLoadingCode = null
                    )
                }

                // Restart Call Mode as soon as its new STT backend is ready;
                // do not hold the microphone off while TTS warms in the
                // background.
                if (wasContinuousCallMode && !_uiState.value.pttEnabled) {
                    runCatching { pttController.startContinuous() }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t

                // A failed load must not leave the UI claiming that the new
                // language is ready. Restore the previous selection.
                speechLanguagePreferences.setSelectedLanguageCode(previousLanguageCode)
                routingSpeechToText.onSelectedLanguageChanged(previousLanguageCode)

                _uiState.update {
                    it.copy(
                        selectedLanguageCode = previousLanguageCode,
                        selectedLanguage = displayLanguageName(previousLanguageCode),
                        languageLoadingCode = null
                    )
                }

                if (wasContinuousCallMode && !_uiState.value.pttEnabled) {
                    runCatching { pttController.startContinuous() }
                }
                return@launch
            }

            // Incoming multilingual TTS is warmed independently. It must not
            // extend the visible language-loading state or delay Call Mode.
            MmsTtsLanguage.fromIsoCode(languageCode)?.let { language ->
                viewModelScope.launch {
                    runCatching { mmsTtsEngine.preload(language) }
                }
            }
        }
    }

    fun setPttEnabled(enabled: Boolean) {
        val state = _uiState.value