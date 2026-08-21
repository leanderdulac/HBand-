package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalBorder
import kotlinx.coroutines.delay

enum class BreathingPhase(val label: String, val durationSeconds: Int, val color: Color) {
    INHALE("Inspire profundamente...", 4, Color(0xFF0288D1)),
    HOLD("Segure a respiração...", 4, Color(0xFF7C3AED)),
    EXHALE("Expire devagar...", 4, Color(0xFF059669)),
    REST("Pausa & Relaxamento...", 2, Color(0xFFD97706))
}

@Composable
fun BreathingExerciseCard(
    totalBreathingSeconds: Int,
    onSaveSession: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var phaseSecondsRemaining by remember { mutableIntStateOf(BreathingPhase.INHALE.durationSeconds) }
    var sessionTotalSeconds by remember { mutableIntStateOf(0) }

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerVibrationForPhase(phase: BreathingPhase) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = when (phase) {
                        BreathingPhase.INHALE -> longArrayOf(0, 150, 100, 200, 100, 300)
                        BreathingPhase.HOLD -> longArrayOf(0, 80, 500, 80)
                        BreathingPhase.EXHALE -> longArrayOf(0, 300, 100, 200, 100, 100)
                        BreathingPhase.REST -> longArrayOf(0, 50)
                    }
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        } catch (e: Exception) {
            // Ignore if vibration unavailable or denied
        }
    }

    // Active timer loop
    LaunchedEffect(isRunning) {
        if (isRunning) {
            triggerVibrationForPhase(currentPhase)
            while (isRunning) {
                delay(1000L)
                sessionTotalSeconds++
                phaseSecondsRemaining--

                if (phaseSecondsRemaining <= 0) {
                    currentPhase = when (currentPhase) {
                        BreathingPhase.INHALE -> BreathingPhase.HOLD
                        BreathingPhase.HOLD -> BreathingPhase.EXHALE
                        BreathingPhase.EXHALE -> BreathingPhase.REST
                        BreathingPhase.REST -> BreathingPhase.INHALE
                    }
                    phaseSecondsRemaining = currentPhase.durationSeconds
                    triggerVibrationForPhase(currentPhase)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                vibrator?.cancel()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }

    // Smooth scaling animation for the breathing ring
    val targetScale = when (currentPhase) {
        BreathingPhase.INHALE -> 1.35f
        BreathingPhase.HOLD -> 1.35f
        BreathingPhase.EXHALE -> 0.85f
        BreathingPhase.REST -> 0.85f
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isRunning) targetScale else 1.0f,
        animationSpec = tween(
            durationMillis = if (isRunning) currentPhase.durationSeconds * 1000 else 500,
            easing = LinearEasing
        ),
        label = "breathing_circle_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("breathing_exercise_card"),
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
                            .background(Color(0xFFECFDF5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Respiração Guiada",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = "Coaching por Vibração • Relaxamento Caixa",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFECFDF5)
                ) {
                    Text(
                        text = "Total: ${totalBreathingSeconds / 60}m ${totalBreathingSeconds % 60}s",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF047857),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("total_breathing_duration_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive Breathing Visual Circle Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .background(currentPhase.color.copy(alpha = 0.2f))
                )

                // Inner Core Circle
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(if (isRunning) (animatedScale * 0.85f) else 1.0f)
                        .clip(CircleShape)
                        .background(currentPhase.color),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isRunning) {
                            Text(
                                text = "$phaseSecondsRemaining",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.testTag("breathing_phase_seconds_text")
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phase Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = currentPhase.color.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, currentPhase.color.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRunning) currentPhase.label else "Pronto para iniciar sessão de relaxamento",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = currentPhase.color,
                        modifier = Modifier.testTag("breathing_phase_label_text")
                    )

                    Text(
                        text = "Sessão: ${sessionTotalSeconds / 60}m ${sessionTotalSeconds % 60}s",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF334155),
                        modifier = Modifier.testTag("session_timer_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isRunning = !isRunning },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF059669)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_breathing_exercise_button")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "Pausar" else "Iniciar Ciclo",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (sessionTotalSeconds > 0) {
                    Button(
                        onClick = {
                            isRunning = false
                            onSaveSession(sessionTotalSeconds)
                            sessionTotalSeconds = 0
                            phaseSecondsRemaining = BreathingPhase.INHALE.durationSeconds
                            currentPhase = BreathingPhase.INHALE
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_breathing_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Concluir e Salvar",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
