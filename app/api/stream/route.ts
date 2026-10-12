import { NextResponse } from 'next/server';

const MEDIAMTX_BASE = process.env.MEDIAMTX_URL || 'http://127.0.0.1:9997';
const MEDIAMTX_API_USER = process.env.MEDIAMTX_API_USER || 'dashboard';
const MEDIAMTX_API_PASSWORD = process.env.MEDIAMTX_API_PASSWORD || 'change-this-password';

function mediaMtxHeaders() {
  const credentials = Buffer.from(`${MEDIAMTX_API_USER}:${MEDIAMTX_API_PASSWORD}`).toString('base64');
  return {
    Authorization: `Basic ${credentials}`,
    'Content-Type': 'application/json',
  };
}

export async function GET() {
  try {
    const response = await fetch(new URL('/v3/config/paths/list', MEDIAMTX_BASE), {
      headers: mediaMtxHeaders(),
      cache: 'no-store',
    });

    if (!response.ok) {
      const responseText = await response.text();
      throw new Error(`MediaMTX rejected the path list (${response.status}): ${responseText || response.statusText}`);
    }

    const pathList = await response.json();
    const cameras = (pathList.items || []).flatMap((path: { name?: string; source?: string }) => {
      const match = path.name?.match(/^cam_(\d{1,3}(?:_\d{1,3}){3})_(\d+)_(main|sub)$/);
      if (!match || !path.source) return [];

      let sourcePath: string;
      try {
        sourcePath = new URL(path.source).pathname;
      } catch {
        return [];
      }

      const type = sourcePath.startsWith('/Streaming/Channels/')
        ? 'hikvision'
        : sourcePath.startsWith('/unicast/')
          ? 'uniview'
          : null;
      if (!type) return [];

      const ip = match[1].replace(/_/g, '.');
      const channel = Number(match[2]);
      const stream = match[3] as 'main' | 'sub';

      return [{
        id: path.name,
        name: `Camera ${ip} CH${channel} (${stream === 'main' ? 'Main' : 'Sub'})`,
        type,
        ip,
        channel,
        stream,
      }];
    });

    return NextResponse.json({ cameras });
  } catch (error: unknown) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Unknown stream list error' },
      { status: 502 }
    );
  }
}

function buildRtspUrl({ type, ip, username, password, channel, stream }: {
  type: 'hikvision' | 'uniview';
  ip: string;
  username: string;
  password: string;
  channel: number;
  stream: 'main' | 'sub';
}) {
  if (type === 'hikvision') {
    const hikChannel = `${channel}${stream === 'main' ? '01' : '02'}`;
    return `rtsp://${encodeURIComponent(username)}:${encodeURIComponent(password)}@${ip}:554/Streaming/Channels/${hikChannel}`;
  }

  const unvStream = stream === 'main' ? '0' : '1';
  return `rtsp://${encodeURIComponent(username)}:${encodeURIComponent(password)}@${ip}:554/unicast/c${channel}/s${unvStream}/live`;
}

async function registerPath({ id, type, ip, username, password, channel, stream }: {
  id: string;
  type: 'hikvision' | 'uniview';
  ip: string;
  username: string;
  password: string;
  channel: number;
  stream: 'main' | 'sub';
}) {
  const rtspUrl = buildRtspUrl({ type, ip, username, password, channel, stream });
  const mediaMTXPath = new URL(`/v3/config/paths/add/${encodeURIComponent(id)}`, MEDIAMTX_BASE).toString();

  let response: Response;
  try {
    response = await fetch(mediaMTXPath, {
      method: 'POST',
      headers: mediaMtxHeaders(),
      body: JSON.stringify({
        source: rtspUrl,
        sourceProtocol: 'tcp',
        sourceOnDemand: true,
      }),
    });
  } catch (fetchError: unknown) {
    throw new Error(
      `MediaMTX is not reachable at ${MEDIAMTX_BASE}. Start MediaMTX or set MEDIAMTX_URL. ${fetchError instanceof Error ? fetchError.message : ''}`
    );
  }

  const responseText = await response.text();
  if (!response.ok && response.status !== 400) {
    throw new Error(
      `MediaMTX rejected the stream route (${response.status}): ${responseText || response.statusText}`
    );
  }

  return { ok: true, path: id };
}

async function deletePath(id: string) {
  const mediaMTXPath = new URL(`/v3/config/paths/delete/${encodeURIComponent(id)}`, MEDIAMTX_BASE).toString();

  let response: Response;
  try {
    response = await fetch(mediaMTXPath, { method: 'DELETE', headers: mediaMtxHeaders() });
  } catch (fetchError: unknown) {
    throw new Error(
      `MediaMTX is not reachable at ${MEDIAMTX_BASE}. Start MediaMTX or set MEDIAMTX_URL. ${fetchError instanceof Error ? fetchError.message : ''}`
    );
  }

  const responseText = await response.text();
  if (!response.ok && response.status !== 404) {
    throw new Error(
      `MediaMTX rejected path deletion (${response.status}): ${responseText || response.statusText}`
    );
  }

  return { ok: true, path: id };
}

function expandIpRange(ipPattern: string) {
  const trimmed = ipPattern.trim();

  if (!trimmed) return [];

  if (/^\d{1,3}(?:\.\d{1,3}){3}$/.test(trimmed)) {
    return [trimmed];
  }

  if (trimmed.endsWith('.')) {
    const prefix = trimmed.slice(0, -1);
    return Array.from({ length: 254 }, (_, index) => `${prefix}.${index + 1}`);
  }

  const matches = trimmed.match(/^(\d{1,3}(?:\.\d{1,3}){3})-(\d{1,3})$/);
  if (matches) {
    const prefix = matches[1].match(/^(\d{1,3}\.\d{1,3}\.\d{1,3}\.)/);
    if (!prefix) return [];
    const base = prefix[1];
    const start = Number(matches[2]);
    return Array.from({ length: Math.max(0, 255 - start + 1) }, (_, index) => `${base}${start + index}`);
  }

  return [];
}

export async function POST(request: Request) {
  try {
    const payload = await request.json();

    if (payload?.autoDiscover) {
      const { type, username, password, ipPattern, startChannel, endChannel, stream } = payload.autoDiscover;

      if (!type || !username || !password || !ipPattern || !['main', 'sub'].includes(stream)) {
        return NextResponse.json({ error: 'Missing auto-discovery settings.' }, { status: 400 });
      }

      const ips = expandIpRange(ipPattern);
      const discovered: Array<{
        id: string;
        name: string;
        type: 'hikvision' | 'uniview';
        ip: string;
        channel: number;
        stream: 'main' | 'sub';
      }> = [];
      const seen = new Set<string>();

      for (const ip of ips) {
        for (let channel = Number(startChannel || 1); channel <= Number(endChannel || 1); channel += 1) {
          const candidateStreams: Array<'main' | 'sub'> = [stream];

          for (const currentStream of candidateStreams) {
            const id = `cam_${ip.replace(/\./g, '_')}_${channel}_${currentStream}`;
            const key = `${ip}:${channel}:${currentStream}`;
            if (seen.has(key)) continue;

            try {
              await registerPath({
                id,
                type,
                ip,
                username,
                password,
                channel,
                stream: currentStream,
              });

              seen.add(key);
              discovered.push({
                id,
                name: `Camera ${ip} CH${channel} (${currentStream === 'main' ? 'Main' : 'Sub'})`,
                type,
                ip,
                channel,
                stream: currentStream,
              });
            } catch {
              // Ignore non-working camera variations.
            }
          }
        }
      }

      return NextResponse.json({ success: true, cameras: discovered });
    }

    const { id, type, ip, username, password, channel, stream } = payload;

    if (!id || !type || !ip || !username || !password || !channel || !stream) {
      return NextResponse.json({ error: 'Missing camera configuration.' }, { status: 400 });
    }

    await registerPath({ id, type, ip, username, password, channel, stream });
    return NextResponse.json({ success: true, streamPath: id });
  } catch (error: unknown) {
    return NextResponse.json({ error: error instanceof Error ? error.message : 'Unknown stream configuration error' }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  try {
    const payload = await request.json();
    const { id } = payload || {};

    if (!id) {
      return NextResponse.json({ error: 'Missing camera id.' }, { status: 400 });
    }

    await deletePath(id);
    return NextResponse.json({ success: true, removed: id });
  } catch (error: unknown) {
    return NextResponse.json({ error: error instanceof Error ? error.message : 'Unknown stream removal error' }, { status: 500 });
  }
}
