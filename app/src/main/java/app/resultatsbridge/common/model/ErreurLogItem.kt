package app.resultatsbridge.common.model

data class ErreurLogItem(
    val id: Int,
    val timestamp: String,
    val idTournoi: Int,
    val equipeNumero: Int,
    val etape: String,
    val message: String
)
