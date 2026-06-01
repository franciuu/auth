// ---------------------------------------------------------------------------
// API client + token management.
//   - Access token is kept in memory only (never localStorage).
//   - Refresh token lives in an httpOnly cookie set by the backend; we also
//     persist a non-sensitive flag so a page reload can attempt a silent refresh.
//   - A timer auto-refreshes the access token 5 minutes before it expires.
//   - The CSRF (XSRF-TOKEN) cookie is echoed as X-XSRF-TOKEN on mutations.
// ---------------------------------------------------------------------------

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

let accessToken = null;
let refreshTimer = null;
// Survives reloads: tells us a refresh cookie may exist so we can re-auth silently.
const SESSION_FLAG = 'hasSession';

function decodeJwt(token) {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function readCookie(name) {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

function scheduleRefresh(token) {
  if (refreshTimer) clearTimeout(refreshTimer);
  const claims = decodeJwt(token);
  if (!claims || !claims.exp) return;
  // Refresh 5 minutes (300s) before expiry, but never sooner than 1s from now.
  const msUntilRefresh = Math.max(claims.exp * 1000 - Date.now() - 5 * 60 * 1000, 1000);
  refreshTimer = setTimeout(() => {
    auth.refresh().catch(() => auth.clear());
  }, msUntilRefresh);
}

export const auth = {
  setAccessToken(token) {
    accessToken = token;
    if (token) {
      sessionStorage.setItem(SESSION_FLAG, '1');
      scheduleRefresh(token);
    }
  },

  getAccessToken() {
    return accessToken;
  },

  isAuthenticated() {
    return !!accessToken || sessionStorage.getItem(SESSION_FLAG) === '1';
  },

  // Returns { email, roles } decoded from the current access token.
  getIdentity() {
    if (!accessToken) return null;
    const claims = decodeJwt(accessToken);
    if (!claims) return null;
    return { email: claims.sub, roles: claims.roles || [] };
  },

  isAdmin() {
    const id = auth.getIdentity();
    return !!id && id.roles.includes('ROLE_ADMIN');
  },

  clear() {
    accessToken = null;
    if (refreshTimer) clearTimeout(refreshTimer);
    sessionStorage.removeItem(SESSION_FLAG);
  },

  async register({ email, fullName, password }) {
    return request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, fullName, password }),
    });
  },

  async login({ email, password }) {
    const data = await request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    auth.setAccessToken(data.accessToken);
    return data;
  },

  // Silent refresh using the httpOnly cookie (no body needed).
  async refresh() {
    const data = await request('/api/auth/refresh', { method: 'POST' });
    auth.setAccessToken(data.accessToken);
    return data;
  },

  async logout() {
    try {
      await request('/api/auth/logout', { method: 'POST' });
    } finally {
      auth.clear();
    }
  },
};

// Core fetch wrapper: attaches Authorization + CSRF headers, retries once on 401.
async function request(path, options = {}, retry = true) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }
  const method = (options.method || 'GET').toUpperCase();
  if (method !== 'GET' && method !== 'HEAD') {
    const xsrf = readCookie('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
  }

  const res = await fetch(API_BASE + path, {
    ...options,
    headers,
    credentials: 'include', // send/receive the httpOnly refresh + CSRF cookies
  });

  // Auto-refresh once on 401 for non-auth endpoints, then retry the request.
  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    try {
      await auth.refresh();
      return request(path, options, false);
    } catch {
      auth.clear();
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
      throw new ApiError('Session expired', 401, {});
    }
  }

  const text = await res.text();
  const data = text ? safeJson(text) : {};
  if (!res.ok) {
    throw new ApiError(data.message || 'Request failed', res.status, data);
  }
  return data;
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

// Domain calls -------------------------------------------------------------
export const api = {
  getProfile: () => request('/api/user/profile'),
  listUsers: () => request('/api/admin/users'),
  disableUser: (userId) => request(`/api/admin/disable-user/${userId}`, { method: 'POST' }),
};
