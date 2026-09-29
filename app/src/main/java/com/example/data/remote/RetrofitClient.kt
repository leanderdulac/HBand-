package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    const val DEFAULT_BASE_URL = "https://healthtech-secure-api-5794833455.us-central1.run.app/"

    private fun builtInKey(): String = try {
        com.example.BuildConfig::class.java.getField("HEALTHTECH_INGEST_API_KEY").get(null) as? String ?: ""
    } catch (_: Exception) { "" }

    private val configuration = MutableStateFlow(IngestConfiguration.resolve(DEFAULT_BASE_URL, null, builtInKey()))
    val configurationState = configuration.map { it.status }

    @Synchronized
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)
        configuration.value = IngestConfiguration.resolve(
            prefs.getString("custom_api_base_url", null) ?: DEFAULT_BASE_URL,
            prefs.getString("custom_api_key", null), builtInKey(),
        )
    }

    @Synchronized
    fun updateConfig(context: Context, newBaseUrl: String, newApiKey: String) {
        val next = IngestConfiguration.resolve(newBaseUrl, newApiKey, builtInKey())
        require(next.status.origin != "Indisponível") { IngestConfiguration.INVALID_URL }
        val persisted = context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE).edit()
            .putString("custom_api_base_url", newBaseUrl.trim())
            .putString("custom_api_key", newApiKey.trim()).commit()
        check(persisted) { "Não foi possível salvar a configuração do serviço." }
        configuration.value = next
    }

    /** Capture once per queue attempt; later edits cannot mix old origin with new key. */
    fun captureIngestTransport(): IngestTransport = configuration.value.transport(::createService)

    internal fun createService(baseUrl: String, key: String): HealthTechApiService = Retrofit.Builder()
        .baseUrl(baseUrl).client(createClient(key))
        .addConverterFactory(MoshiConverterFactory.create()).build().create(HealthTechApiService::class.java)

    internal fun createClient(key: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BleLabNetworkInterceptor())
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().removeHeader("X-API-Key")
            if (key.isNotEmpty()) request.header("X-API-Key", key)
            chain.proceed(request.build())
        }
        .followRedirects(false).followSslRedirects(false)
        // Do not log URLs, headers or clinical payloads at this boundary.
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /** Existing consumers retain a facade, not a stale Retrofit instance. */
    val apiService: HealthTechApiService = object : HealthTechApiService {
        override suspend fun checkHealth() = configuration.value.healthService(::createService).checkHealth()
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?) =
            requireIngestService().ingestWearableData(body, idempotencyKey)
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?) =
            requireIngestService().batchIngestWearableData(body, idempotencyKey)
    }

    private fun requireIngestService(): HealthTechApiService {
        val transport = captureIngestTransport()
        return checkNotNull(transport.service) { transport.configurationError.orEmpty() }
    }
}
