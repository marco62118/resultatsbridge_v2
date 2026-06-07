// ACTIVITÉ : Accueil — choix du mode Organisateur / Joueur
package app.resultatsbridge.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.resultatsbridge.client.ClientActivity
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.common.AppScaffold
import app.resultatsbridge.common.URL_SERVEUR_ONLINE
import app.resultatsbridge.common.utils.getSecurePrefs
import app.resultatsbridge.organisateur.OrganisateurActivity
import app.resultatsbridge.screens.LoginGlobalScreen

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            // null = vérification en cours, false = non authentifié, true = authentifié
            var estAuthentifie by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                val token = getSecurePrefs(context).getString("tokenJoueur", "") ?: ""
                if (token.isEmpty()) {
                    estAuthentifie = false
                    return@LaunchedEffect
                }
                val valide = ClientNetworkUtils.verifierTokenJoueur(token)
                if (!valide) {
                    getSecurePrefs(context).edit().remove("tokenJoueur").apply()
                }
                estAuthentifie = valide
            }

            when (estAuthentifie) {
                null  -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                false -> LoginGlobalScreen(onConnecte = { estAuthentifie = true })
                true  -> ContenuPrincipal()
            }
        }
    }
}

@Composable
private fun ContenuPrincipal() {
    val context = LocalContext.current
    var modeOnline by remember { mutableStateOf(false) }  // ✅ Local par défaut
    var modeJeu by remember { mutableStateOf("encheres_mains") }

            AppScaffold(
                ipAddress = "",
                numeroTournoi = null,
                equipeChoisie = null,
                nomEcran = "Accueil — Choix du mode",
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // ── Mode de saisie : 3 boutons côte à côte ──────
                        Text(
                            "Mode de saisie :",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "rapide"         to "contrat\nuniquement",
                                "encheres_mains" to "Enchères\n& Mains"
                            ).forEach { (valeur, label) ->
                                val selected = modeJeu == valeur
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { modeJeu = valeur }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick  = { modeJeu = valeur },
                                        modifier = Modifier.scale(0.8f).size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text      = label,
                                        style     = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Mode connexion : 2 boutons côte à côte ──────
                        Text(
                            "Mode connexion :",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                false to "Local",
                                true  to "Online"
                            ).forEach { (valeur, label) ->
                                val selected = modeOnline == valeur
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { modeOnline = valeur }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick  = { modeOnline = valeur },
                                        modifier = Modifier.scale(0.8f).size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text      = label,
                                        style     = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ✅ Adresse sélectionnée en dessous
                        Text(
                            text  = if (modeOnline) URL_SERVEUR_ONLINE else "Serveur local Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (modeOnline) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Bouton Joueur ───────────────────────────────
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(context, ClientActivity::class.java).apply {
                                        putExtra("mode_online", modeOnline)
                                        putExtra("mode_jeu", modeJeu)
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text("🎮 Joueur")
                        }

                        Spacer(modifier = Modifier.height(64.dp))

                        // ── Bouton Organisateur ─────────────────────────
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(context, OrganisateurActivity::class.java).apply {
                                        putExtra("mode_online", modeOnline)
                                        putExtra("mode_jeu", modeJeu)
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text("👔 Organisateur")
                        }
                    }
                }
            )
}