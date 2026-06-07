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
fun DropdownMenuCouleur(
    selected: String,
    onSelect: (String) -> Unit,
    // NOUVEAUX PARAMÈTRES
    isEnabled: Boolean,
    backgroundColor: Color
) {
    val options = listOf("♣", "♦", "♥", "♠", "SA")
    var expanded by remember { mutableStateOf(false) }

    val colorFor = { symbol: String ->
        when (symbol) {
            "♦", "♥" -> Color.Red
            else -> Color.Black
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ⭐ CHANGEMENT : Utilisation de backgroundColor
            .background(backgroundColor, shape = RoundedCornerShape(6.dp))
            // ⭐ CHANGEMENT : Utilisation de 'isEnabled' pour le clic
            .clickable(enabled = isEnabled) { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        // ⭐ CHANGEMENT : Grisage du texte si inactif
        val textColor = if (isEnabled) colorFor(selected) else Color.Gray

        SymboleSuite(symbole = selected, color = textColor, sizeDp = 26f)

        DropdownMenu(expanded = expanded && isEnabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        SymboleSuite(symbole = option, color = colorFor(option), sizeDp = 26f)
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}