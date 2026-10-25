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

const CAMERA_ORDER_STORAGE_KEY = 'local-nvr-dashboard-camera-order';
const FULLSCREEN_VIEW_COUNTS = [1, 4, 8, 16, 32] as const;
type FullscreenViewCount = typeof FULLSCREEN_VIEW_COUNTS[number];

export default function Home() {
  const dashboardRef = useRef<HTMLElement>(null);
  const cameraOrderRef = useRef<string[]>([]);
  const cameraOrderLoadedRef = useRef(false);
  const fullscreenSelectionInitializedRef = useRef(false);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [editingCameraId, setEditingCameraId] = useState<string | null>(null);
  const [draggedCameraId, setDraggedCameraId] = useState<string | null>(null);
  const [dropTargetCameraId, setDropTargetCameraId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [fullscreenCameraIds, setFullscreenCameraIds] = useState<string[]>([]);
  const [fullscreenViewCount, setFullscreenViewCount] = useState<FullscreenViewCount>(1);
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
  const fullscreenCameras = fullscreenCameraIds
    .map((id) => cameras.find((camera) => camera.id === id))
    .filter((camera): camera is Camera => camera !== undefined);
  const fullscreenColumns = Math.ceil(Math.sqrt(Math.max(fullscreenCameras.length, 1)));
  const fullscreenRows = Math.ceil(fullscreenCameras.length / fullscreenColumns);

  const sortCamerasNumerically = (first: Camera, second: Camera) => {
    const ipComparison = first.ip.localeCompare(second.ip, undefined, { numeric: true });
    if (ipComparison !== 0) return ipComparison;
    if (first.channel !== second.channel) return first.channel - second.channel;
    return first.stream.localeCompare(second.stream);
  };

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(document.fullscreenElement === dashboardRef.current);
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  useEffect(() => {
    if (cameras.length > 0 && !fullscreenSelectionInitializedRef.current) {
      fullscreenSelectionInitializedRef.current = true;
      setFullscreenCameraIds([cameras[0].id]);
      return;
    }

    const availableIds = new Set(cameras.map((camera) => camera.id));
    setFullscreenCameraIds((current) => {
      const next = current.filter((id) => availableIds.has(id));
      return next.length === current.length ? current : next;
    });
  }, [cameras]);

  useEffect(() => {
    let active = true;

    if (!cameraOrderLoadedRef.current) {
      cameraOrderLoadedRef.current = true;
      try {
        const savedOrder = window.localStorage.getItem(CAMERA_ORDER_STORAGE_KEY);
        const parsedOrder: unknown = savedOrder ? JSON.parse(savedOrder) : [];
        if (Array.isArray(parsedOrder) && parsedOrder.every((id) => typeof id === 'string')) {
          cameraOrderRef.current = parsedOrder;
        }
      } catch (error: unknown) {
        console.warn('Could not load the saved camera order:', error);
      }
    }

    const refreshCameras = async () => {
      try {
        const response = await fetch('/api/stream', { cache: 'no-store' });
        if (!response.ok) throw new Error('Could not load shared camera views.');
        const data = await response.json();
        if (active) {
          setCameras((current) => {
            const currentNames = new Map(current.map((camera) => [camera.id, camera.name]));
            const refreshed = ((data.cameras || []) as Camera[])
              .map((camera) => ({
                ...camera,
                name: camera.id === editingCameraId ? currentNames.get(camera.id) || camera.name : camera.name,
              }))
              .sort(sortCamerasNumerically);
            const camerasById = new Map(refreshed.map((camera: Camera) => [camera.id, camera]));
            const preferredOrder = cameraOrderRef.current.length
              ? cameraOrderRef.current
              : current.map((camera) => camera.id);
            const ordered = preferredOrder
              .map((id) => camerasById.get(id))
              .filter((camera): camera is Camera => camera !== undefined);
            const orderedIds = new Set(ordered.map((camera) => camera.id));
            const next = [...ordered, ...refreshed.filter((camera: Camera) => !orderedIds.has(camera.id))];
            cameraOrderRef.current = next.map((camera) => camera.id);
            return next;
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

  const moveCamera = (sourceId: string, targetId: string) => {
    if (sourceId === targetId) return;

    const sourceIndex = cameras.findIndex((camera) => camera.id === sourceId);
    const targetIndex = cameras.findIndex((camera) => camera.id === targetId);
    if (sourceIndex < 0 || targetIndex < 0) return;

    const reordered = [...cameras];
    const [camera] = reordered.splice(sourceIndex, 1);
    reordered.splice(targetIndex, 0, camera);
    cameraOrderRef.current = reordered.map((item) => item.id);
    setCameras(reordered);
    const selectedCameraIds = new Set(fullscreenCameraIds);
    setFullscreenCameraIds(
      reordered.filter((item) => selectedCameraIds.has(item.id)).map((item) => item.id)
    );

    try {
      window.localStorage.setItem(CAMERA_ORDER_STORAGE_KEY, JSON.stringify(cameraOrderRef.current));
    } catch (error: unknown) {
      console.warn('Could not save the camera order:', error);
    }
  };

  const toggleFullscreenCamera = (cameraId: string, selected: boolean) => {
    setFullscreenCameraIds((current) => {
      if (selected) {
        return current.includes(cameraId) || current.length >= fullscreenViewCount
          ? current
          : [...current, cameraId];
      }
      return current.filter((id) => id !== cameraId);
    });
  };

  const updateFullscreenViewCount = (count: FullscreenViewCount) => {
    setFullscreenViewCount(count);
    setFullscreenCameraIds((current) => current.slice(0, count));
  };

  const closeCameraView = (cameraId: string) => {
    toggleFullscreenCamera(cameraId, false);
    setScanSummary('Camera closed from view.');
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
    if (fullscreenCameras.length === 0) return 1;
    if (viewMode === 'auto') {
      return Math.min(Math.max(Math.ceil(Math.sqrt(fullscreenCameras.length)), 1), 4);
    }
    const requestedColumns = { '2x2': 2, '3x3': 3, '4x4': 4 }[viewMode] ?? 1;
    return Math.min(requestedColumns, fullscreenCameras.length);
  })();

  const resolvedRows = Math.ceil(fullscreenCameras.length / resolvedColumns) || 1;

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
          {!isFullscreen && (
            <div className="mb-3 flex items-center justify-between gap-3 text-xs text-slate-400">
              <span>{cameras.length} connected</span>
              <span className="truncate text-right">
                {cameras.length > 1 ? 'Drag cameras to rearrange · ' : ''}
                {scanSummary}
              </span>
            </div>
          )}

          {isFullscreen ? (
            fullscreenCameras.length > 0 ? (
              <div
                className="grid h-full min-h-0 min-w-0 flex-1 gap-3"
                style={{
                  gridTemplateColumns: `repeat(${fullscreenColumns}, minmax(0, 1fr))`,
                  gridTemplateRows: `repeat(${fullscreenRows}, minmax(0, 1fr))`,
                }}
              >
                {fullscreenCameras.map((camera, index) => (
                  <VideoPlayer
                    key={camera.id}
                    streamId={camera.id}
                    cameraName={`${index + 1}. ${camera.name}`}
                    singleView
                  />
                ))}
              </div>
            ) : (
              <div className="flex h-full min-h-0 flex-1 items-center justify-center text-slate-500">
                Select a camera to load its stream.
              </div>
            )
          ) : cameras.length === 0 ? (
            <div className="flex flex-col items-center justify-center border-2 border-dashed border-slate-800 rounded-xl h-[calc(100%-116px)] text-slate-500">
              <p>No streams connected yet.</p>
              <p className="text-xs mt-1">Configure an NVR source on the left to activate WebRTC grid rendering.</p>
            </div>
          ) : (
            <div className="flex h-[calc(100%-116px)] min-h-0 flex-col gap-3">
              <div className="flex shrink-0 flex-wrap items-center gap-3 rounded-lg border border-slate-800 bg-slate-900/80 p-2">
                <label className="flex items-center gap-2 text-sm text-slate-200">
                  Maximum cameras to load
                  <select
                    aria-label="Maximum cameras to load"
                    value={fullscreenViewCount}
                    onChange={(event) => {
                      const count = FULLSCREEN_VIEW_COUNTS.find(
                        (option) => option === Number(event.target.value)
                      );
                      if (count) updateFullscreenViewCount(count);
                    }}
                    className="rounded border border-slate-700 bg-slate-900 px-2 py-1.5 text-sm text-slate-200"
                  >
                    {FULLSCREEN_VIEW_COUNTS.map((count) => (
                      <option key={count} value={count}>{count} {count === 1 ? 'camera' : 'cameras'}</option>
                    ))}
                  </select>
                </label>
                <details className="relative z-20">
                  <summary className="cursor-pointer rounded border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm text-slate-200">
                    Select cameras ({fullscreenCameras.length}/{fullscreenViewCount})
                  </summary>
                  <div className="absolute left-0 top-full mt-1 max-h-56 w-72 overflow-y-auto rounded-lg border border-slate-700 bg-slate-900 p-2 shadow-xl">
                    {cameras.map((camera, index) => (
                      <label
                        key={camera.id}
                        className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm text-slate-200 hover:bg-slate-800"
                      >
                        <input
                          type="checkbox"
                          checked={fullscreenCameraIds.includes(camera.id)}
                          disabled={
                            !fullscreenCameraIds.includes(camera.id) &&
                            fullscreenCameraIds.length >= fullscreenViewCount
                          }
                          onChange={(event) => toggleFullscreenCamera(camera.id, event.target.checked)}
                          className="accent-blue-500"
                        />
                        <span className="min-w-0 truncate">{index + 1}. {camera.name}</span>
                      </label>
                    ))}
                  </div>
                </details>
                <span className="text-xs text-slate-400">
                  Unselected cameras do not load.
                </span>
              </div>
              {fullscreenCameras.length > 0 ? (
                <div
                  className="grid min-h-0 flex-1 gap-3"
                  style={{
                    gridTemplateColumns: `repeat(${resolvedColumns}, minmax(0, 1fr))`,
                    gridTemplateRows: `repeat(${resolvedRows}, minmax(0, 1fr))`,
                  }}
                >
                  {fullscreenCameras.map((cam, index) => (
                    <div
                      key={cam.id}
                      draggable
                      onDragStart={(event) => {
                        event.dataTransfer.effectAllowed = 'move';
                        event.dataTransfer.setData('text/plain', cam.id);
                        setDraggedCameraId(cam.id);
                      }}
                      onDragOver={(event) => {
                        event.preventDefault();
                        event.dataTransfer.dropEffect = 'move';
                        setDropTargetCameraId(cam.id);
                      }}
                      onDrop={(event) => {
                        event.preventDefault();
                        const sourceId = event.dataTransfer.getData('text/plain') || draggedCameraId;
                        if (sourceId) moveCamera(sourceId, cam.id);
                        setDraggedCameraId(null);
                        setDropTargetCameraId(null);
                      }}
                      onDragEnd={() => {
                        setDraggedCameraId(null);
                        setDropTargetCameraId(null);
                      }}
                      className={`group relative min-h-0 cursor-grab active:cursor-grabbing ${
                        draggedCameraId === cam.id ? 'opacity-40' : ''
                      } ${
                        dropTargetCameraId === cam.id && draggedCameraId !== cam.id
                          ? 'ring-2 ring-blue-400 ring-offset-2 ring-offset-slate-950'
                          : ''
                      }`}
                      aria-label={`Draggable camera tile: ${cam.name}`}
                    >
                      <button
                        type="button"
                        onClick={() => closeCameraView(cam.id)}
                        className="absolute right-2 top-2 z-20 flex h-6 w-6 items-center justify-center rounded-full bg-black/70 text-xs text-white border border-slate-700 hover:bg-red-600 transition"
                        aria-label={`Close ${cam.name} from view`}
                      >
                        ×
                      </button>
                      <VideoPlayer streamId={cam.id} cameraName={`${index + 1}. ${cam.name}`} />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flex min-h-0 flex-1 items-center justify-center rounded-xl border-2 border-dashed border-slate-800 text-slate-500">
                  Select a camera to load its stream.
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
