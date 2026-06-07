// ÉCRAN : Sélection de l'équipe — le joueur choisit son équipe dans la liste du tournoi
package app.resultatsbridge.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.resultatsbridge.common.model.Equipe

@Composable
fun SelectionEquipeScreen(
    idTournoi: Int,
    typeTournoi: String,
    equipes: List<Equipe>,
    onEquipeChoisie: (Equipe) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()) // ✅ scroll activé
    ) {
        Text("Tournoi #$idTournoi - Type : $typeTournoi", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Sélectionnez une équipe :", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(12.dp))

        equipes.forEach { equipe ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEquipeChoisie(equipe) }
                    .padding(vertical = 4.dp)
            ) {
                EquipeItem(equipe)
            }
        }
    }
}
