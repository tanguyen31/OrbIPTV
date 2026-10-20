package com.orbit.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.iptv.data.Repo
import com.orbit.iptv.data.Source
import com.orbit.iptv.data.SourceKind
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.Route
import com.orbit.iptv.ui.components.FocusSurface
import com.orbit.iptv.ui.components.RoundIconButton
import com.orbit.iptv.ui.components.toast
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import java.text.DateFormat
import java.util.Date
import java.util.Locale

val ResizeLabels = listOf("Ajusté", "Remplir l'écran", "Zoom")

private enum class PinPurpose { SET, DISABLE_FILTER }

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val settings by Repo.settings.collectAsState()
    val sources by Repo.sources.collectAsState()
    val active by Repo.activeSource.collectAsState()
    val loading by Repo.loading.collectAsState()
    val catalog by Repo.catalog.collectAsState()
    var pinDialog by remember { mutableStateOf<PinPurpose?>(null) }
    var deleteTarget by remember { mutableStateOf<Source?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val edge = Dims.edge(tv)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = edge, end = edge, top = if (tv) 28.dp else 4.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                if (!tv) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onClick = nav.back, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text("Réglages", style = MaterialTheme.typography.headlineMedium)
            }
        }

        // ---- Source active -------------------------------------------------
        item(key = "h-source") { Header("Mon abonnement") }
        active?.let { src ->
            item(key = "active") {
                Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Orbit.Surface)
                        .padding(18.dp),
                ) {
                    Text(src.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (src.kind == SourceKind.XTREAM) "Xtream Codes · ${src.url}" else "Playlist M3U",
                        color = Orbit.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    src.expiry?.let {
                        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.FRANCE).format(Date(it))
                        val expired = it < System.currentTimeMillis()
                        Text(
                            if (expired) "Abonnement expiré le $date" else "Expire le $date",
                            color = if (expired) Orbit.Danger else Orbit.Text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    src.maxConnections?.let {
                        Text("$it connexion${if (it > 1) "s" else ""} simultanée${if (it > 1) "s" else ""}", color = Orbit.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "${catalog.live.size} chaînes · ${catalog.movies.size} films · ${catalog.series.size} séries",
                        color = Orbit.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "refresh") {
            SettingRow(
                Icons.Rounded.Refresh,
                "Actualiser le catalogue",
                if (loading) "Mise à jour en cours…" else "Récupère les nouvelles chaînes, films et séries",
                onClick = {
                    Repo.loadCatalog(force = true)
                    context.toast("Actualisation du catalogue…")
                },
                trailing = { if (loading) CircularProgressIndicator(color = Orbit.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) },
            )
        }

        // ---- Sources ----------------------------------------------------------------
        item(key = "h-sources") { Header("Sources") }
        items(sources, key = { "src:${it.id}" }) { s ->
            val isActive = s.id == active?.id
            SettingRow(
                Icons.Rounded.Dns,
                s.name,
                (if (s.kind == SourceKind.XTREAM) "Xtream Codes" else "Playlist M3U") + if (isActive) " · active" else " · appuie pour l'utiliser",
                onClick = {
                    if (!isActive) {
                        Repo.switchSource(s.id)
                        context.toast("Source « ${s.name} » activée")
                    }
                },
                onLongClick = { deleteTarget = s },
                trailing = {
                    if (isActive) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = Orbit.Accent)
                    } else {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = Orbit.Muted, modifier = Modifier.size(20.dp))
                    }
                },
            )
        }
        item(key = "add-source") {
            SettingRow(Icons.Rounded.Add, "Ajouter une source", "Xtream Codes ou lien M3U", onClick = { nav.open(Route.AddSource) })
        }
        item(key = "hint-sources") {
            Text(
                "Appui long sur une source pour la supprimer.",
                color = Orbit.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }

        // ---- Lecture ---------------------------------------------------------------------
        item(key = "h-playback") { Header("Lecture") }
        item(key = "autoplay") {
            SettingRow(
                Icons.Rounded.SkipNext,
                "Épisode suivant automatique",
                "Lance l'épisode suivant à la fin d'un épisode",
                onClick = { Repo.updateSettings { it.copy(autoplayNext = !it.autoplayNext) } },
                trailing = { OrbitSwitch(settings.autoplayNext) },
            )
        }
        item(key = "resize") {
            SettingRow(
                Icons.Rounded.AspectRatio,
                "Format d'image par défaut",
                ResizeLabels[settings.resizeMode.coerceIn(0, 2)],
                onClick = { Repo.updateSettings { it.copy(resizeMode = (it.resizeMode + 1) % 3) } },
            )
        }

        // ---- Contrôle parental --------------------------------------------------------------
        item(key = "h-parental") { Header("Contrôle parental") }
        item(key = "adult") {
            SettingRow(
                Icons.Rounded.Lock,
                "Masquer les contenus pour adultes",
                "Cache les catégories adultes partout dans l'application",
                onClick = {
                    if (settings.hideAdult) {
                        if (settings.pin != null) pinDialog = PinPurpose.DISABLE_FILTER
                        else Repo.updateSettings { it.copy(hideAdult = false) }
                    } else {
                        Repo.updateSettings { it.copy(hideAdult = true) }
                    }
                },
                trailing = { OrbitSwitch(settings.hideAdult) },
            )
        }
        item(key = "pin") {
            SettingRow(
                Icons.Rounded.Password,
                if (settings.pin == null) "Créer un code PIN" else "Changer le code PIN",
                if (settings.pin == null) "Empêche de désactiver le filtre sans code" else "Code PIN actif",
                onClick = { pinDialog = PinPurpose.SET },
            )
        }

        // ---- Données ----------------------------------------------------------------------------
        item(key = "h-data") { Header("Données") }
        item(key = "clear") {
            SettingRow(
                Icons.Rounded.DeleteSweep,
                "Effacer l'historique",
                "Reprise de lecture et chaînes récentes",
                onClick = { confirmClear = true },
            )
        }
        item(key = "about") {
            SettingRow(
                Icons.Rounded.Info,
                "Orbit IPTV 2.0",
                "Lecteur IPTV pour smartphone et Android TV. Aucun contenu n'est fourni par l'application.",
                onClick = {},
            )
        }
    }

    pinDialog?.let { purpose ->
        PinDialog(
            title = if (purpose == PinPurpose.SET) "Nouveau code PIN" else "Code PIN",
            message = if (purpose == PinPurpose.SET) "Choisis un code à 4 chiffres." else "Saisis ton code pour afficher les contenus adultes.",
            onDismiss = { pinDialog = null },
            onConfirm = { code ->
                when (purpose) {
                    PinPurpose.SET -> {
                        Repo.updateSettings { it.copy(pin = code) }
                        context.toast("Code PIN enregistré")
                        pinDialog = null
                    }
                    PinPurpose.DISABLE_FILTER -> {
                        if (code == settings.pin) {
                            Repo.updateSettings { it.copy(hideAdult = false) }
                            pinDialog = null
                        } else {
                            context.toast("Code incorrect")
                        }
                    }
                }
            },
        )
    }

    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Supprimer « ${s.name} » ?") },
            text = { Text("La source et son catalogue seront retirés de cet appareil.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteSource(s.id)
                    deleteTarget = null
                }) { Text("Supprimer", color = Orbit.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annuler") } },
            containerColor = Orbit.Surface,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Les reprises de lecture et les chaînes récentes seront effacées.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.clearHistory()
                    confirmClear = false
                    context.toast("Historique effacé")
                }) { Text("Effacer", color = Orbit.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
            containerColor = Orbit.Surface,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text.uppercase(),
        color = Orbit.Accent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun OrbitSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Orbit.OnAccent,
            checkedTrackColor = Orbit.Accent,
            uncheckedThumbColor = Orbit.Muted,
            uncheckedTrackColor = Orbit.SurfaceHigh,
            uncheckedBorderColor = Orbit.Stroke,
        ),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.02f,
        color = Orbit.Surface,
        focusedColor = Orbit.SurfaceHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Orbit.Text, modifier = Modifier.size(20.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = Orbit.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun PinDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, color = Orbit.Muted)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    placeholder = { Text("••••") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (code.length == 4) onConfirm(code) }, enabled = code.length == 4) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = Orbit.Surface,
    )
}
