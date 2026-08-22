package com.example.data.remote

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    const val DEFAULT_BASE_URL = "https://healthtech-secure-api-5794833455.us-central1.run.app/"
    // Nunca hardcode uma chave real aqui — esta constante existe apenas como último fallback
    // quando nem BuildConfig.HEALTHTECH_INGEST_API_KEY (via .env) nem a config salva pelo
    // usuário em Ajustes estão disponíveis. Configure a chave real via .env (gitignored) ou
    // na tela de Ajustes do app.
    const val DEFAULT_API_KEY = ""

    @Volatile
    private var customBaseUrl: String? = null

    @Volatile
    private var customApiKey: String? = null

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)
        customBaseUrl = prefs.getString("custom_api_base_url", null)
        customApiKey = prefs.getString("custom_api_key", null)
    }

    fun updateConfig(context: Context, newBaseUrl: String, newApiKey: String) {
        val formattedUrl = if (newBaseUrl.endsWith("/")) newBaseUrl else "$newBaseUrl/"
        customBaseUrl = formattedUrl
        customApiKey = newApiKey
        val prefs = context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("custom_api_base_url", formattedUrl)
            .putString("custom_api_key", newApiKey)
            .apply()
        rebuildRetrofit()
    }

    val currentBaseUrl: String
        get() = customBaseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL

    val apiKey: String
        get() {
            val custom = customApiKey
            if (!custom.isNullOrBlank()) return custom
            return try {
                val key = com.example.BuildConfig::class.java.getField("HEALTHTECH_INGEST_API_KEY").get(null) as? String ?: ""
                if (key.isNotEmpty() && key != "PAT-HBAND-001") key else DEFAULT_API_KEY
            } catch (e: Exception) {
                DEFAULT_API_KEY
            }
        }

    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                val key = apiKey
                if (key.isNotEmpty()) {
                    requestBuilder.header("X-API-Key", key)
                    requestBuilder.header("x-api-key", key)
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var _retrofit: Retrofit? = null

    @Volatile
    private var _apiService: HealthTechApiService? = null

    private fun rebuildRetrofit() {
        val url = currentBaseUrl
        val client = buildOkHttpClient()
        val retrofitInstance = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        _retrofit = retrofitInstance
        _apiService = retrofitInstance.create(HealthTechApiService::class.java)
    }

    val apiService: HealthTechApiService
        get() {
            var service = _apiService
            if (service == null) {
                synchronized(this) {
                    service = _apiService
                    if (service == null) {
                        rebuildRetrofit()
                        service = _apiService!!
                    }
                }
            }
            return service!!
        }
}

