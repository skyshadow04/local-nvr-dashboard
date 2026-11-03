'use client';
import { useEffect, useRef, useState } from 'react';

interface VideoPlayerProps {
  streamId: string;
  cameraName: string;
  singleView?: boolean;
}

type PlaybackState = 'connecting' | 'waiting' | 'live' | 'paused' | 'reconnecting';

const STALL_TIMEOUT_MS = 20000;

export default function VideoPlayer({ streamId, cameraName, singleView = false }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const retryNowRef = useRef<(() => void) | null>(null);
  const [playbackState, setPlaybackState] = useState<PlaybackState>('connecting');
  const [statusMessage, setStatusMessage] = useState('Connecting to camera…');

  useEffect(() => {
    let active = true;
    let peerConnection: RTCPeerConnection | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let disconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let attemptTimer: ReturnType<typeof setTimeout> | null = null;
    let watchdogTimer: ReturnType<typeof setInterval> | null = null;
    let attemptId = 0;
    let reconnectAttempt = 0;
    let connectedAt: number | null = null;
    let lastVideoProgressAt: number | null = null;
    let lastVideoCurrentTime = -1;
    let videoIsLive = false;

    function clearAttemptTimers() {
      if (disconnectTimer) clearTimeout(disconnectTimer);
      if (attemptTimer) clearTimeout(attemptTimer);
      if (watchdogTimer) clearInterval(watchdogTimer);
      disconnectTimer = null;
      attemptTimer = null;
      watchdogTimer = null;
    }

    function clearVideo() {
      const video = videoRef.current;
      if (!video) return;
      video.pause();
      video.srcObject = null;
    }

    function scheduleReconnect(message: string, requestedDelay?: number) {
      if (!active || reconnectTimer) return;

      attemptId += 1;
      clearAttemptTimers();
      peerConnection?.close();
      peerConnection = null;
      clearVideo();

      const delay = requestedDelay ?? Math.min(1000 * 2 ** reconnectAttempt, 15000);
      reconnectAttempt += 1;
      setPlaybackState('reconnecting');
      setStatusMessage(`${message} Retrying in ${Math.ceil(delay / 1000)}s…`);
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        void initWebRTC();
      }, delay);
    }

    retryNowRef.current = () => scheduleReconnect('Manual reconnect requested.', 0);

    function initWebRTC() {
      if (!active) return;

      const currentAttempt = ++attemptId;
      const pc = new RTCPeerConnection({ iceServers: [] });
      const isCurrentAttempt = () => (
        active && attemptId === currentAttempt && peerConnection === pc
      );
      connectedAt = null;
      lastVideoProgressAt = null;
      lastVideoCurrentTime = -1;
      videoIsLive = false;
      setPlaybackState(reconnectAttempt > 0 ? 'reconnecting' : 'connecting');
      setStatusMessage(reconnectAttempt > 0 ? 'Reconnecting to camera…' : 'Connecting to camera…');

      peerConnection = pc;
      pc.addTransceiver('video', { direction: 'recvonly' });

      attemptTimer = setTimeout(() => {
        if (isCurrentAttempt()) {
          scheduleReconnect('Camera did not start in time.');
        }
      }, 30000);

      pc.onconnectionstatechange = () => {
        if (!isCurrentAttempt()) return;

        if (pc.connectionState === 'connected') {
          if (disconnectTimer) clearTimeout(disconnectTimer);
          disconnectTimer = null;
          connectedAt = Date.now();
          lastVideoProgressAt = connectedAt;
          if (attemptTimer) clearTimeout(attemptTimer);
          attemptTimer = null;
          const video = videoRef.current;
          if (videoIsLive && video && !video.paused && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
            lastVideoCurrentTime = video.currentTime;
            setPlaybackState('live');
            setStatusMessage('Live');
          } else {
            setPlaybackState('waiting');
            setStatusMessage('Connected. Waiting for video…');
          }
          return;
        }

        if (pc.connectionState === 'failed') {
          scheduleReconnect('Video connection failed.');
        } else if (pc.connectionState === 'disconnected') {
          setPlaybackState('waiting');
          setStatusMessage('Connection interrupted. Checking camera…');
          disconnectTimer = setTimeout(() => {
            if (isCurrentAttempt() && pc.connectionState === 'disconnected') {
              scheduleReconnect('Camera connection was lost.');
            }
          }, 5000);
        }
      };

      pc.ontrack = (event) => {
        if (!isCurrentAttempt()) return;

        const video = videoRef.current;
        if (!video) return;
        video.srcObject = event.streams[0] ?? new MediaStream([event.track]);
        video.playsInline = true;
        video.muted = true;
        setPlaybackState('waiting');
        setStatusMessage('Camera connected. Waiting for the first video frame…');
        void video.play().catch(() => {
          if (isCurrentAttempt()) {
            setPlaybackState('waiting');
            setStatusMessage('Waiting for video playback…');
          }
        });
      };

      watchdogTimer = setInterval(() => {
        if (!isCurrentAttempt() || pc.connectionState !== 'connected') return;

        const video = videoRef.current;
        if (!video || video.paused) return;

        const now = Date.now();
        if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.currentTime > 0) {
          if (video.currentTime > lastVideoCurrentTime + 0.05) {
            lastVideoProgressAt = now;
            lastVideoCurrentTime = video.currentTime;
            if (!videoIsLive) {
              videoIsLive = true;
              setPlaybackState('live');
              setStatusMessage('Live');
            }
          }
        }

        const lastProgress = lastVideoProgressAt ?? connectedAt;
        if (lastProgress !== null && now - lastProgress > STALL_TIMEOUT_MS) {
          scheduleReconnect('No video frames received. Camera may be stalled.');
        }
      }, 3000);

      void (async () => {
        try {
          const offer = await pc.createOffer();
          if (!isCurrentAttempt()) return;
          await pc.setLocalDescription(offer);
          await waitForIceGatheringComplete(pc);
          if (!isCurrentAttempt()) return;

          const abortController = new AbortController();
          const requestTimer = setTimeout(() => abortController.abort(), 15000);
          let response: Response;
          try {
            response = await fetch(`/api/whep/${encodeURIComponent(streamId)}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/sdp' },
              body: pc.localDescription?.sdp ?? '',
              signal: abortController.signal,
            });
          } finally {
            clearTimeout(requestTimer);
          }

          if (!isCurrentAttempt()) return;
          if (!response.ok) {
            const serverMessage = await response.text().catch(() => '');
            const normalizedMessage = serverMessage.toLowerCase();
            if (response.status === 400 && normalizedMessage.includes('codecs not supported')) {
              throw new Error(
                'This camera is publishing H.265/HEVC, which browser WebRTC cannot play. Set this camera/NVR stream to H.264, then reconnect it.'
              );
            }
            if (response.status === 400 && normalizedMessage.includes('source of path') && normalizedMessage.includes('timed out')) {
              throw new Error(
                'MediaMTX could not get video from the NVR. Verify the NVR username/password, channel number, and selected stream profile. The NVR must provide an H.264 stream, and the Docker host must be able to reach its RTSP service on TCP port 554.'
              );
            }
            throw new Error(
              `MediaMTX rejected peer handshake (${response.status})${serverMessage ? `: ${serverMessage}` : ''}. Check that the stream path exists and MediaMTX WebRTC is running on port 8889.`
            );
          }

          const answerSdp = await response.text();
          if (isCurrentAttempt() && pc.signalingState !== 'closed') {
            await pc.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: answerSdp }));
          }
        } catch (error: unknown) {
          if (isCurrentAttempt()) {
            scheduleReconnect(error instanceof Error ? error.message : 'Stream connection error.');
          }
        }
      })();
    }

    function waitForIceGatheringComplete(pc: RTCPeerConnection): Promise<void> {
      if (pc.iceGatheringState === 'complete') return Promise.resolve();

      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
          cleanup();
          reject(new Error('Timed out gathering WebRTC network candidates. Check that UDP port 8190 is reachable.'));
        }, 10000);

        const handleGatheringStateChange = () => {
          if (pc.iceGatheringState === 'complete') {
            cleanup();
            resolve();
          }
        };

        const cleanup = () => {
          clearTimeout(timeout);
          pc.removeEventListener('icegatheringstatechange', handleGatheringStateChange);
        };

        pc.addEventListener('icegatheringstatechange', handleGatheringStateChange);
        handleGatheringStateChange();
      });
    }

    const handlePlaying = () => {
      if (!active) return;
      lastVideoProgressAt = Date.now();
      videoIsLive = true;
      reconnectAttempt = 0;
      setPlaybackState('live');
      setStatusMessage('Live');
    };

    const handleWaiting = () => {
      if (!active || videoRef.current?.paused) return;
      videoIsLive = false;
      setPlaybackState('waiting');
      setStatusMessage('Video buffering. Checking camera…');
    };

    const handlePause = () => {
      if (!active || !videoRef.current?.srcObject) return;
      videoIsLive = false;
      setPlaybackState('paused');
      setStatusMessage('Playback paused');
    };

    const video = videoRef.current;
    video?.addEventListener('playing', handlePlaying);
    video?.addEventListener('waiting', handleWaiting);
    video?.addEventListener('stalled', handleWaiting);
    video?.addEventListener('pause', handlePause);

    void initWebRTC();

    return () => {
      active = false;
      attemptId += 1;
      retryNowRef.current = null;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      clearAttemptTimers();
      video?.removeEventListener('playing', handlePlaying);
      video?.removeEventListener('waiting', handleWaiting);
      video?.removeEventListener('stalled', handleWaiting);
      video?.removeEventListener('pause', handlePause);
      peerConnection?.close();
      peerConnection = null;
      clearVideo();
    };
  }, [streamId]);

  const isLive = playbackState === 'live';
  const isPaused = playbackState === 'paused';
  const statusLabel = isLive ? 'Live' : isPaused ? 'Paused' : playbackState === 'reconnecting' ? 'Reconnecting' : 'Loading';

  return (
    <div className={`relative bg-black rounded-lg overflow-hidden border border-slate-800 ${singleView ? 'h-full w-full' : 'aspect-video'}`}>
      <video
        ref={videoRef}
        autoPlay
        muted
        controls
        playsInline
        preload="auto"
        className={`w-full h-full ${singleView ? 'object-contain' : 'object-cover'}`}
      />
      <div className="absolute top-2 left-2 z-10 flex items-center gap-2 rounded bg-black/70 px-2 py-1 text-xs text-white">
        <span>{cameraName}</span>
        <span className={`h-2 w-2 rounded-full ${isLive ? 'bg-green-400' : isPaused ? 'bg-amber-400' : 'bg-blue-400 animate-pulse'}`} />
        <span>{statusLabel}</span>
      </div>
      {!isLive && !isPaused && (
        <div
          className="absolute inset-0 z-[5] flex flex-col items-center justify-center gap-3 bg-slate-950/85 p-4 text-center text-sm text-slate-200"
          role="status"
          aria-live="polite"
        >
          <span className="h-7 w-7 animate-spin rounded-full border-2 border-slate-600 border-t-blue-400" aria-hidden="true" />
          <span>{statusMessage}</span>
          <button
            type="button"
            onClick={() => retryNowRef.current?.()}
            className="rounded border border-slate-600 px-3 py-1.5 text-xs hover:border-blue-400 hover:text-white"
          >
            Reconnect now
          </button>
        </div>
      )}
    </div>
  );
}
