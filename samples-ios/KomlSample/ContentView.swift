import SwiftUI
import KomlEngine

struct ContentView: View {
    @State private var output: String = "Press Generate to stream tokens. Press Cancel to abort mid-stream."
    @State private var isGenerating: Bool = false
    @State private var currentTask: Task<Void, Never>? = nil

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Button(action: generate) {
                    Text("Generate")
                        .frame(maxWidth: .infinity)
                }
                .disabled(isGenerating)
                .buttonStyle(.borderedProminent)

                Button(action: cancel) {
                    Text("Cancel")
                        .frame(maxWidth: .infinity)
                }
                .disabled(!isGenerating)
                .buttonStyle(.bordered)
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
    }

    private func generate() {
        output = ""
        isGenerating = true

        currentTask = Task {
            defer { isGenerating = false }

            do {
                // Bundled GGUF — see samples-ios/README.md for how to add one
                guard let modelPath = Bundle.main.path(forResource: "model", ofType: "gguf") else {
                    output = "Error: bundle model.gguf into the app target (see README)"
                    return
                }

                let coordinator = try await LlmKit.shared.initialize(
                    config: LlmKitConfig(maxConcurrentSessions: 1)
                )
                let info = ModelInfo(
                    id: "local-test",
                    displayName: "Local Test Model",
                    sizeBytes: 0,
                    sha256: "",
                    downloadUrl: "",
                    promptTemplate: PromptTemplate.none,
                    contextWindow: 2048,
                    license: ModelLicense(
                        spdxId: "UNKNOWN",
                        displayName: "Unknown",
                        fullTextUrl: nil,
                        termsUrl: nil,
                        requiresAcceptance: false
                    ),
                    recommendedRamMb: 512
                )
                let handle = ModelHandle(info: info, localPath: modelPath)
                let session = try await coordinator.loadModel(
                    handle: handle,
                    runtime: RuntimeConfig(contextSize: 2048, threads: 4, gpuLayers: 0)
                )

                defer {
                    Task { try? await session.unload() }
                }

                let flow = session.generate(
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
                    case .token(let token):
                        output += token.text
                    case .done(let done):
                        output += "\n\n[\(done.reason), \(done.stats.generatedTokens) tokens, " +
                            String(format: "%.1f", done.stats.tokensPerSecond) + " tok/s]"
                    case .error(let err):
                        output += "\n\nError: \(err.cause.message ?? "unknown")"
                    }
                }
            } catch is CancellationError {
                output += "\n\n[cancelled]"
            } catch {
                output = "Error: \(error.localizedDescription)"
            }
        }
    }

    private func cancel() {
        currentTask?.cancel()
    }
}
