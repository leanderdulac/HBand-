package com.example.data.ingest

import com.example.data.model.IngestResponse
import com.example.data.remote.HealthTechApiService
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response

/** Adapts old per-item fixtures to the batch envelope; maxBatchItems=1 is explicit in those tests. */
abstract class SingleOnlyTestApi : HealthTechApiService {
    override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
        val batch = JSONObject(Buffer().also { body.writeTo(it) }.readUtf8())
        val readings = batch.getJSONArray("readings")
        check(readings.length() == 1) { "Per-item fixture received multiple readings" }
        val row = readings.getJSONObject(0)
        val response = ingestWearableData(row.toString().toRequestBody(), row.getString("client_reading_id"))
        if (!response.isSuccessful) return Response.error(response.code(), response.errorBody()!!)
        val receipt = response.body() ?: return Response.success(null)
        val result = JSONObject().put("patient_id", receipt.patient_id).put("reading_id", receipt.reading_id)
            .put("ingest_status", receipt.ingest_status)
        val entry = JSONObject().put("index", 0).put("client_reading_id", receipt.client_reading_id)
            .put("status", receipt.ingest_status).put("result", result)
        return Response.success(JSONObject().put("patient_id", batch.getString("patient_id"))
            .put("results", JSONArray().put(entry)).toString().toResponseBody())
    }
}

/** Explicit synthetic current-contract receipt for tests of local storage/concurrency. */
fun withSyntheticReceipt(response: Response<IngestResponse>, request: RequestBody): Response<IngestResponse> {
    if (!response.isSuccessful || response.body() == null) return response
    val payload = JSONObject(Buffer().also { request.writeTo(it) }.readUtf8())
    return Response.success(response.code(), response.body()!!.copy(
        client_reading_id = payload.getString("client_reading_id"),
        patient_id = payload.getString("patient_id"),
        reading_id = "synthetic-stored-${payload.getString("client_reading_id")}",
    ))
}
