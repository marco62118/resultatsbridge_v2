// ÉCRAN : Vérification des mains — affiche les 4 mains avant validation de la donne
package app.resultatsbridge.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.model.EquipeDonneInfo

/**
 * Écran affichant la liste des équipes ayant joué une donne
 */
@Composable
fun VerificationMainsScreen(
    idTournoi: Int,
    numeroDonne: Int,
    equipesAyantJoue: List<EquipeDonneInfo>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onAfficherDonne: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // En-tête
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).background(Color.Red, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text(
                text = "Vérification Donne $numeroDonne",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contenu
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Red
                )
            }
        } else {
            // ✅ Liste des équipes (non cliquable, juste informative)
            Text(
                text = "Équipes ayant joué cette donne :",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),  // ✅ Prend l'espace disponible
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(equipesAyantJoue) { info ->
                    EquipeItem(info = info)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Bouton unique en bas pour afficher la donne
            if (equipesAyantJoue.isNotEmpty()) {
                Button(
                    onClick = {
                        onAfficherDonne()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Afficher la donne", fontSize = 16.sp)
                }
            }
        }
    }
}


@Composable
private fun EquipeItem(info: EquipeDonneInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "NS ${info.equipeNS} vs EO ${info.equipeEO}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Contrat : ${info.contrat} par ${info.declarant}",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}
