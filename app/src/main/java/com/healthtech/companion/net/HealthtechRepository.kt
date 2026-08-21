package com.healthtech.companion.net

import android.util.Log
import com.example.data.model.IngestResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

interface HealthtechApi {
    @Headers("Content-Type: application/json")
    @POST("api/v1/wearables/ingest")
    suspend fun ingestData(@Body body: okhttp3.RequestBody): Response<IngestResponse>
}

class HealthtechRepository private constructor(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            if (apiKey.isNotEmpty()) {
                requestBuilder.header("X-API-Key", apiKey)
                requestBuilder.header("x-api-key", apiKey)
            }
            chain.proceed(requestBuilder.build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val api = retrofit.create(HealthtechApi::class.java)

    suspend fun smokeHeart(patientId: String = "PAT-HBAND-001"): Response<IngestResponse> {
        Log.d("HealthtechRepository", "Executing smokeHeart test for patientId: $patientId against $baseUrl")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = isoFormat.format(Date())

        val jsonPayload = """
            {
              "patient_id": "$patientId",
              "device_id": "HBand-V100-TEST",
              "timestamp": "$timestamp",
              "heart_rate": 75,
              "blood_pressure": {
                "systolic": 120,
                "diastolic": 80
              },
              "spo2": 98,
              "temperature": 36.6,
              "steps": 1200,
              "calories": 45.0,
              "service": "healthtech-secure-api"
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonPayload.toRequestBody(mediaType)
        return api.ingestData(requestBody)
    }

    companion object {
        fun create(baseUrl: String, apiKey: String): HealthtechRepository {
            return HealthtechRepository(baseUrl, apiKey)
        }
    }
}
