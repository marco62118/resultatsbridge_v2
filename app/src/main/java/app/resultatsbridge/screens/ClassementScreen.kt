// ÉCRAN : Classement du tournoi (scores Neuberg + %)
package app.resultatsbridge.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.resultatsbridge.common.model.ClassementItem
import kotlin.math.floor

/**
 * 🔹 Écran de sécurité qui vérifie les données avant affichage
 */
@Composable
fun SafeClassementScreen(
    classement: List<ClassementItem>,
    onConsulterDetails: () -> Unit
) {
    Log.i("SafeClassementScreen", "🧩 Entrée dans SafeClassementScreen (${classement.size} équipes)")

    if (classement.isEmpty()) {
        Log.e("SafeClassementScreen", "❌ Liste vide")
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Aucun classement disponible.", color = Color.Red)
        }
    } else {
        ClassementScreen(
            classement         = classement,
            onConsulterDetails = onConsulterDetails
        )
    }
}

@Composable
fun ClassementScreen(
    classement: List<ClassementItem>,
    onConsulterDetails: () -> Unit
) {
    // Détecter si c'est un tournoi Mitchell (au moins un item a une orientation)
    val estMitchell = classement.any { it.orientationMitchell != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp)
    ) {
        Text(
            text       = "🏆 Classement Final",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (estMitchell) {
            // ── Classement Mitchell : deux sections NS et EO ──────────────────
            // Les rang sont recalculés séparément dans chaque groupe

            val equipesNS = classement
                .filter { it.orientationMitchell == "NS" }
                .sortedByDescending { it.pts }
            val equipesEO = classement
                .filter { it.orientationMitchell == "EO" }
                .sortedByDescending { it.pts }

            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── Section NS ────────────────────────────────────────────────
                item {
                    Text(
                        text       = "🔵 Classement Nord-Sud",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(equipesNS.mapIndexed { index, item ->
                    // Recalcul du rang dans le groupe NS
                    val rangNS = if (index == 0 || equipesNS[index - 1].pts != item.pts)
                        index + 1
                    else
                        equipesNS.indexOfFirst { it.pts == item.pts } + 1
                    item to rangNS
                }) { (item, rangNS) ->
                    CarteEquipe(
                        item        = item,
                        rang        = rangNS,
                        couleurFond = Color(0xFFE3F2FD), // bleu clair pour NS
                        couleurPts  = Color(0xFF1565C0)
                    )
                }

                // ── Séparateur ────────────────────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ── Section EO ────────────────────────────────────────────────
                item {
                    Text(
                        text       = "🔴 Classement Est-Ouest",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error,
                        modifier   = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(equipesEO.mapIndexed { index, item ->
                    // Recalcul du rang dans le groupe EO
                    val rangEO = if (index == 0 || equipesEO[index - 1].pts != item.pts)
                        index + 1
                    else
                        equipesEO.indexOfFirst { it.pts == item.pts } + 1
                    item to rangEO
                }) { (item, rangEO) ->
                    CarteEquipe(
                        item        = item,
                        rang        = rangEO,
                        couleurFond = Color(0xFFFFEBEE), // rouge clair pour EO
                        couleurPts  = Color(0xFFB71C1C)
                    )
                }
            }

        } else {
            // ── Classement Howell : liste unique (comportement existant) ──────
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(classement) { item ->
                    CarteEquipe(
                        item        = item,
                        rang        = item.rang,
                        couleurFond = Color(0xFFF1F8E9),
                        couleurPts  = Color(0xFF2E7D32)
                    )
                }
            }
        }

        // ── Bouton fixé en bas ────────────────────────────────────────────────
        Button(
            onClick  = onConsulterDetails,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Voir le détail des donnes")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable réutilisable pour afficher une carte d'équipe
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CarteEquipe(
    item: ClassementItem,
    rang: Int,
    couleurFond: Color,
    couleurPts: Color
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors    = CardDefaults.cardColors(containerColor = couleurFond)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                // Afficher NS/EO si Mitchell
                val suffixeOrientation = item.orientationMitchell?.let { " ($it)" } ?: ""
                Text(
                    text       = "Équipe ${item.numeroEquipe}$suffixeOrientation  (Rang $rang)",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "${item.joueur1Prenom} ${item.joueur1Nom} & ${item.joueur2Prenom} ${item.joueur2Nom}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "${if (item.pts == floor(item.pts)) item.pts.toInt().toString() else "%.1f".format(item.pts)} pts",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = couleurPts,
                    fontWeight = FontWeight.Bold
                )
                if (item.scorePct > 0) {
                    Text(
                        text  = "${"%.1f".format(item.scorePct)} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = couleurPts
                    )
                }
            }
        }
    }
}
