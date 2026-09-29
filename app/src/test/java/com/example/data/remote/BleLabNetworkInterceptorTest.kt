package com.example.data.remote

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BleLabNetworkInterceptorTest {
    private fun client(lab: Boolean, reachedTransport: AtomicInteger) = OkHttpClient.Builder()
        .addInterceptor(BleLabNetworkInterceptor(lab))
        .addInterceptor { chain ->
            reachedTransport.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("synthetic").body("{}".toResponseBody()).build()
        }.build()

    @Test fun lab_async_request_reports_failure_without_reaching_transport() {
        val reached = AtomicInteger()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<IOException>()
        val client = client(true, reached)
        try {
            client.newCall(Request.Builder().url("https://example.invalid/").build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { failure.set(e); completed.countDown() }
                override fun onResponse(call: Call, response: Response) { response.close(); completed.countDown() }
            })
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertTrue(failure.get()?.message?.contains("nenhum envio") == true)
            assertEquals(0, reached.get())
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun normal_build_preserves_existing_transport() {
        val reached = AtomicInteger()
        val client = client(false, reached)
        client.newCall(Request.Builder().url("https://example.invalid/").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(1, reached.get())
        client.connectionPool.evictAll()
    }
}
