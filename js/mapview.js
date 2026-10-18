// Two interchangeable map views with the same interface:
// GoogleMapView (live data — Google requires Places results on a Google map) and
// LeafletMapView (demo mode, OpenStreetMap tiles, no key needed).
import { esc } from './util.js';

// Above this many places, pins render as small dots and expand on hover/click so the map stays readable.
const COMPACT_OVER = 60;

export function pinHTML(v, compact = false) {
  const label = v.priceLevel ? '$'.repeat(v.priceLevel) : (v.category === 'Cafe' ? '☕' : '•');
  return `<div class="pin${compact ? ' compact' : ''}" data-pin="${esc(v.id)}"><span class="pin-p">${label}</span>${v.rating ? `<span class="pin-r">★ ${v.rating.toFixed(1)}</span>` : ''}</div>`;
}
function el(html) { const d = document.createElement('div'); d.innerHTML = html.trim(); return d.firstChild; }

function visibleBox(div) {
  const r = div.getBoundingClientRect();
  const hiddenBottom = Math.max(0, r.bottom - innerHeight);
  return { width: r.width, height: r.height - hiddenBottom - Math.max(0, -r.top), hiddenBottom };
}
const BRAND = '#e0482d';

export class GoogleMapView {
  async init(container) {
    this.maps = await google.maps.importLibrary('maps');
    ({ AdvancedMarkerElement: this.AME } = await google.maps.importLibrary('marker'));
    this.map = new this.maps.Map(container, {
      center: { lat: -33.87, lng: 151.21 }, zoom: 13, mapId: 'DEMO_MAP_ID', clickableIcons: false,
      streetViewControl: false, mapTypeControl: false, fullscreenControl: false, gestureHandling: 'greedy',
    });
    this.markers = new Map();
  }
  setArea(center, radius) {
    if (!this.circle) this.circle = new this.maps.Circle({ map: this.map, strokeColor: BRAND, strokeOpacity: 0.7, strokeWeight: 2, fillColor: BRAND, fillOpacity: 0.05, clickable: false });
    this.circle.setCenter(center); this.circle.setRadius(radius);
    this.fit();
    setTimeout(() => this.fit(), 400); // again once the container/tiles have settled
    if (this.centerMarker) this.centerMarker.map = null;
    this.centerMarker = new this.AME({ map: this.map, position: center, content: el('<div class="center-dot"></div>'), title: 'Search centre' });
  }
  setMarkers(venues, onClick) {
    for (const { m } of this.markers.values()) m.map = null;
    this.markers.clear();
    const compact = venues.length > COMPACT_OVER;
    for (const v of venues) {
      const content = el(pinHTML(v, compact));
      const m = new this.AME({ map: this.map, position: { lat: v.lat, lng: v.lng }, content, title: v.name, gmpClickable: true });
      let last = 0;
      const handler = () => { const now = Date.now(); if (now - last < 400) return; last = now; onClick(v.id); };
      m.addListener('click', handler);
      try { m.addEventListener('gmp-click', handler); } catch { /* older API */ }
      this.markers.set(v.id, { m, content });
    }
  }
  _toggle(cls, id) {
    for (const [k, { m, content }] of this.markers) {
      const on = k === id;
      content.classList.toggle(cls, on);
      if (on) m.zIndex = 9999; else if (!content.classList.contains('active') && !content.classList.contains('hover')) m.zIndex = null;
    }
  }
  highlight(id) { this._toggle('active', id); }
  hover(id) { this._toggle('hover', id); }
  // fitBounds() is silently ignored if the map hasn't finished initialising, so work out
  // the centre and zoom ourselves from the container size (Web Mercator metres-per-pixel).
  // Only the on-screen part of the map counts: at the top of the page the sticky map pane
  // hangs below the viewport, so centre the circle in what's actually visible.
  fit() {
    if (!this.circle) return;
    const c = this.circle.getCenter(), r = this.circle.getRadius();
    const v = visibleBox(this.map.getDiv());
    const px = Math.min(v.width, v.height) - 32;
    if (px <= 0) return;
    const metresPerPx = (2 * r) / px;
    const zoom = Math.log2(156543.03392 * Math.cos(c.lat() * Math.PI / 180) / metresPerPx);
    this.map.setCenter(c);
    this.map.setZoom(Math.max(3, Math.min(18, Math.floor(zoom))));
    if (v.hiddenBottom > 0) this.map.panBy(0, Math.round(v.hiddenBottom / 2));
  }
  // The map may have been laid out while hidden (0×0), so re-fit once it's visible.
  resize() { setTimeout(() => this.fit(), 60); }
  static async mini(container, v) {
    const maps = await google.maps.importLibrary('maps');
    const { AdvancedMarkerElement } = await google.maps.importLibrary('marker');
    const map = new maps.Map(container, { center: { lat: v.lat, lng: v.lng }, zoom: 16, mapId: 'DEMO_MAP_ID', disableDefaultUI: true, zoomControl: true, clickableIcons: false });
    const content = el(pinHTML(v)); content.classList.add('active');
    new AdvancedMarkerElement({ map, position: { lat: v.lat, lng: v.lng }, content, title: v.name });
  }
}

let leafletLoading;
export function loadLeaflet() {
  if (window.L) return Promise.resolve();
  return leafletLoading ||= new Promise((resolve, reject) => {
    const css = document.createElement('link');
    css.rel = 'stylesheet'; css.href = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css';
    document.head.appendChild(css);
    const s = document.createElement('script');
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js';
    s.onload = resolve; s.onerror = () => reject(new Error('Could not load the demo map.'));
    document.head.appendChild(s);
  });
}

const tiles = () => L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' });

export class LeafletMapView {
  async init(container) {
    this.map = L.map(container, { zoomControl: true }).setView([-33.88, 151.21], 14);
    tiles().addTo(this.map);
    this.markers = new Map();
  }
  setArea(center, radius) {
    const ll = [center.lat, center.lng];
    if (!this.circle) {
      this.circle = L.circle(ll, { radius, color: BRAND, weight: 2, fillOpacity: 0.05, interactive: false }).addTo(this.map);
      this.centerMarker = L.marker(ll, { icon: L.divIcon({ html: '<div class="center-dot"></div>', className: 'center-wrap', iconSize: [16, 16] }), interactive: false }).addTo(this.map);
    } else {
      this.circle.setLatLng(ll); this.circle.setRadius(radius); this.centerMarker.setLatLng(ll);
    }
    this.fit();
  }
  fit() {
    if (!this.circle) return;
    const { hiddenBottom } = visibleBox(this.map.getContainer());
    this.map.fitBounds(this.circle.getBounds(), { paddingTopLeft: [16, 16], paddingBottomRight: [16, 16 + hiddenBottom] });
  }
  setMarkers(venues, onClick) {
    for (const { m } of this.markers.values()) m.remove();
    this.markers.clear();
    const compact = venues.length > COMPACT_OVER;
    for (const v of venues) {
      const icon = L.divIcon({ html: pinHTML(v, compact), className: 'pin-wrap', iconSize: null });
      const m = L.marker([v.lat, v.lng], { icon, title: v.name }).addTo(this.map);
      m.on('click', () => onClick(v.id));
      this.markers.set(v.id, { m });
    }
  }
  _toggle(cls, id) {
    for (const [k, { m }] of this.markers) {
      const pin = m.getElement()?.querySelector('.pin');
      pin?.classList.toggle(cls, k === id);
      const lifted = pin && (pin.classList.contains('active') || pin.classList.contains('hover'));
      m.setZIndexOffset(lifted ? 1000 : 0);
    }
  }
  highlight(id) { this._toggle('active', id); }
  hover(id) { this._toggle('hover', id); }
  resize() { setTimeout(() => { this.map.invalidateSize(); this.fit(); }, 60); }
  static mini(container, v) {
    const map = L.map(container, { zoomControl: true, attributionControl: true }).setView([v.lat, v.lng], 16);
    tiles().addTo(map);
    L.marker([v.lat, v.lng], { icon: L.divIcon({ html: pinHTML(v).replace('class="pin"', 'class="pin active"'), className: 'pin-wrap', iconSize: null }) }).addTo(map);
  }
}
