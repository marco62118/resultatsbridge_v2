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
fun DropdownMenuDeclarant(
    selected: String,
    onSelect: (String) -> Unit,
    // NOUVEAUX PARAMÈTRES
    isEnabled: Boolean,
    backgroundColor: Color
) {
    val directions = listOf("Nord", "Est", "Sud", "Ouest")
    var expanded by remember { mutableStateOf(false) }

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
        Text(
            selected,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        DropdownMenu(expanded = expanded && isEnabled, onDismissRequest = { expanded = false }) {
            directions.forEach { dir ->
                DropdownMenuItem(
                    text = { Text(dir, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    onClick = {
                        onSelect(dir)
                        expanded = false
                    }
                )
            }
        }
    }
}