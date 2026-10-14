package com.onthego

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.onthego.service.BubbleManager
import com.onthego.service.TranscriptionService
import com.onthego.stt.ModelDownloader

/** First launch: permissions in order, then offline model OR key. Plain words. */
class FirstRunActivity : AppCompatActivity() {

    private val micPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshSteps()
        }

    private var modelDone = false
    private var keyDone = false
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.load(this)
        setContentView(R.layout.activity_firstrun)

        findViewById<Button>(R.id.btnAllowMic).setOnClickListener {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
        findViewById<Button>(R.id.btnAllowOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !BubbleManager.canDraw(this)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                )
            } else {
                refreshSteps()
            }
        }
        findViewById<Button>(R.id.btnAllowBattery).setOnClickListener {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                )
            } else {
                refreshSteps()
            }
        }

        val providers = listOf("Groq", "OpenAI", "Custom")
        val sp = findViewById<Spinner>(R.id.spKeyProvider)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        sp.setSelection(0)

        findViewById<Button>(R.id.btnDownloadModel).setOnClickListener { downloadModel() }
        findViewById<Button>(R.id.btnSaveKey).setOnClickListener { saveKey() }
        findViewById<Button>(R.id.btnFinish).setOnClickListener { finishSetup() }

        refreshSteps()
    }

    override fun onResume() {
        super.onResume()
        refreshSteps()
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun noKill(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun refreshSteps() {
        val mic = hasMic()
        val overlay = BubbleManager.canDraw(this)
        val batt = noKill()
        findViewById<View>(R.id.stepOverlay).visibility = if (mic) View.VISIBLE else View.GONE
        findViewById<View>(R.id.stepBattery).visibility =
            if (mic && overlay) View.VISIBLE else View.GONE
        findViewById<View>(R.id.stepChoice).visibility =
            if (mic && overlay && batt) View.VISIBLE else View.GONE
        if (ModelDownloader.isReady(this)) {
            modelDone = true
            showDone()
        }
    }

    private fun downloadModel() {
        if (downloading) return
        downloading = true
        val row = findViewById<LinearLayout>(R.id.firstrunProgressRow)
        val bar = findViewById<ProgressBar>(R.id.firstrunModelProgress)
        val pct = findViewById<TextView>(R.id.firstrunModelPct)
        row.visibility = View.VISIBLE
        Thread {
            try {
                ModelDownloader.download(this) { p ->
                    runOnUiThread {
                        bar.progress = p
                        pct.text = "$p%"
                    }
                }
                runOnUiThread {
                    downloading = false
                    modelDone = true
                    Config.engineMode = "offline"
                    Config.save(this)
                    showDone()
                    Toast.makeText(this, "Voice pack ready", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    downloading = false
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveKey() {
        val key = findViewById<EditText>(R.id.etApiKey).text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "Paste your key first", Toast.LENGTH_SHORT).show()
            return
        }
        val prov = findViewById<Spinner>(R.id.spKeyProvider).selectedItem.toString()
        Config.sttProvider = when (prov) {
            "OpenAI" -> "openai"
            "Custom" -> "custom"
            else -> "groq"
        }
        Config.engineMode = "api"
        KeysStore.setSttKey(this, key)
        Config.save(this)
        keyDone = true
        findViewById<EditText>(R.id.etApiKey).text.clear()
        showDone()
        Toast.makeText(this, "Key saved", Toast.LENGTH_SHORT).show()
    }

    private fun showDone() {
        if (modelDone || keyDone) {
            findViewById<TextView>(R.id.firstrunDone).visibility = View.VISIBLE
            findViewById<Button>(R.id.btnFinish).visibility = View.VISIBLE
        }
    }

    private fun finishSetup() {
        Config.firstRunDone = true
        Config.save(this)
        startForegroundService(Intent(this, TranscriptionService::class.java))
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
