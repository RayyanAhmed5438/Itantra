package com.tactical.app.di

import android.content.Context
import com.tactical.platform.api.ble.BleBeaconAdvertiser
import com.tactical.platform.api.ble.BleConnectionManager
import com.tactical.platform.api.ble.BleBeaconScanner
import com.tactical.platform.api.wifi.WifiDirectManager
import com.tactical.platform.ble.AndroidBleAdvertiser
import com.tactical.platform.ble.AndroidBleConnectionManager
import com.tactical.platform.ble.AndroidBleScanner
import com.tactical.platform.wifi.AndroidWifiDirectManager
import android.net.wifi.p2p.WifiP2pManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun provideBleBeaconAdvertiser(
        @ApplicationContext context: Context
    ): BleBeaconAdvertiser =
        AndroidBleAdvertiser(context)

    @Provides
    @Singleton
    fun provideBleBeaconScanner(
        @ApplicationContext context: Context
    ): BleBeaconScanner =
        AndroidBleScanner(context)

    @Provides
    @Singleton
    fun provideBleConnectionManager(
        @ApplicationContext context: Context,
        registry: com.tactical.platform.radio.BleConnectionRegistry,
        identityStore: DeviceIdentityStore
    ): BleConnectionManager =
        AndroidBleConnectionManager(
            context = context,
            registry = registry,
            localDeviceId = identityStore.deviceIdValue
        )

    @Provides
    @Singleton
    fun provideWifiDirectManager(
        @ApplicationContext context: Context,
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ): WifiDirectManager =
        AndroidWifiDirectManager(
            context,
            manager,
            channel
        )
}