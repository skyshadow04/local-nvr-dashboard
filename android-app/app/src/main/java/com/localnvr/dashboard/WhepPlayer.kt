package com.localnvr.dashboard

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun WhepPlayer(serverUrl: String, camera: Camera, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val factory = remember { WebRtcRuntime.initialize(context.applicationContext) }
    val renderer = remember(serverUrl, camera.id) {
        SurfaceViewRenderer(context).apply {
            init(WebRtcRuntime.eglContext(), null)
            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
        }
    }
    var status by remember(serverUrl, camera.id) { mutableStateOf("Connecting to camera…") }

    AndroidView(
        factory = { renderer },
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        update = { view ->
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111827)),
        contentAlignment = Alignment.Center,
    ) {
        Text(status, color = Color.White)
    }

    DisposableEffect(serverUrl, camera.id, renderer, factory) {
        val active = AtomicBoolean(true)
        val remoteTrack = AtomicReference<VideoTrack?>(null)
        val gatheringComplete = CompletableDeferred<Unit>()
        val mainHandler = Handler(Looper.getMainLooper())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                val message = when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> "Live"
                    PeerConnection.IceConnectionState.FAILED -> "Video connection failed. Check Tailscale and UDP 8190."
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Video disconnected. Reopen this camera to reconnect."
                    else -> "Connecting to camera…"
                }
                mainHandler.post {
                    if (active.get()) status = message
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    gatheringComplete.complete(Unit)
                }
            }

            override fun onIceCandidate(candidate: IceCandidate) = Unit

            override fun onAddStream(stream: MediaStream) {
                stream.videoTracks.firstOrNull()?.let(::attachTrack)
            }

            override fun onRemoveStream(stream: MediaStream) {
                if (active.get()) {
                    stream.videoTracks.forEach { it.removeSink(renderer) }
                    remoteTrack.set(null)
                }
            }

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.let(::attachTrack)
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                (transceiver.receiver.track() as? VideoTrack)?.let(::attachTrack)
            }

            private fun attachTrack(track: VideoTrack) {
                if (!active.get()) return
                remoteTrack.getAndSet(track)?.removeSink(renderer)
                track.addSink(renderer)
            }
        }

        val peer = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(emptyList()),
            observer,
        )

        if (peer == null) {
            status = "Could not create a WebRTC connection."
        } else {
            val transceiver = peer.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )

            if (transceiver == null) {
                status = "Could not create a video receiver."
            } else scope.launch {
                try {
                    val offer = createOffer(peer)
                    setLocalDescription(peer, offer)
                    withTimeout(15_000) { gatheringComplete.await() }
                    val gatheredOffer = peer.localDescription
                        ?: throw IllegalStateException("WebRTC did not produce a local offer.")
                    val answerSdp = DashboardApi.postOffer(serverUrl, camera.id, gatheredOffer.description)
                    setRemoteDescription(peer, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
                } catch (error: Exception) {
                    if (active.get()) {
                        status = error.message ?: "Could not start camera video."
                    }
                }
            }
        }

        onDispose {
            active.set(false)
            scope.cancel()
            remoteTrack.getAndSet(null)?.removeSink(renderer)
            peer?.close()
            renderer.release()
        }
    }
}

private suspend fun createOffer(peer: PeerConnection): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        peer.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                if (continuation.isActive) continuation.resume(description)
            }

            override fun onSetSuccess() = Unit

            override fun onCreateFailure(error: String) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            }

            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
    suspendCancellableCoroutine<Unit> { continuation ->
        peer.setLocalDescription(object : SdpObserver {
            override fun onSetSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onSetFailure(error: String) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            }

            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }, description)
    }
}

private suspend fun setRemoteDescription(peer: PeerConnection, description: SessionDescription) {
    suspendCancellableCoroutine<Unit> { continuation ->
        peer.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onSetFailure(error: String) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            }

            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }, description)
    }
}
