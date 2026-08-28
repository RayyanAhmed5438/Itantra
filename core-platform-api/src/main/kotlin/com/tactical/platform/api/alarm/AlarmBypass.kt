package com.tactical.platform.api.alarm

/**
 * Temporarily overrides system-level audio restrictions so an emergency
 * alert can actually be heard — bypassing Do Not Disturb and forcing
 * media volume to max. Implemented in platform-android. Consumed by
 * SystemSquelchBreaker (feature-emergency) and feature-sentry, both of
 * which need to guarantee an alert is audible regardless of the device's
 * current sound settings.
 */
interface AlarmBypass {

    /**
     * Bypasses Do Not Disturb and sets media volume to max. Expected to
     * be paired with a resetVolume() call once the alert finishes playing
     * — this method does not restore anything on its own.
     */
    suspend fun bypassDndAndMaxVolume()

    /**
     * Restores whatever DND/volume state was in effect before
     * bypassDndAndMaxVolume() was called.
     */
    suspend fun resetVolume()
}

/**
 * One real gap worth flagging, given how central
 * this is to the emergency path: what happens if
 * bypassDndAndMaxVolume() is called a second time
 * before resetVolume() runs from the first call?
 * (E.g., a second emergency alert arrives while the
 * first is still playing.) Nothing in the spec or in
 * this interface's shape answers that — worth raising
 * alongside the other open questions, since getting
 * this wrong could mean the second alert's resetVolume()
 * restores state to what it captured mid-override
 * rather than the true original pre-alert settings.
 */