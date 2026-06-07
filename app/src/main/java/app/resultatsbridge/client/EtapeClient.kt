package app.resultatsbridge.client

enum class EtapeClient {
    CONNEXION,
    SELECTION_EQUIPE,
    RECUPER_MOUVEMENT,
    MOUVEMENT_EN_COURS,
    ENCHERES,
    AFFICHAGE_MAINS,
    AFFICHAGE_MAINS_PHOTO,
    DIALOG_RELAIS,                 // Affichage du dialog table relais (stable, pas de re-incrément)
    SAISIE_MAINS_RELAIS,           // Saisie/lecture des mains d'une donne relais
    VERIFIER_TOURNOI,
    ENREGISTRE_DONNE,
    ATTENTE_CLASSEMENT,
    TOURNOI_TERMINE,
    CHANGEMENT_DE_MOUVEMENT,
    AFFICHAGE_DETAILS_RESULTATS,
    AFFICHAGE_DONNE,
    VERIFICATION_MAINS,
    VERIFICATION_MAINS_AFFICHAGE,
    ERREUR
}
