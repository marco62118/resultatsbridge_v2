package app.resultatsbridge.common.model

data class Mouvement(
    val mvntNumero: Int,// ici il y aura 5 mouvement 5 changement de table
    val tableNumero: Int,// c'est le numéro d'une des 3 tables
    val equipeNS: Int,// le numéro de l'équipe qui jouer en Nord/Sud
    val joueur1NSNom: String,
    val joueur1NSPrenom: String,
    val joueur2NSNom: String,
    val joueur2NSPrenom: String,
    val equipeEO: Int,// le numéro de l'équipe qui jouer en Est/Ouest
    val joueur1EONom: String,
    val joueur1EOPrenom: String,
    val joueur2EONom: String,
    val joueur2EOPrenom: String,
    val donnes: List<DonneDetail>,// liste des donnes avec leur détal dans la classe Donne Detail
    val indexDonneAJouer: Int // index de la donne à Jouer dans le mouvement


)