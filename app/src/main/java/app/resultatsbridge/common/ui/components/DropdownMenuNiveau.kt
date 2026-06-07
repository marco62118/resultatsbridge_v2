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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DropdownMenuNiveau(
    selected: Int,
    onSelect: (Int) -> Unit,
    isEnabled: Boolean,
    backgroundColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(0) + (1..7) // 0 = Passe Générale


    val label = when (selected) {
        0 -> "" // vide tant que rien n’est choisi
        else -> selected.toString()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor, shape = RoundedCornerShape(6.dp))
            .clickable(enabled = isEnabled) { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        // ⭐ CHANGEMENT : Grisage du texte si inactif
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        DropdownMenu(expanded = expanded && isEnabled, onDismissRequest = { expanded = false }) {
            options.forEach { niveau ->
                val text = if (niveau == 0) "Passe Générale" else niveau.toString()
                DropdownMenuItem(
                    text = { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    onClick = {
                        onSelect(niveau)
                        expanded = false
                    }
                )
            }
        }
    }
}