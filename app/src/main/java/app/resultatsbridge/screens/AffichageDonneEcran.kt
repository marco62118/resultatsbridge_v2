// ÉCRAN : Affichage donne — tableau enchères + mains N/S/E/O (AffichageDonneEcrant + TableauEncheres)
package app.resultatsbridge.screens

import android.app.Activity
import app.resultatsbridge.BuildConfig
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import app.resultatsbridge.common.ui.components.CarteUnifiee
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.EcranPleinScaffold
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.model.AnnonceJoueur


@Composable
fun AffichageDonneEcrant(
    idTournoi: Int,
    dateTournoi: String,
    numeroDonne: Int,
    mains: Map<String, List<Carte>>,
    encheres: List<AnnonceJoueur>,
    equipeNS: Int,
    equipeEO: Int,
    vulnerable: String,
    donneur: String,
    contrat: String = "",
    declarant: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }

    EcranPleinScaffold {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20))) {
            if (BuildConfig.DEBUG) Text("[ Affichage donne complète ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)

            val mainsTriees = mapOf(
                "N" to trierCartes(mains["N"] ?: emptyList()),
                "S" to trierCartes(mains["S"] ?: emptyList()),
                "E" to trierCartes(mains["E"] ?: emptyList()),
                "O" to trierCartes(mains["O"] ?: emptyList())
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp)) {
                val cardW = maxWidth.value / 13f
                val cardH = cardW * 2.2f

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // NORD — horizontal
                    Text("NORD", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 13) {
                            CarteUnifiee(mainsTriees["N"]?.getOrNull(i), largeur = cardW.dp, hauteur = cardH.dp, valeurSizeSp = 19f, symbolSizeDp = 18f)
                        }
                    }

                    // OUEST | TableauEncheres | EST
                    Row(Modifier.fillMaxWidth()) {
                        // OUEST : rotate +90° → base (symbole) vers la gauche (Ouest)
                        Column(Modifier.width(cardH.dp)) {
                            Text("OUEST", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            for (i in 0 until 13) {
                                Box(Modifier.rotateWithLayout(90f)) {
                                    CarteUnifiee(mainsTriees["O"]?.getOrNull(i), largeur = cardW.dp, hauteur = cardH.dp, valeurSizeSp = 19f, symbolSizeDp = 18f)
                                }
                            }
                        }

                        // Centre : infos donne + tableau enchères + bouton retour
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Donne $numeroDonne : ", color = Color.White, fontSize = 12.sp)
                                Text("NS $equipeNS", color = Color.Yellow, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                Text(" / ", color = Color.White, fontSize = 12.sp)
                                Text("EO $equipeEO", color = Color.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            TableauEncheres(encheres, vulnerable, contrat, declarant, donneur)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp)
                            ) {
                                Box(Modifier.size(36.dp).background(Color.Red, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Text("Retour", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Tournoi $idTournoi du $dateTournoi", color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }

                        // EST : rotate -90° → base (symbole) vers la droite (Est)
                        Column(Modifier.width(cardH.dp)) {
                            Text("EST", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            for (i in 0 until 13) {
                                Box(Modifier.rotateWithLayout(-90f)) {
                                    CarteUnifiee(mainsTriees["E"]?.getOrNull(i), largeur = cardW.dp, hauteur = cardH.dp, valeurSizeSp = 19f, symbolSizeDp = 18f)
                                }
                            }
                        }
                    }

                    // SUD — horizontal
                    Text("SUD", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 13) {
                            CarteUnifiee(mainsTriees["S"]?.getOrNull(i), largeur = cardW.dp, hauteur = cardH.dp, valeurSizeSp = 19f, symbolSizeDp = 18f)
                        }
                    }
                }
            }
        }
    }
}

// Échange les dimensions layout (width↔height) puis applique la rotation visuelle.
// Le parent voit cardH×cardW en layout ; la carte s'affiche tournée à 90°.
private fun Modifier.rotateWithLayout(degrees: Float): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.height, placeable.width) {
                placeable.placeRelative(
                    x = -(placeable.width - placeable.height) / 2,
                    y = -(placeable.height - placeable.width) / 2
                )
            }
        }
        .rotate(degrees)

private val ORDRE_COULEUR = mapOf("P" to 0, "C" to 1, "T" to 2, "K" to 3)
private val ORDRE_VALEUR  = mapOf("A" to 0, "R" to 1, "D" to 2, "V" to 3, "10" to 4,
    "9" to 5, "8" to 6, "7" to 7, "6" to 8, "5" to 9, "4" to 10, "3" to 11, "2" to 12)

private fun trierCartes(cartes: List<Carte>): List<Carte> =
    cartes.sortedWith(compareBy({ ORDRE_COULEUR[it.couleur] ?: 99 }, { ORDRE_VALEUR[it.valeur] ?: 99 }))

@Composable
fun MainHorizontaleAdaptative(cartes: List<Carte>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val largeurCarte = maxWidth / 13.5f
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            trierCartes(cartes).forEach { CarteUnifiee(it, largeurCarte, largeurCarte * 1.6f, fond = Color.White) }
        }
    }
}

// =========================================================================
// Reconstruire les enchères à partir du contrat
// =========================================================================

fun reconstruireEncheres(
    contrat: String,
    declarant: String,
    donneur: String
): List<AnnonceJoueur> {
    val encheres = mutableListOf<AnnonceJoueur>()

    val posDeclarant = when (declarant.uppercase()) {
        "NORD" -> "N"
        "SUD" -> "S"
        "EST" -> "E"
        "OUEST" -> "O"
        else -> declarant.uppercase().take(1)
    }

    val positions = listOf("O", "N", "E", "S")

    val indexDonneur = positions.indexOf(donneur.uppercase())
    val indexDeclarant = positions.indexOf(posDeclarant)

    if (indexDonneur == -1 || indexDeclarant == -1) {
        Log.e("TableauEncheres", "❌ Donneur ou déclarant invalide: donneur=$donneur, declarant=$posDeclarant")
        return emptyList()
    }

    var index = indexDonneur
    while (index != indexDeclarant) {
        encheres.add(AnnonceJoueur(positions[index], "Passe"))
        index = (index + 1) % 4
    }

    encheres.add(AnnonceJoueur(posDeclarant, contrat))

    index = (indexDeclarant + 1) % 4
    repeat(3) {
        encheres.add(AnnonceJoueur(positions[index], "Passe"))
        index = (index + 1) % 4
    }

    Log.i("TableauEncheres", "✅ Enchères reconstruites: ${encheres.size} annonces")
    encheres.forEach { Log.i("TableauEncheres", "   ${it.joueur}: ${it.annonce}") }

    return encheres
}

@Composable
fun TableauEncheres(
    encheres: List<AnnonceJoueur>,
    vulnerable: String,
    contrat: String = "",
    declarant: String = "",
    donneur: String = ""
) {
    val positionsSource = listOf("N", "E", "S", "O")
    val encheresFinales = if (encheres.isEmpty() && contrat.isNotEmpty() && declarant.isNotEmpty()) {
        Log.i("AffichageDonneEcran", "TableauEncheres🔄 Reconstruction des enchères depuis contrat: $contrat par $declarant")
        reconstruireEncheres(contrat, declarant, donneur)
    } else {
        encheres
    }

    val encheresAlignees = mutableListOf<String>()

    if (encheresFinales.isNotEmpty()) {
        val premierJoueur = encheresFinales.first().joueur
        val decalage = positionsSource.indexOf(premierJoueur)
        repeat(decalage) { encheresAlignees.add("") }
        encheresFinales.forEach { ann ->
            val texteOriginal = ann.annonce.uppercase()
            val texteFormate = when {
                texteOriginal == "PASSE" -> "Passe"
                texteOriginal == "X" || texteOriginal == "CONTRE" -> "X"
                texteOriginal == "XX" || texteOriginal == "SURCONTRE" -> "XX"
                else -> texteOriginal
                    .replace("S", " SA")
                    .replace("P", " ♠")
                    .replace("C", " ♥")
                    .replace("K", " ♦")
                    .replace("T", " ♣")
            }
            encheresAlignees.add(texteFormate)
        }
    }

    Surface(
        modifier = Modifier.width(220.dp).heightIn(max = 200.dp),
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                positionsSource.forEach { pos ->
                    val nom = when(pos) { "O" -> "OUEST"; "N" -> "NORD"; "E" -> "EST"; else -> "SUD" }
                    val isVulnerable = when (vulnerable.uppercase()) {
                        "NS" -> pos == "N" || pos == "S"
                        "EO" -> pos == "E" || pos == "O"
                        "T" -> true
                        else -> false
                    }
                    val fondCouleur = if (isVulnerable) Color(0xFFEF5350) else Color(0xFFECEFF1)
                    val texteCouleur = if (isVulnerable) Color.White else Color.Black
                    Box(
                        modifier = Modifier.weight(1f).background(fondCouleur).border(1.dp, Color(0xFF546E7A)).padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(nom, textAlign = TextAlign.Center, color = texteCouleur, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val lignes = encheresAlignees.chunked(4)
                items(lignes) { ligne ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        for (i in 0..3) {
                            val texte = ligne.getOrNull(i) ?: ""
                            val couleurText = when {
                                texte.contains("♥") || texte.contains("♦") -> Color.Red
                                texte == "" -> Color.Transparent
                                else -> Color.Black
                            }
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, Color(0xFF546E7A)).padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = couleurText)
                            }
                        }
                    }
                }
            }
        }
    }
}
