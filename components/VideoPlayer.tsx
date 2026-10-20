'use client';
import { useEffect, useRef, useState } from 'react';

interface VideoPlayerProps {
  streamId: string;
  cameraName: string;
  singleView?: boolean;
}

export default function VideoPlayer({ streamId, cameraName, singleView = false }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const pcRef = useRef<RTCPeerConnection | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let pc: RTCPeerConnection | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let reconnectAttempt = 0;

    function scheduleReconnect() {
      if (!active || reconnectTimer) return;

      const delay = Math.min(1000 * 2 ** reconnectAttempt, 10000);
      reconnectAttempt += 1;
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        void initWebRTC();
      }, delay);
    }

    async function initWebRTC() {
      try {
        if (!active) return;

        pc?.close();
        pc = new RTCPeerConnection({
          iceServers: [],
        });
        pcRef.current = pc;

        pc.onconnectionstatechange = () => {
          if (pc?.connectionState === 'connected') {
            reconnectAttempt = 0;
            setError(null);
          } else if (pc?.connectionState === 'failed' || pc?.connectionState === 'disconnected') {
            scheduleReconnect();
          }
        };

        pc.ontrack = (event) => {
          if (videoRef.current && active) {
            const [stream] = event.streams;
            if (!stream) return;

            videoRef.current.srcObject = stream;
            videoRef.current.playsInline = true;
            videoRef.current.muted = true;
            void videoRef.current.play().catch(() => undefined);
          }
        };

        pc.addTransceiver('video', { direction: 'recvonly' });

        const offer = await pc.createOffer({ offerToReceiveVideo: true });
        await pc.setLocalDescription(offer);

        const whepUrl = `/api/whep/${encodeURIComponent(streamId)}`;
        const res = await fetch(whepUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/sdp' },
          body: pc.localDescription?.sdp ?? '',
        });

        if (!res.ok) {
          const serverMsg = await res.text().catch(() => '');
          const normalizedServerMsg = serverMsg.toLowerCase();
          if (res.status === 400 && normalizedServerMsg.includes('codecs not supported')) {
            throw new Error(
              'This camera is publishing H.265/HEVC, which browser WebRTC cannot play. Set this camera/NVR stream to H.264, then reconnect it.'
            );
          }
          if (res.status === 400 && normalizedServerMsg.includes('source of path') && normalizedServerMsg.includes('timed out')) {
            throw new Error(
              'MediaMTX could not get video from the NVR. Verify the NVR username/password, channel number, and selected stream profile. The NVR must provide an H.264 stream, and the Docker host must be able to reach its RTSP service on TCP port 554.'
            );
          }
          throw new Error(
            `MediaMTX rejected peer handshake (${res.status})${serverMsg ? `: ${serverMsg}` : ''}. Check that the stream path exists and MediaMTX WebRTC is running on port 8889.`
          );
        }

        const answerSdp = await res.text();
        if (active && pc.signalingState !== 'closed') {
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: answerSdp }));
        }
      } catch (err: unknown) {
        if (active) {
          setError(err instanceof Error ? err.message : 'Stream connection error');
          scheduleReconnect();
        }
      }
    }

    void initWebRTC();

    return () => {
      active = false;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (pc) {
        pc.close();
      }
      if (pcRef.current) {
        pcRef.current.close();
        pcRef.current = null;
      }
      if (videoRef.current) {
        videoRef.current.pause();
        videoRef.current.srcObject = null;
      }
    };
  }, [streamId]);

  return (
    <div className={`relative bg-black rounded-lg overflow-hidden border border-slate-800 ${singleView ? 'h-full w-full' : 'aspect-video'}`}>
      <div className="absolute top-2 left-2 z-10 bg-black/60 text-white text-xs px-2 py-1 rounded">
        {cameraName}
      </div>
      {error ? (
        <div className="flex h-full items-center justify-center text-red-500 text-sm p-4 text-center">
          {error}
        </div>
      ) : (
        <video
          ref={videoRef}
          autoPlay
          muted
          controls
          playsInline
          preload="auto"
          className={`w-full h-full ${singleView ? 'object-contain' : 'object-cover'}`}
        />
      )}
    </div>
  );
}
