import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';
import { SESSION_EXPIRED_EVENT, tokens } from './tokens';

function json(status: number, body: unknown = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('api client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    localStorage.clear();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends the access token', async () => {
    tokens.set('access-1', 'refresh-1');
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));

    await request('/vehicles');

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe('Bearer access-1');
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    tokens.set('expired', 'refresh-1');
    fetchMock
      .mockResolvedValueOnce(json(401))
      .mockResolvedValueOnce(json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }))
      .mockResolvedValueOnce(json(200, [{ id: 'v1' }]));

    const result = await request<{ id: string }[]>('/vehicles');

    expect(result).toEqual([{ id: 'v1' }]);
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/refresh');
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ refreshToken: 'refresh-1' });
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer access-2');
    expect(tokens.refresh()).toBe('refresh-2');
  });

  it('shares one refresh between concurrent 401s', async () => {
    tokens.set('expired', 'refresh-1');
    let releaseRefresh: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) => {
      if (url === '/api/v1/auth/refresh') {
        return new Promise<Response>(resolve => { releaseRefresh = resolve; });
      }
      return Promise.resolve(tokens.access() === 'access-2' ? json(200, {}) : json(401));
    });

    const calls = Promise.all([request('/a'), request('/b'), request('/c')]);
    await vi.waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url === '/api/v1/auth/refresh')).toBe(true));
    releaseRefresh(json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }));
    await calls;

    const refreshCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/v1/auth/refresh');
    expect(refreshCalls).toHaveLength(1);
  });

  it('ends the session when the refresh token is rejected', async () => {
    tokens.set('expired', 'revoked');
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);
    fetchMock.mockResolvedValueOnce(json(401)).mockResolvedValueOnce(json(401));

    await expect(request('/vehicles')).rejects.toBeInstanceOf(ApiError);

    expect(expired).toHaveBeenCalledOnce();
    expect(tokens.access()).toBeNull();
    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('does not refresh on 403 (authenticated but not allowed)', async () => {
    tokens.set('access-1', 'refresh-1');
    fetchMock.mockResolvedValueOnce(json(403, { message: 'Access denied' }));

    await expect(request('/rollouts')).rejects.toMatchObject({ status: 403, message: 'Access denied' });
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(tokens.access()).toBe('access-1');
  });
});
