import SwiftUI

struct CameraListView: View {
    @StateObject private var model = DashboardModel()

    var body: some View {
        NavigationStack {
            List {
                Section("Dashboard") {
                    TextField("http://100.x.y.z:3001", text: $model.serverAddress)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()

                    Button {
                        Task { await model.connect() }
                    } label: {
                        HStack {
                            Text(model.isLoading ? "Connecting…" : "Connect / Refresh")
                            Spacer()
                            if model.isLoading {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(model.isLoading || model.serverAddress.isEmpty)

                    Text("Connect over Tailscale. Keep Tailscale connected while viewing.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let message = model.message {
                    Section {
                        Text(message)
                            .foregroundStyle(model.cameras.isEmpty ? .secondary : .red)
                    }
                }

                Section("Cameras") {
                    if model.cameras.isEmpty {
                        Text("Connect to your dashboard to load configured cameras.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(model.cameras) { camera in
                            NavigationLink(value: camera) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(camera.name)
                                        .font(.headline)
                                    Text("\(camera.ip) · CH\(camera.channel) · \(camera.stream.uppercased())")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Local NVR")
            .navigationDestination(for: Camera.self) { camera in
                CameraPlayerView(camera: camera, model: model)
            }
            .task {
                if !model.serverAddress.isEmpty && model.cameras.isEmpty {
                    await model.connect()
                }
            }
        }
    }
}

private struct CameraPlayerView: View {
    let camera: Camera
    @ObservedObject var model: DashboardModel

    var body: some View {
        Group {
            if let serverURL = try? model.baseURL() {
                CameraPlaybackView(serverURL: serverURL, camera: camera)
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "network.slash")
                        .font(.largeTitle)
                    Text("Dashboard address unavailable")
                        .font(.headline)
                    Text("Return to the camera list and connect to the dashboard.")
                        .foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.center)
            }
        }
        .navigationTitle(camera.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
