package app.resultatsbridge.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DropdownMenuPlis(
    selected: Int?,
    niveauContrat: Int,
    signe: String,
    onSelect: (Int) -> Unit,
    isEnabled: Boolean,
    backgroundColor: Color
) {

    val maxPlis = when (signe) {
        "+" -> (7 - niveauContrat).coerceAtLeast(0)
        "-" -> niveauContrat + 6
        else -> 0  // signe "=" → 0 pli
    }
    val options = (0..maxPlis).toList()
    var expanded by remember { mutableStateOf(false) }

    val label = selected?.toString() ?: ""

    // Règle d'incohérence visuelle (prioritaire sur le fond gris de verrouillage)
    val incoherentPlis = signe != "=" && selected == 0
    // Si l'état est INCOHÉRENT, on utilise le fond d'incohérence, sinon le fond passé (actif ou inactif)
    val finalFond = if (incoherentPlis) Color(0xFFFFE0E0) else backgroundColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ⭐ CHANGEMENT : Utilisation du fond final (incohérence ou isEnabled)
            .background(finalFond, shape = RoundedCornerShape(6.dp))
            // ⭐ CHANGEMENT : Utilisation de 'isEnabled' pour le clic
            .clickable(enabled = isEnabled) { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        // ⭐ CHANGEMENT : Grisage du texte si inactif
        Text(
            label,
            fontSize = 24.sp,

            color = Color.Black
        )

        DropdownMenu(expanded = expanded && isEnabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString(), fontSize = 24.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}