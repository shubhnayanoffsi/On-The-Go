package com.onthego

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.onthego.ota.OtaUpdater
import com.onthego.service.BubbleManager
import com.onthego.service.TranscriptionService
import com.onthego.stt.ModelDownloader

/** Settings screen. Every control reads/writes Config. English only. */
class MainActivity : AppCompatActivity() {

    private val micPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.load(this)
        setContentView(R.layout.activity_main)

        setupSpinners()
        setupSwitches()
        setupButtons()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun setupSpinners() {
        fun bind(sp: Spinner, items: List<String>, cur: String, set: (String) -> Unit) {
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
            sp.setSelection(items.indexOf(cur).coerceAtLeast(0))
            sp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, i: Int, id: Long) {
                    set(items[i]); Config.save(this@MainActivity)
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        bind(findViewById(R.id.spLanguage), listOf("en", "auto"), Config.language) { Config.language = it }
        bind(findViewById(R.id.spCardTime), listOf("3", "5", "10"),
            Config.cardDurationSeconds.toString()) { Config.cardDurationSeconds = it.toInt() }
        bind(findViewById(R.id.spBubblePos),
            listOf("bottom_right", "bottom_left", "top_right", "top_left"),
            Config.bubblePosition) { Config.bubblePosition = it }
        bind(findViewById(R.id.spBubbleSize), listOf("medium", "small"), Config.bubbleSize) { Config.bubbleSize = it }
        bind(findViewById(R.id.spAi), listOf("off", "api"), Config.aiFixMode) { Config.aiFixMode = it }
        bind(findViewById(R.id.spChannel), listOf("stable", "beta"), Config.updateChannel) { Config.updateChannel = it }
    }

    private fun setupSwitches() {
        fun bind(id: Int, cur: Boolean, set: (Boolean) -> Unit) {
            findViewById<Switch>(id).apply {
                isChecked = cur
                setOnCheckedChangeListener { _, on -> set(on); Config.save(this@MainActivity) }
            }
        }
        bind(R.id.swVad, Config.vadEnabled) { Config.vadEnabled = it }
        bind(R.id.swClipboard, Config.clipboard) { Config.clipboard = it }
        bind(R.id.swCard, Config.card) { Config.card = it }
        bind(R.id.swHistory, Config.historyFile) { Config.historyFile = it }
        bind(R.id.swBoot, Config.autostartOnBoot) { Config.autostartOnBoot = it }

        findViewById<SeekBar>(R.id.seekVad).apply {
            progress = Config.vadStrict - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Config.vadStrict = p + 1
                    findViewById<TextView>(R.id.vadLabel).text = "VAD strict: ${p + 1}"
                    Config.save(this@MainActivity)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        findViewById<TextView>(R.id.vadLabel).text = "VAD strict: ${Config.vadStrict}"
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnMic).setOnClickListener {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startForegroundService(Intent(this, TranscriptionService::class.java))
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, TranscriptionService::class.java))
            BubbleManager.hide()
        }
        findViewById<Button>(R.id.btnModel).setOnClickListener {
            findViewById<TextView>(R.id.modelStatus).text = "Downloading..."
            Thread {
                try {
                    ModelDownloader.download(this) {}
                    runOnUiThread { refreshStatus() }
                } catch (e: Exception) {
                    runOnUiThread {
                        findViewById<TextView>(R.id.modelStatus).text = "Failed: ${e.message}"
                    }
                }
            }.start()
        }
        findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            findViewById<TextView>(R.id.updateStatus).text = "Checking..."
            Thread {
                val msg = try {
                    val json = java.net.URL(OtaUpdater.LATEST_URL).readText()
                    val ver = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "?"
                    val installed = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"
                    if (OtaUpdater.isNewer(installed, ver)) "Update found: $ver. Open Releases to install."
                    else "You are on latest ($installed)."
                } catch (e: Exception) {
                    "Check failed: ${e.message}"
                }
                runOnUiThread { findViewById<TextView>(R.id.updateStatus).text = msg }
            }.start()
        }
    }

    private fun refreshStatus() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val overlay = BubbleManager.canDraw(this)
        val pm = getSystemService(PowerManager::class.java)
        val batt = pm.isIgnoringBatteryOptimizations(packageName)
        findViewById<TextView>(R.id.permStatus).text =
            "Mic: ${if (mic) "yes" else "NO"} | Overlay: ${if (overlay) "yes" else "NO"} | Battery-no-kill: ${if (batt) "yes" else "NO"}"

        val mf = ModelDownloader.modelFile(this)
        findViewById<TextView>(R.id.modelStatus).text =
            if (ModelDownloader.isReady(this)) "Ready (${mf.length() / 1_000_000} MB)"
            else "Not downloaded yet"

        findViewById<TextView>(R.id.versionText).text =
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
    }
}
