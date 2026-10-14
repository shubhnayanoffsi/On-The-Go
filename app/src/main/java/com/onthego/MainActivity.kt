package com.onthego

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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

/** Friendly settings screen. Every control reads/writes Config. English only. */
class MainActivity : AppCompatActivity() {

    private val micPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermWarn()
        }

    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.load(this)
        if (!Config.firstRunDone) {
            startActivity(Intent(this, FirstRunActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        setupSpinners()
        setupVad()
        setupSwitch()
        setupEndpoints()
        setupButtons()
        findViewById<TextView>(R.id.tvPermWarn).setOnClickListener {
            startActivity(Intent(this, FirstRunActivity::class.java))
        }
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        if (!Config.firstRunDone) return
        refreshAll()
    }

    private fun bind(
        sp: Spinner,
        labels: List<String>,
        values: List<String>,
        cur: String,
        set: (String) -> Unit,
    ) {
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        sp.setSelection(values.indexOf(cur).coerceAtLeast(0))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) {
                set(values[i]); Config.save(this@MainActivity); refreshVisibility()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupSpinners() {
        bind(findViewById(R.id.spLanguage), listOf("English", "Auto-detect"),
            listOf("en", "auto"), Config.language) { Config.language = it }
        bind(findViewById(R.id.spEngine),
            listOf("Automatic", "Offline only", "Fast API only"),
            listOf("auto", "offline", "api"), Config.engineMode) { Config.engineMode = it }
        bind(findViewById(R.id.spSttProvider), listOf("Groq", "OpenAI", "Custom"),
            listOf("groq", "openai", "custom"), Config.sttProvider) { Config.sttProvider = it }
        bind(findViewById(R.id.spBubbleSize), listOf("Small", "Medium"),
            listOf("small", "medium"), Config.bubbleSize) { Config.bubbleSize = it }
        bind(findViewById(R.id.spAi), listOf("Off", "Fix with API"),
            listOf("off", "api"), Config.aiFixMode) { Config.aiFixMode = it }
        bind(findViewById(R.id.spFixProvider), listOf("OpenAI", "Grok", "Gemini", "Custom"),
            listOf("openai", "grok", "gemini", "custom"), Config.fixProvider) { Config.fixProvider = it }
        bind(findViewById(R.id.spChannel), listOf("Stable", "Beta"),
            listOf("stable", "beta"), Config.updateChannel) { Config.updateChannel = it }
    }

    private fun setupVad() {
        val names = listOf("Skip a little silence", "Skip some silence", "Skip lots of silence")
        findViewById<SeekBar>(R.id.seekVad).apply {
            progress = (Config.vadStrict - 1).coerceIn(0, 2)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Config.vadStrict = (p + 1).coerceIn(1, 3)
                    findViewById<TextView>(R.id.vadLabel).text =
                        "Silence skip: ${names[Config.vadStrict - 1]}"
                    Config.save(this@MainActivity)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        findViewById<TextView>(R.id.vadLabel).text =
            "Silence skip: ${names[(Config.vadStrict - 1).coerceIn(0, 2)]}"
    }

    private fun setupSwitch() {
        findViewById<Switch>(R.id.swBoot).apply {
            isChecked = Config.autostartOnBoot
            setOnCheckedChangeListener { _, on ->
                Config.autostartOnBoot = on; Config.save(this@MainActivity)
            }
        }
    }

    private fun setupEndpoints() {
        fun bindEt(id: Int, cur: String, set: (String) -> Unit) {
            findViewById<EditText>(id).apply {
                setText(cur)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        set(s.toString().trim()); Config.save(this@MainActivity)
                    }
                })
            }
        }
        bindEt(R.id.etSttEndpoint, Config.sttEndpoint) { Config.sttEndpoint = it }
        bindEt(R.id.etFixEndpoint, Config.fixEndpoint) { Config.fixEndpoint = it }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            ensureMic()
            startForegroundService(Intent(this, TranscriptionService::class.java))
            refreshAll()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, TranscriptionService::class.java))
            BubbleManager.hide()
            refreshAll()
        }
        findViewById<Button>(R.id.btnModel).setOnClickListener { downloadModel() }
        findViewById<Button>(R.id.btnMicTest).setOnClickListener { micTest() }
        findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            findViewById<TextView>(R.id.updateStatus).text = "Checking..."
            Thread {
                var msg = ""
                try {
                    OtaUpdater.checkAndUpdate(this) { msg = it }
                } catch (e: Exception) {
                    msg = "Check failed: ${e.message}"
                }
                val out = msg
                runOnUiThread { findViewById<TextView>(R.id.updateStatus).text = out }
            }.start()
        }
    }

    private fun ensureMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun micTest() {
        val st = findViewById<TextView>(R.id.micTestStatus)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            st.text = "Mic permission missing. Allow it first."
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        st.text = "Listening 3s, speak now..."
        Thread {
            try {
                val cap = com.onthego.audio.AudioCapture()
                cap.start()
                Thread.sleep(3000)
                val pcm = cap.stop()
                val info = cap.info()
                runOnUiThread {
                    st.text = if (info.samples == 0) {
                        "Got 0 bytes. Close other recorder apps and retry."
                    } else {
                        "Heard ${"%.1f".format(info.seconds)}s, loudness ${info.peakPct}%." +
                            if (info.peakPct < 3) " Too quiet, speak closer." else " Mic works."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { st.text = "Mic failed: ${e.message}" }
            }
        }.start()
    }

    private fun downloadModel() {
        if (downloading) return
        downloading = true
        val row = findViewById<LinearLayout>(R.id.modelProgressRow)
        val bar = findViewById<ProgressBar>(R.id.modelProgress)
        val pct = findViewById<TextView>(R.id.modelPct)
        row.visibility = View.VISIBLE
        findViewById<TextView>(R.id.modelStatus).text = "Downloading..."
        Thread {
            try {
                val names = listOf(ModelDownloader.TINY, ModelDownloader.BASE)
                for ((i, name) in names.withIndex()) {
                    if (ModelDownloader.isReady(this, name)) continue
                    ModelDownloader.download(this, name) { p ->
                        val all = (i * 100 + p) / names.size
                        runOnUiThread {
                            bar.progress = all
                            pct.text = "$all%"
                            findViewById<TextView>(R.id.modelStatus).text =
                                "Downloading voice pack... $all%"
                        }
                    }
                }
                runOnUiThread {
                    downloading = false
                    refreshModel()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    downloading = false
                    findViewById<TextView>(R.id.modelStatus).text =
                        "Download failed: ${e.message}"
                }
            }
        }.start()
    }

    private fun refreshAll() {
        findViewById<TextView>(R.id.versionText).text =
            "Version ${packageManager.getPackageInfo(packageName, 0).versionName}"
        findViewById<TextView>(R.id.serviceState).text =
            if (isServiceRunning()) "Running — tap the button to dictate"
            else "Not running"
        refreshModel()
        refreshVisibility()
        refreshPermWarn()
    }

    private fun refreshModel() {
        val status = findViewById<TextView>(R.id.modelStatus)
        val row = findViewById<LinearLayout>(R.id.modelProgressRow)
        if (ModelDownloader.isPackReady(this)) {
            val mb = ModelDownloader.packSizeMb(this)
            status.text = "Ready ($mb MB on your phone)"
            row.visibility = View.GONE
        } else if (!downloading) {
            status.text = "Not downloaded yet"
            row.visibility = View.GONE
        }
    }

    private fun refreshVisibility() {
        val online = Config.engineMode != "offline"
        findViewById<View>(R.id.sttProviderRow).visibility =
            if (online) View.VISIBLE else View.GONE
        findViewById<View>(R.id.etSttEndpoint).visibility =
            if (online && Config.sttProvider == "custom") View.VISIBLE else View.GONE
        findViewById<View>(R.id.fixProviderRow).visibility =
            if (Config.aiFixMode == "api") View.VISIBLE else View.GONE
        findViewById<View>(R.id.etFixEndpoint).visibility =
            if (Config.aiFixMode == "api" && Config.fixProvider == "custom") {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun refreshPermWarn() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val overlay = BubbleManager.canDraw(this)
        val pm = getSystemService(PowerManager::class.java)
        val batt = pm.isIgnoringBatteryOptimizations(packageName)
        findViewById<TextView>(R.id.tvPermWarn).visibility =
            if (mic && overlay && batt) View.GONE else View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ActivityManager::class.java)
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == TranscriptionService::class.java.name }
    }
}
