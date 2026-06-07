package app.resultatsbridge.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.model.Equipe
import app.resultatsbridge.v2.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EcranPleinScaffold(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        content()
    }
}
@Composable
fun AppScaffold(
    ipAddress: String,
    numeroTournoi: Int?,
    equipeChoisie: Equipe?,  // ✅ gardé en param��tre pour compatibilité mais non affiché
    nomEcran: String = "",   // DEBUG — à supprimer avant mise en service
    content: @Composable () -> Unit
) {
    val dateDuJour = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2E7D32))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(8.dp)
    ) {
        // ── Bandeau ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color.White)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text      = "Amicale de Bridge de l'Embrunais",
                color     = Color.White,
                maxLines  = 1,
                style     = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            // Tournoi + date sur une ligne
            val tournoiText = if (numeroTournoi != null)
                "Tournoi N° $numeroTournoi  —  $dateDuJour"
            else
                "Tournoi du $dateDuJour"

            Text(
                text  = tournoiText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            // IP uniquement si disponible
            if (ipAddress.isNotEmpty()) {
                Text(
                    text  = "📡 $ipAddress",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Yellow
                )
            }

            if (BuildConfig.DEBUG && nomEcran.isNotEmpty()) {
                Text(
                    text      = "[ $nomEcran ]",
                    style     = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color     = Color(0xFFFFEB3B),
                    fontWeight= FontWeight.Bold
                )
            }
        }

        // ── Contenu ───────────────────────────────────────
        content()
    }
}
