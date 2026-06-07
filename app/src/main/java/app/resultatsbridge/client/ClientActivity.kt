// ACTIVITÉ : Joueur — activité principale, gère toutes les étapes EtapeClient
package app.resultatsbridge.client

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.gson.Gson
import app.resultatsbridge.screens.VerificationDonneEcran
import app.resultatsbridge.screens.VerificationMainsScreen
import app.resultatsbridge.common.AppScaffold
import app.resultatsbridge.common.theme.BridgeServeurTheme
import app.resultatsbridge.organisateur.data.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import app.resultatsbridge.common.model.TournoiConfig
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.resultatsbridge.main.BaseActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import app.resultatsbridge.common.model.AnnonceJoueur
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.ChangementDeMouvement
import app.resultatsbridge.common.model.ChangementDeMouvementEntry
import app.resultatsbridge.common.model.ClassementItem
import app.resultatsbridge.common.model.ContratInfo
import app.resultatsbridge.common.model.DonneDataAEnregistrer
import app.resultatsbridge.common.model.DonneResultatDetail
import app.resultatsbridge.common.model.Equipe
import app.resultatsbridge.common.model.EquipeDonneInfo
import app.resultatsbridge.common.model.Joueur
import app.resultatsbridge.common.model.Mouvement
import app.resultatsbridge.common.model.MouvementResult
import app.resultatsbridge.screens.AffichageDonneEcrant
import app.resultatsbridge.screens.AffichageMainsLectureScreen
import app.resultatsbridge.screens.AffichageMainsScreen
import app.resultatsbridge.screens.ConnexionScreen
import app.resultatsbridge.screens.EcranChangementMouvement
import app.resultatsbridge.screens.EcranErreurJoueur
import app.resultatsbridge.screens.EcranMessage
import app.resultatsbridge.screens.EncheresParJoueurScreen
import app.resultatsbridge.screens.MouvementScreen
import app.resultatsbridge.screens.ResultatsDetails4Equ2T21DScreen
import app.resultatsbridge.screens.ResultatsDetailsScreen
import app.resultatsbridge.screens.SafeClassementScreen
import app.resultatsbridge.screens.SelectionEquipeScreen
import kotlin.jvm.javaClass

class ClientActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val OrganisateurIsJoueur = intent.getBooleanExtra("Organisateur_is_Joueur", false)
        val modeOnline           = intent.getBooleanExtra("mode_online", false)
        val idTournoiExtra       = intent.getIntExtra("id_tournoi", -1)
        val dateTournoi          = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
        val ipServeurExtra       = intent.getStringExtra("ip_serveur") ?: ""
        val modeJeu              = intent.getStringExtra("mode_jeu") ?: "rapide"
        val useLocal             = OrganisateurIsJoueur && !modeOnline

        if (!useLocal) {
            ClientNetworkUtils.initialiserServeur(ipServeurExtra)
            Log.i("ClientActivity", "🌐 Serveur initialisé : $ipServeurExtra")
        }
        Log.i("ClientActivity", "👑 Mode ${if (useLocal) "LOCAL" else "ONLINE"} (ID Tournoi: $idTournoiExtra)")

        setContent {
            BridgeServeurTheme {
                // ── États principaux ───────────────────────────────────────────────
                val etape = rememberSaveable {
                    mutableStateOf(
                        if (OrganisateurIsJoueur) EtapeClient.SELECTION_EQUIPE
                        else EtapeClient.CONNEXION
                    )
                }
                val equipes = remember { mutableStateListOf<Equipe>() }
                val equipeChoisie = remember { mutableStateOf<Equipe?>(null) }
                val mouvement = remember { mutableStateOf<Mouvement?>(null) }
                val tournoi = remember { mutableStateOf<Pair<Int, String>?>(null) }
                val idTournoi = tournoi.value?.first ?: -1
                val indexDonneAJouer = remember { mutableStateOf(0) }

                val contratFinal = remember { mutableStateOf<ContratInfo?>(null) }
                val donneAEnregistrer = remember { mutableStateOf<DonneDataAEnregistrer?>(null) }
                val mainsSelectionnees = remember { mutableStateOf<List<List<Carte>>?>(null) }
                val visiteMainsEffectuee = remember { mutableStateOf(false) }
                val aCliqueAfficherMains = remember { mutableStateOf(false) }

                LaunchedEffect(mouvement.value?.indexDonneAJouer) {
                    visiteMainsEffectuee.value = false
                    aCliqueAfficherMains.value = false
                }

                var etapeRetourNonTermine = remember { mutableStateOf<EtapeClient?>(null) }
                val mvntTermineNumero = remember { mutableStateOf<Int?>(null) }
                val equipesMvntTermine = remember { mutableStateOf<Pair<Int, Int>?>(null) }
                val changementDeMouvement =
                    remember { mutableStateOf<ChangementDeMouvement?>(null) }
                val coroutineScope = rememberCoroutineScope()
                val context = this@ClientActivity

                // ── États affichage donnes/résultats ──────────────────────────────
                var mainsAAfficher by remember { mutableStateOf<Map<String, List<Carte>>?>(null) }
                var numDonneSelect by remember { mutableStateOf(0) }
                var encheresAAfficher by remember { mutableStateOf<List<AnnonceJoueur>>(emptyList()) }
                var vulnerable by remember { mutableStateOf("P") }
                var donneur by remember { mutableStateOf("N") }
                var contrat by remember { mutableStateOf("") }
                var declarant by remember { mutableStateOf("") }
                var eqNSSelect by remember { mutableStateOf(0) }
                var eqEOSelect by remember { mutableStateOf(0) }
                var listeDetailsDonnes by remember {
                    mutableStateOf<List<DonneResultatDetail>>(
                        emptyList()
                    )
                }

                // ── État pour savoir d'où on vient quand on consulte les détails ──
                // Permet au bouton Retour de AFFICHAGE_DETAILS_RESULTATS de revenir
                // au bon écran (TOURNOI_TERMINE ou CHANGEMENT_DE_MOUVEMENT)
                var etapeRetourDetails by remember { mutableStateOf<EtapeClient>(EtapeClient.TOURNOI_TERMINE) }

                // ── Écran d'erreur joueur ─────────────────────────────────────────
                var erreurMessage by remember { mutableStateOf("") }
                var erreurEtapeRetour by remember { mutableStateOf<EtapeClient>(EtapeClient.MOUVEMENT_EN_COURS) }
                val afficherErreur: (String, EtapeClient) -> Unit = { message, retour ->
                    val etapeNom = etape.value.name
                    val tournoiId = tournoi.value?.first ?: -1
                    val equipeNum = equipeChoisie.value?.equipeNumero ?: -1
                    erreurMessage = message
                    erreurEtapeRetour = retour
                    etape.value = EtapeClient.ERREUR
                    coroutineScope.launch(Dispatchers.IO) {
                        if (useLocal) DatabaseManager.enregistrerErreurLog(
                            context,
                            tournoiId,
                            equipeNum,
                            etapeNom,
                            message
                        )
                        else ClientNetworkUtils.logErreurServeur(
                            tournoiId,
                            equipeNum,
                            etapeNom,
                            message
                        )
                    }
                }

                // ── États vérification mains ──────────────────────────────────────
                var numeroDonneAVerifier by remember { mutableStateOf(0) }
                var equipesAyantJoueDonne by remember {
                    mutableStateOf<List<EquipeDonneInfo>>(
                        emptyList()
                    )
                }
                var chargementEquipesEnCours by remember { mutableStateOf(true) }
                var erreurChargementEquipes by remember { mutableStateOf<String?>(null) }

                // ── États RELAIS ──────────────────────────────────────────────────
                var mouvementRelais by remember { mutableStateOf<Mouvement?>(null) }
                var indexDonneRelaisEnCours by remember { mutableStateOf(0) }
                var nombreDonnesRelaisSaisies by remember { mutableStateOf(0) }
                var relaisStartPhoto by remember { mutableStateOf(false) }

                // ── Init mode Organisateur ────────────────────────────────────────
                LaunchedEffect(Unit) {
                    if (OrganisateurIsJoueur && idTournoiExtra != -1) {
                        if (useLocal) {
                            withContext(Dispatchers.IO) {
                                val (idOuvert, typeTournoi) = DatabaseManager.getTournoiOuvert(
                                    context
                                ) ?: (null to null)
                                if (idOuvert != null && idOuvert == idTournoiExtra && typeTournoi != null) {
                                    tournoi.value = idOuvert to typeTournoi
                                    val liste =
                                        DatabaseManager.getEquipesDuTournoi(context, idTournoiExtra)
                                    if (liste.isNotEmpty()) {
                                        equipes.addAll(liste)
                                    } else {
                                        tournoi.value = null
                                        afficherErreur(
                                            "Aucune équipe trouvée pour ce tournoi.",
                                            EtapeClient.CONNEXION
                                        )
                                    }
                                } else {
                                    tournoi.value = null
                                    afficherErreur(
                                        "Impossible de charger le tournoi.",
                                        EtapeClient.CONNEXION
                                    )
                                }
                            }
                        } else {
                            withContext(Dispatchers.IO) {
                                val tournoiOnline = ClientNetworkUtils.verifierTournoiOuvert()
                                if (tournoiOnline != null) {
                                    tournoi.value = tournoiOnline
                                    val liste =
                                        ClientNetworkUtils.recupererListeEquipes(tournoiOnline.first)
                                    if (!liste.isNullOrEmpty()) {
                                        equipes.addAll(liste)
                                    } else {
                                        afficherErreur(
                                            "Aucune équipe trouvée sur le serveur.",
                                            EtapeClient.CONNEXION
                                        )
                                    }
                                } else {
                                    afficherErreur(
                                        "Aucun tournoi ouvert sur le serveur.",
                                        EtapeClient.CONNEXION
                                    )
                                }
                            }
                        }
                    }
                }

                // Bloquer le retour système pendant les étapes de saisie active
                BackHandler(
                    enabled = etape.value in setOf(
                        EtapeClient.MOUVEMENT_EN_COURS,
                        EtapeClient.ENCHERES,
                        EtapeClient.AFFICHAGE_MAINS,
                        EtapeClient.AFFICHAGE_MAINS_PHOTO,
                        EtapeClient.SAISIE_MAINS_RELAIS,
                        EtapeClient.ENREGISTRE_DONNE
                    )
                ) { }

                // ══════════════════════════════════════════════════════════════════
                // ÉCRANS PLEIN ÉCRAN (sans AppScaffold)
                // ══════════════════════════════════════════════════════════════════

                if (etape.value == EtapeClient.AFFICHAGE_MAINS || etape.value == EtapeClient.AFFICHAGE_MAINS_PHOTO) {
                    val photoMode = etape.value == EtapeClient.AFFICHAGE_MAINS_PHOTO
                    val mvnt = mouvement.value
                    val idx = mvnt?.indexDonneAJouer ?: -1
                    val donnePortion = mvnt?.donnes?.getOrNull(idx)
                    val numDonne = donnePortion?.numero ?: 0
                    val mainsDirect = donnePortion?.mains
                    val mainsValides =
                        mainsDirect != null && mainsDirect.size == 4 && mainsDirect.all { it.size == 13 }
                    if (mainsValides) {
                        AffichageMainsLectureScreen(
                            numeroDonne = numDonne,
                            mains = mainsDirect!!.map { codes -> codes.map { Carte.fromCode(it) } },
                            onRetour = { etape.value = EtapeClient.MOUVEMENT_EN_COURS }
                        )
                    } else {
                        AffichageMainsScreen(
                            numeroDonne = numDonne,
                            onRetour = {
                                aCliqueAfficherMains.value = false
                                etape.value = EtapeClient.MOUVEMENT_EN_COURS
                            },
                            onEnregistrer = {
                                mainsSelectionnees.value = it
                                etape.value = EtapeClient.MOUVEMENT_EN_COURS
                            },
                            demarrerPhoto = photoMode
                        )
                    }
                    return@BridgeServeurTheme

                } else if (etape.value == EtapeClient.SAISIE_MAINS_RELAIS) {
                    val mvntRelais = mouvementRelais
                    val donnePortion = mvntRelais?.donnes?.getOrNull(indexDonneRelaisEnCours)
                    val numDonne = donnePortion?.numero ?: 0
                    val mainsExistantes = donnePortion?.mains
                    val mainsValides = mainsExistantes != null
                            && mainsExistantes.size == 4
                            && mainsExistantes.all { it.size == 13 }

                    if (mainsValides) {
                        AffichageMainsLectureScreen(
                            numeroDonne = numDonne,
                            mains = mainsExistantes!!.map { codes ->
                                codes.map {
                                    Carte.fromCode(
                                        it
                                    )
                                }
                            },
                            onRetour = { etape.value = EtapeClient.DIALOG_RELAIS }
                        )
                    } else {
                        AffichageMainsScreen(
                            numeroDonne = numDonne,
                            onRetour = { etape.value = EtapeClient.DIALOG_RELAIS },
                            demarrerPhoto = relaisStartPhoto,
                            onEnregistrer = { mainsCartes ->
                                val mainsCodesString =
                                    mainsCartes.map { main -> main.map { it.code } }
                                val idxSaisi = indexDonneRelaisEnCours
                                coroutineScope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        if (useLocal) DatabaseManager.enregistrerMainsRelais(
                                            context,
                                            idTournoi,
                                            numDonne,
                                            mainsCodesString
                                        )
                                        else ClientNetworkUtils.enregistrerMainsRelais(
                                            idTournoi,
                                            numDonne,
                                            mainsCodesString
                                        )
                                    }
                                    if (ok) {
                                        mouvementRelais = mouvementRelais?.let { mvnt ->
                                            mvnt.copy(donnes = mvnt.donnes.mapIndexed { i, d ->
                                                if (i == idxSaisi) d.copy(mains = mainsCodesString) else d
                                            })
                                        }
                                        nombreDonnesRelaisSaisies =
                                            mouvementRelais?.donnes?.count { d ->
                                                d.mains != null && d.mains.size == 4 && d.mains.all { it.size == 13 }
                                            } ?: 0
                                    }
                                    etape.value = EtapeClient.DIALOG_RELAIS
                                }
                            }
                        )
                    }
                    return@BridgeServeurTheme

                    /*} else if (etape.value == EtapeClient.AFFICHAGE_DETAILS_RESULTATS) {
                        // ── Écran de détails des donnes ───────────────────────────────
                        // onBack retourne vers l'étape d'origine (TOURNOI_TERMINE ou
                        // CHANGEMENT_DE_MOUVEMENT selon etapeRetourDetails)
                        ResultatsDetailsScreen(
                            idTournoi = idTournoi,
                            dateTournoi = dateTournoi,
                            resultats = listeDetailsDonnes,
                            OrganisateurIsJoueur = OrganisateurIsJoueur,
                            useLocal = useLocal,
                            typeTournoi = tournoi.value?.second ?: "",
                            onBack = { etape.value = etapeRetourDetails },
                            onSelectDonne = { mains, encheres, numero, eqNS, eqEO, vulne, donn, contr, decl ->
                                mainsAAfficher = mains; encheresAAfficher = encheres
                                numDonneSelect = numero; eqNSSelect = eqNS; eqEOSelect = eqEO
                                vulnerable = vulne; donneur = donn; contrat = contr; declarant = decl
                                etape.value = EtapeClient.AFFICHAGE_DONNE
                            }
                        )
                        return@BridgeServeurTheme
    */
                } else if (etape.value == EtapeClient.AFFICHAGE_DETAILS_RESULTATS) {
                    // ── Écran de détails des donnes ───────────────────────────────
                    // Branchement selon le type de tournoi :
                    //   par4equ2t21d → écran dédié avec résultats filtrés par mouvement
                    //   autres       → écran standard
                    //
                    // Pour par4equ2t21d, on n'affiche que les donnes du/des mouvements
                    // déjà terminés :
                    //   mvntTermineNumero = 1 → donnes 1-7   seulement
                    //   mvntTermineNumero = 2 → donnes 1-14  (mvnt 1 + 2)
                    //   mvntTermineNumero = null (fin tournoi) → toutes les donnes (1-21)

                    val typeTournoiCourant = tournoi.value?.second ?: ""

                    if (typeTournoiCourant == "par4equ2t21d") {
                        // Filtrer les résultats selon le mouvement terminé
                        val resultatsFiltrés = when (mvntTermineNumero.value) {
                            1 -> listeDetailsDonnes.filter { it.numeroDonne in 1..7 }
                            2 -> listeDetailsDonnes.filter { it.numeroDonne in 1..14 }
                            else -> listeDetailsDonnes  // null = fin tournoi → tout afficher
                        }
                        val titreMouvement = when (mvntTermineNumero.value) {
                            1 -> "Mouvement 1 (donnes 1-7)"
                            2 -> "Mouvements 1 & 2 (donnes 1-14)"
                            else -> "Résultats complets (donnes 1-21)"
                        }

                        ResultatsDetails4Equ2T21DScreen(
                            idTournoi = idTournoi,
                            dateTournoi = dateTournoi,
                            titreMouvement = titreMouvement,
                            resultats = resultatsFiltrés,
                            useLocal = useLocal,
                            onBack = { etape.value = etapeRetourDetails },
                            onSelectDonne = { mains, encheres, numero, eqNS, eqEO, vulne, donn, contr, decl ->
                                mainsAAfficher = mains; encheresAAfficher = encheres
                                numDonneSelect = numero; eqNSSelect = eqNS; eqEOSelect = eqEO
                                vulnerable = vulne; donneur = donn; contrat = contr; declarant =
                                decl
                                etape.value = EtapeClient.AFFICHAGE_DONNE
                            }
                        )
                    } else {
                        ResultatsDetailsScreen(
                            idTournoi = idTournoi,
                            dateTournoi = dateTournoi,
                            resultats = listeDetailsDonnes,
                            OrganisateurIsJoueur = OrganisateurIsJoueur,
                            useLocal = useLocal,
                            onBack = { etape.value = etapeRetourDetails },
                            onSelectDonne = { mains, encheres, numero, eqNS, eqEO, vulne, donn, contr, decl ->
                                mainsAAfficher = mains; encheresAAfficher = encheres
                                numDonneSelect = numero; eqNSSelect = eqNS; eqEOSelect = eqEO
                                vulnerable = vulne; donneur = donn; contrat = contr; declarant =
                                decl
                                etape.value = EtapeClient.AFFICHAGE_DONNE
                            }
                        )
                    }
                    return@BridgeServeurTheme

                } else if (etape.value == EtapeClient.AFFICHAGE_DONNE) {
                    AffichageDonneEcrant(
                        idTournoi = idTournoi,
                        dateTournoi = dateTournoi,
                        numeroDonne = numDonneSelect,
                        equipeNS = eqNSSelect,
                        equipeEO = eqEOSelect,
                        mains = mainsAAfficher ?: emptyMap(),
                        encheres = encheresAAfficher,
                        vulnerable = vulnerable,
                        donneur = donneur,
                        contrat = contrat,
                        declarant = declarant,
                        onBack = { etape.value = EtapeClient.AFFICHAGE_DETAILS_RESULTATS }
                    )
                    return@BridgeServeurTheme

                } else if (etape.value == EtapeClient.VERIFICATION_MAINS_AFFICHAGE) {
                    VerificationDonneEcran(
                        numeroDonne = numDonneSelect,
                        mains = mainsAAfficher!!,
                        equipeNS = eqNSSelect,
                        equipeEO = eqEOSelect,
                        vulnerable = vulnerable,
                        donneur = donneur,
                        contrat = contrat,
                        declarant = declarant,
                        onBack = { etape.value = EtapeClient.MOUVEMENT_EN_COURS }
                    )
                    return@BridgeServeurTheme

                } else if (etape.value == EtapeClient.ERREUR) {
                    EcranErreurJoueur(
                        message = erreurMessage,
                        onValider = { etape.value = erreurEtapeRetour }
                    )
                    return@BridgeServeurTheme

                } else if (etape.value == EtapeClient.DIALOG_RELAIS) {
                    val totalDonnes = mouvementRelais?.donnes?.size ?: 0
                    val donnesSaisies = mouvementRelais?.donnes?.count { d ->
                        d.mains != null && d.mains.size == 4 && d.mains.all { it.size == 13 }
                    } ?: 0
                    val tableSuivanteVisible = donnesSaisies >= totalDonnes

                    AppScaffold(
                        ipAddress = ipServeurExtra,
                        numeroTournoi = tournoi.value?.first,
                        equipeChoisie = equipeChoisie.value
                    ) { }

                    AlertDialog(
                        onDismissRequest = { },
                        title = {
                            Text(
                                "🏖️ Table Relais - Mouvement ${mvntTermineNumero.value ?: "?"}",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    "Installez-vous à la table relais.\n\nPas de donnes à jouer ! Vous pouvez saisir les mains pour aider les autres équipes.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(12.dp))

                                mouvementRelais?.donnes?.forEachIndexed { idx, donne ->
                                    val mainsDejaLa =
                                        donne.mains != null && donne.mains.size == 4 && donne.mains.all { it.size == 13 }
                                    if (mainsDejaLa) {
                                        OutlinedButton(
                                            onClick = {
                                                indexDonneRelaisEnCours = idx
                                                etape.value = EtapeClient.SAISIE_MAINS_RELAIS
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                                            border = BorderStroke(1.dp, Color(0xFF2E7D32))
                                        ) {
                                            Text("👁 Voir mains - Donne ${donne.numero}", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    indexDonneRelaisEnCours = idx
                                                    relaisStartPhoto = false
                                                    etape.value = EtapeClient.SAISIE_MAINS_RELAIS
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("✏️ Manuel\nDonne ${donne.numero}", fontWeight = FontWeight.Normal)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    indexDonneRelaisEnCours = idx
                                                    relaisStartPhoto = true
                                                    etape.value = EtapeClient.SAISIE_MAINS_RELAIS
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1565C0)),
                                                border = BorderStroke(1.dp, Color(0xFF1565C0))
                                            ) {
                                                Text("📷 Photo\nDonne ${donne.numero}", fontWeight = FontWeight.Normal)
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                if ((mvntTermineNumero.value
                                        ?: 0) < TournoiConfig.NBRE_MOUVEMENTS
                                ) {
                                    if (changementDeMouvement.value != null) {
                                        Text(
                                            "➡️ Votre prochain mouvement :",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        changementDeMouvement.value?.entries?.forEach { entry ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        "Mouvement ${changementDeMouvement.value?.mvntSuivant ?: "?"} - Table ${entry.tableNumero}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        "Éq. ${entry.equipe.equipeNumero} (NS) : ${entry.equipe.joueur1.prenom} ${entry.equipe.joueur1.nom} & ${entry.equipe.joueur2.prenom} ${entry.equipe.joueur2.nom}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    Text(
                                                        "Éq. ${entry.adversaire.equipeNumero} (EO) : ${entry.adversaire.joueur1.prenom} ${entry.adversaire.joueur1.nom} & ${entry.adversaire.joueur2.prenom} ${entry.adversaire.joueur2.nom}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        etape.value = EtapeClient.ATTENTE_CLASSEMENT
                                    }
                                }

                                if (!tableSuivanteVisible && totalDonnes > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "💡 Saisissez les $totalDonnes donnes pour débloquer 'Table Suivante', ou appuyez sur 'Passer'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            val actionSuivante = {
                                mouvementRelais = null
                                nombreDonnesRelaisSaisies = 0
                                indexDonneRelaisEnCours = 0
                                changementDeMouvement.value = null
                                val mvntCourant = mvntTermineNumero.value ?: 0
                                if (mvntCourant >= TournoiConfig.NBRE_MOUVEMENTS) etape.value =
                                    EtapeClient.ATTENTE_CLASSEMENT
                                else etape.value = EtapeClient.CHANGEMENT_DE_MOUVEMENT
                            }
                            if (tableSuivanteVisible) {
                                Button(onClick = { actionSuivante() }) { Text("Table Suivante ➡️") }
                            } else {
                                OutlinedButton(onClick = { actionSuivante() }) { Text("Passer la saisie →") }
                            }
                        }
                    )
                    return@BridgeServeurTheme

                } else {
                    val nomEcranDebug = when (etape.value) {
                        EtapeClient.CONNEXION                  -> "Connexion joueur"
                        EtapeClient.SELECTION_EQUIPE           -> "Sélection équipe"
                        EtapeClient.RECUPER_MOUVEMENT          -> "Chargement mouvement..."
                        EtapeClient.MOUVEMENT_EN_COURS         -> "Mouvement — Saisie résultat"
                        EtapeClient.ENCHERES                   -> "Saisie enchères"
                        EtapeClient.AFFICHAGE_MAINS            -> "Vérification des mains"
                        EtapeClient.AFFICHAGE_MAINS_PHOTO      -> "PhotoSaisie — En cours de tournoi"
                        EtapeClient.DIALOG_RELAIS              -> "Table Relais"
                        EtapeClient.SAISIE_MAINS_RELAIS        -> "Batch — Saisie mains relais"
                        EtapeClient.VERIFIER_TOURNOI           -> "Vérification tournoi"
                        EtapeClient.ENREGISTRE_DONNE           -> "Enregistrement donne..."
                        EtapeClient.ATTENTE_CLASSEMENT         -> "Attente classement"
                        EtapeClient.TOURNOI_TERMINE            -> "Tournoi terminé — Classement"
                        EtapeClient.CHANGEMENT_DE_MOUVEMENT    -> "Changement de mouvement"
                        EtapeClient.AFFICHAGE_DETAILS_RESULTATS -> "Détails résultats"
                        EtapeClient.AFFICHAGE_DONNE            -> "Affichage donne"
                        EtapeClient.VERIFICATION_MAINS         -> "Vérification mains"
                        EtapeClient.VERIFICATION_MAINS_AFFICHAGE -> "Affichage vérification mains"
                        EtapeClient.ERREUR                     -> "ERREUR"
                    }
                    AppScaffold(
                        ipAddress = ipServeurExtra,
                        numeroTournoi = tournoi.value?.first,
                        equipeChoisie = equipeChoisie.value,
                        nomEcran = nomEcranDebug
                    ) {
                        Log.i("ClientActivity", "🧭 ÉTAPE = ${etape.value}")
                        when (etape.value) {

                            EtapeClient.CONNEXION -> ConnexionScreen(
                                modeOnlineInitial = modeOnline,
                                onEquipesRecues = { id, type, liste ->
                                    tournoi.value = id to type
                                    equipes.clear(); equipes.addAll(liste)
                                    etape.value = EtapeClient.SELECTION_EQUIPE
                                },
                                onMessage = { Log.i("ClientActivity", "📩 $it") }
                            )

                            EtapeClient.SELECTION_EQUIPE -> {
                                val currentTournoi = tournoi.value
                                if (currentTournoi != null) {
                                    SelectionEquipeScreen(
                                        idTournoi = currentTournoi.first,
                                        typeTournoi = currentTournoi.second,
                                        equipes = equipes,
                                        onEquipeChoisie = {
                                            equipeChoisie.value = it; etape.value =
                                            EtapeClient.RECUPER_MOUVEMENT
                                        }
                                    )
                                } else {
                                    EcranMessage(
                                        titre = "Chargement en cours...",
                                        message = "Veuillez patienter.",
                                        texteBouton = "Réessayer",
                                        onOk = { etape.value = EtapeClient.CONNEXION })
                                }
                            }

                            EtapeClient.RECUPER_MOUVEMENT -> {
                                val compteur = remember { mutableStateOf(0) }
                                LaunchedEffect(equipeChoisie.value) {
                                    compteur.value++
                                    val num = compteur.value
                                    val eq = equipeChoisie.value ?: return@LaunchedEffect
                                    val tournoiId = tournoi.value?.first ?: return@LaunchedEffect
                                    Log.i(
                                        "RECUPER_MOUVEMENT",
                                        "🔁 #$num — équipe=${eq.equipeNumero}, tournoiId=$tournoiId"
                                    )

                                    val result = withContext(Dispatchers.IO) {
                                        if (useLocal) DatabaseManager.getMouvementPourEquipe(
                                            context,
                                            tournoiId,
                                            eq.equipeNumero
                                        )
                                        else ClientNetworkUtils.recupererMouvement(
                                            tournoiId,
                                            eq.equipeNumero
                                        )
                                    }
                                    Log.i(
                                        "RECUPER_MOUVEMENT",
                                        "📦 #$num → résultat=${result?.javaClass?.simpleName ?: "null"}"
                                    )

                                    when (result) {
                                        is MouvementResult.Complet -> {
                                            val mvnt = result.mouvement
                                            val estRelais = listOf(
                                                mvnt.joueur1NSNom,
                                                mvnt.joueur2NSNom,
                                                mvnt.joueur1EONom,
                                                mvnt.joueur2EONom
                                            )
                                                .any { it.trim().lowercase() == "relais" }
                                            Log.i(
                                                "RECUPER_MOUVEMENT",
                                                "🃏 #$num — mvnt=${mvnt.mvntNumero}, table=${mvnt.tableNumero}, relais=$estRelais"
                                            )

                                            if (mvnt.indexDonneAJouer == 0 && mvnt.mvntNumero > 1 && !result.tousTermines) {
                                                // Nouveau mouvement, tous n'ont pas encore terminé : attendre
                                                //mémorise le mouvement
                                                mouvement.value = mvnt
                                                indexDonneAJouer.value = 0
                                                contratFinal.value =
                                                    null; mainsSelectionnees.value =
                                                    null; aCliqueAfficherMains.value = false
                                                mvntTermineNumero.value = mvnt.mvntNumero - 1
                                                equipesMvntTermine.value =
                                                    mvnt.equipeNS to mvnt.equipeEO
                                                Log.i(
                                                    "RECUPER_MOUVEMENT",
                                                    "✅ #$num → CHANGEMENT_DE_MOUVEMENT (mvntTermine=${mvnt.mvntNumero - 1})"
                                                )
                                                etape.value = EtapeClient.CHANGEMENT_DE_MOUVEMENT

                                            } else {
                                                if (estRelais) {
                                                    mouvementRelais = mvnt
                                                    indexDonneRelaisEnCours = 0
                                                    nombreDonnesRelaisSaisies =
                                                        mvnt.donnes.count { d ->
                                                            d.mains != null && d.mains.size == 4 && d.mains.all { it.size == 13 }
                                                        }
                                                    mvntTermineNumero.value = mvnt.mvntNumero
                                                    equipesMvntTermine.value =
                                                        mvnt.equipeNS to mvnt.equipeEO
                                                    Log.i(
                                                        "RECUPER_MOUVEMENT",
                                                        "⏩ #$num — passerTableRelais équipe=${eq.equipeNumero}"
                                                    )
                                                    withContext(Dispatchers.IO) {
                                                        if (useLocal) DatabaseManager.incrementerMouvementEquipe(
                                                            context,
                                                            tournoiId,
                                                            eq.equipeNumero
                                                        )
                                                        else ClientNetworkUtils.passerTableRelais(
                                                            tournoiId,
                                                            eq.equipeNumero
                                                        )
                                                    }
                                                    val futurResult = withContext(Dispatchers.IO) {
                                                        if (useLocal) DatabaseManager.getMouvementPourEquipe(
                                                            context,
                                                            tournoiId,
                                                            eq.equipeNumero
                                                        )
                                                        else ClientNetworkUtils.recupererMouvement(
                                                            tournoiId,
                                                            eq.equipeNumero
                                                        )
                                                    }
                                                    val futurMvnt =
                                                        (futurResult as? MouvementResult.Complet)?.mouvement
                                                    Log.i(
                                                        "RECUPER_MOUVEMENT",
                                                        "🔮 #$num — futurMvnt=${futurMvnt?.mvntNumero}, table=${futurMvnt?.tableNumero}"
                                                    )
                                                    if (futurMvnt != null) {
                                                        mouvement.value = futurMvnt
                                                        indexDonneAJouer.value =
                                                            futurMvnt.indexDonneAJouer
                                                        contratFinal.value =
                                                            null; mainsSelectionnees.value =
                                                            null; aCliqueAfficherMains.value = false
                                                    }
                                                    changementDeMouvement.value =
                                                        futurMvnt?.let { f ->
                                                            ChangementDeMouvement(
                                                                mvntSuivant = f.mvntNumero,
                                                                entries = listOf(
                                                                    ChangementDeMouvementEntry(
                                                                        tableNumero = f.tableNumero,
                                                                        equipe = Equipe(
                                                                            f.equipeNS,
                                                                            Joueur(
                                                                                0,
                                                                                f.joueur1NSNom,
                                                                                f.joueur1NSPrenom
                                                                            ),
                                                                            Joueur(
                                                                                0,
                                                                                f.joueur2NSNom,
                                                                                f.joueur2NSPrenom
                                                                            ),
                                                                            tournoiId
                                                                        ),
                                                                        adversaire = Equipe(
                                                                            f.equipeEO,
                                                                            Joueur(
                                                                                0,
                                                                                f.joueur1EONom,
                                                                                f.joueur1EOPrenom
                                                                            ),
                                                                            Joueur(
                                                                                0,
                                                                                f.joueur2EONom,
                                                                                f.joueur2EOPrenom
                                                                            ),
                                                                            tournoiId
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        }
                                                    Log.i(
                                                        "RECUPER_MOUVEMENT",
                                                        "✅ #$num → DIALOG_RELAIS"
                                                    )
                                                    etape.value = EtapeClient.DIALOG_RELAIS

                                                } else {
                                                    mouvement.value = mvnt
                                                    contratFinal.value =
                                                        null; mainsSelectionnees.value =
                                                        null; aCliqueAfficherMains.value = false
                                                    indexDonneAJouer.value = mvnt.indexDonneAJouer
                                                    Log.i(
                                                        "RECUPER_MOUVEMENT",
                                                        "✅ #$num → MOUVEMENT_EN_COURS (indexDonne=${mvnt.indexDonneAJouer})"
                                                    )
                                                    etape.value = EtapeClient.MOUVEMENT_EN_COURS
                                                }
                                            }
                                        }

                                        is MouvementResult.ClassementEnAttente -> {
                                            Log.i(
                                                "RECUPER_MOUVEMENT",
                                                "✅ #$num → ATTENTE_CLASSEMENT"
                                            )
                                            etape.value = EtapeClient.ATTENTE_CLASSEMENT
                                        }

                                        is MouvementResult.Erreur -> {
                                            Log.e(
                                                "RECUPER_MOUVEMENT",
                                                "❌ #$num → Erreur: ${result.message}"
                                            )
                                            afficherErreur(
                                                "Impossible de récupérer le mouvement.",
                                                EtapeClient.RECUPER_MOUVEMENT
                                            )
                                        }

                                        else -> {
                                            Log.e(
                                                "RECUPER_MOUVEMENT",
                                                "❌ #$num → Résultat inattendu: ${result?.javaClass?.simpleName ?: "null"}"
                                            )
                                            afficherErreur(
                                                "Erreur inattendue lors du chargement.",
                                                EtapeClient.RECUPER_MOUVEMENT
                                            )
                                        }
                                    }
                                }
                            }

                            EtapeClient.MOUVEMENT_EN_COURS -> {
                                val eq = equipeChoisie.value ?: return@AppScaffold
                                val mvnt = mouvement.value ?: return@AppScaffold
                                val tournoiId = tournoi.value!!.first
                                MouvementScreen(
                                    modeJeu = modeJeu,
                                    equipe = eq,
                                    date = dateTournoi,
                                    idTournoi = tournoiId,
                                    mouvement = mvnt,
                                    contratFinal = contratFinal.value,
                                    typeTournoi = tournoi.value?.second ?: "",
                                    onEditEncheres = {
                                        donneur = mouvement.value?.donnes?.getOrNull(
                                            mouvement.value?.indexDonneAJouer ?: 0
                                        )?.donneur ?: "N"
                                        etape.value = EtapeClient.ENCHERES
                                    },
                                    onQuitter = {
                                        equipeChoisie.value = null
                                        mouvement.value = null
                                        contratFinal.value = null
                                        mainsSelectionnees.value = null
                                        etape.value = EtapeClient.SELECTION_EQUIPE
                                    },
                                    onAfficherMains = {
                                        aCliqueAfficherMains.value = true
                                        visiteMainsEffectuee.value = true
                                        etape.value = EtapeClient.AFFICHAGE_MAINS
                                    },
                                    onAfficherMainsPhoto = {
                                        aCliqueAfficherMains.value = true
                                        visiteMainsEffectuee.value = true
                                        etape.value = EtapeClient.AFFICHAGE_MAINS_PHOTO
                                    },
                                    onDonneSuivante = { },
                                    onEnregistrerDonne = { donne ->
                                        donneAEnregistrer.value =
                                            donne.copy(mains = mainsSelectionnees.value?.map { m -> m.map { it.code } })
                                        etape.value = EtapeClient.ENREGISTRE_DONNE
                                    },
                                    onMajContrat = { contratFinal.value = it },
                                    aCliqueAfficherMains = aCliqueAfficherMains,
                                    visiteMainsEffectuee = visiteMainsEffectuee,
                                    mainsSelectionnees = mainsSelectionnees,
                                    onVerifierMains = { numeroDonne ->
                                        numeroDonneAVerifier = numeroDonne
                                        coroutineScope.launch {
                                            chargementEquipesEnCours =
                                                true; erreurChargementEquipes = null
                                            try {
                                                equipesAyantJoueDonne = if (useLocal)
                                                    withContext(Dispatchers.IO) {
                                                        DatabaseManager.getEquipesAyantJoueDonne(
                                                            context,
                                                            idTournoi,
                                                            numeroDonne
                                                        )
                                                    }
                                                else ClientNetworkUtils.getEquipesAyantJoueDonne(
                                                    idTournoi,
                                                    numeroDonne
                                                )
                                                if (equipesAyantJoueDonne.isEmpty()) erreurChargementEquipes =
                                                    "Aucune équipe n'a encore joué cette donne"
                                            } catch (e: Exception) {
                                                erreurChargementEquipes = "Erreur de chargement"
                                            } finally {
                                                chargementEquipesEnCours = false
                                            }
                                            etape.value = EtapeClient.VERIFICATION_MAINS
                                        }
                                    }
                                )
                            }

                            EtapeClient.VERIFICATION_MAINS -> {
                                VerificationMainsScreen(
                                    idTournoi = idTournoi,
                                    numeroDonne = numeroDonneAVerifier,
                                    equipesAyantJoue = equipesAyantJoueDonne,
                                    isLoading = chargementEquipesEnCours,
                                    errorMessage = erreurChargementEquipes,
                                    onBack = { etape.value = EtapeClient.MOUVEMENT_EN_COURS },
                                    onAfficherDonne = {
                                        coroutineScope.launch {
                                            try {
                                                val dc = if (useLocal)
                                                    withContext(Dispatchers.IO) {
                                                        DatabaseManager.getDonneComplete(
                                                            context,
                                                            idTournoi,
                                                            numeroDonneAVerifier,
                                                            equipesAyantJoueDonne.first().equipeNS
                                                        )
                                                    }
                                                else ClientNetworkUtils.recupererDonneComplete(
                                                    idTournoi,
                                                    numeroDonneAVerifier,
                                                    equipesAyantJoueDonne.first().equipeNS
                                                )
                                                if (dc != null) {
                                                    mainsAAfficher = dc.mains; encheresAAfficher =
                                                        dc.encheres
                                                    vulnerable = dc.vulnerable; donneur = dc.donneur
                                                    contrat = dc.contrat; declarant = dc.declarant
                                                    numDonneSelect = numeroDonneAVerifier
                                                    eqNSSelect =
                                                        equipesAyantJoueDonne.first().equipeNS
                                                    eqEOSelect =
                                                        equipesAyantJoueDonne.first().equipeEO
                                                    etape.value =
                                                        EtapeClient.VERIFICATION_MAINS_AFFICHAGE
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "ClientActivity",
                                                    "❌ Erreur chargement mains : ${e.message}"
                                                )
                                                afficherErreur(
                                                    "Impossible d'afficher les mains.",
                                                    EtapeClient.VERIFICATION_MAINS
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            EtapeClient.ENCHERES -> {
                                val mvnt = mouvement.value ?: return@AppScaffold
                                val donne = mvnt.donnes?.getOrNull(mvnt.indexDonneAJouer)
                                    ?: return@AppScaffold
                                EncheresParJoueurScreen(donneur = donne.donneur) { contratInfo ->
                                    contratFinal.value = contratInfo
                                    etape.value = EtapeClient.MOUVEMENT_EN_COURS
                                }
                            }

                            EtapeClient.AFFICHAGE_MAINS -> {}
                            EtapeClient.AFFICHAGE_MAINS_PHOTO -> {}
                            EtapeClient.DIALOG_RELAIS -> {}
                            EtapeClient.SAISIE_MAINS_RELAIS -> {}
                            EtapeClient.AFFICHAGE_DETAILS_RESULTATS -> {}
                            EtapeClient.AFFICHAGE_DONNE -> {}
                            EtapeClient.VERIFICATION_MAINS_AFFICHAGE -> {}
                            EtapeClient.ERREUR -> {}

                            EtapeClient.ENREGISTRE_DONNE -> {
                                val donne = donneAEnregistrer.value ?: return@AppScaffold
                                LaunchedEffect(donne) {
                                    val gson = Gson()
                                    val retour = withContext(Dispatchers.IO) {
                                        if (useLocal) DatabaseManager.enregistreDonne(
                                            context,
                                            idTournoi = tournoi.value!!.first,
                                            mvntNumero = donne.mvntNumero,
                                            equipeNS = donne.equipeNS!!,
                                            equipeEO = donne.equipeEO!!,
                                            numeroTable = donne.numeroTable!!,
                                            numeroDonne = donne.numeroDonne,
                                            indexDonneJouee = donne.indexDonneJouee,
                                            contrat = donne.contrat,
                                            declarant = donne.declarant,
                                            resultatContrat = donne.signe,
                                            points = donne.points,
                                            nombrePlis = donne.plis,
                                            carteEntame = donne.carteEntame,
                                            historiqueJson = gson.toJson(donne.historique),
                                            mainsJson = gson.toJson(donne.mains)
                                        )
                                        else ClientNetworkUtils.enregistreDonne(
                                            idTournoi = tournoi.value!!.first,
                                            mvntNumero = donne.mvntNumero,
                                            equipeNS = donne.equipeNS!!,
                                            equipeEO = donne.equipeEO!!,
                                            numeroTable = donne.numeroTable!!,
                                            numeroDonne = donne.numeroDonne,
                                            indexDonneJouee = donne.indexDonneJouee,
                                            contrat = donne.contrat,
                                            declarant = donne.declarant,
                                            resultatContrat = donne.signe,
                                            points = donne.points,
                                            nombrePlis = donne.plis,
                                            carteEntame = donne.carteEntame,
                                            historique = donne.historique,
                                            mains = donne.mains
                                        )
                                    }
                                    Log.i(
                                        "ClientActivity",
                                        "🔍 retour=$retour NBRE_DONNES=${TournoiConfig.NBRE_DONNES_PAR_TABLE} NBRE_MOUVEMENTS=${TournoiConfig.NBRE_MOUVEMENTS}"
                                    )
                                    if (retour < 0) {
                                        afficherErreur(
                                            "Résultat impossible à enregistrer.",
                                            EtapeClient.MOUVEMENT_EN_COURS
                                        ); return@LaunchedEffect
                                    }
                                    if (retour >= TournoiConfig.NBRE_DONNES_PAR_TABLE) {
                                        val mvntCourant = mouvement.value ?: return@LaunchedEffect
                                        if (mvntCourant.mvntNumero == TournoiConfig.NBRE_MOUVEMENTS) {
                                            etape.value = EtapeClient.ATTENTE_CLASSEMENT
                                        } else {
                                            mvntTermineNumero.value = mvntCourant.mvntNumero
                                            equipesMvntTermine.value =
                                                mvntCourant.equipeNS to mvntCourant.equipeEO
                                            etape.value = EtapeClient.CHANGEMENT_DE_MOUVEMENT
                                        }
                                    } else {
                                        mouvement.value =
                                            mouvement.value?.copy(indexDonneAJouer = retour)
                                        contratFinal.value = null; aCliqueAfficherMains.value =
                                            false; mainsSelectionnees.value = null
                                        etape.value = EtapeClient.MOUVEMENT_EN_COURS
                                    }
                                }
                            }

                            // ══════════════════════════════════════════════════════
                            // CHANGEMENT_DE_MOUVEMENT
                            // ══════════════════════════════════════════════════════
                            EtapeClient.CHANGEMENT_DE_MOUVEMENT -> {
                                val tournoiId = tournoi.value?.first ?: return@AppScaffold
                                LaunchedEffect(mvntTermineNumero.value, equipesMvntTermine.value) {
                                    if (changementDeMouvement.value == null) {
                                        val mvntTermine =
                                            mvntTermineNumero.value ?: return@LaunchedEffect
                                        val eqNS =
                                            equipesMvntTermine.value?.first ?: return@LaunchedEffect
                                        val eqEO = equipesMvntTermine.value?.second
                                            ?: return@LaunchedEffect
                                        changementDeMouvement.value = withContext(Dispatchers.IO) {
                                            if (useLocal) DatabaseManager.getFuturMouvement(
                                                context,
                                                tournoiId,
                                                mvntTermine,
                                                eqNS,
                                                eqEO
                                            )
                                            else ClientNetworkUtils.getFuturMouvement(
                                                tournoiId,
                                                mvntTermine,
                                                eqNS,
                                                eqEO
                                            )
                                        }
                                    }
                                }
                                EcranChangementMouvement(
                                    mvntTermineNumero = mvntTermineNumero.value,
                                    equipes = equipesMvntTermine.value,
                                    changement = changementDeMouvement.value,
                                    equipeChoisie = equipeChoisie.value?.equipeNumero,
                                    // ── Type du tournoi pour afficher le bouton provisoire ──
                                    typeTournoi = tournoi.value?.second ?: "",
                                    onVerifierFinMouvement = {
                                        val mvnt = mvntTermineNumero.value
                                            ?: return@EcranChangementMouvement false
                                        if (useLocal) DatabaseManager.toutesEquipesOntTermineMouvement(
                                            context,
                                            tournoiId,
                                            mvnt
                                        )
                                        else ClientNetworkUtils.toutesEquipesOntTermineMouvement(
                                            tournoiId,
                                            mvnt
                                        )
                                    },
                                    // ── Classement provisoire sur les donnes déjà jouées ──
                                    // Charge les détails disponibles et navigue vers
                                    // AFFICHAGE_DETAILS_RESULTATS. Le retour reviendra
                                    // sur CHANGEMENT_DE_MOUVEMENT grâce à etapeRetourDetails.
                                    onClassementProvisoire = {
                                        coroutineScope.launch {
                                            try {
                                                val details = withContext(Dispatchers.IO) {
                                                    if (useLocal) DatabaseManager.getDonneResultatDetails(
                                                        context,
                                                        tournoiId
                                                    )
                                                    else ClientNetworkUtils.recupererDetailsDonnes(
                                                        tournoiId
                                                    )
                                                }
                                                if (details != null) {
                                                    listeDetailsDonnes = details
                                                    // Retour vers CHANGEMENT_DE_MOUVEMENT après consultation
                                                    etapeRetourDetails =
                                                        EtapeClient.CHANGEMENT_DE_MOUVEMENT
                                                    etape.value =
                                                        EtapeClient.AFFICHAGE_DETAILS_RESULTATS
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "ClientActivity",
                                                    "❌ Erreur classement provisoire : ${e.message}"
                                                )
                                                afficherErreur(
                                                    "Impossible de charger le classement provisoire.",
                                                    EtapeClient.CHANGEMENT_DE_MOUVEMENT
                                                )
                                            }
                                        }
                                    },
                                    onOk = {
                                        changementDeMouvement.value = null
                                        mvntTermineNumero.value = null
                                        equipesMvntTermine.value = null
                                        etape.value = EtapeClient.RECUPER_MOUVEMENT
                                    }
                                )
                            }

                            EtapeClient.VERIFIER_TOURNOI -> {
                                var etatVerifie by remember { mutableStateOf<String?>(null) }
                                var enCours by remember { mutableStateOf(false) }
                                val etapeRetour = etapeRetourNonTermine.value
                                val idTournoiNonNull = tournoi.value?.first
                                LaunchedEffect(idTournoiNonNull) {
                                    etapeRetourNonTermine.value = null
                                    if (idTournoiNonNull == null) {
                                        etatVerifie = "ID manquant."; return@LaunchedEffect
                                    }
                                    try {
                                        enCours = true
                                        val etat = withContext(Dispatchers.IO) {
                                            if (useLocal) DatabaseManager.verifierEtatTournoi(
                                                context,
                                                idTournoiNonNull
                                            )
                                            else ClientNetworkUtils.verifierEtatTournoi(
                                                idTournoiNonNull
                                            )
                                        }
                                        etatVerifie = etat
                                        when (etat) {
                                            "TERMINE" -> etape.value = EtapeClient.TOURNOI_TERMINE
                                            "NON_TERMINE" -> etape.value =
                                                etapeRetour ?: EtapeClient.ATTENTE_CLASSEMENT

                                            else -> etape.value = EtapeClient.ATTENTE_CLASSEMENT
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "ClientActivity",
                                            "❌ Erreur vérification tournoi : ${e.message}"
                                        )
                                        afficherErreur(
                                            "Impossible de vérifier l'état du tournoi.",
                                            EtapeClient.ATTENTE_CLASSEMENT
                                        )
                                    } finally {
                                        enCours = false
                                    }
                                }
                                EcranMessage(
                                    titre = "Vérification",
                                    message = if (enCours) "En cours..." else "État : $etatVerifie"
                                )
                            }

                            EtapeClient.ATTENTE_CLASSEMENT -> {
                                EcranMessage(
                                    titre = "Classement en cours...",
                                    message = "Veuillez patienter que toutes les équipes aient fini.",
                                    texteBouton = "🔍 Voir le classement",
                                    onOk = {
                                        etapeRetourNonTermine.value =
                                            EtapeClient.ATTENTE_CLASSEMENT; etape.value =
                                        EtapeClient.VERIFIER_TOURNOI
                                    }
                                )
                            }

                            EtapeClient.TOURNOI_TERMINE -> {
                                var classementAffiche by remember {
                                    mutableStateOf<List<ClassementItem>?>(
                                        null
                                    )
                                }
                                var erreur by remember { mutableStateOf<String?>(null) }
                                var enCours by remember { mutableStateOf(false) }
                                val idTournoiNonNull = tournoi.value?.first
                                val scope = rememberCoroutineScope()
                                LaunchedEffect(idTournoiNonNull) {
                                    if (idTournoiNonNull == null) {
                                        erreur = "ID non disponible."; return@LaunchedEffect
                                    }
                                    try {
                                        enCours = true; erreur = null
                                        classementAffiche = withContext(Dispatchers.IO) {
                                            if (useLocal) {
                                                val lignes = DatabaseManager.getClassementStocke(
                                                    context,
                                                    idTournoiNonNull
                                                )
                                                val mapEq = equipes.associateBy { it.equipeNumero }
                                                val typeTournoi = tournoi.value?.second ?: ""
                                                val nbreEquipes = equipes.size
                                                val nbreTables = nbreEquipes / 2

                                                lignes.map { c ->
                                                    val eq = mapEq[c.numeroEquipe]
                                                    // En Mitchell : orientation fixe selon le numéro d'équipe
                                                    // equipeNumero > nbreTables → NS, sinon → EO
                                                    val orientation =
                                                        if (typeTournoi == "Mitchell" || typeTournoi == "MitchellGueridon") {
                                                            if (c.numeroEquipe > nbreTables) "NS" else "EO"
                                                        } else null

                                                    ClassementItem(
                                                        rang = c.rang,
                                                        numeroEquipe = c.numeroEquipe,
                                                        pts = c.totalPts,
                                                        joueur1Nom = eq?.joueur1?.nom ?: "?",
                                                        joueur1Prenom = eq?.joueur1?.prenom ?: "?",
                                                        joueur2Nom = eq?.joueur2?.nom ?: "?",
                                                        joueur2Prenom = eq?.joueur2?.prenom ?: "?",
                                                        orientationMitchell = orientation,
                                                        scorePct = c.scorePct
                                                    )
                                                }
                                            } else ClientNetworkUtils.recupererResultatsTournoi(
                                                idTournoiNonNull,
                                                equipes,
                                                etape,
                                                tournoi.value?.second ?: ""
                                            )
                                        }
                                        if (classementAffiche == null) erreur =
                                            "Impossible de récupérer les résultats."
                                    } catch (e: Exception) {
                                        erreur = e.message ?: "Erreur"
                                    } finally {
                                        enCours = false
                                    }
                                }
                                when {
                                    erreur != null -> EcranMessage(
                                        titre = "Classement",
                                        message = erreur!!
                                    )

                                    enCours -> EcranMessage(
                                        titre = "Classement...",
                                        message = "Veuillez patienter..."
                                    )

                                    classementAffiche == null -> EcranMessage(
                                        titre = "Classement",
                                        message = "Préparation..."
                                    )

                                    classementAffiche!!.isEmpty() -> EcranMessage(
                                        titre = "Classement",
                                        message = "Aucun classement disponible."
                                    )

                                    else -> SafeClassementScreen(
                                        classement = classementAffiche!!,
                                        onConsulterDetails = {
                                            scope.launch {
                                                try {
                                                    val details = withContext(Dispatchers.IO) {
                                                        if (useLocal) DatabaseManager.getDonneResultatDetails(
                                                            context,
                                                            idTournoiNonNull!!
                                                        )
                                                        else ClientNetworkUtils.recupererDetailsDonnes(
                                                            idTournoiNonNull!!
                                                        )
                                                    }
                                                    if (details != null) {
                                                        listeDetailsDonnes = details
                                                        // Retour vers TOURNOI_TERMINE après consultation
                                                        etapeRetourDetails =
                                                            EtapeClient.TOURNOI_TERMINE
                                                        etape.value =
                                                            EtapeClient.AFFICHAGE_DETAILS_RESULTATS
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("ClientActivity", "Erreur détails", e)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } // FIN AppScaffold
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("ClientActivity", "🔴 onDestroy")
    }
}
