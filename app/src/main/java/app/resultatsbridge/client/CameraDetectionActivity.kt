// ACTIVITÉ : Caméra — capture photo pour détection des cartes (CameraX)
package app.resultatsbridge.client

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.resultatsbridge.main.BaseActivity
import java.io.File

class CameraDetectionActivity : BaseActivity() {

    companion object {
        const val EXTRA_CARTES = "CARTES"
        const val EXTRA_JOUEUR_NOM = "JOUEUR_NOM"
    }

    private var capturedPhotoFile: File? = null
    private var capturedPhotoUri: Uri? = null
    private var activeWebView: WebView? = null
    private var cameraLaunchTime = 0L

    private val isLoading = mutableStateOf(true)
    private val statusMsg = mutableStateOf("Ouverture caméra...")
    private val detectedCartes = mutableStateOf<List<String>>(emptyList())

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            supprimerPhotoGalerie()
            val filePath = capturedPhotoFile?.absolutePath
            if (filePath != null) { statusMsg.value = "Analyse en cours…"; startWebAnalysisFromFile(filePath) }
            else { setResult(RESULT_CANCELED); finish() }
        } else { setResult(RESULT_CANCELED); finish() }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else { setResult(RESULT_CANCELED); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val joueurNom = intent.getStringExtra(EXTRA_JOUEUR_NOM) ?: "Joueur"

        setContent {
            val loading by isLoading
            val status by statusMsg
            val cartes by detectedCartes

            if (loading) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B6623)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            } else {
                CorrectionScreen(
                    joueurNom = joueurNom,
                    initialCartes = cartes,
                    onEnregistrer = { codes ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_CARTES, codes.toTypedArray()))
                        finish()
                    },
                    onAnnuler = { setResult(RESULT_CANCELED); finish() }
                )
            }
        }
        openCamera()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA); return
        }
        cameraLaunchTime = System.currentTimeMillis()
        val photoFile = File.createTempFile("bridge_", ".jpg", cacheDir)
        capturedPhotoFile = photoFile
        capturedPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraLauncher.launch(capturedPhotoUri!!)
    }

    private fun supprimerPhotoGalerie() {
        try {
            val cutoffSec = (cameraLaunchTime / 1000) - 1
            contentResolver.delete(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} >= ?",
                arrayOf(cutoffSec.toString())
            )
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebAnalysisFromFile(filePath: String) {
        val wv = WebView(this)
        activeWebView = wv
        wv.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }
        wv.addJavascriptInterface(RoboflowJsInterface(wv), "Android")
        val js = """<!DOCTYPE html><html><body>
<canvas id="c" style="display:none"></canvas>
<script>
var img=new Image();
img.onload=async function(){
    var MAX=640,w=img.width,h=img.height;
    if(w>MAX){h=h*MAX/w;w=MAX;}
    var c=document.getElementById('c');
    c.width=w;c.height=h;
    c.getContext('2d').drawImage(img,0,0,w,h);
    var out=c.toDataURL('image/jpeg',0.7).split(',')[1];
    try{
        var r=await fetch('https://detect.roboflow.com/cartesbridgev2-tzy3j/25?api_key=V53Tf7oqdPXcCtcdp5y3',
            {method:'POST',body:out,headers:{'Content-Type':'application/x-www-form-urlencoded'}});
        var d=await r.json();
        if(d.predictions&&d.predictions.length>0){
            var imgW=d.image&&d.image.width?d.image.width:w;
            var s=[...d.predictions].sort(function(a,b){return a.x-b.x;});
            var f=[s[0]];
            for(var i=1;i<s.length;i++){
                if((s[i].x-f[f.length-1].x)/imgW>=0.02)f.push(s[i]);
            }
            Android.onPredictions(f.map(function(p){
                return p.class+'|'+Math.round(p.x/imgW*100)+'|'+Math.round(p.width/imgW*100);
            }).join(','));
        }else{Android.onError('Aucune carte detectee');}
    }catch(e){Android.onError(e.message||'Erreur reseau');}
};
img.onerror=function(){Android.onError('Erreur chargement image');};
img.src='file://$filePath';
</script></body></html>"""
        val htmlFile = File(cacheDir, "roboflow_detect.html")
        htmlFile.writeText(js)
        wv.loadUrl("file://${htmlFile.absolutePath}")
    }

    private inner class RoboflowJsInterface(private val wv: WebView) {
        @JavascriptInterface
        fun onPredictions(data: String) {
            runOnUiThread {
                wv.destroy(); activeWebView = null
                capturedPhotoFile?.delete(); capturedPhotoFile = null
                detectedCartes.value = parseRoboflowResults(data)
                isLoading.value = false
            }
        }
        @JavascriptInterface
        fun onError(msg: String) {
            runOnUiThread {
                wv.destroy(); activeWebView = null
                capturedPhotoFile?.delete(); capturedPhotoFile = null
                detectedCartes.value = List(13) { "?" }
                isLoading.value = false
            }
        }
    }

    private fun parseRoboflowResults(data: String): List<String> {
        if (data.isBlank()) return List(13) { "?" }
        data class Det(val code: String, val cx: Float, val boxW: Float)
        val dets = data.split(",").mapNotNull { item ->
            val parts = item.trim().split("|")
            val code = parseLabel(parts[0]) ?: return@mapNotNull null
            val cx = parts.getOrNull(1)?.toFloatOrNull()?.div(100f) ?: return@mapNotNull null
            val boxW = parts.getOrNull(2)?.toFloatOrNull()?.div(100f) ?: 0.077f
            Det(code, cx, boxW)
        }.sortedBy { it.cx }
        if (dets.isEmpty()) return List(13) { "?" }
        val deduped = mutableListOf(dets[0])
        for (i in 1 until dets.size) {
            if (dets[i].cx - deduped.last().cx >= 0.02f) deduped.add(dets[i])
        }
        val spacing = (if (deduped.size > 1) (deduped.last().cx - deduped.first().cx) / (deduped.size - 1)
        else deduped[0].boxW.coerceAtLeast(0.04f)).coerceIn(0.04f, 0.12f)
        val result = mutableListOf(deduped[0].code)
        for (i in 1 until deduped.size) {
            val gap = deduped[i].cx - deduped[i - 1].cx
            repeat(((gap / spacing) - 0.6f).toInt().coerceAtLeast(0)) { result.add("?") }
            result.add(deduped[i].code)
        }
        while (result.size < 13) result.add("?")
        return result
    }

    private fun parseLabel(label: String): String? {
        if (label.length < 2) return null
        val couleur = when (label.last()) {
            'C' -> "C"; 'K' -> "K"; 'P' -> "P"; 'T' -> "T"; else -> return null
        }
        val valeur = when (val v = label.dropLast(1)) {
            "A","R","D","V","10","2","3","4","5","6","7","8","9" -> v
            else -> return null
        }
        return valeur + couleur
    }

    override fun onDestroy() { activeWebView?.destroy(); super.onDestroy() }
}

// ── Écran de correction ────────────────────────────────────────────────────────

@Composable
private fun CorrectionScreen(
    joueurNom: String,
    initialCartes: List<String>,
    onEnregistrer: (List<String>) -> Unit,
    onAnnuler: () -> Unit
) {
    var cartes by remember { mutableStateOf(initialCartes.toMutableList()) }
    var selectedIdx by remember { mutableStateOf(-1) }
    var kbValue by remember { mutableStateOf("") }
    var kbSuit by remember { mutableStateOf("") }

    val valRow1 = listOf("A","R","D","V","10")
    val valRow2 = listOf("9","8","7","6","5","4","3","2")
    val suits = listOf("P" to "♠", "C" to "♥", "K" to "♦", "T" to "♣")

    val peutValider = kbValue.isNotEmpty() && kbSuit.isNotEmpty() && selectedIdx >= 0
    val peutSupprimer = cartes.size > 13 && selectedIdx >= 0
    val peutEnregistrer = cartes.size == 13 && cartes.none { it == "?" || it.length < 2 }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B6623)).padding(8.dp)) {
        Text("Main de $joueurNom", color = Color.White, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            textAlign = TextAlign.Center)
        Text("${cartes.count { it != "?" && it.length >= 2 }}/13 cartes identifiées",
            color = Color(0xFFB2DFDB), fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center)

        // Rangée 1 (max 7 cartes)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until minOf(7, cartes.size)) {
                CarteSlotCam(cartes[i], selectedIdx == i, { selectCarte(i, cartes, selectedIdx) { si, v, s -> selectedIdx = si; kbValue = v; kbSuit = s } }, Modifier.weight(1f))
            }
            repeat((7 - minOf(7, cartes.size)).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(3.dp))
        // Rangée 2 (cartes 7 à fin)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val row2 = cartes.drop(7)
            for (i in row2.indices) {
                val idx = i + 7
                CarteSlotCam(row2[i], selectedIdx == idx, { selectCarte(idx, cartes, selectedIdx) { si, v, s -> selectedIdx = si; kbValue = v; kbSuit = s } }, Modifier.weight(1f))
            }
            repeat((7 - row2.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }

        // Clavier inline
        if (selectedIdx >= 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                valRow1.forEach { v ->
                    Button(onClick = { kbValue = v }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (kbValue == v) Color(0xFFFFD54F) else Color(0xFF37474F)),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) {
                        Text(v, color = if (kbValue == v) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                valRow2.forEach { v ->
                    Button(onClick = { kbValue = v }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (kbValue == v) Color(0xFFFFD54F) else Color(0xFF37474F)),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) {
                        Text(v, color = if (kbValue == v) Color.Black else Color.White, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                suits.forEach { (code, sym) ->
                    val isRed = code == "C" || code == "K"
                    Button(onClick = { kbSuit = code }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (kbSuit == code) Color(0xFFFFD54F) else Color.White),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) {
                        Text(sym, color = if (isRed) Color.Red else Color.Black, fontSize = 22.sp)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Button(onClick = {
                    if (peutValider) {
                        val list = cartes.toMutableList(); list[selectedIdx] = kbValue + kbSuit
                        cartes = list; selectedIdx = -1; kbValue = ""; kbSuit = ""
                    }
                }, enabled = peutValider, modifier = Modifier.weight(1f)) { Text("Valider", fontWeight = FontWeight.Bold) }
                Button(onClick = {
                    if (peutSupprimer) {
                        val list = cartes.toMutableList(); list.removeAt(selectedIdx)
                        cartes = list; selectedIdx = -1; kbValue = ""; kbSuit = ""
                    }
                }, enabled = peutSupprimer, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))) {
                    Text("Supprimer", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth().background(Color(0xFF1B5E20)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAnnuler, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))) {
                Text("Annuler", color = Color.White)
            }
            Button(onClick = { onEnregistrer(cartes.toList()) }, enabled = peutEnregistrer,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                Text("Valider cette main ✓", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

private fun selectCarte(
    idx: Int, cartes: List<String>, currentSel: Int,
    onResult: (Int, String, String) -> Unit
) {
    if (currentSel == idx) { onResult(-1, "", ""); return }
    val code = cartes.getOrElse(idx) { "?" }
    val v = if (code.length >= 2 && code != "?") code.dropLast(1) else ""
    val s = if (code.length >= 1 && code != "?") when (code.last()) {
        'P' -> "P"; 'C' -> "C"; 'K' -> "K"; 'T' -> "T"; else -> ""
    } else ""
    onResult(idx, v, s)
}

@Composable
private fun CarteSlotCam(code: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val isHole = code == "?" || code.length < 2
    val couleur = if (!isHole) code.last().toString() else "?"
    val valeur = if (!isHole) code.dropLast(1) else "?"
    val isRed = couleur == "C" || couleur == "K"
    val sym = when (couleur) { "P" -> "♠"; "C" -> "♥"; "K" -> "♦"; "T" -> "♣"; else -> "?" }
    Box(
        modifier = modifier.aspectRatio(0.65f).clip(RoundedCornerShape(6.dp))
            .background(if (isHole) Color(0xFFF0F0F0) else Color(0xFFFFF9E6))
            .then(if (isSelected) Modifier.border(2.dp, Color.Yellow, RoundedCornerShape(6.dp)) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isHole) Text("?", color = Color.LightGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valeur, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(sym, color = if (isRed) Color.Red else Color.Black, fontSize = 13.sp)
        }
    }
}
