package app.resultatsbridge.common.model
/**
 * Data class pour stocker les infos d'une équipe ayant joué
 */
data class EquipeDonneInfo(
    val equipeNS: Int,
    val equipeEO: Int,
    val contrat: String,
    val declarant: String
)