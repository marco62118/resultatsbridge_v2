// OUTIL DE DÉVELOPPEMENT — À SUPPRIMER avant mise en service
// Affiche le nom de l'écran en bandeau jaune translucide en haut de chaque page
package app.resultatsbridge.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DebugBandeau(nom: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCCFFEB3B)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "[ $nom ]",
            color = Color(0xFF000000),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
