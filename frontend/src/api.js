const KEY = 'ct_token';
export const auth = {
  get: () => localStorage.getItem(KEY),
  set: (t) => localStorage.setItem(KEY, t),
  clear: () => localStorage.removeItem(KEY),
};

export async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(auth.get() ? { Authorization: 'Bearer ' + auth.get() } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401) { auth.clear(); window.location.href = '/login'; throw new Error('Session expired'); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}
