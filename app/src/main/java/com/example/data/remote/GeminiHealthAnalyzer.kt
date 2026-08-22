package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateSevenDayInsight(metrics: List<HBandSensorMetricEntity>): String = withContext(Dispatchers.IO) {
        if (metrics.isEmpty()) {
            return@withContext "Nenhuma telemetria registrada no banco de dados local nos últimos 7 dias. Conecte seu dispositivo para gerar análises de saúde com IA."
        }

        val sevenDaysAgoMs = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val recentMetrics = metrics.filter { it.timestampMillis >= sevenDaysAgoMs }.ifEmpty { metrics }

        val recordCount = recentMetrics.size
        val avgHr = recentMetrics.map { it.heartRate }.average().toInt()
        val minHr = recentMetrics.minOf { it.heartRate }
        val maxHr = recentMetrics.maxOf { it.heartRate }
        val avgHrv = recentMetrics.map { it.hrvScore }.average().toInt()
        val avgSys = recentMetrics.map { it.systolicBp }.average().toInt()
        val avgDia = recentMetrics.map { it.diastolicBp }.average().toInt()
        val totalSteps = recentMetrics.maxOfOrNull { it.steps } ?: 0
        val totalCalories = recentMetrics.maxOfOrNull { it.calories }?.toInt() ?: 0
        val maxSleepMins = recentMetrics.maxOfOrNull { it.deepSleepMinutes + it.lightSleepMinutes } ?: 0
        val activeMins = (recentMetrics.count { it.heartRate >= 75 || it.steps > 0 } * 3.5).toInt()

        // Check rate limit cooldown
        if (System.currentTimeMillis() - lastRateLimitMs < RATE_LIMIT_COOLDOWN_MS) {
            Log.d(TAG, "In rate-limit cooldown. Using rule-based insight.")
            return@withContext generateFallbackInsight(avgHr, avgHrv, totalSteps, activeMins)
        }

        val promptText = """
            Você é um assistente pessoal de saúde com IA analisando a telemetria dos sensores de um dispositivo vestível de um usuário nos últimos 7 dias a partir do banco de dados local. Responda ESTRITAMENTE em Português do Brasil (PT-BR).
            
            Resumo dos Dados dos Últimos 7 Dias:
            - Amostras Registradas: $recordCount métricas
            - Frequência Cardíaca Média: $avgHr BPM (Mín: $minHr, Máx: $maxHr)
            - Pontuação Média de VFC (HRV): $avgHrv / 100
            - Pressão Arterial Média: $avgSys/$avgDia mmHg
            - Pico de Passos Diários: $totalSteps passos
            - Movimento Ativo: ~$activeMins minutos
            - Calorias Diárias Estimadas: $totalCalories kcal
            - Sono Registrado: ${maxSleepMins / 60}h ${maxSleepMins % 60}m
            
            Forneça um resumo de saúde conciso de 2 a 3 frases e uma recomendação prática. Foque no equilíbrio cardiovascular, na recuperação/VFC e no nível de atividade. Seja encorajador, objetivo e profissional. NÃO use títulos em markdown nem listas com marcadores. Escreva totalmente em português do Brasil.
        """.trimIndent()

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
            return@withContext generateFallbackInsight(avgHr, avgHrv, totalSteps, activeMins)
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

        return@withContext generateFallbackInsight(avgHr, avgHrv, totalSteps, activeMins)
    }

    private fun generateFallbackInsight(avgHr: Int, avgHrv: Int, steps: Int, activeMins: Int): String {
        val hrvStatus = if (avgHrv >= 65) "excelente recuperação do sistema nervoso autônomo" else "capacidade moderada de recuperação"
        val hrStatus = if (avgHr in 60..80) "faixa cardiovascular de repouso estável" else "esforço fisiológico elevado"
        val actStatus = if (steps >= 8000 || activeMins >= 30) "ótimos níveis de atividade física" else "movimento diário constante"

        return "Nos últimos 7 dias, sua frequência cardíaca média foi de $avgHr BPM, refletindo uma $hrStatus, com pontuação de VFC de $avgHrv ($hrvStatus). Você registrou ~$activeMins minutos ativos e $steps passos máximos, mantendo $actStatus nos seus registros de telemetria."
    }
}
