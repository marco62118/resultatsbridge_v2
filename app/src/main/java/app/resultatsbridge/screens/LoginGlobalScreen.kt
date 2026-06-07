package app.resultatsbridge.screens

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.resultatsbridge.client.ClientNetworkUtils
import app.resultatsbridge.common.URL_SERVEUR_ONLINE
import app.resultatsbridge.common.utils.getSecurePrefs
import kotlinx.coroutines.launch

@Composable
fun LoginGlobalScreen(onConnecte: () -> Unit) {
    val context = LocalContext.current
    val securePrefs = remember { getSecurePrefs(context) }
    rememberCoroutineScope()

    // Bascule entre l'écran de connexion et l'écran d'inscription
    var ecran by remember { mutableStateOf("connexion") }
    var messageSucces by remember { mutableStateOf<String?>(null) }

    if (ecran == "connexion") {
        EcranConnexion(
            securePrefs   = securePrefs,
            messageSucces = messageSucces,
            onConnecte    = onConnecte,
            onInscription = { ecran = "inscription"; messageSucces = null }
        )
    } else {
        EcranInscription(
            onInscrit = { message ->
                messageSucces = message
                ecran = "connexion"
            },
            onAnnuler = { ecran = "connexion" }
        )
    }
}

@Composable
private fun EcranConnexion(
    securePrefs: SharedPreferences,
    messageSucces: String?,
    onConnecte: () -> Unit,
    onInscription: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var emailInput    by remember { mutableStateOf(securePrefs.getString("lastEmail", "") ?: "") }
    var mdpInput      by remember { mutableStateOf("") }
    var mdpVisible    by remember { mutableStateOf(false) }
    var messageErreur by remember { mutableStateOf<String?>(null) }
    var chargement    by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🃏 Résultats Bridge",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Connectez-vous pour utiliser l'application",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        // Message de succès après inscription
        messageSucces?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(it, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it; messageErreur = null },
            label = { Text("Votre email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = mdpInput,
            onValueChange = { mdpInput = it; messageErreur = null },
            label = { Text("Mot de passe") },
            visualTransformation = if (mdpVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { mdpVisible = !mdpVisible }) {
                    Icon(
                        imageVector = if (mdpVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (mdpVisible) "Masquer" else "Afficher"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        messageErreur?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(msg, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    if (emailInput.isBlank() || mdpInput.isBlank()) {
                        messageErreur = "Email et mot de passe obligatoires"
                        return@launch
                    }
                    chargement = true
                    messageErreur = null
                    ClientNetworkUtils.initialiserServeur(URL_SERVEUR_ONLINE)
                    val auth = ClientNetworkUtils.connexionJoueur(emailInput, mdpInput)
                    if (auth == null) {
                        messageErreur = "❌ Email ou mot de passe incorrect"
                        chargement = false
                        return@launch
                    }
                    securePrefs.edit()
                        .putString("lastEmail", emailInput)
                        .putString("tokenJoueur", auth.tokenJoueur)
                        .apply()
                    Log.i("LoginGlobalScreen", "✅ Connexion OK → ${auth.prenom} ${auth.nom}")
                    chargement = false
                    onConnecte()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !chargement
        ) {
            Text(if (chargement) "Connexion en cours..." else "Se connecter")
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onInscription,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Créer un compte")
        }
    }
}

@Composable
private fun EcranInscription(
    onInscrit: (String) -> Unit,
    onAnnuler: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var nom           by remember { mutableStateOf("") }
    var prenom        by remember { mutableStateOf("") }
    var emailInput    by remember { mutableStateOf("") }
    var mdpInput      by remember { mutableStateOf("") }
    var mdpVisible    by remember { mutableStateOf(false) }
    var codeInput     by remember { mutableStateOf("") }
    var messageErreur by remember { mutableStateOf<String?>(null) }
    var chargement    by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Créer un compte",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Entrez le code fourni par votre organisateur",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = prenom,
            onValueChange = { prenom = it; messageErreur = null },
            label = { Text("Prénom") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = nom,
            onValueChange = { nom = it; messageErreur = null },
            label = { Text("Nom") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it; messageErreur = null },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = mdpInput,
            onValueChange = { mdpInput = it; messageErreur = null },
            label = { Text("Mot de passe (6 caractères minimum)") },
            visualTransformation = if (mdpVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { mdpVisible = !mdpVisible }) {
                    Icon(
                        imageVector = if (mdpVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (mdpVisible) "Masquer" else "Afficher"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it.uppercase(); messageErreur = null },
            label = { Text("Code du club") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        messageErreur?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(msg, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    if (prenom.isBlank() || nom.isBlank() || emailInput.isBlank() ||
                        mdpInput.isBlank() || codeInput.isBlank()) {
                        messageErreur = "Tous les champs sont obligatoires"
                        return@launch
                    }
                    if (mdpInput.length < 6) {
                        messageErreur = "Le mot de passe doit contenir au moins 6 caractères"
                        return@launch
                    }
                    chargement = true
                    messageErreur = null
                    ClientNetworkUtils.initialiserServeur(URL_SERVEUR_ONLINE)
                    val etat = ClientNetworkUtils.inscrireJoueur(nom, prenom, emailInput, mdpInput, codeInput)
                    chargement = false
                    when (etat) {
                        "OK"            -> onInscrit("✅ Compte créé ! Vous pouvez maintenant vous connecter.")
                        "CODE_INVALIDE" -> messageErreur = "❌ Code club invalide. Vérifiez avec votre organisateur."
                        "EMAIL_EXISTE"  -> messageErreur = "❌ Cet email est déjà utilisé."
                        else            -> messageErreur = "❌ Erreur, veuillez réessayer."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !chargement
        ) {
            Text(if (chargement) "Inscription en cours..." else "Créer mon compte")
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAnnuler, modifier = Modifier.fillMaxWidth()) {
            Text("← Retour à la connexion")
        }
    }
}
