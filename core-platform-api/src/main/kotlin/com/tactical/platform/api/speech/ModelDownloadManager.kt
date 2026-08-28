package com.tactical.platform.api.speech

import com.tactical.domain.speech.ModelIdentifier
import com.tactical.domain.speech.ModelStatus
import kotlinx.coroutines.flow.Flow

/**
 * Ensures a model asset is present locally — extracted from APK assets to
 * internal storage on first launch, not fetched over a network (this app
 * is fully offline). Implemented in platform-android as
 * DynamicModelDownloadManager.
 */
interface ModelDownloadManager {

    /**
     * Ensures the given model is present, emitting Loading(progress) while
     * copying/verifying, then Ready. Emits Missing if extraction fails —
     * or Failed(reason) for a more specific failure, since this module's
     * ModelStatus carries that case as an addition beyond the written spec
     * (see core-domain README).
     */
    fun ensureModelAvailable(id: ModelIdentifier): Flow<ModelStatus>
}