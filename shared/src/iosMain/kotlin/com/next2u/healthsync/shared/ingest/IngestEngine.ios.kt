package com.next2u.healthsync.shared.ingest

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * Foreground-style URLSession: never discretionary, so a flush triggered by a BLE
 * wake-up (bluetooth-central / state restoration) goes out immediately instead of
 * being deferred by iOS. Fails fast offline; the queue keeps the reading.
 */
internal actual fun defaultIngestEngine(): HttpClientEngine = Darwin.create {
    configureSession {
        setDiscretionary(false)
        setWaitsForConnectivity(false)
        setAllowsCellularAccess(true)
        setAllowsConstrainedNetworkAccess(true)
        setAllowsExpensiveNetworkAccess(true)
    }
}
