package com.localnvr.dashboard

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

object WebRtcRuntime {
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null

    @Synchronized
    fun initialize(context: Context): PeerConnectionFactory {
        peerConnectionFactory?.let { return it }

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        val egl = EglBase.create()
        eglBase = egl
        val factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory
        return factory
    }

    fun eglContext(): EglBase.Context =
        checkNotNull(eglBase) { "WebRTC must be initialized before creating a video view." }.eglBaseContext
}
