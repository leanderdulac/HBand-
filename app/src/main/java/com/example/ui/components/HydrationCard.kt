package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HydrationLogEntity
import com.example.ui.theme.MinimalBorder

@Composable
fun HydrationCard(
    currentMl: Int,
    targetGoalMl: Int,
    logs: List<HydrationLogEntity>,
    onAddWater: (Int) -> Unit,
    onResetToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (currentMl.toFloat() / targetGoalMl.coerceAtLeast(1000)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "hydration_progress")
    val percentage = (progress * 100).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hydration_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
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
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = Color(0xFF0288D1),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Rastreamento de Hidratação",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = "Meta Diária • $targetGoalMl mL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (percentage >= 100) Color(0xFFE8F5E9) else Color(0xFFE0F2FE)
                ) {
                    Text(
                        text = if (percentage >= 100) "Meta Atingida!" else "$percentage% Concluído",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (percentage >= 100) Color(0xFF2E7D32) else Color(0xFF0288D1),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("hydration_percentage_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Progress Stats Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF0F9FF),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Total de Água Hoje",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF0369A1)
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$currentMl",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0C4A6E),
                                    modifier = Modifier.testTag("hydration_current_ml_text")
                                )
                                Text(
                                    text = " / $targetGoalMl mL",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF0288D1),
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "${(targetGoalMl - currentMl).coerceAtLeast(0)} mL restantes",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0288D1)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .testTag("hydration_progress_bar"),
                        color = Color(0xFF0288D1),
                        trackColor = Color(0xFFE0F2FE)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "REGISTRO RÁPIDO DE ÁGUA",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = Color(0xFF475569)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Add Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAddWater(250) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_250ml_water_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+250 mL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Copo", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                    }
                }

                Button(
                    onClick = { onAddWater(500) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_500ml_water_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+500 mL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Garrafa", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                    }
                }

                Button(
                    onClick = { onAddWater(750) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_750ml_water_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+750 mL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Jarra", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                    }
                }

                OutlinedButton(
                    onClick = onResetToday,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("reset_hydration_button")
                ) {
                    Text("Resetar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                }
            }
        }
    }
}
