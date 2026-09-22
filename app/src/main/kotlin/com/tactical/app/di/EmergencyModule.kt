package com.tactical.app.di

import com.tactical.emergency.broadcast.EmergencyBroadcaster
import com.tactical.emergency.broadcast.RadiusEmergencyBroadcaster
import com.tactical.emergency.receiver.DefaultEmergencyReceiver
import com.tactical.emergency.receiver.EmergencyReceiver
import com.tactical.emergency.squelch.SquelchBreaker
import com.tactical.emergency.squelch.SystemSquelchBreaker
import com.tactical.platform.api.alarm.AlarmBypass
import com.tactical.platform.api.audio.AudioPlayer
import com.tactical.platform.api.flashlight.FlashlightController
import com.tactical.platform.api.speech.TextToSpeech
import com.tactical.engine.mesh.service.MeshService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object EmergencyModule {

    @Provides
    @Singleton
    fun provideEmergencyBroadcaster(
        meshService: MeshService
    ): EmergencyBroadcaster =
        RadiusEmergencyBroadcaster(meshService)

    @Provides
    @Singleton
    fun provideEmergencyReceiver(
        meshService: MeshService
    ): EmergencyReceiver =
        DefaultEmergencyReceiver(meshService)

    @Provides
    @Singleton
    fun provideEmergencySquelchBreaker(
        audioPlayer: AudioPlayer,
        alarmBypass: AlarmBypass,
        flashlightController: FlashlightController,
        textToSpeech: TextToSpeech
    ): SquelchBreaker =
        SystemSquelchBreaker(
            audioPlayer = audioPlayer,
            alarmBypass = alarmBypass,
            flashlightController = flashlightController,
            textToSpeech = textToSpeech,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
}
