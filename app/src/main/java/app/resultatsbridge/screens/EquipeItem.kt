// COMPOSANT : EquipeItem — ligne d'affichage d'une équipe dans une liste
package app.resultatsbridge.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.resultatsbridge.common.model.Equipe

/**
 * Carte affichant une équipe :
 * - Numéro d'équipe
 * - Joueur1 (Nom + Prénom)
 * - Joueur2 (Nom + Prénom)
 * - Pour Mitchell : orientation (NS/EO) et table de départ
 */
@Composable
fun EquipeItem(
    equipe: Equipe,
    orientationMitchell: String? = null,  // "NS" ou "EO", null si pas Mitchell
    tableMitchell: Int? = null            // table de départ, null si pas Mitchell
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Équipe n°${equipe.equipeNumero}")
                // Affichage NS/EO + table uniquement si Mitchell
                if (orientationMitchell != null && tableMitchell != null) {
                    val couleur = if (orientationMitchell == "NS")
                        Color(0xFF1565C0)  // bleu pour NS
                    else
                        Color(0xFFB71C1C)  // rouge pour EO
                    Text(
                        text       = "$orientationMitchell — Table $tableMitchell",
                        fontWeight = FontWeight.Bold,
                        color      = couleur
                    )
                }
            }
            Text(text = "${equipe.joueur1.nom} ${equipe.joueur1.prenom}")
            Text(text = "${equipe.joueur2.nom} ${equipe.joueur2.prenom}")
        }
    }
}