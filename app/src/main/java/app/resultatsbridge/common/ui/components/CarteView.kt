package app.resultatsbridge.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import app.resultatsbridge.common.model.Carte

@Composable
fun CarteView(carte: Carte?) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .border(1.dp, Color.DarkGray)
            .background(Color(0xFFFFFAE6))
            .padding(4.dp)
    ) {
        if (carte != null) {
            val color = SuiteStyle.couleurCode(carte.couleur)
            Column {
                Text(text = carte.valeur, fontSize = 15.sp, color = Color.Black)
                SuiteSymbol(couleur = carte.couleur, color = color, sizeDp = 16f)
            }
        }
    }
}
