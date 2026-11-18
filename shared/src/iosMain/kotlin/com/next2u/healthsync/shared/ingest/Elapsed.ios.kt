package com.next2u.healthsync.shared.ingest

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun defaultElapsedMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
