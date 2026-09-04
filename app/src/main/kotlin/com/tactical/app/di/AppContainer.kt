package com.tactical.app.di

import android.content.Context
import com.tactical.domain.identity.DeviceId
import com.tactical.engine.discovery.catalog.InMemoryDeviceCatalog
import com.tactical.engine.discovery.proximity.RssiProximityEstimator
import com.tactical.engine.discovery.scanner.CompositeBeaconScanner
import com.tactical.engine.discovery.service.DefaultDiscoveryService
import com.tactical.engine.mesh.deduplication.RollingBloomFilter
import com.tactical.engine.mesh.quality.RssiLinkQualityMonitor
import com.tactical.engine.mesh.router.FloodMeshRouter
import com.tactical.engine.mesh.ttl.DecrementingTtlTracker
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import com.tactical.platform.speech.FakeSpeechToText
import com.tactical.platform.speech.FakeTextToSpeech
import com.tactical.protocol.hashing.XxHashPacketHasher
import com.tactical.protocol.serialization.BinaryPacketSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class AppContainer(val context: Context) {

    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val localDeviceId = DeviceId("SENTINEL-COMMANDER")

    val serializer = BinaryPacketSerializer()
    val hasher = XxHashPacketHasher(serializer)
    val dedupFilter = RollingBloomFilter()
    val ttlTracker = DecrementingTtlTracker()

    val floodRouter = FloodMeshRouter(
        localDeviceId = localDeviceId,
        hasher = hasher,
        dedup = dedupFilter,
        ttlTracker = ttlTracker
    )

    val stubTransport = object : RadioTransport {
        private val rawFlow = MutableSharedFlow<RawPacket>()
        override fun incoming(): Flow<RawPacket> = rawFlow
        override suspend fun broadcast(raw: RawPacket): com.tactical.domain.result.TacticalResult<Unit> {
            return com.tactical.domain.result.TacticalResult.Success(Unit)
        }
    }

    val linkQualityMonitor = RssiLinkQualityMonitor()
    val deviceCatalog = InMemoryDeviceCatalog()
    val proximityEstimator = RssiProximityEstimator()

    val fakeStt = FakeSpeechToText()
    val fakeTts = FakeTextToSpeech()
}
