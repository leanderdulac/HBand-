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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalBreathingPhaseColors
import com.example.ui.theme.LocalStatusColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import com.example.ui.BreathingSaveState
import com.example.ui.BreathingSaveStatus
import java.util.UUID

enum class BreathingPhase(val label: String, val durationSeconds: Int) {
    INHALE("Inspire profundamente...", 4),
    HOLD("Segure a respiração...", 4),
    EXHALE("Expire devagar...", 4),
    REST("Pausa & Relaxamento...", 2),
}

@Composable
private fun BreathingPhase.themeColor(): Color {
    val colors = LocalBreathingPhaseColors.current
    return when (this) {
        BreathingPhase.INHALE -> colors.inhale
        BreathingPhase.HOLD -> colors.hold
        BreathingPhase.EXHALE -> colors.exhale
        BreathingPhase.REST -> colors.rest
    }
}

@Composable
fun BreathingExerciseCard(
    totalBreathingSeconds: Int?,
    onSaveSession: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    saveState: BreathingSaveState? = null,
    readFailed: Boolean = false,
    onRetryRead: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Keep only the counted draft across recreation. Running deliberately resets
    // to false so no time is inferred or saved while the UI is being recreated.
    var isRunning by remember { mutableStateOf(false) }
    var currentPhase by rememberSaveable { mutableStateOf(BreathingPhase.INHALE) }
    var phaseSecondsRemaining by rememberSaveable { mutableIntStateOf(BreathingPhase.INHALE.durationSeconds) }
    var sessionTotalSeconds by rememberSaveable { mutableIntStateOf(0) }
    var requestToken by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRetry by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val receipt = saveState?.takeIf { it.token == requestToken && it.seconds == sessionTotalSeconds }
    val saving = requestToken != null && receipt?.status == BreathingSaveStatus.SAVING
    val unconfirmed = requestToken != null && receipt?.status != BreathingSaveStatus.SAVED && !saving
    val anySaving = saveState?.status == BreathingSaveStatus.SAVING

    fun clearDraft() {
        isRunning = false
        sessionTotalSeconds = 0
        phaseSecondsRemaining = BreathingPhase.INHALE.durationSeconds
        currentPhase = BreathingPhase.INHALE
        requestToken = null
    }

    fun submit() {
        if (sessionTotalSeconds <= 0 || anySaving) return
        isRunning = false
        val token = UUID.randomUUID().toString()
        requestToken = token
        onSaveSession(token, sessionTotalSeconds)
    }

    LaunchedEffect(receipt) {
        if (receipt?.status == BreathingSaveStatus.SAVED) clearDraft()
    }

    if (confirmRetry && unconfirmed) {
        BreathingConfirmationDialog(
            title = "Conferir antes de tentar novamente",
            explanation = "O tempo pode já ter sido salvo. Confira o total salvo. Uma nova tentativa pode duplicar esse tempo.",
            confirmLabel = "Conferi; salvar novamente",
            confirmEnabled = !anySaving,
            onConfirm = { confirmRetry = false; submit() },
            onDismiss = { confirmRetry = false },
        )
    }
    if (confirmDiscard && unconfirmed) {
        BreathingConfirmationDialog(
            title = "Encerrar sem salvar novamente?",
            explanation = "Isso descarta somente o tempo deste exercício na tela. Um registro que já tenha sido salvo continuará no celular.",
            confirmLabel = "Descartar rascunho",
            confirmEnabled = !anySaving,
            onConfirm = { confirmDiscard = false; clearDraft() },
            onDismiss = { confirmDiscard = false },
        )
    }

    val vibrator = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } // Haptics are optional; retain the exercise and its draft.
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
                if (!isRunning || requestToken != null) break
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

    DisposableEffect(lifecycleOwner, vibrator) {
        fun cancelVibration() {
            try {
                vibrator?.cancel()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isRunning = false
                cancelVibration()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cancelVibration()
        }
    }

    // Smooth scaling animation for the breathing ring
    val targetScale = when (currentPhase) {
        BreathingPhase.INHALE -> 1.35f
        BreathingPhase.HOLD -> 1.35f
        BreathingPhase.EXHALE -> 0.85f
        BreathingPhase.REST -> 0.85f
    }

    val phaseColor = currentPhase.themeColor()
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(LocalStatusColors.current.normalContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = LocalStatusColors.current.normalForeground,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Respiração Guiada",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Orientação visual e vibração",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LocalStatusColors.current.normalContainer
                ) {
                    Text(
                        text = if (readFailed) "Tempo salvo indisponível" else totalBreathingSeconds?.let { "Tempo salvo: ${it / 60}\u00A0min ${it % 60}\u00A0s" }
                            ?: "Carregando tempo salvo…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalStatusColors.current.normalForeground,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("total_breathing_duration_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (readFailed) LocalReadNotice("o tempo salvo", onRetryRead)

            Text(
                "Para guardar o tempo, toque em Concluir e salvar e aguarde a confirmação.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Breathing Visual Circle Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulse Ring
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .background(phaseColor.copy(alpha = 0.2f))
                )

                // Inner Core Circle
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(if (isRunning) (animatedScale * 0.85f) else 1.0f)
                        .clip(CircleShape)
                        .background(phaseColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isRunning) {
                            Text(
                                text = "$phaseSecondsRemaining",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (phaseColor.luminance() > 0.179f) Color.Black else Color.White,
                                modifier = Modifier.testTag("breathing_phase_seconds_text")
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
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
                color = phaseColor.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isRunning) currentPhase.label else if (sessionTotalSeconds > 0) "Exercício pausado" else "Toque em Iniciar quando estiver pronto.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("breathing_phase_label_text")
                    )

                    Text(
                        text = "Tempo neste exercício: ${sessionTotalSeconds / 60}\u00A0min ${sessionTotalSeconds % 60}\u00A0s",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("session_timer_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { if (requestToken == null) isRunning = !isRunning },
                    enabled = requestToken == null && !anySaving,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("toggle_breathing_exercise_button")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "Pausar" else if (sessionTotalSeconds > 0) "Continuar exercício" else "Iniciar",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (sessionTotalSeconds > 0) {
                    if (saving || unconfirmed) Text(
                        text = if (saving) "Salvando o tempo deste exercício…" else "Gravação não confirmada. O tempo deste exercício foi mantido. Confira o total salvo antes de decidir o que fazer.",
                        modifier = Modifier.testTag("breathing_save_feedback"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = {
                            if (requestToken == null) submit()
                            else if (unconfirmed) confirmRetry = true
                        },
                        enabled = !anySaving && receipt?.status != BreathingSaveStatus.SAVED,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("save_breathing_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (saving) "Salvando…" else if (unconfirmed) "Tentar salvar novamente" else "Concluir e salvar",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (unconfirmed) OutlinedButton(
                        onClick = { confirmDiscard = true },
                        enabled = !anySaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Encerrar sem salvar novamente") }
                }
            }
        }
    }
}
