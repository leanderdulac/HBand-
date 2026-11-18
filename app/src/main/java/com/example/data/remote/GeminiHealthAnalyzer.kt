package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiHealthAnalyzer {

    private const val TAG = "GeminiHealthAnalyzer"
    private const val GEMINI_MODEL = "gemini-3.6-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    @Volatile
    private var customApiKey: String? = null

    fun setCustomApiKey(key: String?) {
        customApiKey = key
    }

    @Volatile
    private var lastRateLimitMs: Long = 0L
    private const val RATE_LIMIT_COOLDOWN_MS = 60_000L // 60s cooldown on 429

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(BleLabNetworkInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateSevenDayInsight(
        metrics: List<HBandSensorMetricEntity>,
        profile: UserProfileEntity? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): String = withContext(Dispatchers.IO) {
        val promptText = buildInsightPrompt(metrics, profile, nowMillis)
            ?: return@withContext EMPTY_CLINICAL_INSIGHT
        val fallback = fallbackFrom(metrics, nowMillis)

        // Check rate limit cooldown
        if (System.currentTimeMillis() - lastRateLimitMs < RATE_LIMIT_COOLDOWN_MS) {
            Log.d(TAG, "In rate-limit cooldown. Using rule-based insight.")
            return@withContext fallback
        }

        val custom = customApiKey
        val apiKey = if (!custom.isNullOrBlank()) {
            custom
        } else {
            try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key is unconfigured or default placeholder. Using rule-based daily insight.")
            return@withContext fallback
        }

        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (response.isSuccessful && responseString.isNotEmpty()) {
                val responseJson = JSONObject(responseString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "")
                        if (text.isNotBlank()) {
                            return@withContext text.trim()
                        }
                    }
                }
            } else {
                if (response.code == 429) {
                    lastRateLimitMs = System.currentTimeMillis()
                    Log.w(TAG, "Gemini API Rate Limit 429 hit. Initiating 60s cooldown.")
                } else {
                    Log.w(TAG, "Gemini API HTTP Response ${response.code}: $responseString")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API Exception: ${e.localizedMessage}")
        }

        return@withContext fallback
    }
}
