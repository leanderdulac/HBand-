package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalBorder

enum class SyncDisplayStatus {
    SYNCING,
    OFFLINE,
    FULLY_SYNCED,
    PENDING_QUEUE,
    FAILED
}

@Composable
fun SyncStatusIndicator(
    syncStatus: SyncDisplayStatus,
    pendingCount: Int,
    syncedCount: Int,
    failedCount: Int,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier,
    consecutiveFailures: Int = 0,
    onRefreshHealth: (() -> Unit)? = null,
    onMarkLocalSynced: (() -> Unit)? = null,
    onRetryAll: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val (badgeBg, badgeFg, statusTitle, statusSubtitle, statusIcon) = when (syncStatus) {
        SyncDisplayStatus.SYNCING -> SyncUiConfig(
            bgColor = Color(0xFFD1E4FF),
            fgColor = Color(0xFF004A77),
            title = "Syncing",
            subtitle = "WorkManager uploading Room DB metrics to HealthTech API...",
            icon = Icons.Default.Sync
        )
        SyncDisplayStatus.OFFLINE -> SyncUiConfig(
            bgColor = Color(0xFFFFF3E0),
            fgColor = Color(0xFFB56C00),
            title = "Offline",
            subtitle = "Metrics held in Room SQLite database until network restores",
            icon = Icons.Default.CloudOff
        )
        SyncDisplayStatus.FULLY_SYNCED -> SyncUiConfig(
            bgColor = Color(0xFFE8F5E9),
            fgColor = Color(0xFF2E7D32),
            title = "Fully Synced",
            subtitle = "All local Room database metrics uploaded to HealthTech API",
            icon = Icons.Default.CheckCircle
        )
        SyncDisplayStatus.PENDING_QUEUE -> SyncUiConfig(
            bgColor = Color(0xFFF1F4F9),
            fgColor = Color(0xFF00639B),
            title = "Queued ($pendingCount Pending)",
            subtitle = "WorkManager periodic background sync scheduled",
            icon = Icons.Default.Schedule
        )
        SyncDisplayStatus.FAILED -> SyncUiConfig(
            bgColor = Color(0xFFFFEBEE),
            fgColor = Color(0xFFC62828),
            title = "Sync Warning ($failedCount Failed)",
            subtitle = "Recent upload failed. Tap to retry WorkManager task.",
            icon = Icons.Default.ErrorOutline
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workmanager_sync_status_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier
                                .size(24.dp)
                                .then(
                                    if (syncStatus == SyncDisplayStatus.SYNCING) {
                                        Modifier.rotate(rotationAngle)
                                    } else Modifier
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF44474E),
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Manual Trigger WorkManager Sync Button
                Button(
                    onClick = onTriggerSync,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (syncStatus == SyncDisplayStatus.FAILED) Color(0xFFC62828) else Color(0xFF00639B)
                    ),
                    modifier = Modifier.testTag("trigger_workmanager_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (syncStatus == SyncDisplayStatus.SYNCING) "Syncing" else "Sync Now",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Queue Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF8F9FF))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill("Pending Work", "$pendingCount", Color(0xFF00639B))
                StatusDivider()
                StatusPill("Uploaded", "$syncedCount", Color(0xFF2E7D32))
                StatusDivider()
                StatusPill("Failed Retries", "$failedCount", Color(0xFFC62828))
            }

            AnimatedVisibility(visible = consecutiveFailures >= 2 || failedCount > 0 || syncStatus == SyncDisplayStatus.FAILED) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("consecutive_failures_warning_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFF1F0),
                        border = BorderStroke(1.dp, Color(0xFFFFCDD2))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sync Warning: Multiple Consecutive Failures",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFB71C1C)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (consecutiveFailures >= 2) {
                                            "$consecutiveFailures consecutive upload attempts failed. Please perform a manual connectivity check (Wi-Fi / Cellular) or verify API endpoint."
                                        } else {
                                            "Upload attempts encountered errors ($failedCount failed items). Suggest checking network connectivity and endpoint status."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF5D1010)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onMarkLocalSynced != null) {
                                    Button(
                                        onClick = onMarkLocalSynced,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mark_local_synced_button")
                                    ) {
                                        Text("Salvar Local", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }

                                if (onRetryAll != null) {
                                    OutlinedButton(
                                        onClick = onRetryAll,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF00639B)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00639B)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("retry_all_failed_button")
                                    ) {
                                        Text("Reprocessar", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }

                                if (onRefreshHealth != null) {
                                    OutlinedButton(
                                        onClick = onRefreshHealth,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                                        modifier = Modifier.testTag("check_connectivity_health_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WifiOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Testar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SyncUiConfig(
    val bgColor: Color,
    val fgColor: Color,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun StatusPill(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF44474E)
        )
    }
}

@Composable
private fun StatusDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 24.dp)
            .background(Color(0xFFE2E8F0))
    )
}
