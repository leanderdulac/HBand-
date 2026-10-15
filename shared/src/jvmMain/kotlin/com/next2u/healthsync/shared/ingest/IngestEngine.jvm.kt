package com.next2u.healthsync.shared.ingest

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun defaultIngestEngine(): HttpClientEngine = CIO.create()
