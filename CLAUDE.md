# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Instructions générales

- Toujours répondre en français.
- Avant toute correction de bug, analyser tous les fichiers impactés et expliquer les conséquences sur l'ensemble du projet.
- Ne jamais faire un correctif partiel sans vérifier la cohérence globale.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew clean                  # Clean build artifacts
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires connected device/emulator)
./gradlew test --tests "com.example.MyClass.myTest"  # Run a single test
```

## Architecture

**TournoiBridgeOnline** is a native Android app (Kotlin + Jetpack Compose) for managing Bridge card game tournaments. It supports two user roles: **Organisateur** (tournament director) and **Joueur** (player).

### Dual-mode networking model

- **Local mode**: The organizer's device runs an embedded HTTP server (NanoHTTPD on port 8080) that serves tournament data to players on the same LAN. Players connect via the organizer's local IP.
- **Online mode**: Players connect to the remote server at `https://resultats-bridge.alwaysdata.net/asso` (defined in `common/ClientConfig`).

### Package structure under `app/src/main/java/.../tournoibridgeonline/`

| Package | Role |
|---|---|
| `main/` | `MainActivity` (mode selection entry point), `BaseActivity` (forces fixed font scale/density) |
| `client/` | Player-side UI (`ClientActivity`), network layer (`ClientNetworkUtils`), UI state enum (`EtapeClient`) |
| `organisateur/` | Organizer UI (`OrganisateurActivity`), local HTTP server (`ServerManager`, `ClientServeurHTTP`), SQLite access (`DatabaseHelper`, `DatabaseManager`) |
| `screens/` | Shared Compose screens: bidding (`Encheres`), deal display (`AffichageDonne`), rankings (`Classement`), deal verification |
| `common/model/` | Domain entities: `Equipe`, `Joueur`, `Mouvement`, `Donne`, `Enchere`, etc. |
| `common/ui/components/` | Reusable Compose components (card view, Bridge-specific dropdowns) |

### Data persistence

The organizer module copies a pre-seeded `tournoi.db` SQLite file from `assets/` at first launch. `DatabaseHelper` manages schema/copy; `DatabaseManager` provides all CRUD operations. Players do not persist data locally.

### Key technology choices

- Jetpack Compose (UI) + Material3 theming
- NanoHTTPD 2.3.1 for the embedded HTTP server
- GSON 2.10.1 for JSON serialization over HTTP
- `androidx.security.crypto` for encrypted SharedPreferences
- Version catalog at `gradle/libs.versions.toml`; Compose BOM 2024.09.00

### Language note

All domain code, UI labels, and comments are in **French** (Bridge tournament terminology). Variable/function names follow French vocabulary (e.g., `equipe` = team, `joueur` = player, `donne` = deal, `enchere` = bid, `classement` = ranking).

---

## Rapport d'audit architectural — TournoiBridgeOnline

### Problèmes CRITIQUES

#### 1. Injection SQL — `DatabaseManager.php`
`FROM $typeDeTournoi m` → interpolation directe du type de tournoi dans la requête SQL.
Si un attaquant contrôle la valeur `type`, il peut exécuter du SQL arbitraire.
**Correctif :** liste blanche des types autorisés (`mitchell`, `howell`, `par4equ2t21d`) avant interpolation.

#### 2. Absence d'isolation entre associations (multi-tenant)
La majorité des requêtes filtrent par `id_tournoi` sans vérifier que ce tournoi appartient à l'association authentifiée. Un club A peut lire/modifier les données du club B.
**Correctif :** ajouter `AND id_association = ?` à tous les WHERE sur les tables `tournois`, `equipes`, `resultats`, `donnes`.

#### 3. SSL Trust-All — `ClientNetworkUtils.kt:93-102`
Un `X509TrustManager` vide accepte tous les certificats → attaque MITM triviale sur LAN.
Couplé au token API transmis en clair dans les paramètres GET.
**Correctif :** Certificate Pinning.

#### 4. Credentials SMTP en dur dans les fichiers PHP
Mot de passe `JmmMa0562+` présent en clair dans 3+ fichiers PHP.
**Correctif :** variables d'environnement ou fichier de config hors dépôt.

---

### Problèmes HAUTS

#### 5. État global mutable non thread-safe — `TournoiConfig.kt`
`object TournoiConfig` avec `var NBRE_MOUVEMENTS`, `NBRE_TABLES`, `EQUIPE_RELAIS`, `NBRE_DONNES_PAR_TABLE` modifiés depuis plusieurs threads sans synchronisation → race conditions.

#### 6. Mega-classe `ClientActivity.kt` (839 lignes, 0 ViewModel)
Gère : 17 états navigation (`EtapeClient`), appels réseau, accès SQLite, 25+ variables d'état, toute l'UI Compose. Pas de ViewModel, pas de Repository.

#### 7. Gestion d'erreur réseau incomplète — `ClientActivity.kt:149-164`
Appels réseau dans `LaunchedEffect` sans try/catch → crash silencieux si exception. Pas de mécanisme de retry.

#### 8. Mot de passe stocké dans SharedPreferences — `ConnexionScreen.kt:86-87`
`lastMdp` sauvegardé dans EncryptedSharedPreferences. Stocker le token de session suffit.

#### 9. Double finalisation concurrente — `enregistreDonne` PHP
Quand `nbre_enregistrement` atteint 0, `finaliserClassementTournoi()` est appelé. Si 2 équipes enregistrent simultanément la dernière donne → double exécution possible.
**Correctif :** verrou `SELECT FOR UPDATE` sur `tournois` dans la transaction.

---

### Problèmes MOYENS

#### 10. Navigation par enum fragile — `EtapeClient.kt` + `ClientActivity.kt`
17 états, `etapeRetourDetails` en mémoire locale perdue si Activity recrée. Pas de Jetpack Navigation.

#### 11. Curseurs SQLite sans try-finally — `DatabaseManager.kt:38-48`
`cursor.close()` sans protection `finally` → fuite de curseur si exception.

#### 12. Pas de transactions SQLite — `ClientServeurHTTP.kt:261-278`
Enregistrement donne en plusieurs étapes sans transaction → BD incohérente si échec partiel.

#### 13. Duplication de code
- Création `EncryptedSharedPreferences` (~70 lignes) copiée entre `ConnexionScreen.kt` et `OrganisateurActivity.kt`.
- Logique reconstruction `Mouvement` dupliquée entre `ClientNetworkUtils.kt` et `ClientServeurHTTP.kt`.

#### 14. Format non-JSON `verifierTournoiOuvert` — `serverBridge.php`
Retourne du texte pipe-delimited `id|type|nbreMouvements|nbreDonnesParTable|equipeRelais`. Seul endpoint non-JSON → fragile.
**Correctif :** retourner JSON comme tous les autres endpoints.

#### 15. Erreurs internes exposées au client — `DatabaseManager.php`
`return MouvementResult::Erreur("Erreur serveur: " . $e->getMessage())` révèle noms de tables/colonnes/requêtes SQL.
**Correctif :** logger côté serveur, retourner message générique.

#### 16. Timeouts réseau incohérents — `ClientNetworkUtils.kt`
3 000 ms, 6 000 ms, 15 000 ms selon les fonctions sans logique apparente.

#### 17. Token API en clair dans l'URL — `ClientNetworkUtils.kt:143-145`
Token transmis en paramètre GET → visible dans logs réseau.

#### 18. Pas de rate limiting sur reset mot de passe
Code 6 chiffres, valide 15 min, sans blocage après X tentatives → force brute possible.

---

### Bugs cycle mouvement/relais — correctifs validés

#### Bug A — `DatabaseManager.php` lignes 729-732
`getMouvementPourEquipe` PHP fait un UPDATE BD alors que Kotlin ne le fait jamais.
**Correctif :** supprimer entièrement le bloc :
```php
if ($tousTermines) {
    $pdo->prepare("UPDATE equipes SET mvnt_numero=?, index_donne_jouee=? WHERE id_tournoi=? AND equipe_numero=?")
        ->execute([$mvntNumero, -1, $idTournoi, $equipeNumero]);
}
```

#### Bug B — `serverBridge.php` ligne 391
`passerTableRelais` appelle `incrementerMouvementEquipe` → double incrément de `mvnt_numero`.
**Correctif :** créer `DatabaseManager::passerTableRelais()` qui fait uniquement `mvnt+1, index=nbre-1` SANS toucher à `nbre_enregistrement`.

#### Bug C — `incrementerMouvementEquipe` PHP et Kotlin
`index = nbre_donnes_par_table - 1` → pour nbre=1, index=0, mouvement paraît déjà terminé.
**Correctif :** `index = -1` dans les deux fichiers.

#### Bug D — `ClientActivity.kt` ligne 484
`mvntTermineNumero.value = mvnt.mvntNumero` → faux, `mvntNumero` est déjà le futur calculé en mémoire.
**Correctif :** `mvntTermineNumero.value = mvnt.mvntNumero - 1`

---

### Neuberg + score% — état au 25/04/2026

Modifications **appliquées** dans les fichiers suivants, APK **non encore rebuilé ni testé** :

| Fichier | Changement |
|---|---|
| `ClassementItem.kt` | Ajout `scorePct: Double = 0.0` |
| `ClassementManager.kt` | Formule Neuberg + Ntop + `dealsParEquipe` + `scorePct` |
| `DatabaseManager.kt` `getClassementStocke` | Calcul `scorePct` depuis BD |
| `ClassementScreen.kt` | Affichage `"62,5 %"` sous les pts |
| `ClientActivity.kt` | `scorePct = c.scorePct` |
| `ClientServeurHTTP.kt` | `put("scorePct", c.scorePct)` dans JSON |
| `ClientNetworkUtils.kt` | `scorePct = o.optDouble("scorePct", 0.0)` |
| `CalculClassementManager.php` | `scorePct` dans chaque item classement |

Formule Neuberg : `scoreAjuste = ((N × (scoreReel + 1)) / n) − 1`
Score% : `(ptsObtenus / (nDonnesJouées × Ntop)) × 100`

---

### Tableau de synthèse

| Sévérité | Problème | Fichier |
|---|---|---|
| CRITIQUE | Injection SQL (`$typeDeTournoi`) | `DatabaseManager.php` |
| CRITIQUE | Pas d'isolation entre associations | Tous les endpoints PHP |
| CRITIQUE | Credentials SMTP en dur | Fichiers PHP |
| CRITIQUE | SSL Trust-All + token en GET | `ClientNetworkUtils.kt` |
| HAUTE | Double finalisation concurrente | `enregistreDonne` PHP |
| HAUTE | Mega-classe sans ViewModel | `ClientActivity.kt` |
| HAUTE | Mot de passe en SharedPreferences | `ConnexionScreen.kt` |
| MOYENNE | Pas de rate limiting reset mdp | `api_club.php`, `api_adherent.php` |
| MOYENNE | Erreurs internes exposées | `DatabaseManager.php` |
| MOYENNE | TournoiConfig non thread-safe | `TournoiConfig.kt` |
| MOYENNE | Curseurs SQLite sans try-finally | `DatabaseManager.kt` |
| MOYENNE | verifierTournoiOuvert format texte | `serverBridge.php` |
| BASSE | Zéro test unitaire | Android + PHP |
| BASSE | Logs excessifs en production | `ClientNetworkUtils.kt` |

---

## Fonctionnalité — Lien public par tournoi

### Objectif
Permettre à un joueur externe (sans compte) de consulter les résultats d'un tournoi via un lien partagé par l'organisateur. L'adhérent du club garde son accès habituel (email + mdp, liste complète de ses tournois).

### Deux chemins de publication
- **Tournoi local** (le plus courant) : organisateur joue en LAN, puis exporte via l'app Android → `/importerTournoi` → `token_public` généré à l'import.
- **Tournoi online** : `/getResultatsTournoi` → `token_public` généré à la finalisation du classement.

### Règles
- Chaque tournoi finalisé reçoit automatiquement un `token_public` (32 hex chars, `bin2hex(random_bytes(16))`).
- Le lien public : `resultats_tournoi.html?id=X&tokenPublic=<token>`
- Avec le lien : accès à ce tournoi uniquement, sans compte.
- Sans le lien : impossible d'accéder (token aléatoire, non devinable).
- L'adhérent du club garde son accès via `resultats.html` (email + mdp) — inchangé.

### Fichiers modifiés
| Fichier | Changement |
|---|---|
| BD | `ALTER TABLE tournois ADD COLUMN token_public VARCHAR(32) DEFAULT NULL` |
| `DatabaseManager.php` | `importerTournoi` : génère + stocke `token_public`. `getTournoisTermines` : retourne `token_public`. Nouvelles méthodes : `getDetailTournoiParTokenPublic`, `getDonneCompleteParTokenPublic`, `genererOuGetTokenPublic` |
| `serverBridge.php` | `getResultatsTournoi` : génère `token_public` + le retourne dans la réponse |
| `api_public.php` | Nouveau fichier — endpoint sans auth, actions `tournoi` + `donne` |
| `api_club.php` | Nouvelle action `tournois` — retourne liste avec `token_public` |
| `resultats_tournoi.html` | Lit `tokenPublic` en URL, route vers `api_public.php` si présent |
| `mon_compte.html` | Affiche le lien public copiable pour chaque tournoi terminé |
