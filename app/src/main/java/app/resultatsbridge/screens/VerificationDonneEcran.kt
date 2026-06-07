// ÉCRAN : Vérification donne — comparaison résultat enregistré vs attendu
package app.resultatsbridge.screens

import android.app.Activity
import app.resultatsbridge.v2.BuildConfig
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.EcranPleinScaffold
import app.resultatsbridge.common.model.Carte
import app.resultatsbridge.common.ui.components.CarteUnifiee

/**
 * Écran de vérification d'une donne
 * Affiche uniquement les 4 mains sans les enchères
 */
@Composable
fun VerificationDonneEcran(

    numeroDonne: Int,
    mains: Map<String, List<Carte>>,
    equipeNS: Int,
    equipeEO: Int,
    vulnerable: String,
    donneur: String,
    contrat: String,
    declarant: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Forcer orientation portrait
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }

    EcranPleinScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1B5E20))  // Tapis vert
        ) {
            if (BuildConfig.DEBUG) Text("[ Vérification donne ]", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            // ✅ BANDEAU AVEC BOUTON RETOUR
            val vertBandeau = Color(0xFF2E7D32)
            val styleSerre = TextStyle(lineHeight = 14.sp)

            Column(modifier = Modifier.fillMaxWidth()) {
                // LIGNE 1 : Infos donne
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(vertBandeau)
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Donne $numeroDonne : ",
                        color = Color.White,
                        fontSize = 13.sp,
                        style = styleSerre
                    )
                    Text(
                        text = "NS $equipeNS",
                        color = Color.Yellow,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        style = styleSerre
                    )
                    Text(
                        text = " contre ",
                        color = Color.White,
                        fontSize = 13.sp,
                        style = styleSerre,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Text(
                        text = "EO $equipeEO",
                        color = Color.Cyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        style = styleSerre
                    )
                }

                // LIGNE 3 : Contrat et infos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(vertBandeau)
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Donneur: $donneur",
                        color = Color.White,
                        fontSize = 13.sp,
                        style = styleSerre,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "Vulnérable: $vulnerable",
                        color = Color.White,
                        fontSize = 13.sp,
                        style = styleSerre,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (contrat.isNotEmpty() && declarant.isNotEmpty()) {
                        Text(
                            text = "Contrat: $contrat par $declarant",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            style = styleSerre,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // ✅ AFFICHAGE DES 4 MAINS
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // NORD
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NORD",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    MainHorizontaleAdaptative(mains["N"] ?: emptyList())
                }

                // CENTRE : OUEST | EST
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.padding(start = 2.dp)) {
                        Text(
                            "OUEST",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        mains["O"]?.forEach { CarteUnifiee(it, 38.dp, 50.dp, shape = RoundedCornerShape(5.dp), fond = Color.White, valeurSizeSp = 14f, symbolSizeDp = 13f) }
                    }

                    // ✅ BOUTON RETOUR EN BAS AU CENTRE
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            //.fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F)  // Rouge
                        )
                    ) {

                        Text("Retour", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(modifier = Modifier.padding(end = 2.dp)) {
                        Text(
                            "EST",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        mains["E"]?.forEach { CarteUnifiee(it, 38.dp, 50.dp, shape = RoundedCornerShape(5.dp), fond = Color.White, valeurSizeSp = 14f, symbolSizeDp = 13f) }
                    }
                }

                // SUD
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MainHorizontaleAdaptative(mains["S"] ?: emptyList())
                    Text("SUD", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


