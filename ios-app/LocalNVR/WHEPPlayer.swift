import SwiftUI
import Combine
import WebRTC

private enum WebRTCFactory {
    static let shared = RTCPeerConnectionFactory(
        encoderFactory: RTCDefaultVideoEncoderFactory(),
        decoderFactory: RTCDefaultVideoDecoderFactory()
    )
}

@MainActor
final class WHEPPlayer: NSObject, ObservableObject {
    @Published private(set) var videoTrack: RTCVideoTrack?
    @Published private(set) var status = "Connecting to camera…"

    private let serverURL: URL
    private let camera: Camera
    private var peerConnection: RTCPeerConnection?
    private var gatheringContinuation: CheckedContinuation<Void, Error>?
    private var gatheringTimeout: Task<Void, Never>?
    private var isStopped = false

    init(serverURL: URL, camera: Camera) {
        self.serverURL = serverURL
        self.camera = camera
        super.init()
        start()
    }

    func stop() {
        isStopped = true
        gatheringTimeout?.cancel()
        gatheringTimeout = nil
        finishGathering(.failure(CancellationError()))
        peerConnection?.delegate = nil
        peerConnection?.close()
        peerConnection = nil
        videoTrack = nil
    }

    private func start() {
        let configuration = RTCConfiguration()
        configuration.iceServers = []
        configuration.sdpSemantics = .unifiedPlan
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)

        guard let peer = WebRTCFactory.shared.peerConnection(
            with: configuration,
            constraints: constraints,
            delegate: self
        ) else {
            status = "Could not create a WebRTC connection."
            return
        }
        peerConnection = peer

        let transceiverInit = RTCRtpTransceiverInit()
        transceiverInit.direction = .recvOnly
        guard peer.addTransceiver(of: .video, init: transceiverInit) != nil else {
            status = "Could not create a video receiver."
            return
        }

        peer.offer(for: constraints) { [weak self, weak peer] offer, error in
            Task { @MainActor [weak self, weak peer] in
                guard let self, let peer, !self.isStopped else { return }
                guard let offer, error == nil else {
                    self.status = error?.localizedDescription ?? "Could not create a WebRTC offer."
                    return
                }

                peer.setLocalDescription(offer) { [weak self, weak peer] error in
                    Task { @MainActor [weak self, weak peer] in
                        guard let self, let peer, !self.isStopped else { return }
                        if let error {
                            self.status = error.localizedDescription
                            return
                        }

                        do {
                            try await self.waitForICEGathering(peer)
                            guard !self.isStopped, let localOffer = peer.localDescription else {
                                throw CancellationError()
                            }
                            let answerSDP = try await DashboardClient.postOffer(
                                serverURL: self.serverURL,
                                streamID: self.camera.id,
                                sdp: localOffer.sdp
                            )
                            let answer = RTCSessionDescription(type: .answer, sdp: answerSDP)
                            peer.setRemoteDescription(answer) { [weak self] error in
                                Task { @MainActor [weak self] in
                                    guard let self, !self.isStopped, let error else { return }
                                    self.status = error.localizedDescription
                                }
                            }
                        } catch is CancellationError {
                            return
                        } catch {
                            self.status = error.localizedDescription
                        }
                    }
                }
            }
        }
    }

    private func waitForICEGathering(_ peer: RTCPeerConnection) async throws {
        if peer.iceGatheringState == .complete { return }

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            gatheringContinuation = continuation
            gatheringTimeout = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                guard !Task.isCancelled else { return }
                self?.finishGathering(.failure(
                    DashboardError.message("Timed out gathering WebRTC candidates. Check Tailscale and UDP port 8190.")
                ))
            }
        }
    }

    private func finishGathering(_ result: Result<Void, Error>) {
        guard let continuation = gatheringContinuation else { return }
        gatheringContinuation = nil
        gatheringTimeout?.cancel()
        gatheringTimeout = nil
        continuation.resume(with: result)
    }
}

extension WHEPPlayer: RTCPeerConnectionDelegate {
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChangeSignalingState stateChanged: RTCSignalingState) {}

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        guard let track = stream.videoTracks.first else { return }
        Task { @MainActor [weak self] in self?.videoTrack = track }
    }

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    nonisolated func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        Task { @MainActor [weak self] in
            guard let self, !self.isStopped else { return }
            switch newState {
            case .connected, .completed:
                self.status = "Live"
            case .failed:
                self.status = "Video connection failed. Check Tailscale and UDP port 8190."
            case .disconnected:
                self.status = "Video disconnected. Reopen this camera to reconnect."
            default:
                self.status = "Connecting to camera…"
            }
        }
    }

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
        guard newState == .complete else { return }
        Task { @MainActor [weak self] in self?.finishGathering(.success(())) }
    }

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didStartReceivingOn transceiver: RTCRtpTransceiver) {
        guard let track = transceiver.receiver.track as? RTCVideoTrack else { return }
        Task { @MainActor [weak self] in self?.videoTrack = track }
    }

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didAdd receiver: RTCRtpReceiver, streams: [RTCMediaStream]) {
        guard let track = receiver.track as? RTCVideoTrack else { return }
        Task { @MainActor [weak self] in self?.videoTrack = track }
    }

    nonisolated func peerConnection(_ peerConnection: RTCPeerConnection, didRemove receiver: RTCRtpReceiver) {}
}

struct CameraPlaybackView: View {
    let serverURL: URL
    let camera: Camera
    @StateObject private var player: WHEPPlayer

    init(serverURL: URL, camera: Camera) {
        self.serverURL = serverURL
        self.camera = camera
        _player = StateObject(wrappedValue: WHEPPlayer(serverURL: serverURL, camera: camera))
    }

    var body: some View {
        VStack(spacing: 12) {
            VideoRendererView(track: player.videoTrack)
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .background(.black)
                .clipShape(RoundedRectangle(cornerRadius: 12))

            Label(player.status, systemImage: player.status == "Live" ? "dot.radiowaves.left.and.right" : "video")
                .font(.subheadline)
                .foregroundStyle(player.status == "Live" ? .green : .secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text("\(camera.ip) · CH\(camera.channel) · \(camera.stream.uppercased())")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            Spacer()
        }
        .padding()
        .background(Color(uiColor: .systemGroupedBackground))
        .onDisappear { player.stop() }
    }
}

private struct VideoRendererView: UIViewRepresentable {
    let track: RTCVideoTrack?

    func makeUIView(context: Context) -> RTCMTLVideoView {
        let view = RTCMTLVideoView(frame: .zero)
        view.videoContentMode = .scaleAspectFit
        return view
    }

    func updateUIView(_ view: RTCMTLVideoView, context: Context) {
        if context.coordinator.track !== track {
            context.coordinator.track?.remove(view)
            context.coordinator.track = track
            track?.add(view)
        }
    }

    static func dismantleUIView(_ view: RTCMTLVideoView, coordinator: Coordinator) {
        coordinator.track?.remove(view)
        coordinator.track = nil
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator {
        var track: RTCVideoTrack?
    }
}
