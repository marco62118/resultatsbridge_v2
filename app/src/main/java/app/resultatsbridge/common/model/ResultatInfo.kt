package app.resultatsbridge.common.model

data class ResultatInfo(
    val signe: String,
    val plis: Int, //nombre de plis en plus ou en moins par défaut 0
    val points: Int // calculé par une fonction
)