package io.github.univjh.pleosapp.data

import androidx.test.platform.app.InstrumentationRegistry
import io.github.univjh.pleosapp.vehicle.PleosSignalSource
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in: requires a running Pleos Virtual Console publishing vehicle signals. */
class SdkSmokeTest {
    @Test fun receivesFreshVehicleSamples() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sdkSmoke") == "true")
        val source = PleosSignalSource(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            source.start()
            val frames = withTimeout(15_000) { source.frames.take(50).toList() }
            println("SDK_STATUS: ${source.status.value}")
            println("SDK_FRAMES: valid=${frames.count { it.valid }}/${frames.size}; first=${frames.first()}; last=${frames.last()}")
            assertTrue("Fresh speed/gear data must be received", frames.any { it.valid })
        } finally { source.stop() }
    }
}
