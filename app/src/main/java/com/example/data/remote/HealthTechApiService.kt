package com.example.data.remote

import com.example.data.model.HealthCheckResponse
import com.example.data.model.IngestResponse
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface HealthTechApiService {

    @GET("api/health")
    suspend fun checkHealth(): Response<HealthCheckResponse>

    @Headers("Content-Type: application/json")
    @POST("api/v1/wearables/ingest")
    suspend fun ingestWearableData(
        @Body body: RequestBody,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): Response<IngestResponse>

    @Headers("Content-Type: application/json")
    @POST("api/v1/wearables/batch-ingest")
    suspend fun batchIngestWearableData(
        @Body body: RequestBody,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): Response<ResponseBody>
}
