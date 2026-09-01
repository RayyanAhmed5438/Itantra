package com.tactical.emergency.trigger

import kotlinx.coroutines.flow.StateFlow

interface PanicTrigger {
    fun startHold()
    fun releaseHold()
    fun state(): StateFlow<PanicTriggerState>
}
