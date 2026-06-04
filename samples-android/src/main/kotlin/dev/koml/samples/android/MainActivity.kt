package dev.koml.samples.android

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.koml.core.LlmCoordinator
import dev.koml.core.LlmSession
import dev.koml.core.config.RuntimeConfig
import dev.koml.core.download.DownloadState
import dev.koml.core.model.ModelHandle
import dev.koml.core.model.ModelInfo
import dev.koml.core.session.GenParams
import dev.koml.core.session.TokenEvent
import dev.koml.engine.LlmKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var coordinator: LlmCoordinator
    private var session: LlmSession? = null
    private var currentJob: Job? = null
    private var models: List<ModelInfo> = emptyList()
    private var selected: ModelInfo? = null

    private lateinit var spinner: Spinner
    private lateinit var meta: TextView
    private lateinit var downloadBtn: Button
    private lateinit var loadBtn: Button
    private lateinit var deleteBtn: Button
    private lateinit var generateBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var output: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.model_spinner)
        meta = findViewById(R.id.model_meta)
        downloadBtn = findViewById(R.id.download_button)
        loadBtn = findViewById(R.id.load_button)
        deleteBtn = findViewById(R.id.delete_button)
        generateBtn = findViewById(R.id.generate_button)
        cancelBtn = findViewById(R.id.cancel_button)
        output = findViewById(R.id.output_text)
        progress = findViewById(R.id.download_progress)
        downloadStatus = findViewById(R.id.download_status)

        lifecycleScope.launch {
            coordinator = LlmKit.initialize()
            models = coordinator.registry.curated()
            spinner.adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                models.map { it.displayName },
            )
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    selected = models[pos]
                    refreshButtonsForSelection()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            if (models.isNotEmpty()) {
                selected = models.first()
                refreshButtonsForSelection()
            }
        }

        downloadBtn.setOnClickListener { startDownload() }
        loadBtn.setOnClickListener { loadModel() }
        deleteBtn.setOnClickListener { deleteModel() }
        generateBtn.setOnClickListener { startGenerate() }
        cancelBtn.setOnClickListener { currentJob?.cancel() }
    }

    private fun refreshButtonsForSelection() {
        val model = selected ?: return
        meta.text = "${formatSize(model.sizeBytes)}  ·  ctx ${model.contextWindow}  ·  " +
            "${model.license.spdxId}  ·  RAM ~${model.recommendedRamMb} MB"

        lifecycleScope.launch {
            val downloaded = coordinator.downloader.isDownloaded(model.id)
            downloadBtn.isEnabled = !downloaded
            loadBtn.isEnabled = downloaded
            deleteBtn.isEnabled = downloaded
            progress.visibility = View.GONE
            downloadStatus.visibility = View.GONE
        }
    }

    private fun startDownload() {
        val model = selected ?: return
        downloadBtn.isEnabled = false
        progress.visibility = View.VISIBLE
        downloadStatus.visibility = View.VISIBLE
        progress.progress = 0

        lifecycleScope.launch {
            try {
                coordinator.downloader.download(model).collect { state ->
                    when (state) {
                        is DownloadState.Progress -> {
                            val pct = if (state.totalBytes > 0) {
                                (state.bytesDownloaded * 100 / state.totalBytes).toInt()
                            } else 0
                            progress.progress = pct
                            downloadStatus.text =
                                "${formatSize(state.bytesDownloaded)} / ${formatSize(state.totalBytes)}  ·  " +
                                    "${formatSize(state.bytesPerSecond)}/s"
                        }
                        is DownloadState.Completed -> {
                            downloadStatus.text = "Downloaded ✓"
                            progress.visibility = View.GONE
                            loadBtn.isEnabled = true
                            deleteBtn.isEnabled = true
                        }
                        is DownloadState.Failed -> {
                            downloadStatus.text = "Failed: ${state.error.message}"
                            downloadBtn.isEnabled = true
                        }
                        DownloadState.Paused -> {
                            downloadStatus.text = "Paused"
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "download failed", e)
                downloadStatus.text = "Error: ${e.message}"
                downloadBtn.isEnabled = true
            }
        }
    }

    private fun loadModel() {
        val model = selected ?: return
        loadBtn.isEnabled = false
        generateBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                val handle = coordinator.downloader.localModels()
                    .firstOrNull { it.info.id == model.id }
                    ?: ModelHandle(info = model, localPath = "")
                if (handle.localPath.isBlank()) {
                    output.text = "Local file not found for ${model.id}"
                    return@launch
                }
                session?.unload()
                session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 2048))
                output.text = "Model loaded. Press Generate."
                generateBtn.isEnabled = true
            } catch (e: Throwable) {
                Log.e(TAG, "loadModel failed", e)
                output.text = "Load failed: ${e.message}"
                loadBtn.isEnabled = true
            }
        }
    }

    private fun deleteModel() {
        val model = selected ?: return
        lifecycleScope.launch {
            coordinator.downloader.delete(model.id)
            refreshButtonsForSelection()
            output.text = "Deleted ${model.id}."
        }
    }

    private fun startGenerate() {
        val s = session ?: return
        output.text = ""
        generateBtn.isEnabled = false
        cancelBtn.isEnabled = true

        currentJob = lifecycleScope.launch {
            try {
                s.generate(
                    prompt = "The capital of France is",
                    params = GenParams(maxTokens = 64),
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Token -> output.append(event.text)
                        is TokenEvent.Done -> output.append(
                            "\n\n[${event.reason}, ${event.stats.generatedTokens} tokens, " +
                                "${"%.1f".format(event.stats.tokensPerSecond)} tok/s]",
                        )
                        is TokenEvent.Error -> output.append("\n\nError: ${event.cause.message}")
                    }
                }
            } catch (e: CancellationException) {
                output.append("\n\n[cancelled]")
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "generation failed", e)
                output.append("\n\nError: ${e.message}")
            } finally {
                generateBtn.isEnabled = true
                cancelBtn.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
        lifecycleScope.launch { session?.unload() }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.size - 1) {
            size /= 1024
            unit++
        }
        return "%.1f %s".format(size, units[unit])
    }

    companion object {
        private const val TAG = "KomlSample"
    }
}
