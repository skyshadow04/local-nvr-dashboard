'use client';
import { useEffect, useRef, useState } from 'react';
import VideoPlayer from '@/components/VideoPlayer';

interface Camera {
  id: string;
  name: string;
  type: 'hikvision' | 'uniview';
  ip: string;
  channel: number;
  stream: 'main' | 'sub';
}

export default function Home() {
  const dashboardRef = useRef<HTMLElement>(null);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [editingCameraId, setEditingCameraId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [fullscreenCameraIndex, setFullscreenCameraIndex] = useState(0);
  const [viewMode, setViewMode] = useState<'auto' | '2x2' | '3x3' | '4x4'>('auto');
  const [scanSummary, setScanSummary] = useState('No streams connected yet.');

  // Form states
  const [type, setType] = useState<'hikvision' | 'uniview'>('hikvision');
  const [ip, setIp] = useState('10.39.1.99');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [startChannel, setStartChannel] = useState(1);
  const [endChannel, setEndChannel] = useState(16);
  const [stream, setStream] = useState<'main' | 'sub'>('sub'); // Default to sub-stream for grid layouts
  const activeFullscreenCameraIndex = Math.min(fullscreenCameraIndex, Math.max(cameras.length - 1, 0));
  const activeFullscreenCamera = cameras[activeFullscreenCameraIndex];

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(document.fullscreenElement === dashboardRef.current);
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  useEffect(() => {
    if (!isFullscreen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (cameras.length === 0) return;
      if (event.target instanceof HTMLElement && event.target.closest('button, input, select, textarea, video')) {
        return;
      }

      if (event.key === 'ArrowLeft') {
        setFullscreenCameraIndex(Math.max(activeFullscreenCameraIndex - 1, 0));
      } else if (event.key === 'ArrowRight') {
        setFullscreenCameraIndex(Math.min(activeFullscreenCameraIndex + 1, cameras.length - 1));
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isFullscreen, cameras.length, activeFullscreenCameraIndex]);

  useEffect(() => {
    let active = true;

    const refreshCameras = async () => {
      try {
        const response = await fetch('/api/stream', { cache: 'no-store' });
        if (!response.ok) throw new Error('Could not load shared camera views.');
        const data = await response.json();
        if (active) {
          setCameras((current) => {
            const currentNames = new Map(current.map((camera) => [camera.id, camera.name]));
            return (data.cameras || []).map((camera: Camera) => ({
              ...camera,
              name: camera.id === editingCameraId ? currentNames.get(camera.id) || camera.name : camera.name,
            }));
          });
        }
      } catch (error: unknown) {
        if (active && cameras.length === 0) {
          setScanSummary(error instanceof Error ? error.message : 'Could not load shared camera views.');
        }
      }
    };

    void refreshCameras();
    const refreshTimer = setInterval(() => void refreshCameras(), 5000);

    return () => {
      active = false;
      clearInterval(refreshTimer);
    };
  }, [cameras.length, editingCameraId]);

  const updateCameraName = (cameraId: string, name: string) => {
    setCameras((current) => current.map((camera) => (
      camera.id === cameraId ? { ...camera, name } : camera
    )));
  };

  const saveCameraName = async (cameraId: string, name: string) => {
    try {
      const response = await fetch('/api/stream', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: cameraId, name }),
      });
      if (!response.ok) throw new Error('Could not save camera name.');
      setEditingCameraId(null);
      setScanSummary('Camera name saved.');
    } catch {
      setScanSummary('Could not save camera name.');
    }
  };

  const toggleFullscreen = async () => {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
      return;
    }

    await dashboardRef.current?.requestFullscreen();
  };

  const removeCamera = async (cameraId: string) => {
    try {
      const res = await fetch('/api/stream', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: cameraId }),
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok && data?.error) {
        console.warn('Camera removal failed on backend:', data.error);
      }
    } catch (error) {
      console.warn('Camera removal request failed:', error);
    } finally {
      setCameras((prev) => prev.filter((cam) => cam.id !== cameraId));
      setScanSummary('Camera removed from view.');
    }
  };

  const resolvedColumns = (() => {
    if (cameras.length === 0) return 1;
    if (viewMode === 'auto') {
      return Math.min(Math.max(Math.ceil(Math.sqrt(cameras.length)), 1), 4);
    }
    return { '2x2': 2, '3x3': 3, '4x4': 4 }[viewMode] ?? 1;
  })();

  const resolvedRows = Math.ceil(cameras.length / resolvedColumns) || 1;

  const addCamera = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await fetch('/api/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          autoDiscover: { type, ipPattern: ip, username, password, startChannel, endChannel, stream },
        }),
      });

      const data = await res.json();
      if (res.ok && data.success) {
        const discovered: Camera[] = data.cameras || [];
        setCameras((prev) => {
          const existingKeys = new Set(
            prev.map((camera) => `${camera.ip.trim().toLowerCase()}:${camera.channel}:${camera.stream}`)
          );
          const newCameras = discovered.filter((camera) => {
            const key = `${camera.ip.trim().toLowerCase()}:${camera.channel}:${camera.stream}`;
            if (existingKeys.has(key)) return false;
            existingKeys.add(key);
            return true;
          });
          const next = [...prev, ...newCameras];
          setScanSummary(newCameras.length
            ? `${newCameras.length} new camera${newCameras.length === 1 ? '' : 's'} found. Connecting streams...`
            : discovered.length
              ? 'All discovered cameras are already open.'
              : 'No camera streams found in that channel range.');
          return next;
        });
      } else {
        alert(data.error || 'Failed to discover camera streams.');
      }
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Error establishing stream channel configuration.');
    } finally {
      setLoading(false);
    }
  };

  const clearAllCameras = async () => {
    const current = [...cameras];

    for (const camera of current) {
      try {
        await fetch('/api/stream', {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ id: camera.id }),
        });
      } catch (error) {
        console.warn(`Failed to clear camera ${camera.id}:`, error);
      }
    }

    setCameras([]);
    setScanSummary('All connected cameras cleared.');
  };

  return (
    <main
      ref={dashboardRef}
      className={`min-h-screen h-screen bg-slate-950 text-slate-100 overflow-hidden ${isFullscreen ? 'p-3' : 'p-4 md:p-6'}`}
    >
      {!isFullscreen && <div className="flex items-center justify-between gap-3 mb-4">
        <h1 className="text-2xl font-bold tracking-tight">Centralized Local NVR Hub</h1>
        <div className="flex items-center gap-2">
          {(['auto', '2x2', '3x3', '4x4'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setViewMode(mode)}
              className={`px-2.5 py-1.5 rounded text-xs font-medium border transition ${viewMode === mode ? 'bg-blue-600 border-blue-500 text-white' : 'bg-slate-800 border-slate-700 text-slate-300 hover:border-slate-500'}`}
            >
              {mode === 'auto' ? 'Auto' : mode}
            </button>
          ))}
          <button
            type="button"
            onClick={clearAllCameras}
            className="px-2.5 py-1.5 rounded text-xs font-medium border border-slate-700 bg-slate-800 text-slate-300 hover:border-slate-500"
          >
            Clear
          </button>
          <button
            type="button"
            onClick={() => void toggleFullscreen()}
            disabled={cameras.length === 0}
            className="px-2.5 py-1.5 rounded text-xs font-medium border border-slate-700 bg-slate-800 text-slate-300 hover:border-slate-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Full screen
          </button>
        </div>
      </div>}

      <div className={`${isFullscreen ? 'h-full' : 'grid grid-cols-1 gap-4 lg:grid-cols-4 h-[calc(100vh-88px)]'}`}>
        {/* Left Control Panel: Add NVR Feeds */}
        {!isFullscreen && <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl h-fit max-h-full overflow-y-auto">
          <h2 className="text-lg font-semibold mb-4 text-slate-300">Connect NVR Cameras</h2>
          <form onSubmit={addCamera} className="space-y-3 text-sm">
            <div>
              <label className="block mb-1 text-slate-400">Manufacturer Type</label>
              <select className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={type} onChange={(e) => setType(e.target.value as 'hikvision' | 'uniview')}>
                <option value="hikvision">Hikvision (ISAPI Routing)</option>
                <option value="uniview">Uniview (EZUnicast Routing)</option>
              </select>
            </div>
            <div>
              <label className="block mb-1 text-slate-400">NVR IP Address</label>
              <input type="text" className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={ip} onChange={(e) => setIp(e.target.value)} required />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block mb-1 text-slate-400">Username</label>
                <input type="text" className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={username} onChange={(e) => setUsername(e.target.value)} required />
              </div>
              <div>
                <label className="block mb-1 text-slate-400">Password</label>
                <input type="password" className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={password} onChange={(e) => setPassword(e.target.value)} required />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block mb-1 text-slate-400">NVR Channel ID</label>
                <input type="number" min="1" max={endChannel} className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={startChannel} onChange={(e) => setStartChannel(Number(e.target.value))} required />
              </div>
              <div>
                <label className="block mb-1 text-slate-400">Last Channel</label>
                <input type="number" min={startChannel} max="64" className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={endChannel} onChange={(e) => setEndChannel(Number(e.target.value))} required />
              </div>
            </div>
            <div>
              <label className="block mb-1 text-slate-400">Stream Profile</label>
              <select className="w-full bg-slate-800 rounded p-2 border border-slate-700 text-white" value={stream} onChange={(e) => setStream(e.target.value as 'main' | 'sub')}>
                <option value="sub">Sub-stream (Smooth Grid)</option>
                <option value="main">Main-stream (HD Resolution)</option>
              </select>
            </div>
            <button type="submit" disabled={loading} className="w-full mt-4 bg-blue-600 hover:bg-blue-500 disabled:bg-blue-800 text-white font-medium p-2 rounded transition">
              {loading ? 'Searching Channels...' : 'Connect All Cameras'}
            </button>
          </form>

          <div className="mt-6 border-t border-slate-800 pt-4">
            <div className="mb-3 flex items-center justify-between text-[11px] uppercase tracking-wide text-slate-400">
              <span>Open Cameras</span>
              <span>{cameras.length}</span>
            </div>
            <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
              {cameras.length === 0 ? (
                <div className="text-xs text-slate-500">No cameras open.</div>
              ) : (
                cameras.map((cam) => (
                  <div key={cam.id} className="flex items-center justify-between gap-2 rounded bg-slate-800/80 px-2 py-1.5 text-[11px] text-slate-200">
                    <div className="min-w-0">
                      <input
                        type="text"
                        value={cam.name}
                        maxLength={60}
                        aria-label={`Camera name for ${cam.ip} channel ${cam.channel}`}
                        onFocus={() => setEditingCameraId(cam.id)}
                        onChange={(event) => updateCameraName(cam.id, event.target.value)}
                        onBlur={() => void saveCameraName(cam.id, cam.name)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') event.currentTarget.blur();
                        }}
                        className="w-full min-w-0 truncate border-b border-transparent bg-transparent font-medium text-slate-100 outline-none focus:border-blue-400"
                      />
                      <div className="truncate text-slate-400">
                        {cam.type === 'hikvision' ? 'HIK' : 'UNV'} · {cam.ip} · CH {cam.channel}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeCamera(cam.id)}
                      className="rounded bg-slate-700 px-1.5 py-0.5 text-[10px] uppercase text-slate-200 hover:bg-red-600 transition"
                    >
                      Close
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>}

        {/* Right Panel: The Camera Matrix Grid Layout */}
        <div className={`${isFullscreen ? 'flex h-full min-h-0 flex-col' : 'lg:col-span-3 h-full'} overflow-hidden`}>
          {isFullscreen && (
            <button
              type="button"
              onClick={() => void toggleFullscreen()}
              className="absolute right-5 top-5 z-30 rounded border border-slate-700 bg-black/70 px-3 py-2 text-xs font-medium text-slate-200 hover:bg-red-600"
            >
              Exit full screen
            </button>
          )}
          <div className={`mb-3 flex items-center justify-between gap-3 text-xs text-slate-400 ${isFullscreen ? 'pr-32' : ''}`}>
            <span>{isFullscreen && activeFullscreenCamera ? activeFullscreenCamera.name : `${cameras.length} connected`}</span>
            {!isFullscreen && <span className="truncate text-right">{scanSummary}</span>}
          </div>

          {cameras.length === 0 ? (
            <div className="flex flex-col items-center justify-center border-2 border-dashed border-slate-800 rounded-xl h-[calc(100%-116px)] text-slate-500">
              <p>No streams connected yet.</p>
              <p className="text-xs mt-1">Configure an NVR source on the left to activate WebRTC grid rendering.</p>
            </div>
          ) : (
            isFullscreen ? (
              <div className="flex min-h-0 flex-1 flex-col gap-2 sm:gap-3">
                <div className="relative min-h-0 flex-1">
                  {activeFullscreenCamera && (
                    <VideoPlayer
                      key={activeFullscreenCamera.id}
                      streamId={activeFullscreenCamera.id}
                      cameraName={activeFullscreenCamera.name}
                      singleView
                    />
                  )}
                </div>
                <div className="flex shrink-0 items-center justify-center gap-2">
                  <button
                    type="button"
                    onClick={() => setFullscreenCameraIndex(Math.max(activeFullscreenCameraIndex - 1, 0))}
                    disabled={activeFullscreenCameraIndex === 0}
                    aria-label="Previous camera"
                    className="rounded border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Previous
                  </button>
                  <select
                    aria-label="Select fullscreen camera"
                    value={activeFullscreenCamera?.id ?? ''}
                    onChange={(event) => {
                      const nextIndex = cameras.findIndex((camera) => camera.id === event.target.value);
                      if (nextIndex >= 0) setFullscreenCameraIndex(nextIndex);
                    }}
                    className="max-w-[45vw] rounded border border-slate-700 bg-slate-900 px-2 py-2 text-sm text-slate-200"
                  >
                    {cameras.map((camera, index) => (
                      <option key={camera.id} value={camera.id}>
                        {index + 1}. {camera.name}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => setFullscreenCameraIndex(Math.min(activeFullscreenCameraIndex + 1, cameras.length - 1))}
                    disabled={activeFullscreenCameraIndex >= cameras.length - 1}
                    aria-label="Next camera"
                    className="rounded border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-200 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Next
                  </button>
                  <span className="hidden text-xs text-slate-400 sm:inline">
                    {activeFullscreenCameraIndex + 1} / {cameras.length}
                  </span>
                </div>
              </div>
            ) : (
            <div
              className="grid h-[calc(100%-116px)] gap-3"
              style={{
                gridTemplateColumns: `repeat(${resolvedColumns}, minmax(0, 1fr))`,
                gridTemplateRows: `repeat(${resolvedRows}, minmax(0, 1fr))`,
              }}
            >
              {cameras.map((cam) => (
                <div key={cam.id} className="relative min-h-0 group">
                  <button
                    type="button"
                    onClick={() => removeCamera(cam.id)}
                    className="absolute right-2 top-2 z-20 flex h-6 w-6 items-center justify-center rounded-full bg-black/70 text-xs text-white border border-slate-700 hover:bg-red-600 transition"
                    aria-label={`Remove ${cam.name}`}
                  >
                    ×
                  </button>
                  <VideoPlayer streamId={cam.id} cameraName={cam.name} />
                </div>
              ))}
            </div>
            )
          )}
        </div>
      </div>
    </main>
  );
}
