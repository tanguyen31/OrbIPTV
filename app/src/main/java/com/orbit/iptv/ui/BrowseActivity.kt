package com.orbit.iptv.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orbit.iptv.R
import com.orbit.iptv.data.Channel
import com.orbit.iptv.data.Kind
import com.orbit.iptv.data.Repository
import com.orbit.iptv.databinding.ActivityBrowseBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class BrowseActivity : AppCompatActivity() {

    private enum class Tab { LIVE, MOVIES, FAV }

    private lateinit var b: ActivityBrowseBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private var tab = Tab.LIVE
    private var selectedGroup: String? = null
    private var query = ""
    private var currentItems: List<Channel> = emptyList()
    private var filterJob: Job? = null
    private var searchJob: Job? = null
    private var loading = false
    private var loadError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Repository.source() == null) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        b = ActivityBrowseBinding.inflate(layoutInflater)
        setContentView(b.root)

        val horizontalCategories = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        categoryAdapter = CategoryAdapter(horizontalCategories) { position, name ->
            selectedGroup = if (position == 0) null else name
            refreshLists(scrollTop = true)
        }
        b.categoryList.layoutManager = LinearLayoutManager(
            this,
            if (horizontalCategories) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL,
            false,
        )
        b.categoryList.adapter = categoryAdapter

        channelAdapter = ChannelAdapter(
            onClick = { position -> openPlayer(position) },
            onLongClick = { position -> toggleFavorite(position) },
        )
        b.channelList.layoutManager = LinearLayoutManager(this)
        b.channelList.adapter = channelAdapter
        b.channelList.setHasFixedSize(true)

        b.tabLive.setOnClickListener { selectTab(Tab.LIVE) }
        b.tabMovies.setOnClickListener { selectTab(Tab.MOVIES) }
        b.tabFav.setOnClickListener { selectTab(Tab.FAV) }
        b.btnRefresh.setOnClickListener { reload(force = true) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }

        b.searchInput.addTextChangedListener { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300)
                query = text?.toString().orEmpty()
                refreshLists(scrollTop = true)
            }
        }
        b.searchInput.setOnEditorActionListener { v, _, _ ->
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
            b.channelList.requestFocus()
            true
        }

        updateTabs()
        reload(force = false)
    }

    override fun onResume() {
        super.onResume()
        // Les favoris ont pu changer depuis le lecteur.
        if (!loading && Repository.channels.isNotEmpty()) refreshLists(scrollTop = false)
    }

    private fun selectTab(newTab: Tab) {
        if (tab == newTab) return
        tab = newTab
        selectedGroup = null
        updateTabs()
        refreshLists(scrollTop = true)
    }

    private fun updateTabs() {
        b.tabLive.isSelected = tab == Tab.LIVE
        b.tabMovies.isSelected = tab == Tab.MOVIES
        b.tabFav.isSelected = tab == Tab.FAV
    }

    private fun reload(force: Boolean) {
        val source = Repository.source() ?: return
        if (loading) return
        loading = true
        loadError = null
        b.progress.visibility = View.VISIBLE
        b.emptyText.visibility = View.GONE
        lifecycleScope.launch {
            try {
                if (!force && Repository.channels.isEmpty()) {
                    Repository.channels = withContext(Dispatchers.IO) { Repository.readCache() }
                }
                if (force || Repository.channels.isEmpty()) {
                    val list = withContext(Dispatchers.IO) { Repository.fetch(source) }
                    if (list.isNotEmpty()) {
                        Repository.channels = list
                        withContext(Dispatchers.IO) {
                            try { Repository.saveCache() } catch (e: IOException) { }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.message ?: e.javaClass.simpleName
                if (Repository.channels.isNotEmpty()) {
                    Toast.makeText(this@BrowseActivity, getString(R.string.load_error, loadError), Toast.LENGTH_LONG).show()
                }
            } finally {
                loading = false
                b.progress.visibility = View.GONE
            }
            refreshLists(scrollTop = true)
        }
    }

    private fun refreshLists(scrollTop: Boolean) {
        filterJob?.cancel()
        val all = Repository.channels
        val currentTab = tab
        val wantedGroup = selectedGroup
        val q = query.trim().lowercase()
        val favs = Repository.favoriteIds()

        filterJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val base = when (currentTab) {
                    Tab.LIVE -> all.filter { it.kind == Kind.LIVE }
                    Tab.MOVIES -> all.filter { it.kind == Kind.MOVIE }
                    Tab.FAV -> all.filter { it.id in favs }
                }
                val groups = LinkedHashSet<String>()
                for (c in base) groups.add(c.group)
                val group = wantedGroup?.takeIf { it in groups }
                val items = when {
                    q.isNotEmpty() -> base.filter { it.name.lowercase().contains(q) }
                    group != null -> base.filter { it.group == group }
                    else -> base
                }
                Triple(groups.toList(), group, items)
            }
            val (groups, group, items) = result
            selectedGroup = group

            val categories = ArrayList<String>(groups.size + 1)
            categories.add(getString(R.string.all_categories))
            categories.addAll(groups)
            val selectedIndex = if (group == null) 0 else groups.indexOf(group) + 1
            categoryAdapter.submit(categories, selectedIndex)

            currentItems = items
            channelAdapter.submit(items, favs)
            if (scrollTop) b.channelList.scrollToPosition(0)

            val error = loadError
            when {
                items.isNotEmpty() -> b.emptyText.visibility = View.GONE
                all.isEmpty() && error != null -> {
                    b.emptyText.text = getString(R.string.load_error, error)
                    b.emptyText.visibility = View.VISIBLE
                }
                currentTab == Tab.FAV && q.isEmpty() -> {
                    b.emptyText.setText(R.string.empty_favorites)
                    b.emptyText.visibility = View.VISIBLE
                }
                else -> {
                    b.emptyText.setText(R.string.empty_list)
                    b.emptyText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun openPlayer(position: Int) {
        if (position !in currentItems.indices) return
        Repository.playQueue = currentItems
        startActivity(
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_INDEX, position)
        )
    }

    private fun toggleFavorite(position: Int) {
        val ch = currentItems.getOrNull(position) ?: return
        val added = Repository.toggleFavorite(ch.id)
        Toast.makeText(this, if (added) R.string.fav_added else R.string.fav_removed, Toast.LENGTH_SHORT).show()
        if (tab == Tab.FAV) {
            refreshLists(scrollTop = false)
        } else {
            channelAdapter.updateFavorites(Repository.favoriteIds(), position)
        }
    }
}
