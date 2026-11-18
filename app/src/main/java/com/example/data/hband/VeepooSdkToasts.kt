package com.example.data.hband

import com.veepoo.protocol.VPOperateManager

/** Veepoo's FunctionCheckUtil shows English Toasts; keep them off for the patient app. */
object VeepooSdkToasts {
    fun suppressFunctionNotSupported() {
        runCatching { VPOperateManager.setShowFunctionNotSupportToast(false) }
    }

    fun areFunctionNotSupportedToastsEnabled(): Boolean =
        runCatching { VPOperateManager.isShowFunctionNotSupportToast() }.getOrDefault(true)
}
