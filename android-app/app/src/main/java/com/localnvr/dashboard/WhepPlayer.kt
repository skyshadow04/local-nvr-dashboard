package com.localnvr.dashboard

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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
    val isLive = status == "Live"
    val isError = status.contains("failed") || status.contains("Could not") || status.contains("disconnected")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (isLive) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF090D16)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            // WebRTC Video Feed
            AndroidView(
                factory = { renderer },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            )

            // Top Status Bar Overlay (Live Indicator / Error Badge)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                // Top Row: Camera Title & Live Badge
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when {
                            isLive -> Color(0xFF10B981)
                            isError -> Color(0xFFEF4444)
                            else -> Color(0xFFF59E0B)
                        },
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isLive -> "LIVE"
                                        isError -> "OFFLINE"
                                        else -> "CONNECTING"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${camera.stream.uppercase()} STREAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Center Overlay when Connecting or Error
            AnimatedVisibility(
                visible = !isLive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isError) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
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
            observer
        )

        if (peer == null) {
            status = "Could not create a WebRTC connection."
        } else {
            val transceiver = peer.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
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
