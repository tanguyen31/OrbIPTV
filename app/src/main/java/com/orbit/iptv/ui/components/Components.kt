package com.orbit.iptv.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.WatchProgress
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import java.util.Locale

/**
 * Surface cliquable qui grossit et prend un contour blanc quand elle a le focus
 * (télécommande), comme sur Netflix ou l'Apple TV.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.07f,
    showBorder: Boolean = true,
    color: Color = Orbit.Surface,
    focusedColor: Color = color,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocus?.invoke()
                }
            }
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .then(if (focused && showBorder) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) { content(focused) }
}

/** Bouton pilule : blanc quand il a le focus, comme sur l'Apple TV. */
@Composable
fun OrbitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btnScale")
    val bg = when {
        focused -> Color.White
        primary -> Orbit.Accent
        else -> Color.White.copy(alpha = 0.14f)
    }
    val fg = when {
        focused -> Color.Black
        primary -> Orbit.OnAccent
        else -> Color.White
    }
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = if (compact) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Bouton rond avec une icône (actions secondaires). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    focusRequester: FocusRequester? = null,
    tint: Color = Color.White,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "iconScale")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (focused) Color.Black else tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Pastille de filtre (catégorie, saison, tri). */
@Composable
fun OrbitChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Orbit.Text.copy(alpha = 0.92f)
        else -> Orbit.SurfaceHigh
    }
    val fg = if (focused || selected) Color.Black else Orbit.Text
    Box(
        modifier
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocus?.invoke()
                }
            }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 4.dp, color: Color = Orbit.Accent) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Orbit.SurfaceHigh, Orbit.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = Orbit.Muted,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Orbit.Accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(formatRating(rating), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

fun formatRating(r: Double): String = String.format(Locale.FRANCE, "%.1f", r)

/** Affiche de film ou de série, format 2:3. */
@Composable
fun PosterCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null,
    showTitle: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            onLongClick = onLongClick,
            shape = RoundedCornerShape(10.dp),
            color = Orbit.SurfaceHigh,
            focusRequester = focusRequester,
        ) {
            Placeholder(media.title)
            if (!media.poster.isNullOrBlank()) {
                AsyncImage(
                    model = media.poster,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            media.rating?.let {
                RatingBadge(
                    it,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
            if (progress != null && progress > 0f) {
                ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
            }
        }
        if (showTitle) {
            Text(
                media.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = Orbit.Muted,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** Vignette de chaîne en direct, format 16:9 avec le logo centré. */
@Composable
fun ChannelCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            onLongClick = onLongClick,
            shape = RoundedCornerShape(12.dp),
            color = Orbit.SurfaceHigh,
            focusRequester = focusRequester,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2A2A36), Orbit.Surface))),
            )
            if (!media.poster.isNullOrBlank()) {
                AsyncImage(
                    model = media.poster,
                    contentDescription = media.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                )
            } else {
                Text(
                    media.title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                )
            }
            LiveDot(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
        Text(
            media.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Orbit.Muted,
            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Orbit.Live),
    )
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Orbit.Live)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(Modifier.width(5.dp))
        Text("EN DIRECT", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Carte « Reprendre la lecture » en 16:9 avec barre de progression. */
@Composable
fun ContinueCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val p = progress.playable
    FocusSurface(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        onLongClick = onLongClick,
        color = Orbit.SurfaceHigh,
    ) {
        Placeholder(p.title)
        if (!p.poster.isNullOrBlank()) {
            AsyncImage(
                model = p.poster,
                contentDescription = p.title,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!p.subtitle.isNullOrBlank()) {
                Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = Orbit.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ProgressLine(progress.fraction, Modifier.align(Alignment.BottomCenter))
    }
}

/** Rangée horizontale titrée, comme sur l'accueil Netflix. */
@Composable
fun <T> SectionRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val tv = LocalTv.current
    val edge = Dims.edge(tv)
    Column(modifier.padding(top = if (tv) 22.dp else 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Orbit.Text,
            modifier = Modifier.padding(horizontal = edge),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 12.dp),
        ) {
            items(items, key = key) { itemContent(it) }
        }
    }
}

@Composable
fun LoadingView(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Orbit.Accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(18.dp))
        Text(message, color = Orbit.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Orbit.Muted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = Orbit.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            OrbitButton(actionLabel, onAction, primary = true)
        }
    }
}

@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier) {
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("   •   "),
        color = Orbit.Muted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
