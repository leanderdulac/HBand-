package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MinimalBorder
import com.example.util.ProgressImageGenerator
import com.example.util.ShareProgressData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareProgressCard(
    sensorMetrics: List<HBandSensorMetricEntity>,
    hydrationMl: Int,
    breathingSeconds: Int,
    onGenerateShareData: (ShareProgressData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("share_progress_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Preparar um cartão dos registros",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1E1B4B)
                        )
                        Text(
                            text = "Confira os dados antes de escolher com quem compartilhar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4338CA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE0E7FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "O cartão reúne os registros disponíveis dos últimos 7 dias. Cada informação indica seu período. Preparar o cartão ainda não envia nada.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF374151)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SharePreparationButton(
                        prepare = {
                            withContext(Dispatchers.Default) {
                                ProgressImageGenerator.generateWeeklyProgressImage(
                                    context = context,
                                    metrics = sensorMetrics,
                                    hydrationMl = hydrationMl,
                                    breathingSeconds = breathingSeconds
                                )
                            }
                        },
                        onPrepared = onGenerateShareData,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharePreparationButton(prepare: suspend () -> ShareProgressData, onPrepared: (ShareProgressData) -> Unit) {
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (!preparing) {
                preparing = true
                failed = false
                scope.launch {
                    try {
                        onPrepared(prepare())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        preparing = false
                    }
                }
            }
        },
        enabled = !preparing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("share_progress_button"),
    ) { Text(if (preparing) "Preparando cartão…" else "Preparar cartão", style = MaterialTheme.typography.labelLarge) }
    if (preparing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("share_preparation_progress"))
    if (failed) Text(
        "Não foi possível preparar o cartão. Tente novamente.",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("share_preparation_error"),
    )
}
