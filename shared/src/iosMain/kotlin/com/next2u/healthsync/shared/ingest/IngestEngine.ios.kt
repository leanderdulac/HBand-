package com.next2u.healthsync.shared.ingest

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun defaultIngestEngine(): HttpClientEngine = Darwin.create()
