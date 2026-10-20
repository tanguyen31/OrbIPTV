package com.orbit.iptv.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Point d'accès unique aux données de l'application. */
object Repo {
    private lateinit var store: Store
    private val scope = MainScope()
    private var raw = Catalog()
    private var loadJob: Job? = null

    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val favorites = MutableStateFlow<Set<String>>(emptySet())
    val progress = MutableStateFlow<List<WatchProgress>>(emptyList())
    val recentLive = MutableStateFlow<List<String>>(emptyList())
    val settings = MutableStateFlow(Settings())
    val sources = MutableStateFlow<List<Source>>(emptyList())
    val activeSource = MutableStateFlow<Source?>(null)

    private val movieInfos = ConcurrentHashMap<String, MovieInfo>()
    private val seriesInfos = ConcurrentHashMap<String, SeriesInfo>()
    private val epgCache = ConcurrentHashMap<String, Pair<Long, List<Program>>>()
    private val epgPermits = Semaphore(4)

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = Store(context.applicationContext)
        store.migrateV1()
        sources.value = store.sources()
        activeSource.value = sources.value.firstOrNull { it.id == store.activeId } ?: sources.value.firstOrNull()
        favorites.value = store.favorites()
        progress.value = store.progress()
        recentLive.value = store.recentLive()
        settings.value = store.settings()
    }

    // ---- Catalogue ----------------------------------------------------------------

    fun loadCatalog(force: Boolean) {
        val src = activeSource.value ?: return
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            loading.value = true
            error.value = null
            try {
                if (!force && raw.isEmpty) {
                    val cached = withContext(Dispatchers.IO) { store.readCatalog(src.id) }
                    if (!cached.isEmpty) setCatalog(cached)
                }
                if (force || raw.isEmpty) {
                    val fresh = withContext(Dispatchers.IO) { fetch(src) }
                    if (activeSource.value?.id != src.id) return@launch
                    if (!fresh.isEmpty) {
                        setCatalog(fresh)
                        withContext(Dispatchers.IO) { runCatching { store.writeCatalog(src.id, fresh) } }
                    } else if (raw.isEmpty) {
                        error.value = "Aucun contenu trouvé dans cette source."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.friendlyMessage()
            } finally {
                loading.value = false
            }
        }
    }

    private fun setCatalog(c: Catalog) {
        raw = c
        publish()
    }

    private fun publish() {
        _catalog.value = if (settings.value.hideAdult) raw.withoutAdult() else raw
    }

    /** Télécharge le catalogue (hors du thread principal). */
    private fun fetch(src: Source): Catalog = when (src.kind) {
        SourceKind.M3U -> {
            val c = Http.open(src.url)
            try {
                M3uParser.parse(c.inputStream.bufferedReader(Charsets.UTF_8))
            } finally {
                c.disconnect()
            }
        }
        SourceKind.XTREAM -> {
            val client = XtreamClient(src.url, src.user, src.pass)
            val account = client.authenticate()
            updateSource(src.copy(expiry = account.expiry, maxConnections = account.maxConnections))
            client.catalog()
        }
    }

    // ---- Sources --------------------------------------------------------------------

    /** Vérifie la source, charge son catalogue et l'active. Lance une exception en cas d'échec. */
    suspend fun addSource(kind: SourceKind, name: String, url: String, user: String, pass: String) {
        val base = Source(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { if (kind == SourceKind.M3U) "Ma playlist" else "Mon abonnement" },
            kind = kind,
            url = url.trim(),
            user = user.trim(),
            pass = pass.trim(),
        )
        val (source, fresh) = withContext(Dispatchers.IO) {
            try {
                when (kind) {
                    SourceKind.M3U -> {
                        val c = Http.open(base.url)
                        val cat = try {
                            M3uParser.parse(c.inputStream.bufferedReader(Charsets.UTF_8))
                        } finally {
                            c.disconnect()
                        }
                        base to cat
                    }
                    SourceKind.XTREAM -> {
                        val client = XtreamClient(base.url, base.user, base.pass)
                        val account = client.authenticate()
                        base.copy(expiry = account.expiry, maxConnections = account.maxConnections) to client.catalog()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IOException(e.friendlyMessage(), e)
            }
        }
        if (fresh.isEmpty) throw IOException("Aucune chaîne ni vidéo trouvée dans cette source.")
        withContext(Dispatchers.IO) { runCatching { store.writeCatalog(source.id, fresh) } }
        val list = sources.value + source
        store.saveSources(list)
        sources.value = list
        clearCaches()
        store.activeId = source.id
        activeSource.value = source
        error.value = null
        setCatalog(fresh)
    }

    fun switchSource(id: String) {
        val src = sources.value.firstOrNull { it.id == id } ?: return
        if (src.id == activeSource.value?.id) return
        loadJob?.cancel()
        store.activeId = src.id
        activeSource.value = src
        clearCaches()
        raw = Catalog()
        publish()
        loadCatalog(force = false)
    }

    fun deleteSource(id: String) {
        val list = sources.value.filterNot { it.id == id }
        store.saveSources(list)
        store.deleteCatalog(id)
        sources.value = list
        if (activeSource.value?.id == id) {
            loadJob?.cancel()
            raw = Catalog()
            publish()
            activeSource.value = null
            store.activeId = null
            list.firstOrNull()?.let { switchSource(it.id) }
        }
    }

    private fun updateSource(src: Source) {
        val list = sources.value.map { if (it.id == src.id) src else it }
        store.saveSources(list)
        sources.value = list
        if (activeSource.value?.id == src.id) activeSource.value = src
    }

    private fun clearCaches() {
        movieInfos.clear()
        seriesInfos.clear()
        epgCache.clear()
    }

    private fun client(): XtreamClient? {
        val s = activeSource.value ?: return null
        return if (s.kind == SourceKind.XTREAM) XtreamClient(s.url, s.user, s.pass) else null
    }

    // ---- Favoris, historique, récents ----------------------------------------------------

    fun isFavorite(id: String) = id in favorites.value

    /** Retourne true si l'élément est maintenant dans la liste. */
    fun toggleFavorite(id: String): Boolean {
        val set = favorites.value.toMutableSet()
        val added = set.add(id) || run { set.remove(id); false }
        favorites.value = set
        store.saveFavorites(set)
        return added
    }

    fun saveProgress(p: Playable, positionMs: Long, durationMs: Long) {
        if (p.type == MediaType.LIVE || durationMs <= 0) return
        val others = progress.value.filterNot { it.playable.id == p.id }
        val entry = WatchProgress(p, positionMs, durationMs, System.currentTimeMillis())
        val list = if (positionMs.toFloat() / durationMs > 0.95f) {
            // Terminé : on garde une trace très courte pour la série (épisode suivant).
            if (p.type == MediaType.EPISODE) listOf(entry) + others else others
        } else {
            listOf(entry) + others
        }
        val trimmed = list.sortedByDescending { it.updatedAt }.take(80)
        progress.value = trimmed
        store.saveProgress(trimmed)
    }

    fun progressFor(id: String): WatchProgress? = progress.value.firstOrNull { it.playable.id == id }

    fun clearHistory() {
        progress.value = emptyList()
        store.saveProgress(emptyList())
        recentLive.value = emptyList()
        store.saveRecentLive(emptyList())
    }

    fun addRecentLive(id: String) {
        val list = (listOf(id) + recentLive.value.filterNot { it == id }).take(20)
        recentLive.value = list
        store.saveRecentLive(list)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = settings.value
        val new = transform(old)
        settings.value = new
        store.saveSettings(new)
        if (old.hideAdult != new.hideAdult) publish()
    }

    // ---- Fiches et guide TV ------------------------------------------------------------------

    suspend fun movieInfo(m: Media): MovieInfo? {
        movieInfos[m.id]?.let { return it }
        val sid = m.streamId ?: return null
        val c = client() ?: return null
        return withContext(Dispatchers.IO) { runCatching { c.vodInfo(sid) }.getOrNull() }
            ?.also { movieInfos[m.id] = it }
    }

    suspend fun seriesInfo(m: Media): SeriesInfo? {
        seriesInfos[m.id]?.let { return it }
        val sid = m.streamId ?: return null
        val c = client() ?: return null
        return withContext(Dispatchers.IO) { runCatching { c.seriesInfo(sid) }.getOrNull() }
            ?.also { seriesInfos[m.id] = it }
    }

    fun cachedEpg(id: String): List<Program>? = epgCache[id]?.second

    suspend fun epg(streamId: String?, id: String): List<Program> {
        val cached = epgCache[id]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.first < 10 * 60_000 && cached.second.firstOrNull()?.let { it.end > now } != false) {
            return cached.second
        }
        val sid = streamId ?: return emptyList()
        val c = client() ?: return emptyList()
        val list = epgPermits.withPermit {
            withContext(Dispatchers.IO) { runCatching { c.shortEpg(sid) }.getOrDefault(emptyList()) }
        }
        epgCache[id] = now to list
        return list
    }

    suspend fun epg(m: Media): List<Program> =
        if (m.type == MediaType.LIVE) epg(m.streamId, m.id) else emptyList()
}

fun Throwable.friendlyMessage(): String = when (this) {
    is UnknownHostException -> "Serveur introuvable. Vérifie l'adresse et ta connexion."
    is SocketTimeoutException -> "Le serveur ne répond pas. Réessaie dans un instant."
    is ConnectException -> "Connexion refusée par le serveur."
    is javax.net.ssl.SSLException -> "Erreur de connexion sécurisée avec le serveur."
    else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
