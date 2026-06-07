// ÉCRAN : Changement de mouvement — affiche la prochaine table + adversaires, gère la table relais
package app.resultatsbridge.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.model.ChangementDeMouvement
import app.resultatsbridge.common.model.TournoiConfig
import kotlinx.coroutines.launch

private const val TYPE_PAR4EQU2T21D      = "par4equ2t21d"
private const val TYPE_MITCHELL           = "Mitchell"
private const val TYPE_MITCHELL_GUERIDON  = "MitchellGueridon"

// ── Composables locaux ────────────────────────────────────────
@Composable
private fun TexteNormal(texte: String, color: Color = Color.Unspecified) {
    Text(
        text  = texte,
        style = MaterialTheme.typography.bodyLarge,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else color
    )
}

@Composable
private fun TexteGrand(texte: String, couleur: Color = Color.Unspecified) {
    Text(
        text       = texte,
        fontSize   = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color      = couleur
    )
}

// =====================================================
//  ECRAN CHANGEMENT DE MOUVEMENT
// =====================================================
@Composable
fun EcranChangementMouvement(
    mvntTermineNumero: Int?,
    equipes: Pair<Int, Int>?,
    changement: ChangementDeMouvement?,
    equipeChoisie: Int?,
    typeTournoi: String = "",
    onVerifierFinMouvement: suspend () -> Boolean,
    onOk: () -> Unit = {},
    onClassementProvisoire: () -> Unit = {}
) {
    Log.i("EcranChangementMouvement", "APPEL écran changement de mouvement")
    Log.i("EcranChangementMouvement", "équipeChoisie=$equipeChoisie equipes=$equipes typeTournoi=$typeTournoi")

    val estPar4Equ2T21D     = (typeTournoi == TYPE_PAR4EQU2T21D)
    val estMitchell         = (typeTournoi == TYPE_MITCHELL)
    val estMitchellGueridon = (typeTournoi == TYPE_MITCHELL_GUERIDON)

    var enVerification by remember { mutableStateOf(false) }
    var messageAttente by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val couleurNS    = MaterialTheme.colorScheme.primary
    val couleurEO    = MaterialTheme.colorScheme.error
    val couleurTable = MaterialTheme.colorScheme.secondary

    val adversaireDirecte = if (equipeChoisie != null && equipes != null) {
        if (equipes.first == equipeChoisie) equipes.second else equipes.first
    } else null

    Log.i("EcranChangementMouvement", "adversaireDirecte=$adversaireDirecte")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text       = "Changement de mouvement",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (changement == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(4.dp))
                Text("Récupération des nouvelles tables en cours...")
            }
        } else {

            if (messageAttente != null) {
                Text(
                    text       = messageAttente!!,
                    style      = MaterialTheme.typography.bodyMedium,
                    textAlign  = TextAlign.Center,
                    color      = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text      = "Attendez le feu vert de l'Organisateur avant de changer de table",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            val entriesTries = if (equipeChoisie != null) {
                val principale = changement.entries.firstOrNull { entry ->
                    entry.equipe.equipeNumero == equipeChoisie ||
                            entry.adversaire.equipeNumero == equipeChoisie
                }
                listOfNotNull(principale)
            } else {
                changement.entries
            }
            entriesTries.forEachIndexed { index, entry ->

                val equipeEnPremier = when (index) {
                    0    -> equipeChoisie
                    else -> adversaireDirecte
                }

                val estRelais =
                    entry.adversaire.joueur1.nom.trim().lowercase() == "relais" ||
                            entry.adversaire.joueur2.nom.trim().lowercase() == "relais" ||
                            entry.equipe.joueur1.nom.trim().lowercase() == "relais" ||
                            entry.equipe.joueur2.nom.trim().lowercase() == "relais"

                val equipeEnPremierEstNS = equipeEnPremier == null ||
                        entry.equipe.equipeNumero == equipeEnPremier

                val equipeAffichee   = if (equipeEnPremierEstNS) entry.equipe    else entry.adversaire
                val equipeAdversaire = if (equipeEnPremierEstNS) entry.adversaire else entry.equipe
                val positionChoisie  = if (equipeEnPremierEstNS) "NS" else "EO"
                val positionAdverse  = if (equipeEnPremierEstNS) "EO" else "NS"
                val couleurChoisie   = if (equipeEnPremierEstNS) couleurNS else couleurEO
                val couleurAdv       = if (equipeEnPremierEstNS) couleurEO else couleurNS

                Card(
                    modifier  = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text  = "Mouvement ${changement.mvntSuivant}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (estRelais) {
                            // ── Équipe relais : message spécial ──────────────
                            val equipeReelle = if (
                                entry.equipe.joueur1.nom.trim().lowercase() != "relais" &&
                                entry.equipe.joueur2.nom.trim().lowercase() != "relais"
                            ) entry.equipe else entry.adversaire

                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TexteNormal("Équipe N°")
                                TexteGrand("${equipeReelle.equipeNumero}", couleurNS)
                                TexteNormal("allez à la")
                                TexteGrand("Table ${entry.tableNumero} Relais", couleurTable)
                            }

                        } else if (estMitchell || estMitchellGueridon) {
                            // ── Affichage spécifique Mitchell / Mitchell Guéridon ──────────────
                            // Skip uniquement pour Mitchell standard (tables paires), jamais pour Guéridon
                            val aSaute = estMitchell && !estMitchellGueridon &&
                                    TournoiConfig.NBRE_TABLES > 0 &&
                                    TournoiConfig.NBRE_TABLES % 2 == 0 &&
                                    changement.mvntSuivant > TournoiConfig.NBRE_TABLES / 2
                            val couleurTableMitchell = if (aSaute) Color.Red else couleurTable

                            if (equipeEnPremierEstNS) {
                                // L'équipe choisie est NS → elle reste sur place
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TexteNormal("Équipe N°")
                                    TexteGrand("${equipeAffichee.equipeNumero}", couleurNS)
                                    TexteNormal("vous restez")
                                    TexteGrand("Table ${entry.tableNumero}", couleurTableMitchell)
                                }
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TexteNormal("Vos adversaires : Équipe N°")
                                    TexteGrand("${equipeAdversaire.equipeNumero}", couleurEO)
                                    TexteNormal("arrive en EO")
                                }
                            } else {
                                // L'équipe choisie est EO → elle se déplace
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {

                                    if (aSaute) {
                                        TexteGrand("⚠️ ") //sauté à la Table ${entry.tableNumero}", Color.Red)
                                        TexteNormal("Équipe N° ")
                                        TexteGrand("${equipeAffichee.equipeNumero} sauté à la Table ${entry.tableNumero}", couleurTableMitchell)
                                    } else {
                                        TexteNormal("Équipe N°")
                                        TexteGrand("${equipeAffichee.equipeNumero}", couleurEO)
                                        TexteNormal(" rendez vous à la ")
                                        TexteGrand("Table ${entry.tableNumero}", couleurTableMitchell)
                                    }

                                }
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TexteNormal("vous jouerez contre Équipe NS N°")
                                    TexteGrand("${equipeAdversaire.equipeNumero}", couleurNS)
                                }
                            }

                        } else {
                            // ── Affichage Howell : comportement existant inchangé ──
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TexteNormal("Équipe N°")
                                TexteGrand("${equipeAffichee.equipeNumero}", couleurChoisie)
                                TexteNormal("allez table")
                                TexteGrand("${entry.tableNumero}", couleurTable)
                                TexteNormal("en")
                                TexteGrand(positionChoisie, couleurChoisie)
                            }
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TexteNormal("contre Équipe N°")
                                TexteGrand("${equipeAdversaire.equipeNumero}", couleurAdv)
                                TexteNormal("en")
                                TexteGrand(positionAdverse, couleurAdv)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Bouton classement provisoire (uniquement par4equ2t21d) ─────────
            if (estPar4Equ2T21D && mvntTermineNumero != null) {
                OutlinedButton(
                    onClick  = { onClassementProvisoire() },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("📊 Classement provisoire — après mouvement $mvntTermineNumero")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Bouton OK — aller au mouvement suivant ────────────────────────
            Button(
                onClick  = {
                    coroutineScope.launch {
                        enVerification = true
                        messageAttente = null
                        val toutesTerminees = onVerifierFinMouvement()
                        enVerification = false
                        if (toutesTerminees) {
                            onOk()
                        } else {
                            messageAttente = "⏳ Patientez — toutes les équipes n'ont pas encore terminé ce mouvement.\nAttendez le feu vert de l'Organisateur."
                        }
                    }
                },
                enabled  = !enVerification,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (enVerification) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("OK — Aller au mouvement suivant")
                }
            }
        }
    }
}
