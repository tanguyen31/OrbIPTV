package com.orbit.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orbit.iptv.R
import com.orbit.iptv.data.Repository
import com.orbit.iptv.data.Source
import com.orbit.iptv.databinding.ActivitySetupBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.UnknownHostException

class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private var m3uMode = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        when (val s = Repository.source()) {
            is Source.M3u -> {
                m3uMode = true
                b.inputM3u.setText(s.url)
            }
            is Source.Xtream -> {
                b.inputServer.setText(s.server)
                b.inputUser.setText(s.user)
                b.inputPass.setText(s.pass)
            }
            null -> Unit
        }

        b.modeGroup.check(if (m3uMode) R.id.btnModeM3u else R.id.btnModeXtream)
        applyMode()
        b.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                m3uMode = checkedId == R.id.btnModeM3u
                applyMode()
            }
        }

        b.btnConnect.setOnClickListener { connect() }
        val doneListener = android.widget.TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                connect()
                true
            } else {
                false
            }
        }
        b.inputPass.setOnEditorActionListener(doneListener)
        b.inputM3u.setOnEditorActionListener(doneListener)
    }

    private fun applyMode() {
        b.xtreamFields.visibility = if (m3uMode) View.GONE else View.VISIBLE
        b.layoutM3u.visibility = if (m3uMode) View.VISIBLE else View.GONE
        b.errorText.visibility = View.GONE
    }

    private fun buildSource(): Source? {
        b.layoutServer.error = null
        b.layoutUser.error = null
        b.layoutPass.error = null
        b.layoutM3u.error = null
        val required = getString(R.string.error_required)

        if (m3uMode) {
            var url = b.inputM3u.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                b.layoutM3u.error = required
                return null
            }
            if (!url.contains("://")) url = "http://$url"
            return Source.M3u(url)
        }

        val server = b.inputServer.text?.toString()?.trim().orEmpty()
        val user = b.inputUser.text?.toString()?.trim().orEmpty()
        val pass = b.inputPass.text?.toString()?.trim().orEmpty()
        var ok = true
        if (server.isEmpty()) { b.layoutServer.error = required; ok = false }
        if (user.isEmpty()) { b.layoutUser.error = required; ok = false }
        if (pass.isEmpty()) { b.layoutPass.error = required; ok = false }
        return if (ok) Source.Xtream(server, user, pass) else null
    }

    private fun connect() {
        if (busy) return
        val source = buildSource() ?: return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Repository.fetch(source) }
                if (list.isEmpty()) {
                    showError(getString(R.string.error_no_channels))
                    setBusy(false)
                    return@launch
                }
                Repository.saveSource(source)
                Repository.channels = list
                withContext(Dispatchers.IO) {
                    try { Repository.saveCache() } catch (e: IOException) { }
                }
                startActivity(
                    Intent(this@SetupActivity, BrowseActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = when (e) {
                    is UnknownHostException -> "serveur introuvable, vérifie l'adresse"
                    is java.net.SocketTimeoutException -> "le serveur ne répond pas"
                    else -> e.message ?: e.javaClass.simpleName
                }
                showError(getString(R.string.error_generic, reason))
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        b.btnConnect.isEnabled = !value
        b.btnConnect.text = if (value) "" else getString(R.string.connect)
        b.progress.visibility = if (value) View.VISIBLE else View.GONE
        b.progressText.visibility = if (value) View.VISIBLE else View.GONE
        if (value) b.errorText.visibility = View.GONE
    }

    private fun showError(message: String) {
        b.errorText.text = message
        b.errorText.visibility = View.VISIBLE
    }
}
