package com.tactical.domain.speech

/**
 * Current availability state of one model asset, keyed by ModelIdentifier.
 * Emitted as a stream by ModelDownloadManager.ensureModelAvailable() so
 * callers can observe progress without polling.
 *
 * Failed is an addition on top of core.md's three specified cases (Ready/
 * Loading/Missing) — kept so a corrupted/checksum-failed bundled asset has
 * somewhere distinct to go, rather than being indistinguishable from
 * "never extracted yet" (Missing). Flagged here since it's a deliberate
 * deviation from the spec, not an oversight.
 */
sealed interface ModelStatus {

    data object Ready : ModelStatus

    data class Loading(val progress: Float) : ModelStatus {
        init {
            require(progress in 0f..1f) { "progress must be in 0.0..1.0" }
        }
    }

    data object Missing : ModelStatus

    data class Failed(val reason: String) : ModelStatus {
        init {
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }
}