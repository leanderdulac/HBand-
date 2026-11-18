package com.example.data.remote

import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Marks every HealthTech Core request with the app build that sent it.
 *
 * Wire format: `X-App-Version: android-<versionName>+<versionCode>`
 * (for example `android-1.8.3+25`). The value is a header, not a JSON field,
 * so it does not change the ingest body schema. Health, single ingest and
 * batch ingest all go through [RetrofitClient.createClient].
 */
internal class AppVersionInterceptor(
    private val headerValue: String = header(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER, headerValue)
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "X-App-Version"

        /** Printable ASCII only, so a stray newline cannot split the header. */
        fun header(versionName: String, versionCode: Int): String {
            val name = versionName.trim().filter { it.code in 0x21..0x7E }.ifEmpty { "0" }
            return "android-$name+$versionCode"
        }
    }
}
