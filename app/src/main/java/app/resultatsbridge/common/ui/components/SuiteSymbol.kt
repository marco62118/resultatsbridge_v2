package app.resultatsbridge.common.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resultatsbridge.R

private fun drawableForCode(code: String): Int = when (code) {
    "P" -> R.drawable.ic_pique
    "C" -> R.drawable.ic_coeur
    "K" -> R.drawable.ic_carreau
    else -> R.drawable.ic_trefle   // "T"
}

// SuiteSymbol : reçoit un CODE couleur ("P" "C" "K" "T")
@Composable
fun SuiteSymbol(couleur: String, color: Color, sizeDp: Float) {
    Image(
        painter = painterResource(drawableForCode(couleur)),
        contentDescription = null,
        modifier = Modifier.size(sizeDp.dp),
        colorFilter = ColorFilter.tint(color)
    )
}

// SymboleSuite : reçoit un SYMBOLE Unicode ("♣" "♠" "♥" "♦") ou du texte ("SA", "")
// Les 4 couleurs → Image vectorielle. Tout le reste (SA, vide) → Text.
@Composable
fun SymboleSuite(symbole: String, color: Color, sizeDp: Float) {
    val code = SuiteStyle.code(symbole)
    if (code.isNotEmpty()) {
        Image(
            painter = painterResource(drawableForCode(code)),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            colorFilter = ColorFilter.tint(color)
        )
    } else {
        Text(text = symbole, color = color, fontSize = sizeDp.sp)
    }
}
