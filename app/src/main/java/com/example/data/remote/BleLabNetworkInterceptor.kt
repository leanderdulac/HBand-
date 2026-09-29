package com.example.data.remote

import com.example.BuildConfig
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Fail before DNS/socket access, so no-INTERNET Android builds do not crash OkHttp's dispatcher. */
internal class BleLabNetworkInterceptor(private val enabled: Boolean = BuildConfig.BLE_LAB) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (enabled) throw IOException("Ensaio VE30 sem acesso à internet; nenhum envio realizado.")
        return chain.proceed(chain.request())
    }
}
