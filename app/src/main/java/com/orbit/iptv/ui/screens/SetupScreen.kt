package com.orbit.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbit.iptv.data.Repo
import com.orbit.iptv.data.SourceKind
import com.orbit.iptv.ui.OrbitLogo
import com.orbit.iptv.ui.components.OrbitButton
import com.orbit.iptv.ui.components.OrbitChip
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Écran d'accueil (premier lancement) et d'ajout de source. */
@Composable
fun SetupScreen(firstRun: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var kind by rememberSaveable { mutableStateOf(SourceKind.XTREAM) }
    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var m3u by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        error = null
        val ok = if (kind == SourceKind.XTREAM) server.isNotBlank() && user.isNotBlank() && pass.isNotBlank() else m3u.isNotBlank()
        if (!ok) {
            error = "Remplis tous les champs."
            return
        }
        focus.clearFocus()
        busy = true
        scope.launch {
            try {
                if (kind == SourceKind.XTREAM) {
                    Repo.addSource(SourceKind.XTREAM, name, server, user, pass)
                } else {
                    val url = m3u.trim().let { if (it.contains("://")) it else "http://$it" }
                    Repo.addSource(SourceKind.M3U, name, url, "", "")
                }
                busy = false
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false
                error = e.message ?: "Connexion impossible."
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Orbit.Accent,
        unfocusedBorderColor = Orbit.Stroke,
        focusedContainerColor = Orbit.Surface,
        unfocusedContainerColor = Orbit.Surface,
        focusedLabelColor = Orbit.Accent,
        cursorColor = Orbit.Accent,
    )
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1408), Orbit.Bg, Orbit.Bg))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
            ) {
                OrbitLogo(showName = true)
                Spacer(Modifier.height(28.dp))
                Text(
                    if (firstRun) "Bienvenue sur Orbit" else "Ajouter une source",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Orbit est un lecteur : il ne fournit aucune chaîne. Saisis les accès donnés par ton fournisseur IPTV.",
                    color = Orbit.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrbitChip("Xtream Codes", kind == SourceKind.XTREAM, onClick = { kind = SourceKind.XTREAM })
                    OrbitChip("Lien M3U", kind == SourceKind.M3U, onClick = { kind = SourceKind.M3U })
                }
                Spacer(Modifier.height(18.dp))

                val fieldShape = RoundedCornerShape(14.dp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom (facultatif)") },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = next,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                if (kind == SourceKind.XTREAM) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Adresse du serveur") },
                        placeholder = { Text("http://exemple.com:8080") },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        keyboardActions = next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Nom d'utilisateur") },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = next,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Mot de passe") },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Afficher le mot de passe")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = m3u,
                        onValueChange = { m3u = it },
                        label = { Text("URL de la playlist M3U") },
                        placeholder = { Text("http://…/playlist.m3u") },
                        shape = fieldShape,
                        colors = fieldColors,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let {
                    Text(it, color = Orbit.Danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrbitButton(
                        text = if (busy) "Connexion…" else "Se connecter",
                        onClick = { submit() },
                        primary = true,
                        enabled = !busy,
                    )
                    if (onCancel != null && !busy) {
                        OrbitButton("Annuler", onClick = onCancel)
                    }
                    if (busy) {
                        CircularProgressIndicator(color = Orbit.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                if (busy) {
                    Text(
                        "Chargement du catalogue, cela peut prendre quelques secondes…",
                        color = Orbit.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
