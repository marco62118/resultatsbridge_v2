// ACTIVITÉ : Liste des équipes du tournoi en cours
package app.resultatsbridge.organisateur

import android.os.Bundle
import androidx.activity.compose.setContent
import app.resultatsbridge.main.BaseActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ListeEquipesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Récupération des extras
        val equipes = intent.getStringArrayListExtra("liste_equipes") ?: arrayListOf()
        val ipServeur = intent.getStringExtra("ip_serveur") ?: ""
        val idTournoi = intent.getIntExtra("id_tournoi", -1)

        setContent {
            MaterialTheme {
                ListeEquipesUI(equipes, ipServeur, idTournoi)
            }
        }
    }
}

@Composable
fun ListeEquipesUI(equipes: List<String>, ipServeur: String, idTournoi: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ✅ Affichage des infos du tournoi
        Text(
            text = "Tournoi n°$idTournoi sur $ipServeur",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ Liste ou message si vide
        if (equipes.isEmpty()) {
            Text("Aucune équipe disponible.")
        } else {
            LazyColumn {
                items(equipes) { equipe ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Text(
                            text = "➡️ $equipe",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
