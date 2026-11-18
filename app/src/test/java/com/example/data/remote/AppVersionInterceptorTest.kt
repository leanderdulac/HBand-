package com.example.data.remote

import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppVersionInterceptorTest {
    @Test fun header_is_android_version_name_plus_version_code() {
        assertEquals("android-1.8.2+24", AppVersionInterceptor.header("1.8.2", 24))
        assertEquals("android-1.8.2+24", AppVersionInterceptor.header(" 1.8.2\n", 24))
        assertEquals("android-0+1", AppVersionInterceptor.header(" \n\t ", 1))
        assertEquals(
            "android-${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
            AppVersionInterceptor.header(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        )
        assertEquals("android-1.8.2+24", AppVersionInterceptor.header(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
    }

    @Test fun interceptor_sets_the_header_and_leaves_the_api_key() {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(AppVersionInterceptor("android-1.8.2+24"))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("synthetic").body("{}".toResponseBody()).build()
            }
            .build()
        try {
            client.newCall(
                Request.Builder().url("https://synthetic.invalid/api/v1/wearables/batch-ingest")
                    .header("X-API-Key", "synthetic-key")
                    .header("X-App-Version", "stale")
                    .build(),
            ).execute().close()
            assertEquals("android-1.8.2+24", sent!!.header("X-App-Version"))
            assertEquals("synthetic-key", sent!!.header("X-API-Key"))
            assertFalse(sent!!.header("X-App-Version")!!.contains(" "))
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
}
