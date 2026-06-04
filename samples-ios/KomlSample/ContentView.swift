import SwiftUI
import KomlEngine

/// Single-screen sample that exercises the full Phase 2 flow:
/// browse curated models → download with progress → load → stream generate → cancel.
struct ContentView: View {
    @State private var coordinator: LlmCoordinator?
    @State private var models: [ModelInfo] = []
    @State private var selectedId: String = ""
    @State private var downloaded: Bool = false
    @State private var downloadingPct: Double? = nil
    @State private var downloadStatus: String = ""
    @State private var loadingModel: Bool = false
    @State private var session: LlmSession?
    @State private var output: String = "Pick a model → Download → Load → Generate."
    @State private var isGenerating: Bool = false
    @State private var currentTask: Task<Void, Never>?

    private var selectedModel: ModelInfo? {
        models.first(where: { $0.id == selectedId })
    }

    var body: some View {
        VStack(spacing: 12) {
            // Init coordinator + registry list on appear
            if let model = selectedModel {
                modelPicker
                modelMeta(model)
                downloadControls(model)
                Divider().padding(.vertical, 6)
                generateControls
            } else {
                ProgressView("Initializing Koml…")
            }

            ScrollView {
                Text(output)
                    .font(.system(.body, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .background(Color(.secondarySystemBackground))
            .cornerRadius(8)
        }
        .padding()
        .task {
            await initialize()
        }
    }

    private var modelPicker: some View {
        Picker("Model", selection: $selectedId) {
            ForEach(models, id: \.id) { m in
                Text(m.displayName).tag(m.id)
            }
        }
        .pickerStyle(.menu)
        .onChange(of: selectedId) { _ in
            Task { await refreshDownloadedState() }
        }
    }

    private func modelMeta(_ m: ModelInfo) -> some View {
        Text("\(formatSize(m.sizeBytes))  ·  ctx \(m.contextWindow)  ·  \(m.license.spdxId)  ·  RAM ~\(m.recommendedRamMb) MB")
            .font(.caption)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func downloadControls(_ model: ModelInfo) -> some View {
        if let pct = downloadingPct {
            ProgressView(value: pct).progressViewStyle(.linear)
            Text(downloadStatus).font(.caption)
        } else if !downloadStatus.isEmpty {
            Text(downloadStatus).font(.caption)
        }

        HStack {
            Button("Download") {
                Task { await startDownload(model) }
            }
            .disabled(downloaded || downloadingPct != nil)

            Button("Load") {
                Task { await loadModel(model) }
            }
            .disabled(!downloaded || loadingModel)

            Button("Delete") {
                Task { await deleteModel(model) }
            }
            .disabled(!downloaded)
        }
        .buttonStyle(.bordered)
    }

    private var generateControls: some View {
        HStack {
            Button(action: { Task { await startGenerate() } }) {
                Text(isGenerating ? "Generating…" : "Generate")
                    .frame(maxWidth: .infinity)
            }
            .disabled(session == nil || isGenerating)
            .buttonStyle(.borderedProminent)

            Button("Cancel") {
                currentTask?.cancel()
            }
            .disabled(!isGenerating)
            .buttonStyle(.bordered)
        }
    }

    // MARK: — actions

    private func initialize() async {
        do {
            let c = try await LlmKit.shared.initialize(
                config: LlmKitConfig(maxConcurrentSessions: 1)
            )
            coordinator = c
            let list = try await c.registry.curated()
            models = list as? [ModelInfo] ?? []
            if let first = models.first {
                selectedId = first.id
                await refreshDownloadedState()
            }
        } catch {
            output = "Koml init failed: \(error.localizedDescription)"
        }
    }

    private func refreshDownloadedState() async {
        guard let c = coordinator else { return }
        do {
            downloaded = try await c.downloader.isDownloaded(id: selectedId).boolValue
            downloadingPct = nil
            downloadStatus = ""
        } catch {
            downloadStatus = "Status check failed: \(error.localizedDescription)"
        }
    }

    private func startDownload(_ model: ModelInfo) async {
        guard let c = coordinator else { return }
        downloadingPct = 0
        downloadStatus = "Starting…"
        let flow = c.downloader.download(model: model)
        do {
            for await state in flow {
                switch onEnum(of: state) {
                case .progress(let p):
                    if p.totalBytes > 0 {
                        downloadingPct = Double(p.bytesDownloaded) / Double(p.totalBytes)
                    }
                    downloadStatus = "\(formatSize(p.bytesDownloaded)) / \(formatSize(p.totalBytes))  ·  \(formatSize(p.bytesPerSecond))/s"
                case .completed:
                    downloadingPct = nil
                    downloadStatus = "Downloaded ✓"
                    downloaded = true
                case .failed(let f):
                    downloadingPct = nil
                    downloadStatus = "Failed: \(f.error.message ?? "unknown")"
                case .paused:
                    downloadStatus = "Paused"
                }
            }
        } catch {
            downloadingPct = nil
            downloadStatus = "Error: \(error.localizedDescription)"
        }
    }

    private func loadModel(_ model: ModelInfo) async {
        guard let c = coordinator else { return }
        loadingModel = true
        defer { loadingModel = false }
        do {
            let handles = try await c.downloader.localModels() as? [ModelHandle] ?? []
            guard let handle = handles.first(where: { $0.info.id == model.id }) else {
                output = "Local file missing for \(model.id)"
                return
            }
            try await session?.unload()
            session = try await c.loadModel(
                handle: handle,
                runtime: RuntimeConfig(contextSize: 2048, threads: 4, gpuLayers: 0)
            )
            output = "Model loaded. Press Generate."
        } catch {
            output = "Load failed: \(error.localizedDescription)"
        }
    }

    private func deleteModel(_ model: ModelInfo) async {
        guard let c = coordinator else { return }
        _ = try? await c.downloader.delete(id: model.id).boolValue
        await refreshDownloadedState()
        output = "Deleted \(model.id)."
    }

    private func startGenerate() async {
        guard let s = session else { return }
        output = ""
        isGenerating = true
        currentTask = Task {
            defer { isGenerating = false }
            do {
                let flow = s.generate(
                    prompt: "The capital of France is",
                    params: GenParams(
                        maxTokens: 64,
                        temperature: 0.7,
                        topP: 0.9,
                        topK: 40,
                        repeatPenalty: 1.1,
                        stopSequences: [],
                        seed: nil
                    )
                )
                for await event in flow {
                    if Task.isCancelled { break }
                    switch onEnum(of: event) {
                    case .token(let t):
                        output += t.text
                    case .done(let d):
                        output += "\n\n[\(d.reason), \(d.stats.generatedTokens) tokens, " +
                            String(format: "%.1f", d.stats.tokensPerSecond) + " tok/s]"
                    case .error(let err):
                        output += "\n\nError: \(err.cause.message ?? "unknown")"
                    }
                }
            } catch is CancellationError {
                output += "\n\n[cancelled]"
            } catch {
                output += "\n\nError: \(error.localizedDescription)"
            }
        }
    }

    // MARK: — helpers

    private func formatSize(_ bytes: Int64) -> String {
        if bytes <= 0 { return "0 B" }
        let units = ["B", "KB", "MB", "GB"]
        var size = Double(bytes)
        var i = 0
        while size >= 1024 && i < units.count - 1 {
            size /= 1024
            i += 1
        }
        return String(format: "%.1f %@", size, units[i])
    }
}
