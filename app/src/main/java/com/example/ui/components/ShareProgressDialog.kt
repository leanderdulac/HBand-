package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.ShareProgressData
import kotlinx.coroutines.launch

@Composable
fun ShareProgressDialog(
    shareData: ShareProgressData,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    // Saved-state inputs alone do not reject feedback restored for another snapshot.
    key(shareData.uri.toString(), shareData.summaryText) {
        ShareProgressContent(shareData, onDismiss, onShowSnackbar)
    }
}

@Composable
private fun ShareProgressContent(
    shareData: ShareProgressData,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit,
) {
    val context = LocalContext.current
    var actionFeedback by rememberSaveable(shareData.uri.toString(), shareData.summaryText) {
        mutableStateOf<String?>(null)
    }
    val feedbackScroll = rememberScrollState()
    val feedbackScope = rememberCoroutineScope()
    fun showActionFeedback(message: String) {
        actionFeedback = message
        feedbackScope.launch { feedbackScroll.scrollTo(0) }
        onShowSnackbar(message)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("share_progress_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "Confira seu cartão",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A),
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(feedbackScroll)) {
                    actionFeedback?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("share_action_feedback").semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        text = "Compartilhe somente com quem você escolher.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PatientSummaryLayout(first = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 360.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1080f / 1350f)
                                    .clip(RoundedCornerShape(20.dp)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Image(
                                    bitmap = shareData.bitmap.asImageBitmap(),
                                    contentDescription = "Cartão dos registros. Os valores também estão disponíveis em texto.",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("share_progress_image_preview"),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }, second = {
                        Text(shareData.summaryText, style = MaterialTheme.typography.bodyLarge)

                        // Action Buttons
                        Button(
                            onClick = {
                                actionFeedback = null
                                try {
                                    // Cached bytes can disappear while the bitmap preview is retained.
                                    // This checks availability now, not delivery or future receiver access.
                                    if (!shareData.file.isFile || !shareData.file.canRead() || shareData.file.length() == 0L) {
                                        showActionFeedback("A imagem deste cartão não está mais disponível. Feche o cartão e prepare outro para compartilhar, ou copie o texto dos registros.")
                                        return@Button
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, shareData.uri)
                                        putExtra(Intent.EXTRA_TEXT, shareData.summaryText)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Escolher com quem compartilhar")
                                    )
                                } catch (e: Exception) {
                                    showActionFeedback("Não foi possível abrir as opções de compartilhamento. Tente novamente.")
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("share_progress_intent_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Escolher com quem compartilhar",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val message = try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Registros next2u SAÚDE", shareData.summaryText)
                                    clipboard.setPrimaryClip(clip)
                                    "Texto dos registros copiado."
                                } catch (_: Exception) {
                                    "Não foi possível copiar o texto. Tente novamente."
                                }
                                showActionFeedback(message)
                            },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("copy_progress_summary_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Copiar texto dos registros",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("dismiss_share_progress_button"),
                ) { Text("Fechar cartão") }
            }
        }
    }
}
