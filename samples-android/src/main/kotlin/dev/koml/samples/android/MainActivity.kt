package dev.koml.samples.android

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.koml.core.config.RuntimeConfig
import dev.koml.core.model.ModelHandle
import dev.koml.core.model.ModelInfo
import dev.koml.core.model.ModelLicense
import dev.koml.core.model.PromptTemplate
import dev.koml.core.session.GenParams
import dev.koml.core.session.TokenEvent
import dev.koml.engine.LlmKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var currentJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val output = findViewById<TextView>(R.id.output_text)
        val generateBtn = findViewById<Button>(R.id.generate_button)
        val cancelBtn = findViewById<Button>(R.id.cancel_button)

        generateBtn.setOnClickListener {
            output.text = ""
            generateBtn.isEnabled = false
            cancelBtn.isEnabled = true

            currentJob = lifecycleScope.launch {
                try {
                    runGeneration(output)
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

        cancelBtn.setOnClickListener {
            currentJob?.cancel()
        }
    }

    private suspend fun runGeneration(output: TextView) {
        val coordinator = LlmKit.initialize()
        val session = coordinator.loadModel(
            handle = ModelHandle(info = STUB_MODEL_INFO, localPath = MODEL_PATH),
            runtime = RuntimeConfig(contextSize = 2048),
        )

        try {
            session.generate(
                prompt = "The capital of France is",
                params = GenParams(maxTokens = 64),
            ).collect { event ->
                when (event) {
                    is TokenEvent.Token -> output.append(event.text)
                    is TokenEvent.Done -> output.append(
                        "\n\n[${event.reason}, ${event.stats.generatedTokens} tokens, " +
                            "${"%.1f".format(event.stats.tokensPerSecond)} tok/s]"
                    )
                    is TokenEvent.Error -> output.append("\n\nError: ${event.cause.message}")
                }
            }
        } finally {
            session.unload()
        }
    }

    companion object {
        private const val TAG = "KomlSample"
        private const val MODEL_PATH = "/data/local/tmp/model.gguf"

        private val STUB_MODEL_INFO = ModelInfo(
            id = "local-test",
            displayName = "Local Test Model",
            sizeBytes = 0,
            sha256 = "",
            downloadUrl = "",
            promptTemplate = PromptTemplate.None,
            contextWindow = 2048,
            license = ModelLicense(spdxId = "UNKNOWN", displayName = "Unknown"),
            recommendedRamMb = 512,
        )
    }
}
