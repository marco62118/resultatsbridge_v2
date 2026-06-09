package app.resultatsbridge.screens

import android.media.AudioManager
import android.media.MediaActionSound
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun CameraPreviewScreen(
    outputFile: File,
    label: String = "",
    onPhotoPrise: () -> Unit,
    onAnnulee: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture   = remember { ImageCapture.Builder().build() }
    var enCours        by remember { mutableStateOf(false) }
    val shutterSound   = remember { MediaActionSound() }
    DisposableEffect(Unit) { onDispose { shutterSound.release() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Prévisualisation caméra plein écran ─────────────────────────────
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                ProcessCameraProvider.getInstance(ctx).addListener({
                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview  = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Nom du joueur (haut centre) ─────────────────────────────────────
        if (label.isNotEmpty()) {
            Text(
                text       = label,
                color      = Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        // ── Bouton Annuler (haut gauche) ────────────────────────────────────
        IconButton(
            onClick  = onAnnulee,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        ) {
            Box(
                Modifier.size(48.dp).background(Color(0xAA000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Annuler",
                    tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // ── Bouton déclencheur (bas centre) ─────────────────────────────────
        Box(
            modifier         = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            // Anneau extérieur noir — visible sur fond clair ET fond sombre
            Box(Modifier.size(80.dp).background(Color.Black, CircleShape))
            // Bouton intérieur blanc (gris quand prise en cours)
            Button(
                onClick = {
                    if (!enCours) {
                        enCours = true
                        // Son à ~20 % du volume courant
                        val am       = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                        val prevVol  = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val quietVol = (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.07f)
                            .toInt().coerceAtLeast(1)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, quietVol, 0)
                        shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                        Handler(Looper.getMainLooper()).postDelayed({
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, prevVol, 0)
                        }, 400L)
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    onPhotoPrise()
                                }
                                override fun onError(exc: ImageCaptureException) {
                                    enCours = false
                                    onAnnulee()
                                }
                            }
                        )
                    }
                },
                modifier        = Modifier.size(68.dp),
                shape           = CircleShape,
                colors          = ButtonDefaults.buttonColors(
                    containerColor         = if (enCours) Color.Gray else Color.White,
                    disabledContainerColor = Color.Gray
                ),
                enabled         = !enCours,
                contentPadding  = PaddingValues(0.dp)
            ) { }
        }
    }
}
