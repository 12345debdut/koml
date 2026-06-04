package dev.koml.samples.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.runBlocking

private val coordinator: LlmCoordinator = runBlocking { LlmKit.initialize() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.ModelList) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (screen is Screen.Chat) "Koml — Chat" else "Koml — Models") },
                    navigationIcon = {
                        if (screen is Screen.Chat) {
                            TextButton(onClick = { screen = Screen.ModelList }) {
                                Text("← Back")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (val s = screen) {
                    is Screen.ModelList -> ModelListScreen(
                        onLoaded = { handle -> screen = Screen.Chat(handle) },
                    )
                    is Screen.Chat -> ChatScreen(handle = s.handle, onBack = { screen = Screen.ModelList })
                }
            }
        }
    }
}

private sealed class Screen {
    object ModelList : Screen()
    data class Chat(val handle: ModelHandle) : Screen()
}

@Composable
private fun ModelListScreen(onLoaded: (ModelHandle) -> Unit) {
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        models = coordinator.registry.curated()
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        models.forEach { model ->
            ModelRow(model = model, onLoaded = onLoaded)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModelRow(model: ModelInfo, onLoaded: (ModelHandle) -> Unit) {
    var downloadState by remember(model.id) { mutableStateOf<DownloadState?>(null) }
    var alreadyDownloaded by remember(model.id) { mutableStateOf(false) }
    var loading by remember(model.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(model.id) {
        alreadyDownloaded = coordinator.downloader.isDownloaded(model.id)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatSize(model.sizeBytes)}  ·  ctx ${model.contextWindow}  ·  ${model.license.spdxId}  ·  RAM ~${model.recommendedRamMb} MB",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))

            val progress = downloadState
            if (progress is DownloadState.Progress) {
                val pct = if (progress.totalBytes > 0) {
                    progress.bytesDownloaded.toFloat() / progress.totalBytes
                } else 0f
                LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatSize(progress.bytesDownloaded)} / ${formatSize(progress.totalBytes)}  ·  ${formatSize(progress.bytesPerSecond)}/s",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (progress is DownloadState.Failed) {
                Text(
                    "Error: ${progress.error.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alreadyDownloaded || downloadState is DownloadState.Completed) {
                    Button(
                        onClick = {
                            loading = true
                            scope.launch {
                                try {
                                    val handle = (downloadState as? DownloadState.Completed)?.handle
                                        ?: ModelHandle(
                                            info = model,
                                            localPath = run {
                                                // resolve via storage indirectly through downloader's localModels
                                                coordinator.downloader.localModels()
                                                    .firstOrNull { it.info.id == model.id }
                                                    ?.localPath
                                                    ?: error("Local file missing for ${model.id}")
                                            },
                                        )
                                    onLoaded(handle)
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                    ) {
                        Text(if (loading) "Loading…" else "Load")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                coordinator.downloader.download(model).collect { state ->
                                    downloadState = state
                                    if (state is DownloadState.Completed) {
                                        alreadyDownloaded = true
                                    }
                                }
                            }
                        },
                        enabled = downloadState !is DownloadState.Progress,
                    ) {
                        Text("Download")
                    }
                }

                if (alreadyDownloaded) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            coordinator.downloader.delete(model.id)
                            alreadyDownloaded = false
                            downloadState = null
                        }
                    }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(handle: ModelHandle, onBack: () -> Unit) {
    var prompt by remember { mutableStateOf("The capital of France is") }
    var output by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<LlmSession?>(null) }
    var loadingSession by remember { mutableStateOf(true) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(handle.info.id) {
        loadingSession = true
        session = coordinator.loadModel(handle, RuntimeConfig(contextSize = 2048))
        loadingSession = false
    }

    DisposableEffect(handle.info.id) {
        onDispose {
            currentJob?.cancel()
            scope.launch { session?.unload() }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Model: ${handle.info.displayName}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    output = ""
                    currentJob = scope.launch {
                        try {
                            val s = session ?: return@launch
                            s.generate(prompt, GenParams(maxTokens = 128)).collect { event ->
                                when (event) {
                                    is TokenEvent.Token -> output += event.text
                                    is TokenEvent.Done -> output += "\n\n[${event.reason}, ${event.stats.generatedTokens} tokens, ${"%.1f".format(event.stats.tokensPerSecond)} tok/s]"
                                    is TokenEvent.Error -> output += "\n\nError: ${event.cause.message}"
                                }
                            }
                        } catch (e: CancellationException) {
                            output += "\n\n[cancelled]"
                            throw e
                        }
                    }
                },
                enabled = !loadingSession && currentJob?.isActive != true,
            ) {
                Text(if (loadingSession) "Loading model…" else "Send")
            }
            OutlinedButton(
                onClick = { currentJob?.cancel() },
                enabled = currentJob?.isActive == true,
            ) {
                Text("Cancel")
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            Text(
                text = output.ifBlank { "Press Send to stream tokens. Press Cancel to abort mid-stream." },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceAtMost(units.size - 1)
    return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
