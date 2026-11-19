package com.localnvr.dashboard

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
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
fun WhepPlayer(
    serverUrl: String,
    camera: Camera,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val factory = remember { WebRtcRuntime.initialize(context.applicationContext) }

    var retryTrigger by remember(serverUrl, camera.id) { mutableIntStateOf(0) }
    var status by remember(serverUrl, camera.id, retryTrigger) { mutableStateOf("Connecting to camera…") }
    var isAppPaused by remember { mutableStateOf(false) }

    val isLive = status == "Live"
    val isError = status.contains("failed") || status.contains("Could not") || status.contains("disconnected")

    // Observe app lifecycle to reload when resuming from background/lock if disconnected
    DisposableEffect(lifecycleOwner, serverUrl, camera.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isAppPaused = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isAppPaused && status != "Live") {
                        retryTrigger++
                    }
                    isAppPaused = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(if (isCompact) 10.dp else 16.dp))
            .border(
                width = 1.dp,
                color = if (isLive) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp)
            ),
        shape = RoundedCornerShape(if (isCompact) 10.dp else 16.dp),
        color = Color(0xFF090D16)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            // Key scope ensures a fresh SurfaceViewRenderer and AndroidView are created together on reconnect
            key(serverUrl, camera.id, retryTrigger) {
                val renderer = remember {
                    SurfaceViewRenderer(context).apply {
                        init(WebRtcRuntime.eglContext(), null)
                        setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                    }
                }

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

                DisposableEffect(Unit) {
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
                                PeerConnection.IceConnectionState.FAILED -> "Video connection failed. Retrying in 2s…"
                                PeerConnection.IceConnectionState.DISCONNECTED -> "Video disconnected. Retrying in 2s…"
                                else -> "Connecting to camera…"
                            }
                            mainHandler.post {
                                if (active.get()) {
                                    status = message
                                    if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.DISCONNECTED) {
                                        scope.launch {
                                            delay(2_000)
                                            if (active.get()) {
                                                retryTrigger++
                                            }
                                        }
                                    }
                                }
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
                        status = "Could not create a WebRTC connection. Retrying in 3s…"
                        scope.launch {
                            delay(3_000)
                            if (active.get()) {
                                retryTrigger++
                            }
                        }
                    } else {
                        val transceiver = peer.addTransceiver(
                            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                        )

                        if (transceiver == null) {
                            status = "Could not create a video receiver. Retrying in 3s…"
                            scope.launch {
                                delay(3_000)
                                if (active.get()) {
                                    retryTrigger++
                                }
                            }
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
                                    val errReason = error.message ?: "Could not start camera video."
                                    status = "$errReason Retrying in 3s…"
                                    scope.launch {
                                        delay(3_000)
                                        if (active.get()) {
                                            retryTrigger++
                                        }
                                    }
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

            // Sleek, minimal status indicator at top-left (No dark gradient blocking the camera!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCompact) 6.dp else 10.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when {
                            isLive -> Color(0xFF10B981).copy(alpha = 0.9f)
                            isError -> Color(0xFFEF4444).copy(alpha = 0.9f)
                            else -> Color(0xFFF59E0B).copy(alpha = 0.9f)
                        },
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 5.dp else 8.dp,
                                vertical = if (isCompact) 2.dp else 4.dp
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCompact) 5.dp else 6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                if (!isCompact || !isLive) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            isLive -> "LIVE"
                                            isError -> "OFFLINE"
                                            else -> "CONNECTING"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isCompact) 9.sp else 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (!isCompact) {
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
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable(enabled = isError) { retryTrigger++ }
                        .padding(16.dp),
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
                                modifier = Modifier.size(if (isCompact) 28.dp else 40.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(if (isCompact) 26.dp else 36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = if (isCompact) 11.sp else 13.sp,
                            maxLines = 2
                        )
                        if (isError) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Tap to Reload",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = if (isCompact) 10.sp else 11.sp,
                                    modifier = Modifier.padding(
                                        horizontal = if (isCompact) 8.dp else 12.dp,
                                        vertical = if (isCompact) 4.dp else 6.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }
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
