package app.resultatsbridge.organisateur

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.resultatsbridge.main.BaseActivity
import app.resultatsbridge.common.AppScaffold

class ParticipationOrganisateurActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val idTournoi = intent.getIntExtra("id_tournoi", -1)
        val ipServeur = intent.getStringExtra("ip_serveur") ?: ""
        Log.i("ParticipationOrganisateurActivity", "🎮 affichage  Le Organisateur doit choisir")
        setContent {
            AppScaffold(
                ipAddress = ipServeur,
                numeroTournoi = null,
                equipeChoisie = null,
                nomEcran = "Participation Organisateur"
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "🎉 Le tournoi est officiellement ouvert !",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Souhaitez-vous participer en tant que joueur ?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = {
                            Log.i(
                                "ParticipationOrganisateurActivity",
                                "🎮 Le Organisateur choisit de jouer"
                            )
                            val resultIntent = Intent().apply {
                                putExtra("organisateur_veut_jouer", true)
                                putExtra("id_tournoi", idTournoi)
                                putExtra("ip_serveur", ipServeur)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }) {
                            Text("Oui, je veux jouer")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = {
                            Log.i(
                                "ParticipationOrganisateurActivity",
                                "🧑‍⚖️ Le Organisateur reste organisateur"
                            )
                            val resultIntent = Intent().apply {
                                putExtra("organisateur_veut_jouer", false)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }) {
                            Text("Retour Exporter")
                        }
                    }
                }
            }
        }
    }
}