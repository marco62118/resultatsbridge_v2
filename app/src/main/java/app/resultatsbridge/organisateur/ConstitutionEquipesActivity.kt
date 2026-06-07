// ACTIVITÉ : Constitution des équipes (organisateur) — saisie avant démarrage
package app.resultatsbridge.organisateur

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import app.resultatsbridge.main.BaseActivity
import app.resultatsbridge.common.AppScaffold
import app.resultatsbridge.common.theme.BridgeServeurTheme
import app.resultatsbridge.common.model.Joueur
import app.resultatsbridge.screens.ConstitutionEquipesScreen

class ConstitutionEquipesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val idTournoi          = intent.getIntExtra("id_tournoi", -1)
        val nbEquipesRecu      = intent.getIntExtra("nombre_equipes", 0)
        val modeOnline         = intent.getBooleanExtra("mode_online", false)
        // Donnes par table transmis par CreationTournoiActivity (Mitchell uniquement, 0 sinon)
        val nbreDonnesParTable = intent.getIntExtra("nbre_donnes_par_table", 0)
        val typeTournoi = intent.getStringExtra("type_tournoi") ?: ""

        @Suppress("DEPRECATION")
        val joueurs = intent.getParcelableArrayListExtra<Joueur>("joueurs") ?: emptyList()

        Log.i("ConstitutionEquipesActivity", "🎯 idTournoi=$idTournoi reçu")
        Log.i("ConstitutionEquipesActivity", "👥 ${joueurs.size} joueurs reçus via intent")
        Log.i("ConstitutionEquipesActivity", "👥 $nbEquipesRecu nbEquipesRecu reçus via intent")
        Log.i("ConstitutionEquipesActivity", "🌐 modeOnline=$modeOnline")
        Log.i("ConstitutionEquipesActivity", "🃏 nbreDonnesParTable=$nbreDonnesParTable")

        setContent {
            BridgeServeurTheme {
                AppScaffold(
                    ipAddress = "",
                    numeroTournoi = idTournoi,
                    equipeChoisie = null,
                    nomEcran = "Constitution des équipes"
                ) {
                    ConstitutionEquipesScreen(
                        idTournoi = idTournoi,
                        joueursInitiaux = joueurs,
                        nombreEquipes = nbEquipesRecu,
                        modeOnline = modeOnline,
                        nbreDonnesParTable = nbreDonnesParTable,
                        typeTournoi = typeTournoi        // ← nouveau
                    )
                }
            }
        }
    }
}
