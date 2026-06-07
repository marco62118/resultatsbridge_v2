// ÉCRAN : Mouvement — saisie du résultat de la donne en cours
package app.resultatsbridge.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.ContratInfo
import app.resultatsbridge.common.model.DonneDataAEnregistrer
import app.resultatsbridge.common.model.Equipe
import app.resultatsbridge.common.model.Mouvement
import app.resultatsbridge.common.model.ResultatInfo
import app.resultatsbridge.common.model.Tour
import app.resultatsbridge.common.model.TournoiConfig
import app.resultatsbridge.common.ui.components.DropdownMenuCouleur
import app.resultatsbridge.common.ui.components.DropdownMenuDeclarant
import app.resultatsbridge.common.ui.components.DropdownMenuEntameCarte
import app.resultatsbridge.common.ui.components.DropdownMenuEntameCouleur
import app.resultatsbridge.common.ui.components.DropdownMenuInsulte
import app.resultatsbridge.common.ui.components.DropdownMenuNiveau
import app.resultatsbridge.common.ui.components.DropdownMenuPlis
import app.resultatsbridge.common.ui.components.DropdownMenuSigne
import app.resultatsbridge.common.utils.calculerPoints

fun normaliserAnnonce(texte: String): String {
    return texte
        .replace("♠", "P")
        .replace("♥", "C")
        .replace("♦", "K")
        .replace("♣", "T")
}

@Composable
fun MouvementScreen(
    equipe: Equipe,
    date: String,
    idTournoi: Int,
    mouvement: Mouvement,
    contratFinal: ContratInfo?,
    aCliqueAfficherMains: MutableState<Boolean>,
    visiteMainsEffectuee: MutableState<Boolean>,
    onEditEncheres: () -> Unit,
    onDonneSuivante: () -> Unit,
    onAfficherMains: () -> Unit,
    onAfficherMainsPhoto: () -> Unit = {},
    onMajContrat: (ContratInfo) -> Unit,
    onEnregistrerDonne: (DonneDataAEnregistrer) -> Unit,
    mainsSelectionnees: State<List<List<Carte>>?>,
    onVerifierMains: (Int) -> Unit,
    modeJeu: String = "rapide",
    typeTournoi: String = "",
    onQuitter: () -> Unit = {}
) {
    val afficherEncheres = modeJeu != "rapide"
    val afficherMains    = modeJeu == "encheres_mains"

    val indexDonneAJouer = mouvement.indexDonneAJouer
        .coerceIn(0, (mouvement.donnes.size - 1).coerceAtLeast(0))
    val donneEnCours       = mouvement.donnes.getOrNull(indexDonneAJouer)
    val numeroDonneEnCours = donneEnCours?.numero
    if (mouvement.indexDonneAJouer != indexDonneAJouer) {
        Log.w("MouvementScreen", "⚠️ indexDonneAJouer=${mouvement.indexDonneAJouer} hors limites → clampé à $indexDonneAJouer")
    }
    Log.i("MouvementScreen", "indexDonneAJouer : $indexDonneAJouer , donneEnCours?.numero : $numeroDonneEnCours | modeJeu=$modeJeu")

    val scrollState = rememberScrollState()
    val aCliqueSurSigne         = remember { mutableStateOf(false) }
    val aCliqueSurPlis          = remember { mutableStateOf(false) }
    val aCliqueSurEntameCarte   = remember { mutableStateOf(false) }
    val aCliqueSurEntameCouleur = remember { mutableStateOf(false) }

    val aCliqueAfficherResultats = remember(contratFinal) {
        mutableStateOf(
            contratFinal != null &&
                    contratFinal.entameCarte.isNotEmpty() &&
                    contratFinal.entameCouleur.isNotEmpty()
        )
    }

    val contratState = remember(key1 = contratFinal) {
        mutableStateOf(
            contratFinal ?: ContratInfo(
                niveau = 0, couleur = "", insulte = "",
                declarant = "", entameCarte = "", entameCouleur = ""
            )
        )
    }

    val resultat = remember(contratFinal) {
        mutableStateOf(
            ResultatInfo(
                signe = contratFinal?.signe ?: "=",
                plis = contratFinal?.plis ?: 0,
                points = contratFinal?.points ?: 0
            )
        )
    }

    Log.i("MouvementScreen", "État actuel - Signe cliqué: ${aCliqueSurSigne.value}, " +
            "aCliqueSurPlis: ${aCliqueSurPlis.value}, " +
            "aCliqueSurEntameCouleur: ${aCliqueSurEntameCouleur.value}, " +
            "aCliqueSurEntameCarte: ${aCliqueSurEntameCarte.value}, " +
            "aCliqueAfficherMains: ${aCliqueAfficherMains.value}, " +
            "VisiteMains: $visiteMainsEffectuee")

    val fondActif   = MaterialTheme.colorScheme.surface
    val fondInactif = Color(0xFFAAAAAA)

    // Aucun verrouillage : le joueur peut toujours corriger contrat, entame et résultat

    val contratComplet by remember {
        derivedStateOf {
            contratState.value.niveau != 0 &&
                    contratState.value.couleur.isNotEmpty() &&
                    contratState.value.declarant.isNotEmpty()
        }
    }

    var showAlerteMainsManquantes by remember { mutableStateOf(false) }
    var showDialogQuitter         by remember { mutableStateOf(false) }
    var showConfirmDialog         by remember { mutableStateOf(false) }

    LaunchedEffect(
        aCliqueSurEntameCarte.value, aCliqueSurEntameCouleur.value,
        aCliqueAfficherMains.value, visiteMainsEffectuee
    ) {
        if (aCliqueSurEntameCarte.value || aCliqueSurEntameCouleur.value || visiteMainsEffectuee.value) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val executerEnvoi = {
        Log.i("MouvementScreen", "📤 ENVOI : Signe=${resultat.value.signe}, Plis=${resultat.value.plis}")
        Log.i("MouvementScreen", "📤 contratFinal=${contratFinal?.niveau}${contratFinal?.couleur} historique=${contratFinal?.historique?.size ?: "null"} éléments")
        Log.i("MouvementScreen", "📤 contratState historique=${contratState.value.historique?.size ?: "null"} éléments")
        Log.i("MouvementScreen", "📤 contratState historique contenu=${contratState.value.historique}")
        val historiqueNormalise =
            contratState.value.historique
                ?.map { Tour(it.joueur, normaliserAnnonce(it.annonce)) }
                ?: emptyList()
        Log.i("MouvementScreen", "📤 historiqueNormalise=${historiqueNormalise.size} éléments : $historiqueNormalise")
        val contratNormalise     = normaliserAnnonce("${contratState.value.niveau}${contratState.value.couleur}${contratState.value.insulte}")
        val carteEntameNormalise = normaliserAnnonce("${contratState.value.entameCarte}${contratState.value.entameCouleur}")
        onEnregistrerDonne(
            DonneDataAEnregistrer(
                contrat = contratNormalise,
                declarant = contratState.value.declarant,
                signe = resultat.value.signe,
                points = resultat.value.points,
                plis = resultat.value.plis,
                carteEntame = carteEntameNormalise,
                numeroDonne = donneEnCours?.numero ?: 0,
                indexDonneJouee = indexDonneAJouer,
                mvntNumero = mouvement.mvntNumero,
                equipeNS = mouvement.equipeNS,
                equipeEO = mouvement.equipeEO,
                numeroTable = mouvement.tableNumero,
                historique = historiqueNormalise,
                mains = null
            )
        )
    }

    val entameDisponible = contratState.value.entameCarte.isNotEmpty() && contratState.value.entameCouleur.isNotEmpty()
    val donneButtonVisible = when (modeJeu) {
        "encheres_mains" -> visiteMainsEffectuee.value
        else             -> aCliqueAfficherResultats.value && entameDisponible
    }

    if (showDialogQuitter) {
        AlertDialog(
            onDismissRequest = { showDialogQuitter = false },
            title   = { Text("Quitter la saisie ?") },
            text    = { Text("Vous allez revenir au choix d'équipe. La saisie en cours sera perdue.") },
            confirmButton = {
                TextButton(onClick = { showDialogQuitter = false; onQuitter() }) {
                    Text("Retour au choix d'équipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialogQuitter = false }) { Text("Annuler") }
            }
        )
    }

    if (showAlerteMainsManquantes) {
        AlertDialog(
            onDismissRequest = { showAlerteMainsManquantes = false },
            title   = { Text("Mains non saisies") },
            text    = { Text("Vous n'avez pas enregistré les mains pour cette donne. Voulez-vous continuer sans les mains ou retourner les saisir ?") },
            confirmButton = {
                TextButton(onClick = { showAlerteMainsManquantes = false; executerEnvoi() }) { Text("Continuer sans mains") }
            },
            dismissButton = {
                TextButton(onClick = { showAlerteMainsManquantes = false }) { Text("Saisir les mains") }
            }
        )
    }

    if (showConfirmDialog) {
        val c = contratState.value
        val r = resultat.value
        val libelleContrat = if (c.niveau == 0) "Passe Général"
        else "${c.niveau} ${c.couleur}${if (c.insulte.isNotEmpty()) " ${c.insulte}" else ""} par ${c.declarant}"
        val libelleResultat = when (r.signe) {
            "=" -> "Contrat réalisé (=)"
            "+" -> "+${r.plis} levée(s) de plus"
            "-" -> "${r.plis} chute(s)"
            else -> "${r.signe} ${r.plis}"
        }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Vérifier avant de valider", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Donne N° ${donneEnCours?.numero ?: "?"}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    HorizontalDivider()
                    Text("Contrat : $libelleContrat")
                    Text("Résultat : $libelleResultat")
                    if (c.entameCarte.isNotEmpty()) Text("Entame : ${c.entameCarte}${c.entameCouleur}")
                    Text(
                        "Points : ${if (r.points == 9999) "—" else r.points.toString()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    val mainsAbsentes = afficherMains && donneEnCours?.mains == null && mainsSelectionnees.value == null
                    if (mainsAbsentes) showAlerteMainsManquantes = true else executerEnvoi()
                }) { Text("✅ Confirmer") }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showConfirmDialog = false }
                ) { Text("✏️ Corriger") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
// Saut Mitchell : mvntNumero > nbreTables/2 ET nbreTables pair
                        val aSaute = TournoiConfig.NBRE_TABLES > 0 &&
                                TournoiConfig.NBRE_TABLES % 2 == 0 &&
                                mouvement.mvntNumero > TournoiConfig.NBRE_TABLES / 2
                        Text(
                            text       = "Table ${mouvement.tableNumero}",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = if (aSaute) Color.Red else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "   Éq. ", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "${mouvement.equipeNS} NS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(text = " -  Éq. ", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "${mouvement.equipeEO} EO", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Mouvement ${mouvement.mvntNumero}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(text = "   Donnes : ${mouvement.donnes.joinToString { "N° ${it.numero}" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── Alerte partage de donnes T1/TN en MitchellGuéridon sans relais ─────
                    val nbreTables = TournoiConfig.NBRE_TABLES
                    val estGueridon = typeTournoi == "MitchellGueridon"
                    val sansRelais = TournoiConfig.EQUIPE_RELAIS == null
                    if (estGueridon && sansRelais && nbreTables > 0 &&
                        (mouvement.tableNumero == 1 || mouvement.tableNumero == nbreTables)
                    ) {
                        val numeros = mouvement.donnes.map { it.numero }
                        val tablePartenaire = if (mouvement.tableNumero == 1) nbreTables else 1
                        val listeDonnes = numeros.joinToString(", ") { "N°$it" }
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = Color(0xFFFFF9C4),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8F00),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = " ! Donnes $listeDonnes partagées avec table $tablePartenaire",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFC0202),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (donneEnCours != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Donne N° ${donneEnCours.numero}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFC0202),)
                            Text(text = "   Donneur : ", style = MaterialTheme.typography.bodySmall)
                            Text(text = donneEnCours.donneur, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "   Vulnérable : ", style = MaterialTheme.typography.bodySmall)
                            Text(text = donneEnCours.vulnerable, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(text = "⚠️ Donne introuvable (index=${mouvement.indexDonneAJouer}/${mouvement.donnes.size})", style = MaterialTheme.typography.bodySmall, color = Color.Red, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val contrat         = contratState.value
                val contratEstSaisi = (contrat.niveau != 0) || !(contrat.historique.isNullOrEmpty())

                if (afficherEncheres) {
                    Button(onClick = onEditEncheres, modifier = Modifier.fillMaxWidth()) {
                        Text("Éditer les enchères")
                    }
                }
                if (!contratEstSaisi && !afficherEncheres) {
                    Text(
                        text = "Inscrire le contrat final ici :",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (contratEstSaisi) {
                    val libelle = if (contrat.niveau == 0) "Passe Général"
                    else "${contrat.niveau} ${contrat.couleur}" +
                            (if (contrat.insulte == "X") " Contré" else if (contrat.insulte == "XX") " Surcontré" else "") +
                            " joué par : ${contrat.declarant}"
                    Text(
                        text = "Contrat : $libelle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(modifier = Modifier.width(52.dp)) {
                            Text("Niveau", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.width(52.dp).height(44.dp)) {
                                DropdownMenuNiveau(
                                    selected = contratState.value.niveau,
                                    onSelect = {
                                        contratState.value = contratState.value.copy(niveau = it)
                                    },
                                    isEnabled = true,
                                    backgroundColor = fondActif
                                )
                            }
                        }
                        Column(modifier = Modifier.width(58.dp)) {
                            Text("Couleur", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.width(58.dp).height(44.dp)) {
                                DropdownMenuCouleur(
                                    selected = contratState.value.couleur,
                                    onSelect = {
                                        contratState.value = contratState.value.copy(couleur = it)
                                    },
                                    isEnabled = true,
                                    backgroundColor = fondActif
                                )
                            }
                        }
                        Column(modifier = Modifier.width(58.dp)) {
                            Text("Insulte", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.width(58.dp).height(44.dp)) {
                                DropdownMenuInsulte(
                                    selected = contratState.value.insulte,
                                    onSelect = {
                                        contratState.value = contratState.value.copy(insulte = it)
                                    },
                                    isEnabled = true,
                                    backgroundColor = fondActif
                                )
                            }
                        }
                        Column(modifier = Modifier.width(74.dp)) {
                            Text("Déclarant", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.width(74.dp).height(44.dp)) {
                                DropdownMenuDeclarant(
                                    selected = contratState.value.declarant,
                                    onSelect = {
                                        contratState.value = contratState.value.copy(declarant = it)
                                    },
                                    isEnabled = true,
                                    backgroundColor = fondActif
                                )
                            }
                        }
                    }
                }

                if (contratComplet) {
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Carte d'Entame :", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Carte", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.width(66.dp).height(48.dp)) {
                                DropdownMenuEntameCarte(
                                    selected = contratState.value.entameCarte,
                                    onSelect = {
                                        aCliqueSurEntameCarte.value = true; contratState.value =
                                        contratState.value.copy(entameCarte = it)
                                    },
                                    isEnabled = true,
                                    backgroundColor = fondActif
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Couleur", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.width(66.dp).height(48.dp)) {
                                DropdownMenuEntameCouleur(
                                    selected = contratState.value.entameCouleur,
                                    onSelect = {
                                        aCliqueSurEntameCouleur.value = true; contratState.value =
                                        contratState.value.copy(entameCouleur = it)
                                    },
                                    isEnabled = true,
                                    backgroundColor = fondActif
                                )
                            }
                        }
                    }

                    val entameComplete = contratState.value.entameCarte.isNotEmpty() && contratState.value.entameCouleur.isNotEmpty()

                    if (entameComplete) {
                        Spacer(Modifier.height(6.dp))
                        if (!aCliqueAfficherResultats.value) {
                            Button(onClick = { aCliqueAfficherResultats.value = true }, modifier = Modifier.fillMaxWidth()) { Text("Résultats ?") }
                        } else {
                            val incoherent  = (resultat.value.signe == "=" && resultat.value.plis != 0) || (resultat.value.signe != "=" && resultat.value.plis == 0)
                            val pointsColor = if (incoherent) Color.Red else MaterialTheme.colorScheme.primary
                            val pointsText  = if (resultat.value.points == 9999) "" else resultat.value.points.toString()

                            Text("Ajustez le Résultat ci-dessous:", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))

                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.width(84.dp).height(44.dp)) {
                                        DropdownMenuSigne(
                                            selected = resultat.value.signe,
                                            plis = resultat.value.plis,
                                            onSelect = {
                                                aCliqueSurSigne.value = true; resultat.value =
                                                resultat.value.copy(signe = it)
                                            },
                                            isEnabled = true,
                                            backgroundColor = fondActif
                                        )
                                    }
                                    Box(modifier = Modifier.width(84.dp).height(44.dp)) {
                                        DropdownMenuPlis(
                                            selected = resultat.value.plis,
                                            niveauContrat = contratState.value.niveau,
                                            signe = resultat.value.signe,
                                            onSelect = {
                                                aCliqueSurPlis.value = true; resultat.value =
                                                resultat.value.copy(plis = it)
                                            },
                                            isEnabled = true,
                                            backgroundColor = fondActif
                                        )
                                    }
                                    val campGagnant = remember(resultat.value.signe, contratState.value.declarant) {
                                        val d = contratState.value.declarant; val s = resultat.value.signe
                                        if (d.isEmpty() || resultat.value.points == 9999) ""
                                        else if (s == "-") { if (d == "Nord" || d == "Sud") "E/O" else "N/S" }
                                        else { if (d == "Nord" || d == "Sud") "N/S" else "E/O" }
                                    }
                                    Box(modifier = Modifier.width(84.dp).height(44.dp).background(color = fondInactif, shape = RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                        Text(campGagnant, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                                    }
                                    Box(modifier = Modifier.width(140.dp).height(44.dp).background(color = fondInactif, shape = RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                        Text("Points : $pointsText", style = MaterialTheme.typography.titleMedium, color = pointsColor)
                                    }
                                }
                            }

                            LaunchedEffect(
                                contratState.value.niveau, contratState.value.couleur,
                                contratState.value.insulte, contratState.value.declarant,
                                contratState.value.entameCarte, contratState.value.entameCouleur,
                                resultat.value.signe, resultat.value.plis
                            ) {
                                val signe      = resultat.value.signe
                                val plis       = resultat.value.plis
                                val niveau     = contratState.value.niveau
                                val couleur    = contratState.value.couleur
                                val insulte    = contratState.value.insulte
                                val declarant  = contratState.value.declarant
                                val vulnerable = donneEnCours?.vulnerable ?: "Pers"
                                val pts = if (niveau > 0 && couleur.isNotEmpty() && declarant.isNotEmpty() &&
                                    ((signe == "=" && plis == 0) || (signe != "=" && plis != 0))
                                ) calculerPoints(
                                    insulte,
                                    couleur,
                                    vulnerable,
                                    declarant,
                                    niveau,
                                    signe,
                                    plis
                                )
                                else 9999
                                resultat.value = resultat.value.copy(points = pts)

                                val historiqueAConserver = contratFinal?.historique
                                    ?: contratState.value.historique

                                onMajContrat(contratState.value.copy(
                                    signe      = signe,
                                    plis       = plis,
                                    points     = pts,
                                    historique = historiqueAConserver
                                ))
                            }
                            if (afficherMains && !aCliqueAfficherMains.value) {
                                Spacer(Modifier.height(10.dp))
                                val saveContrat = {
                                    val contratActuel = (contratFinal ?: contratState.value).copy(
                                        niveau = contratState.value.niveau, couleur = contratState.value.couleur,
                                        declarant = contratState.value.declarant, insulte = contratState.value.insulte,
                                        entameCarte = contratState.value.entameCarte, entameCouleur = contratState.value.entameCouleur,
                                        historique = (contratFinal?.historique ?: contratState.value.historique),
                                        signe = resultat.value.signe, plis = resultat.value.plis, points = resultat.value.points
                                    )
                                    onMajContrat(contratActuel)
                                    aCliqueAfficherMains.value = true
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(modifier = Modifier.weight(1f), onClick = {
                                        saveContrat(); onAfficherMains()
                                    }) {
                                        Text("✏️ Mains\nmanuel", textAlign = TextAlign.Center, fontSize = 13.sp)
                                    }
                                    Button(modifier = Modifier.weight(1f), onClick = {
                                        saveContrat(); onAfficherMainsPhoto()
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))) {
                                        Text("📷 Mains\nphoto", textAlign = TextAlign.Center, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (donneButtonVisible) {
                            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                                showConfirmDialog = true
                            }) { Text("Vérifier les résultats avant de valider") }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (afficherMains && donneEnCours != null) {
                    Surface(modifier = Modifier.weight(1f), color = Color(0xFF2196F3).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Button(onClick = { onVerifierMains(donneEnCours.numero) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Attention", tint = Color.Yellow, modifier = Modifier.size(20.dp))
                            Text(text = "Vérifier les mains \n (seulement en cas d'erreur de cartes)", fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { showDialogQuitter = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text("Retour", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
