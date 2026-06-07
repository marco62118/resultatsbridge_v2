# DOCUMENTATION COMPLÈTE - RÉSUTATS BRIDGE 
*Générée le 28/04/2026 — Lecture de 80+ fichiers Kotlin et 25+ fichiers PHP*

---

## 1. VUE D'ENSEMBLE DE L'APPLICATION

**TournoiBridgeOnline** est une application Android/serveur PHP pour gérer et jouer des tournois de bridge en ligne. L'application fonctionne en deux modes :

- **Mode Local (offline)** : Toute la gestion du tournoi se fait sur une seule tablette (organisateur + serveur HTTP intégré) via Wi-Fi. Les joueurs se connectent localement à la tablette organisateur.
- **Mode Online (cloud)** : L'organisateur exporte le tournoi vers `https://resultats-bridge.alwaysdata.net/asso`. Les joueurs se connectent au serveur cloud distant.

Deux rôles utilisateur :
1. **Organisateur** : Crée le tournoi, constitue les équipes, démarre le serveur local, calcul des résultats.
2. **Joueur (Client)** : Se connecte, sélectionne son équipe, saisit les donnes, consulte le classement.

Constante serveur : `URL_SERVEUR_ONLINE = "https://resultats-bridge.alwaysdata.net/asso"` (Constants.kt)

---

## 2. FICHIERS KOTLIN — DESCRIPTION DÉTAILLÉE

### 2.1 Entrée de l'application

**BridgeApplication.kt**
Classe `Application` singleton. Enregistre une callback sur le cycle de vie des Activity pour maintenir l'écran allumé (`FLAG_KEEP_SCREEN_ON`). Aucune dépendance externe notable.

**BaseActivity.kt**
Classe parente de toutes les Activity. Surcharge `attachBaseContext()` et `onResume()` pour forcer :
- `fontScale = 1.0f` → bloque la taille de police utilisateur
- `densityDpi = 420` → bloque le zoom grand affichage (Samsung)

Garantit une UX cohérente quelle que soit la configuration système du terminal.

**MainActivity.kt**
Écran de sélection principal (Compose) :
- 3 boutons radio mode saisie : `"rapide"` (contrat seul) / `"encheres"` / `"encheres_mains"`
- 2 boutons radio mode connexion : Local / Online
- Bouton "Joueur" → lance `ClientActivity`
- Bouton "Organisateur" → lance `OrganisateurActivity`
- Affichage bandeau via `AppScaffold()`

Les modes (`modeOnline`, `modeJeu`) sont passés via Intent. Par défaut : `modeOnline=false`.

---

### 2.2 Configuration et constantes

**ClientConfig.kt**
Singleton de configuration mode client.
- `isLocalServerPlayer : Boolean` → court-circuite le réseau vers `DatabaseManager` local
- `setLocalServerPlayerMode(Boolean)` → setteur avec log

**Constants.kt**
- `const val URL_SERVEUR_ONLINE = "https://resultats-bridge.alwaysdata.net/asso"`

**AppScaffold.kt**
Composant Compose réutilisable — bandeau vert `Color(0xFF2E7D32)` avec titre, numéro tournoi, date, IP serveur. Utilisé dans toutes les Activity pour cohérence visuelle.

---

### 2.3 Modèles de données Kotlin

**TournoiConfig.kt**
Singleton global du tournoi (non thread-safe) :
- `NBRE_MOUVEMENTS : Int = 5`
- `NBRE_DONNES_PAR_TABLE : Int = 4`
- `EQUIPE_RELAIS : Int? = null`
- `NBRE_TABLES : Int = 0` (0 = pas Mitchell)

**Mouvement.kt**
Data class représentant une ronde de tournoi :
- `mvntNumero : Int` → numéro de mouvement (1..N)
- `tableNumero : Int`
- `equipeNS : Int`, `joueur1NSNom/Prenom`, `joueur2NSNom/Prenom`
- `equipeEO : Int`, `joueur1EONom/Prenom`, `joueur2EONom/Prenom`
- `donnes : List<DonneDetail>`
- `indexDonneAJouer : Int` (0-based)

**MouvementResult.kt**
Sealed class :
- `MouvementEnCours(mouvement: Mouvement)` → mouvement en cours
- `MouvementSuivant(mouvement: Mouvement)` → passage au mouvement suivant
- `ClassementEnAttente(nbreEnregistrement: Int)` → résultats pas encore prêts
- `Erreur(message: String)`

**Equipe.kt**
- `equipeNumero : Int`, `joueur1 : Joueur`, `joueur2 : Joueur`, `idTournoi : Int`
- Implémente `Parcelable` pour passage via Intent

**Joueur.kt**
- `idJoueur : Int`, `nom : String`, `prenom : String`
- Aussi `Parcelable`

**DonneDetail.kt**
- `numero : Int`, `donneur : String` (N/S/E/O), `vulnerable : String` (T/P/NS/EO)
- `mains : List<List<String>>?` (null si pas encore jouée)

**DonneComplete.kt**
- `mains : Map<String, List<Carte>>` → {"N":[...], "E":[...], "S":[...], "O":[...]}
- `encheres : List<AnnonceJoueur>`, `vulnerable`, `donneur`, `contrat`, `declarant`

**DonneDataAEnregistrer.kt**
Tout ce qui est envoyé au serveur pour enregistrer une donne :
- `contrat : String`, `declarant : String`
- `signe : String` (+/=/-)
- `points : Int` (calculé par `calculerPoints()`)
- `plis : Int` (levées +/-)
- `carteEntame : String`
- `numeroDonne`, `indexDonneJouee`, `mvntNumero : Int`
- `equipeNS`, `equipeEO`, `numeroTable : Int?`
- `historique : List<Tour>?`, `mains : List<List<String>>?`

**DonneResultatDetail.kt**
- `numeroDonne`, `equipeNS`, `equipeEO`, `contrat`, `declarant`, `resultatContrat`, `nombrePli`, `carteEntame`, `pointsNS`, `pointsEO : Int`
- `ptsNS`, `ptsEO : Double` (matchpoints Neuberg)
- `vulnerable : String`

**ClassementItem.kt**
- `rang : Int`, `numeroEquipe : Int`, `pts : Double`
- `joueur1/2 Nom/Prenom : String`
- `orientationMitchell : String?` ("NS", "EO", ou null pour Howell)
- `scorePct : Double` (pourcentage Neuberg, 0-100%)

**ClassementManager.kt**
Object singleton calculant le classement depuis les résultats.
Méthode : `calculerClassementTournoi(resultats): ResultatCalcul`
Algorithme :
1. Regroupe résultats par donne
2. Calcule N = max(nFois jouée pour chaque donne)
3. Pour chaque donne : Simplifiés → Neuberg si nFois < N
4. Accumule points par équipe → rang → scorePct

**ClassementManager4Equ2T21D.kt**
Calcul spécialisé tournoi "par4equ2t21d" (4 équipes, 2 tables, 21 donnes, 3 mouvements).
Croisement par mouvement, barème EBL (voir section 8.4).

**Enchere.kt**
- `niveau : Int` (1..7), `couleur : String` (♣/♦/♥/♠/SA)

**Carte.kt**
- `valeur : String` (A/R/D/V/10..2), `couleur : String` (P/C/K/T)
- Properties : `affichage` ("A♠"), `symbole` ("♠"), `code` ("AT")
- `companion fun fromCode(code: String): Carte`

**ContratInfo.kt**
- `niveau`, `couleur`, `declarant`, `insulte` (""/X/XX)
- `entameCarte`, `entameCouleur`
- `historique : List<Tour>?`
- `signe`, `plis`, `points`

**ChangementDeMouvement.kt**
- `mvntSuivant : Int`, `entries : List<ChangementDeMouvementEntry>`

**ChangementDeMouvementEntry.kt**
- `equipe : Equipe`, `adversaire : Equipe`, `tableNumero : Int`
- Parcelable

**AuthAssociation.kt**
- `idAssociation`, `tokenApi`, `nom : String`

**AnnonceJoueur.kt**
- `joueur : String`, `annonce : String` ("1SA", "passe", "contre"...)

**CreationTournoiResult.kt**
- `idTournoi : Int`, `avertissement : String?`

**EquipeDonneInfo.kt**
- `equipeNS`, `equipeEO : Int`, `contrat`, `declarant : String`

**ErreurLogItem.kt**
- `id`, `idTournoi`, `equipeNumero : Int`, `timestamp`, `etape`, `message : String`

**EtatEnchere.kt**
- `derniereAnnonceur : Int?` (0=O, 1=N, 2=E, 3=S)
- `derniereEnchere : Enchere?`, `contreActif`, `surcontreActif : Int?`

**JeuDeCartes.kt**
Object singleton — lazy property `toutesLesCartes : List<Carte>` (52 cartes).

**ResultatInfo.kt**
- `signe`, `plis`, `points`

**ServerModels.kt**
- `ServerClassement(numeroEquipe, totalPts, rang)`
- `ServerResultatsResponse(etat, classement)`

**Tour.kt**
- `joueur : String`, `annonce : String` (enchère historisée)

---

### 2.4 Utilitaires Kotlin

**CalculerPoints.kt**
`fun calculerPoints(insulte, couleur, vulnerable, declarant, niveau, signe, plis): Int`

- Signe "-" (chute) :
  - Non vulnérable : -50/lev, X → -100/-300/-500, XX = 2× X
  - Vulnérable : -100/lev, X → -200/-500/-800, XX = 2× X
- Signe "+/=" :
  - pointsBase = multiplicateur × base(couleur/niveau)
  - pointsSurlevées = surlevées × valeur
  - primeManche = base ≥ 100 ? (vul? 500 : 300) : 50
  - primeContre = insulte=="X" ? 50 : 100
  - primeChelem = niv 6 ? (vul? 750 : 500) : niv 7 ? (vul? 1500 : 1000) : 0

**SecurePrefsUtils.kt**
`fun getSecurePrefs(context: Context): SharedPreferences`
Utilise `EncryptedSharedPreferences` (AES256_GCM/SIV). Gestion récupération si corruption.

**ChampSaisieSansPadding.kt**
`BasicTextField` Compose sans padding Material3. Padding manuel : start=4.dp, top/bottom=8.dp.

**DropdownMenuBox.kt**
Dropdown Compose simple avec liste d'options.

---

### 2.5 Composants UI Kotlin

**CarteView.kt** — Carte 48×64 dp, fond crème `0xFFFFFAE6`, texte rouge pour ♥♦, noir pour ♠♣

**GrilleCartes.kt** — `LazyVerticalGrid` 13 colonnes (52 cartes). Callback `onCarteCliquee(Carte)`

**MainView.kt** — 13 cartes en 2 rangées (7+6)

**DropdownMenuCouleur.kt** — {♣, ♦, ♥, ♠, SA}

**DropdownMenuDeclarant.kt** — {Nord, Est, Sud, Ouest}

**DropdownMenuEntameCarte.kt** — {2..10, V, D, R, A}

**DropdownMenuEntameCouleur.kt** — {♣, ♦, ♥, ♠}

**DropdownMenuInsulte.kt** — {"", "X", "XX"}

**DropdownMenuNiveau.kt** — {0=Passe, 1..7}

**DropdownMenuPlis.kt** — logique contextuelle : `maxPlis = signe=="+" ? (7-niveau) : signe=="-" ? (niveau+6) : 0`. Fond rose si incohérence (signe="=" ET plis>0).

**DropdownMenuSigne.kt** — {"+", "=", "-"} en 36sp. Fond rose si incohérence.

---

### 2.6 Client (Joueur)

**EtapeClient.kt**
Enum des 17 états de l'écran joueur :
- `CONNEXION` → Écran login serveur
- `SELECTION_EQUIPE` → Choix équipe
- `RECUPER_MOUVEMENT` → Chargement mouvement
- `MOUVEMENT_EN_COURS` → Saisie donne
- `ENCHERES` → Saisie enchères (mode encheres)
- `AFFICHAGE_MAINS` → Affichage mains (mode encheres_mains)
- `DIALOG_RELAIS` → Dialog table relais
- `SAISIE_MAINS_RELAIS` → Saisie mains relais
- `VERIFIER_TOURNOI` → Vérification tournoi ouvert
- `ENREGISTRE_DONNE` → Sauvegarde donne en cours
- `ATTENTE_CLASSEMENT` → Résultats pas encore prêts
- `TOURNOI_TERMINE` → Tournoi terminé
- `CHANGEMENT_DE_MOUVEMENT` → Dialog nouvelle table
- `AFFICHAGE_DETAILS_RESULTATS` → Affichage scores détaillés
- `AFFICHAGE_DONNE` → Consultation donne
- `VERIFICATION_MAINS` / `VERIFICATION_MAINS_AFFICHAGE` → Vérification mains
- `ERREUR` → Écran erreur

**ClientActivity.kt** (~839 lignes)
Activity principale joueur — machine à états basée sur `EtapeClient`.

Intent reçus :
- `OrganisateurIsJoueur : Boolean` → organisateur joue aussi (mode local)
- `mode_online : Boolean`
- `id_tournoi : Int`
- `ip_serveur : String`
- `mode_jeu : String` ("rapide" / "encheres" / "encheres_mains")

States clés :
- `etape : MutableState<EtapeClient>`
- `equipeChoisie : MutableState<Equipe?>`
- `mouvement : MutableState<Mouvement?>`
- `tournoi : MutableState<Pair<Int, String>?>` (ID, type)
- `contratFinal : MutableState<ContratInfo?>`
- `donneAEnregistrer : MutableState<DonneDataAEnregistrer?>`
- `mainsSelectionnees : MutableState<List<List<Carte>>?>`
- `mvntTermineNumero : MutableState<Int?>` → numéro du mouvement terminé
- `equipesMvntTermine : MutableState<Pair<Int,Int>?>` → NS/EO du mouvement terminé
- `changementDeMouvement : MutableState<ChangementDeMouvement?>`
- `mouvementRelais : Mouvement?`
- `indexDonneRelaisEnCours : Int`
- `nombreDonnesRelaisSaisies : Int`

**ClientNetworkUtils.kt**
Utilitaire toutes les requêtes HTTP joueur :
- `initialiserServeur(ip: String)` → stocke IP serveur local
- `connexionAssociation(email, mdp): AuthAssociation?`
- `verifierTournoiOuvert(): Pair<Int, String>?` → (idTournoi, type)
- `getEquipes(idTournoi): List<Equipe>`
- `recupererMouvement(idTournoi, equipeNumero): MouvementResult`
- `enregistrerDonne(data: DonneDataAEnregistrer): MouvementResult`
- `passerTableRelais(idTournoi, equipeNumero): MouvementResult`
- `getClassement(idTournoi): List<ClassementItem>`
- `readUrl(urlStr): String` → GET HTTP
- `postUrl(urlStr, bodyJson): String` → POST HTTP

**ClientTestUI.kt**
Composant Compose test connexion serveur local. Champ IP + bouton test. Callback `onIpReady(String)`.

---

### 2.7 Organisateur

**OrganisateurActivity.kt**
Intent reçus : `mode_online`, `mode_jeu`
States : `idTournoiState`, `typeTournoiState`, `tournoiOuvertState`, `modeOnlineState`, `modeJeuState`
Transitions vers : `CreationTournoiActivity` → `ConstitutionEquipesActivity` → `ParticipationOrganisateurActivity`

**CreationTournoiActivity.kt**
Sélection type tournoi (Howell ou Mitchell). Charge types depuis BD locale (offline) ou serveur (online). Dialog avertissement si Howell.

**ConstitutionEquipesActivity.kt**
Constitution équipes (pairing joueurs 2 par 2).
Intent reçus : `id_tournoi`, `nombre_equipes`, `joueurs`, `nbre_donnes_par_table`, `type_tournoi`
Appelle `ConstitutionEquipesScreen` (composant Compose).

**ListeEquipesActivity.kt**
Affichage liste des équipes du tournoi.

**ParticipationOrganisateurActivity.kt**
Lance `ClientActivity` en mode local pour l'organisateur qui joue.

**DatabaseHelper.kt**
Singleton gestion BD locale SQLite (`tournoi.db`).
- `initializeDatabase(context)` → copie `tournoi.db` depuis assets si première fois
- `getDatabase(context): SQLiteDatabase`
- `closeDatabase()`
- `migrerSiNecessaire()` → ajout colonnes manquantes (nbre_mouvements, etc.)

Tables BD locale :
- `type` : {type PK, nombre_table, nombre_donne}
- `tournois` : {ID, date, type, nbre_equipe, nbre_donne_total, nbre_enregistrement, nbre_mouvements, nbre_donnes_par_table, nbre_tables, ouvert, mouvement}
- `joueurs` : {ID, nom, prenom}
- `equipes` : {ID, equipe_numero, id_joueur1, id_joueur2, id_tournoi, mvnt_numero DEFAULT 1, index_donne_jouee DEFAULT -1}
- `resultats` : {ID, numero_donne, numero_table, equipeNS, equipeEO, pointsNS, pointsEO, contrat, declarant, signe, plis, carteEntame, nombrePli, ptsNS REAL, ptsEO REAL}
- `mains` : {id, carte1..carte13}
- `donnes` : {id, id_tournoi, numero_donne, main_N, main_E, main_S, main_O}
- `encheres` : {id, id_resultat, ordre, joueur, annonce}
- `erreurs_log` : {id, timestamp, id_tournoi, equipe_numero, etape, message}

**DatabaseManager.kt**
Singleton toutes les opérations BD locale :
- `getListeTypesTournoi(context)` → `List<Triple<String, Int, Int>>`
- `getTournoiOuvert(context)` → `Pair<Int, String>?` (ID, type)
- `creerTournoi(...)`, `creerTournoiMitchell(...)`, `creerTournoiMitchellGueridon(...)`
- `miseAJourTournoiMitchell(...)`, `miseAJourTournoiMitchellGueridon(...)`
- `getMouvementPourEquipe(context, idTournoi, equipeNumero): MouvementResult` → **NE MODIFIE JAMAIS LA BD**
- `getMouvementMitchell(...)`, `getMouvementMitchellGueridon(...)` → calcul dynamique
- `enregistrerDonne(context, DonneDataAEnregistrer)` → INSERT résultat + incrémente `index_donne_jouee`
- `incrementerMouvementEquipe(context, idTournoi, equipeNumero)` → **LOCAL UNIQUEMENT, mode relais** : `mvnt_numero+1`, `index_donne_jouee=-1`
- `getClassementStocke(context, idTournoi): List<ClassementItem>`

**ServerManager.kt**
- `startServer(context): String?` → démarre NanoHTTPD port 8080, retourne IP locale
- `stopServer()`

**ClientServeurHTTP.kt**
Serveur HTTP NanoHTTPD port 8080. Routes :
- `GET /verifierTournoiOuvert` → `{id, type, nbreMouvements, nbreDonnesParTable, equipeRelais, nbreTables}`
- `GET /verifierFinMouvement?idTournoi=X&mvntNumero=Y` → `{termine: bool}`
- `GET /getEquipes?idTournoi=X` → liste Equipes JSON
- `GET /getMouvement?idTournoi=X&equipeNumero=Y` → Mouvement JSON complet
- `POST /enregistrerDonne` → DonneDataAEnregistrer JSON
- `GET /passerTableRelais?idTournoi=X&equipe=Y` → ChangementDeMouvement JSON
- `GET /getClassement?idTournoi=X` → classement final
- `GET /getEquipesAyantJoueDonne?idTournoi=X&numeroDonne=Y`

**NetworkUtils.kt**
- `getLocalIpAddress(): String` → IPv4 locale (exclut loopback)

---

### 2.8 Écrans partagés (Screens)

**ConnexionScreen.kt**
Login joueur online. Champs email + mdp. POST /connexionAssociation.php. Stocke `lastEmail` dans EncryptedSharedPreferences.

**SelectionEquipeScreen.kt**
Liste équipes avec noms joueurs. Clic → `equipeChoisie`.

**MouvementScreen.kt**
Écran principal saisie donne :
- En-tête : numéro donne, équipes, vulnérabilité, donneur
- Zone contrat : niveau, couleur, insulte, déclarant
- Zone résultat : signe, plis (dropdown contextuel), points (calculé auto via `calculerPoints()`)
- Zone entame (optionnel) : hauteur + couleur
- Boutons : "Enchères", "Afficher mains", "Enregistrer donne", "Donne suivante"
- Fond rose si incohérence (signe="=" ET plis>0)

**EquipesScreen.kt** — Affichage liste équipes

**EquipeItem.kt** — Composant une ligne équipe

**EcranChangementMouvement.kt** — Dialog changement de table/mouvement

**EcranMessage.kt** — Dialog affichage message simple

**EncheresScreen.kt** — Saisie enchères (si modeJeu != "rapide")

**Encheresparjoueurscreen.kt** — Affichage enchères par joueur

**AffichageDonneEcran.kt** — Affichage donne sans saisie

**AffichageMainsScreen.kt** — Affichage 4 mains (13 cartes chacune)

**ClassementScreen.kt**
Classement final. Mitchell : 2 sections NS/EO. Howell : 1 section unique.
Affiche `scorePct` sous les pts (ex: "62,5 %") si scorePct > 0.

**ConstitutionEquipesScreen.kt** — Composant Compose constitution équipes

**ResultatsDetailsScreen.kt** — Détails résultats par donne. `formatPts(v: Double)` pour affichage ptsNS/ptsEO décimaux.

**ResultatsDetails4Equ2T21DScreen.kt** — Affichage spécialisé 4 équipes croisement 2 tables

**VerificationDonneEcran.kt** — Affichage 4 mains sans enchères (vérification)

**VerificationMainsScreen.kt** — Écran vérification mains distribuées

---

## 3. FICHIERS PHP — DESCRIPTION DÉTAILLÉE

### 3.1 Configuration et utilitaires

**config.php**
Constantes centralisées (protégé .htaccess, inaccessible via HTTP) :
```php
define('DB_HOST', 'mysql-resultats-bridge.alwaysdata.net');
define('DB_NAME', 'resultats-bridge_db');
define('DB_USER', 'resultats-bridge');
define('DB_PASS', 'JmmMa0562+');      // ← à externaliser en variable d'env
define('SMTP_HOST', 'smtp-resultats-bridge.alwaysdata.net');
define('SMTP_USER', 'resultats-bridge@alwaysdata.net');
define('SMTP_PASS', 'JmmMa0562+');    // ← à externaliser en variable d'env
define('SMTP_PORT', 465);
```

**fonctions.php**
- `logServer($message)` → écriture log serveur avec timestamp
- `envoyerEmail($destinataire, $sujet, $corpsHtml): bool` → PHPMailer via SMTP alwaysdata

### 3.2 DTOs PHP

**Mouvement.php**
```php
class Mouvement {
  public $mvntNumero, $tableNumero;
  public $equipeNS, $joueur1NSNom, $joueur1NSPrenom, $joueur2NSNom, $joueur2NSPrenom;
  public $equipeEO, $joueur1EONom, $joueur1EOPrenom, $joueur2EONom, $joueur2EOPrenom;
  public $donnes, $indexDonneAJouer;
}
```

**MouvementResult.php**
```php
class MouvementResult {
  public $status = "", $message = "";
  public ?Mouvement $mouvement = null;
  public ?int $nbreEnregistrement = null;
  public bool $tousTermines = true;

  static function MouvementComplet(Mouvement $m, bool $tous): self { ... }
  static function ClassementEnAttente(int $n): self { ... }
  static function Erreur(string $msg): self { ... }
}
```

**DonneDetail.php**
```php
class DonneDetail {
  public int $numero;
  public ?string $donneur, $vulnerable;
  public ?array $mains;
}
```

### 3.3 DatabaseManager.php (~1398 lignes)

Méthodes principales :
- `connect(): PDO` → connexion MySQL depuis config.php
- `getIdAssociation($tokenApi): int`
- `connexionAssociation($email, $mdp): array?` → {idAssociation, nom, token_api}
- `getTournoiOuvert($idAssociation): array?`
- `getEquipesDuTournoi($idTournoi): array`
- `getMouvementPourEquipe($idTournoi, $equipeNumero): MouvementResult` → **JAMAIS de UPDATE BD**
- `getMouvementMitchell($idTournoi, $equipeNumero): MouvementResult`
- `enregistrerDonne($idTournoi, $data): array`
  - INSERT INTO resultats (...)
  - UPDATE equipes SET index_donne_jouee = index_donne_jouee + 1
  - Ne touche **jamais** à mvnt_numero
- `passerTableRelais($idTournoi, $equipeNumero): void`
  - UPDATE equipes SET mvnt_numero = mvnt_numero + 1, index_donne_jouee = nbre_donnes_par_table - 1
  - Ne décrémente **pas** nbre_enregistrement
- `finaliserClassementTournoi($idTournoi)` → appelle CalculClassementManager
- `importerTournoi($idAssociation, $data): array` → génère token_public
- `genererOuGetTokenPublic($idTournoi): string`
- `getDetailTournoiParTokenPublic($tokenPublic): array?`
- `getDonneCompleteParTokenPublic($tokenPublic, $id, $numeroDonne, $equipeNS): array?`

### 3.4 CalculClassementManager.php (~274 lignes)

`calculerClassementTournoi($resultats): array`
Algorithme Simplifiés + Neuberg + scorePct, identique à `ClassementManager.kt`.
Retour : `{classement: [{numeroEquipe, totalPts, rang, scorePct}, ...]}`

### 3.5 serverBridge.php (~913 lignes) — Routes principales

Reçoit `token_api` via GET/POST/header `X-Token-Api`.

| Route | Méthode | Paramètres | Retour |
|-------|---------|------------|--------|
| `/verifierTournoiOuvert` | GET | — | {id, type, nbreMouvements, nbreDonnesParTable, equipeRelais, nbreTables} |
| `/fermerTournoiOuvert` | POST | — | {etat} |
| `/creerTournoi` | POST | type, nbreEquipes, nbreDonnes, nbreEnregistrement | {etat, idTournoi} |
| `/miseAJourTournoiMitchell` | POST | idTournoi, nbreEquipes, nbreDonnes, nbreEnregistrement, nbreMouvements | {etat} |
| `/enregistrerEquipes` | POST | idTournoi, equipes[] | {etat} |
| `/getPositionsMouvement1` | GET | idTournoi, typeTournoi | {etat, positions} |
| `/getMouvement` | GET | idTournoi, equipeNumero | Mouvement JSON complet |
| `/enregistreDonne` | POST | DonneDataAEnregistrer JSON | {etat, indexDonneAJouer} |
| `/verifierFinMouvement` | GET | idTournoi, mvntNumero | {termine: bool} |
| `/getFuturMouvement` | GET | idTournoi, mvntActuel, equipeNS, equipeEO | {mvntSuivant, entries} |
| `/getEtatTournoi` | GET | idTournoi | {etat: TERMINE\|NON_TERMINE} |
| `/getResultatsTournoi` | GET | idTournoi | {etat, classement, token_public} |
| `/getDetailsDonnes` | GET | idTournoi | [{numero_donne, equipeNS, equipeEO, contrat, ...}] |
| `/getDonneComplete` | GET | idTournoi, numeroDonne, equipeNS | {etat, mains, encheres, vulnerable, donneur} |
| `/getMainsRelais` | GET | idTournoi, numeroDonne | {etat, mains:[[13]×4]} |
| `/enregistrerMainsRelais` | POST | idTournoi, numeroDonne, mains | {etat} |
| `/passerTableRelais` | GET | idTournoi, equipe | {etat} |
| `/importerTournoi` | POST | JSON tournoi complet | {etat, idTournoi} |

### 3.6 APIs complémentaires

**connexionAssociation.php**
`POST {email, mdp}` → `{etat: "OK", token_api, nom, idAssociation}`

**api_public.php** (sans authentification)
- `?action=tournoi&tokenPublic=XXX&id=YYY` → détail tournoi + liste équipes
- `?action=donne&tokenPublic=XXX&id=YYY&numeroDonne=Z&equipeNS=W` → donne complète avec mains/enchères

**api_club.php**
- `?action=connexion` (POST) → {etat, token, idAssociation, nom, nbre_tournois_joues, nbre_tournois_max, code_adherent}
- `?action=mdpOublie` (POST) → envoie code reset 6 chiffres par email (valide 15 min)
- `?action=resetMdp` (POST) → valide code et update mdp_hash
- `?action=tournois` → liste tournois avec token_public

**api_adherent.php**
- `?action=connexion` (POST {email, mdp}) → {etat, token, idAssociation, nomClub, nom, prenom}
- `?action=mdpOublie` (POST)
- Routes gestion profil adhérent

**index.php**
Interface web PC organisateur (HTML/PHP). Affichage tournoi ouvert. Boutons : Créer tournoi, Constitution équipes, Fermer tournoi.

---

## 4. FLUX UTILISATEUR COMPLET

### 4.1 Flux Organisateur

```
Étape 1 : MainActivity
  → Sélection Local/Online + mode saisie → Clic "Organisateur"

Étape 2 : OrganisateurActivity
  → Local : charge tournoi depuis BD (getTournoiOuvert)
  → Online : connexion email/mdp via connexionAssociation.php
  → Affichage "Aucun tournoi" ou "Tournoi N° X"

Étape 3 : CreationTournoiActivity
  → Sélection type Howell ou Mitchell
  → Si Mitchell : saisie nbreDonnesParTable
  → creerTournoi() / creerTournoiMitchell()
  → Transition ConstitutionEquipesActivity

Étape 4 : ConstitutionEquipesActivity
  → Pairing joueurs 2 par 2
  → "Enregistrer et Démarrer" → miseAJourTournoiMitchell() + ouvrirTournoi()

Étape 5 : OrganisateurActivity (retour)
  → ouvert=1 dans BD
  → ServerManager.startServer() → NanoHTTPD port 8080
  → Affichage IP locale

Étape 6 : Clôture
  → fermerTournoiOuvert()
  → Si online : importerTournoi() → génère token_public
```

### 4.2 Flux Joueur

```
Étape 1 : MainActivity → Clic "Joueur"

Étape 2 : CONNEXION (si online)
  → ConnexionScreen (email + mdp)
  → POST /connexionAssociation.php → token_api stocké
  → Transition SELECTION_EQUIPE

Étape 3 : SELECTION_EQUIPE
  → GET /getEquipes?idTournoi=X
  → Clic équipe → equipeChoisie
  → Transition RECUPER_MOUVEMENT

Étape 4 : RECUPER_MOUVEMENT
  → GET /verifierTournoiOuvert → charge TournoiConfig
  → GET /getMouvement?idTournoi=X&equipeNumero=Y
  → Si équipe adverse = "relais" → DIALOG_RELAIS
  → Si MouvementSuivant → CHANGEMENT_DE_MOUVEMENT
  → Sinon → MOUVEMENT_EN_COURS

Étape 5 : MOUVEMENT_EN_COURS
  → MouvementScreen (saisie donne)
  → "Enregistrer" → DonneDataAEnregistrer → ENREGISTRE_DONNE

Étape 6 : ENREGISTRE_DONNE
  → POST /enregistrerDonne
  → MouvementEnCours → donne suivante
  → ClassementEnAttente → ATTENTE_CLASSEMENT
  → Erreur → ERREUR

Étape 7 : Boucle donne
  → indexDonneAJouer++ → GET /getMouvement(indexDonneAJouer=N)
  → Jusqu'à fin du mouvement

Étape 8 : DIALOG_RELAIS (table relais)
  → passerTableRelais() [online] ou incrementerMouvementEquipe() [local]
  → 2e getMouvement() → futurMouvement
  → CHANGEMENT_DE_MOUVEMENT

Étape 9 : TOURNOI_TERMINE
  → GET /getResultatsTournoi → classement final + token_public
  → Affichage ClassementScreen
```

---

## 5. MODÈLES DE DONNÉES — STRUCTURE COMPLÈTE

### 5.1 Tables BD Locale (SQLite Android)

```sql
CREATE TABLE type (
  type TEXT PRIMARY KEY,
  nombre_table INTEGER,
  nombre_donne INTEGER
);

CREATE TABLE tournois (
  ID INTEGER PRIMARY KEY,
  date TEXT, type TEXT,
  nbre_equipe INTEGER, nbre_donne_total INTEGER,
  nbre_enregistrement INTEGER, nbre_mouvements INTEGER,
  nbre_donnes_par_table INTEGER, nbre_tables INTEGER,
  ouvert INTEGER, mouvement TEXT
);

CREATE TABLE joueurs (
  ID INTEGER PRIMARY KEY, nom TEXT, prenom TEXT
);

CREATE TABLE equipes (
  ID INTEGER PRIMARY KEY,
  equipe_numero INTEGER, id_joueur1 INTEGER, id_joueur2 INTEGER,
  id_tournoi INTEGER,
  mvnt_numero INTEGER DEFAULT 1,
  index_donne_jouee INTEGER DEFAULT -1
);

CREATE TABLE resultats (
  ID INTEGER PRIMARY KEY,
  numero_donne INTEGER, numero_table INTEGER,
  equipeNS INTEGER, equipeEO INTEGER,
  pointsNS INTEGER, pointsEO INTEGER,
  contrat TEXT, declarant TEXT, signe TEXT,
  plis INTEGER, carteEntame TEXT, nombrePli INTEGER,
  ptsNS REAL, ptsEO REAL
);

CREATE TABLE mains (
  id INTEGER PRIMARY KEY,
  carte1 TEXT, carte2 TEXT, carte3 TEXT, carte4 TEXT, carte5 TEXT,
  carte6 TEXT, carte7 TEXT, carte8 TEXT, carte9 TEXT, carte10 TEXT,
  carte11 TEXT, carte12 TEXT, carte13 TEXT
);

CREATE TABLE donnes (
  id INTEGER PRIMARY KEY,
  id_tournoi INTEGER, numero_donne INTEGER,
  main_N INTEGER, main_E INTEGER, main_S INTEGER, main_O INTEGER
);

CREATE TABLE encheres (
  id INTEGER PRIMARY KEY,
  id_resultat INTEGER, ordre INTEGER, joueur TEXT, annonce TEXT
);

CREATE TABLE erreurs_log (
  id INTEGER PRIMARY KEY,
  timestamp TEXT, id_tournoi INTEGER,
  equipe_numero INTEGER, etape TEXT, message TEXT
);
```

### 5.2 Tables BD Serveur (MySQL alwaysdata)

```sql
CREATE TABLE associations (
  ID INT PRIMARY KEY AUTO_INCREMENT,
  nom VARCHAR(255), email VARCHAR(255) UNIQUE,
  mdp_hash VARCHAR(64), token_api VARCHAR(64) UNIQUE,
  actif BOOLEAN, nbre_tournois_max INT, code_adherent VARCHAR(50)
);

CREATE TABLE tournois (
  ID INT PRIMARY KEY AUTO_INCREMENT,
  id_association INT, date DATE, type VARCHAR(50),
  nbre_equipe INT, nbre_donne_total INT, nbre_mouvements INT,
  nbre_donnes_par_table INT, nbre_tables INT,
  ouvert BOOLEAN, token_public VARCHAR(64) UNIQUE
);

CREATE TABLE equipes (
  ID INT PRIMARY KEY AUTO_INCREMENT,
  id_tournoi INT, equipe_numero INT,
  id_joueur1 INT, id_joueur2 INT,
  mvnt_numero INT DEFAULT 1,
  index_donne_jouee INT DEFAULT -1,
  nbre_enregistrement INT
);

CREATE TABLE joueurs (
  ID INT PRIMARY KEY AUTO_INCREMENT,
  nom VARCHAR(255), prenom VARCHAR(255),
  email VARCHAR(255), mdp_hash VARCHAR(64), actif BOOLEAN
);

CREATE TABLE resultats (
  ID INT PRIMARY KEY AUTO_INCREMENT,
  id_tournoi INT, numero_donne INT, numero_table INT,
  equipeNS INT, equipeEO INT,
  pointsNS INT, pointsEO INT,
  contrat VARCHAR(10), declarant VARCHAR(10),
  signe VARCHAR(2), plis INT, carteEntame VARCHAR(5),
  nombrePli INT, ptsNS DOUBLE, ptsEO DOUBLE
);

CREATE TABLE mains (
  id INT PRIMARY KEY AUTO_INCREMENT,
  carte1 VARCHAR(3), carte2 VARCHAR(3), carte3 VARCHAR(3), carte4 VARCHAR(3),
  carte5 VARCHAR(3), carte6 VARCHAR(3), carte7 VARCHAR(3), carte8 VARCHAR(3),
  carte9 VARCHAR(3), carte10 VARCHAR(3), carte11 VARCHAR(3), carte12 VARCHAR(3),
  carte13 VARCHAR(3)
);

CREATE TABLE donnes (
  id INT PRIMARY KEY AUTO_INCREMENT,
  id_tournoi INT, numero_donne INT,
  main_N INT, main_E INT, main_S INT, main_O INT
);

CREATE TABLE encheres (
  id INT PRIMARY KEY AUTO_INCREMENT,
  id_resultat INT, ordre INT, joueur VARCHAR(10), annonce VARCHAR(20)
);

CREATE TABLE reset_tokens (
  id INT PRIMARY KEY AUTO_INCREMENT,
  type ENUM('club', 'adherent'), id_cible INT,
  code VARCHAR(10), expire_at DATETIME, utilise BOOLEAN DEFAULT FALSE
);

CREATE TABLE sessions_club (
  id INT PRIMARY KEY AUTO_INCREMENT,
  id_association INT, token VARCHAR(64) UNIQUE, expire DATETIME
);

CREATE TABLE sessions_adherents (
  id INT PRIMARY KEY AUTO_INCREMENT,
  id_joueur INT, token VARCHAR(64) UNIQUE, expire DATETIME
);
```

---

## 6. PROTOCOLE RÉSEAU — FORMAT JSON COMPLET

### 6.1 Authentification

**POST /connexionAssociation.php**
```json
// Requête
{ "email": "asso@example.com", "mdp": "password" }

// Réponse OK
{ "etat": "OK", "token_api": "abc123...", "nom": "Amicale de Bridge d'Embrun", "idAssociation": 5 }
```

**GET /verifierTournoiOuvert**
```json
{ "id": 42, "type": "par4equ2t21d", "nbreMouvements": 3, "nbreDonnesParTable": 7, "equipeRelais": 0, "nbreTables": 2 }
```

### 6.2 Mouvements

**GET /getMouvement?idTournoi=42&equipeNumero=3**
```json
{
  "mvntNumero": 1, "tableNumero": 1,
  "equipeNS": 3,
  "joueur1NSNom": "Dupont", "joueur1NSPrenom": "Alice",
  "joueur2NSNom": "Martin", "joueur2NSPrenom": "Bob",
  "equipeEO": 1,
  "joueur1EONom": "Durand", "joueur1EOPrenom": "Charlie",
  "joueur2EONom": "Bernard", "joueur2EOPrenom": "David",
  "donnes": [
    { "numero": 1, "donneur": "N", "vulnerable": "T", "mains": null }
  ],
  "indexDonneAJouer": 0
}
```

**POST /enregistreDonne**
```json
// Requête
{
  "contrat": "3SA", "declarant": "Nord", "signe": "+", "points": 630, "plis": 1,
  "carteEntame": "2C", "numeroDonne": 1, "indexDonneJouee": 0, "mvntNumero": 1,
  "equipeNS": 3, "equipeEO": 1, "numeroTable": 1,
  "historique": [
    { "joueur": "Ouest", "annonce": "Passe" },
    { "joueur": "Nord", "annonce": "1SA" }
  ],
  "mains": [["AP", "RK", "..."], [...], [...], [...]]
}
// Réponse
{ "etat": "OK", "indexDonneAJouer": 1 }
```

**GET /passerTableRelais?idTournoi=42&equipe=3**
```json
{
  "etat": "OK", "mvntSuivant": 2,
  "entries": [
    {
      "tableNumero": 2,
      "equipe": { "equipeNumero": 3, "joueur1": {...}, "joueur2": {...}, "idTournoi": 42 },
      "adversaire": { "equipeNumero": 2, "joueur1": {...}, "joueur2": {...}, "idTournoi": 42 }
    }
  ]
}
```

### 6.3 Classement

**GET /getResultatsTournoi?idTournoi=42**
```json
{
  "etat": "OK",
  "classement": [
    { "numeroEquipe": 1, "totalPts": 150.5, "rang": 1, "scorePct": 65.4 },
    { "numeroEquipe": 2, "totalPts": 130.0, "rang": 2, "scorePct": 55.2 }
  ],
  "token_public": "a3f8b2c1d4e5f6a7b8c9d0e1f2a3b4c5"
}
```

### 6.4 API Publique (token_public)

**GET /api_public.php?action=tournoi&tokenPublic=XXX&id=42**
```json
{
  "etat": "OK", "date": "2024-04-15", "type": "par4equ2t21d",
  "nbre_equipe": 4, "nbre_donne_total": 21,
  "equipes": [{ "equipeNumero": 1, "joueur1Nom": "A", "joueur1Prenom": "X", "joueur2Nom": "B", "joueur2Prenom": "Y" }]
}
```

**GET /api_public.php?action=donne&tokenPublic=XXX&id=42&numeroDonne=1&equipeNS=1**
```json
{
  "etat": "OK",
  "mains": { "N": ["AP", "RK", "..."], "E": [...], "S": [...], "O": [...] },
  "encheres": [{ "joueur": "Ouest", "annonce": "Passe" }, { "joueur": "Nord", "annonce": "1SA" }],
  "vulnerable": "T", "donneur": "N", "contrat": "3SA", "declarant": "Nord"
}
```

---

## 7. CYCLE MOUVEMENT/RELAIS — ALGORITHME COMPLET

### 7.1 Règle fondamentale (validée)

- `getMouvementPourEquipe` → **JAMAIS de modification BD**
- `enregistrerDonne` → incrémente seulement `index_donne_jouee` de 1
- `passerTableRelais` (online) → `mvnt_numero+1`, `index_donne_jouee = nbre_donnes_par_table - 1`, **PAS de décrémentation nbre_enregistrement**
- `incrementerMouvementEquipe` (LOCAL uniquement) → `mvnt_numero+1`, `index_donne_jouee = -1`

### 7.2 getMouvementPourEquipe (Kotlin/PHP identiques)

```
1. Lire en BD : mvnt_numero, index_donne_jouee de l'équipe
2. indexDonneAJouer = index_donne_jouee + 1
3. Si indexDonneAJouer == nbreDonnesParTable (mouvement terminé) :
   a. Si mvnt_numero == nbreMouvements ET nbreEnregistrement == 0
      → retourner ClassementEnAttente
   b. Sinon → calculer le mouvement SUIVANT en mémoire (mvnt_numero+1, index=0)
      → retourner MouvementSuivant avec mvntNumero = mvnt_numero + 1
4. Si indexDonneAJouer < nbreDonnesParTable
   → retourner MouvementEnCours avec mvntNumero = mvnt_numero
```

### 7.3 enregistrerDonne

```sql
INSERT INTO resultats (...valeurs donne...);
UPDATE equipes SET index_donne_jouee = index_donne_jouee + 1
  WHERE id_tournoi=? AND equipe_numero=?;
-- Ne touche PAS à mvnt_numero
-- Décrémente nbre_enregistrement du tournoi
-- Si nbre_enregistrement == 0 → finaliserClassementTournoi()
```

### 7.4 passerTableRelais (online)

```sql
UPDATE equipes
SET mvnt_numero = mvnt_numero + 1,
    index_donne_jouee = nbre_donnes_par_table - 1
WHERE id_tournoi=? AND equipe_numero=?;
-- Ne décrémente PAS nbre_enregistrement (table relais ne joue pas)
```

### 7.5 incrementerMouvementEquipe (local uniquement, mode relais)

```sql
UPDATE equipes
SET mvnt_numero = mvnt_numero + 1,
    index_donne_jouee = -1
WHERE id_tournoi=? AND equipe_numero=?;
```

### 7.6 Séquence RECUPER_MOUVEMENT (ClientActivity)

```
result = getMouvementPourEquipe(idTournoi, equipe)

if result is MouvementEnCours ou MouvementSuivant:
  mvnt = result.mouvement
  estRelais = (joueurs contiennent "relais")

  if estRelais:
    mouvementRelais = mvnt
    passerTableRelais(idTournoi, equipe)    // BD : mvnt+1, index=nbre-1
    futurResult = getMouvementPourEquipe(idTournoi, equipe)
    changementDeMouvement = futurResult.mouvement
    etape = DIALOG_RELAIS

  else if result is MouvementSuivant:
    mvntTermineNumero = mvnt.mvntNumero - 1  // ← mvntNumero est déjà le futur
    equipesMvntTermine = (mvnt.equipeNS, mvnt.equipeEO)
    etape = CHANGEMENT_DE_MOUVEMENT

  else:
    indexDonneAJouer = mvnt.indexDonneAJouer
    etape = MOUVEMENT_EN_COURS

if result is ClassementEnAttente:
  etape = ATTENTE_CLASSEMENT
```

---

## 8. CALCUL DES SCORES — FORMULES DÉTAILLÉES

### 8.1 Matchpoints Simplifiés

Pour chaque donne jouée nFois fois (nFois ≥ 2) :

```
TOP = (nFois - 1) × 2
Pas = TOP / (nFois - 1) = 2.0

Pour chaque groupe de scores identiques :
  Places (0-based) : de première_place à dernière_place
  Moyenne des places = (première + dernière) / 2
  ptsNS = TOP - (moyennePlace × Pas)
  ptsEO = TOP - ptsNS
```

### 8.2 Formule Neuberg

Appliquée quand une donne a été jouée nFois < N fois (N = max global) :

```
scoreAjuste = ((N × (scoreReel + 1)) / nFois) - 1

Ntop = (N - 1) × 2          ← constante globale du tournoi
ptsEO = Ntop - ptsNS        ← complémentaire invariant
```

### 8.3 Score Pourcentage

```
scorePct = (totalPtsEquipe / (nbreDealsFaits × Ntop)) × 100

Exemple :
  totalPts = 150.0, nbreDeals = 21, Ntop = 10
  scorePct = (150.0 / 210) × 100 = 71.4%
```

### 8.4 Algorithme EBL — Tournoi 4 équipes 2 tables 21 donnes

Structure croisement (3 mouvements de 7 donnes) :
```
Mvnt 1 (donnes  1- 7) : T1 = A vs C  |  T2 = B vs D  →  NS récap = {A,D}, EO récap = {B,C}
Mvnt 2 (donnes  8-14) : T1 = B vs D  |  T2 = A vs C  →  NS récap = {A,B}, EO récap = {C,D}
Mvnt 3 (donnes 15-21) : T1 = A vs B  |  T2 = C vs D  →  NS récap = {A,C}, EO récap = {B,D}
```

Calcul par donne (exemple Mvnt 1) :
```
recapNS = pointsNS(T1, A vs C) + pointsEO(T2, B vs D)
recapEO = pointsEO(T1, A vs C) + pointsNS(T2, B vs D)
diff = recapNS - recapEO

Si diff > 0 → équipes NS récap (A, D) gagnent EBL(diff)
Si diff < 0 → équipes EO récap (B, C) gagnent EBL(|diff|)

Classement final = total EBL par équipe, ordre décroissant
```

---

## 9. TYPES DE TOURNOI ET CALCUL MOUVEMENTS

| Type | nbre_mouvements | Particularité |
|------|----------------|---------------|
| Mitchell tables impaires | = nbre_tables | Pas de skip — calcul dynamique |
| Mitchell tables paires | = nbre_tables - 1 | Skip Mitchell obligatoire au-delà de nbre_tables/2 |
| Mitchell Guéridon | 2 réels + offset | 3 tables : T1, TG (guéridon), T2 |
| Howell | = nbre_equipes - 1 | Table SQL dédiée nommée d'après le type |
| par4equ2t21d | 3 (7+7+7 donnes) | 4 équipes, 2 tables, barème EBL croisé |

**Skip Mitchell** : au mouvement > nbre_tables/2, les équipes EO montent de 2 tables au lieu de 1.

---

## 10. LIEN PUBLIC PAR TOURNOI (token_public)

Chaque tournoi finalisé reçoit automatiquement un `token_public` = `bin2hex(random_bytes(16))` (32 hex chars).

**Lien public** : `resultats_tournoi.html?id=X&tokenPublic=<token>`
- Avec le lien → accès à ce tournoi uniquement, sans compte
- Sans le lien → impossible d'accéder (token aléatoire non devinable)

**Deux chemins de génération** :
- Tournoi local : `importerTournoi()` → token_public généré à l'import
- Tournoi online : `getResultatsTournoi` → token_public généré à la finalisation

**Fichiers impliqués** :
- `DatabaseManager.php` : `importerTournoi()`, `genererOuGetTokenPublic()`
- `serverBridge.php` : `getResultatsTournoi` retourne `token_public`
- `api_public.php` : endpoint sans auth, actions `tournoi` et `donne`
- `api_club.php` : action `tournois` retourne liste avec `token_public`
- `resultats_tournoi.html` : lit `tokenPublic` en URL, route vers `api_public.php`
- `mon_compte.html` : affiche le lien copiable pour chaque tournoi terminé

---

## 11. TABLEAU DE DÉPENDANCES

### Kotlin

| Fichier | Dépend de | Utilisé par |
|---------|-----------|-------------|
| `ClientActivity` | `ClientNetworkUtils`, `DatabaseManager`, `MouvementScreen`, `ClassementManager` | `MainActivity` |
| `ClientNetworkUtils` | `Constants`, `SecurePrefsUtils` | `ClientActivity` |
| `DatabaseManager.kt` | `DatabaseHelper`, `TournoiConfig` | `OrganisateurActivity`, `ClientActivity`, `ClientServeurHTTP` |
| `MouvementScreen` | `ContratInfo`, `DropdownMenus`, `calculerPoints()` | `ClientActivity` |
| `ClassementManager` | — | `ClientActivity`, `OrganisateurActivity` |
| `ServerManager` | `NetworkUtils`, `ClientServeurHTTP` | `OrganisateurActivity` |
| `ClientServeurHTTP` | `DatabaseManager.kt`, NanoHTTPD | `ServerManager` |

### PHP

| Fichier | Dépend de | Utilisé par |
|---------|-----------|-------------|
| `serverBridge.php` | `DatabaseManager.php`, `CalculClassementManager.php`, `Mouvement.php` | `ClientNetworkUtils` (requêtes) |
| `DatabaseManager.php` | `config.php`, `DonneDetail.php` | `serverBridge.php`, `api_public.php`, `api_club.php` |
| `CalculClassementManager.php` | `DatabaseManager.php` | `serverBridge.php` |
| `connexionAssociation.php` | `DatabaseManager.php` | `ClientNetworkUtils.connexionAssociation()` |
| `api_public.php` | `DatabaseManager.php` | Accès public via token_public |

---

## 12. ÉTAT DES MODIFICATIONS EN COURS (au 28/04/2026)

### Neuberg + score% — Modifications appliquées, APK non testé

| Fichier | Changement |
|---------|------------|
| `ClassementItem.kt` | Ajout `scorePct: Double = 0.0` |
| `ClassementManager.kt` | Formule Neuberg + Ntop + dealsParEquipe + scorePct |
| `DatabaseManager.kt` (`getClassementStocke`) | Calcul scorePct depuis BD |
| `ClassementScreen.kt` | Affichage "62,5 %" sous les pts |
| `ClientActivity.kt` | `scorePct = c.scorePct` |
| `ClientServeurHTTP.kt` | `put("scorePct", c.scorePct)` dans JSON |
| `ClientNetworkUtils.kt` | `scorePct = o.optDouble("scorePct", 0.0)` |
| `CalculClassementManager.php` | `scorePct` dans chaque item classement |

**Action requise** : Rebuilder l'APK et tester en mode Mitchell local.

### Bugs cycle mouvement/relais — Correctifs validés, à appliquer

| Bug | Fichier | Correction |
|-----|---------|------------|
| **Bug A** | `DatabaseManager.php` lignes 729-732 | Supprimer le bloc UPDATE BD dans `getMouvementPourEquipe` |
| **Bug B** | `serverBridge.php` route `passerTableRelais` | Créer `DatabaseManager::passerTableRelais()` → mvnt+1, index=nbre-1, SANS décrémenter nbre_enregistrement |
| **Bug C** | `DatabaseManager.php` + `DatabaseManager.kt` | `incrementerMouvementEquipe` → `index = -1` (pas nbre-1) |
| **Bug D** | `ClientActivity.kt` ligne 484 | `mvntTermineNumero.value = mvnt.mvntNumero - 1` |

### Réécriture RECUPER_MOUVEMENT

Code prêt (dans le fichier mémoire `rewrite_recuper_mouvement.md`).
Supprime l'appel redondant à `getMainsRelais` (les mains sont déjà dans `mvnt.donnes`).
À appliquer sur `ClientActivity.kt` lignes ~442-535.
