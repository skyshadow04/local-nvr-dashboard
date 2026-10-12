export async function POST(
  request: Request,
  { params }: { params: Promise<{ streamId: string }> }
) {
  const { streamId } = await params;
  const body = await request.text();

  const whepBase = process.env.MEDIAMTX_WHEP_URL || 'http://127.0.0.1:8889';
  const user = process.env.MEDIAMTX_API_USER || 'dashboard';
  const password = process.env.MEDIAMTX_API_PASSWORD || 'change-this-password';
  const credentials = Buffer.from(`${user}:${password}`).toString('base64');
  const upstream = await fetch(`${whepBase}/${encodeURIComponent(streamId)}/whep`, {
    method: 'POST',
    headers: {
      'Content-Type': request.headers.get('content-type') ?? 'application/sdp',
      Authorization: `Basic ${credentials}`,
    },
    body,
  });

  const headers = new Headers();
  const contentType = upstream.headers.get('content-type');
  if (contentType) headers.set('Content-Type', contentType);

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers,
  });
}
