package app.resultatsbridge.common.model

data class ChangementDeMouvement(
    val mvntSuivant: Int, // le numéro du mouvement qu'on va interroger (souvent mvntTermineNumero+1)
    val entries: List<ChangementDeMouvementEntry>
)
