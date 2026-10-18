export const $ = (s, el = document) => el.querySelector(s);
export const $$ = (s, el = document) => [...el.querySelectorAll(s)];

export function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function haversine(a, b) {
  const R = 6371000, toR = x => x * Math.PI / 180;
  const dLat = toR(b.lat - a.lat), dLng = toR(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toR(a.lat)) * Math.cos(toR(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

// Move `distM` metres from `c` along `bearingDeg` (good enough at city scale).
export function offsetPoint(c, distM, bearingDeg) {
  const b = bearingDeg * Math.PI / 180;
  return {
    lat: c.lat + (distM * Math.cos(b)) / 111320,
    lng: c.lng + (distM * Math.sin(b)) / (111320 * Math.cos(c.lat * Math.PI / 180)),
  };
}

export function fmtDistance(m) {
  if (m == null) return '';
  if (m < 950) return `${Math.max(10, Math.round(m / 10) * 10)} m`;
  return `${(m / 1000).toFixed(m < 9950 ? 1 : 0)} km`;
}

export const walkMins = m => Math.max(1, Math.round(m / 80)); // ~4.8 km/h

export function fmtTime(h, m = 0) {
  const ap = h >= 12 && h < 24 ? 'pm' : 'am';
  let hh = h % 12; if (hh === 0) hh = 12;
  return m ? `${hh}:${String(m).padStart(2, '0')} ${ap}` : `${hh} ${ap}`;
}

export function fmtCount(n) {
  if (!n) return '0';
  return n >= 10000 ? `${Math.round(n / 1000)}k` : n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n);
}

export function hashHue(s) {
  let h = 0;
  for (const ch of String(s)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h % 360;
}

// Deterministic PRNG (mulberry32) so demo data is stable between reloads.
export function seeded(seed) {
  return () => {
    seed |= 0; seed = seed + 0x6D2B79F5 | 0;
    let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

export const sleep = ms => new Promise(r => setTimeout(r, ms));

// localStorage can throw (private mode, blocked storage) — never let that break the app.
export const store = {
  get(k, d) {
    try { const v = localStorage.getItem('forkcast:' + k); return v == null ? d : JSON.parse(v); } catch { return d; }
  },
  set(k, v) {
    try { localStorage.setItem('forkcast:' + k, JSON.stringify(v)); } catch { /* ignore */ }
  },
};
