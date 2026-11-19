package com.next2u.healthsync.shared.ingest

import platform.Foundation.NSBundle

internal actual fun platformAppVersionHeader(): String? {
    val bundle = NSBundle.mainBundle
    val marketing = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    if (marketing == null || build == null) return null
    return AppVersionHeader.ios(marketing, build)
}
