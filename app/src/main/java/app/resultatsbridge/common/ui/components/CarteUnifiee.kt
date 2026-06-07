package app.resultatsbridge.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.common.model.Carte

// Composable unifié pour toutes les cartes bridge.
// Valeur en haut, symbole couleur en bas, 3 Spacer(weight=1f) pour un espacement régulier.
// valeurSizeSp et symbolSizeDp sont des Float pour éviter le bug TextUnit inline-class en paramètre Compose.
@Composable
fun CarteUnifiee(
    carte: Carte?,
    largeur: Dp,
    hauteur: Dp,
    shape: Shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
    fond: Color = Color(0xFFFFF9E6),
    fondVide: Color = Color(0xFFF0F0F0),
    bordureColor: Color = Color.Gray,
    valeurSizeSp: Float = 20f,
    symbolSizeDp: Float = 20f,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(largeur).height(hauteur)
            .clip(shape)
            .background(if (carte == null) fondVide else fond)
            .border(1.dp, bordureColor, shape)
            .then(
                if (onClick != null)
                    Modifier.clickable(enabled = carte != null) { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (carte != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = carte.valeur,
                    fontWeight = FontWeight.Black,
                    fontSize = valeurSizeSp.sp,
                    color = Color.Black,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Spacer(Modifier.weight(0.2f))
                SuiteSymbol(
                    couleur = carte.couleur,
                    color = SuiteStyle.couleurCode(carte.couleur),
                    sizeDp = symbolSizeDp
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
