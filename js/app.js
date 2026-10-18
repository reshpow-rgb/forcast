import { $, $$, esc, fmtDistance, fmtCount, walkMins, store, haversine, hashHue, sleep } from './util.js';
import { enrich, scoreVenue, openStatus, PRICE_NAMES, DIETS, MEALS, FEATURES, CATEGORIES, dietStatus, dietOk, emojiFor } from './enrich.js';
import * as G from './google.js';
import { demoVenues, DEMO_CENTER } from './demo.js';
import { GoogleMapView, LeafletMapView, loadLeaflet } from './mapview.js';
import { icon } from './icons.js';

const cfg = window.FORKCAST_CONFIG || {};
const PAGE = 24;
const RADII = [500, 1000, 1500, 2000, 3000, 5000, 10000];
const COUNTRIES = { AU: 'Australia', NZ: 'New Zealand', GB: 'United Kingdom', IE: 'Ireland', US: 'United States', CA: 'Canada', SG: 'Singapore' };
const EXAMPLES = {
  AU: [['2000', 'Sydney CBD'], ['2010', 'Surry Hills'], ['3000', 'Melbourne CBD'], ['3065', 'Fitzroy'], ['4000', 'Brisbane'], ['6000', 'Perth']],
  NZ: [['1010', 'Auckland CBD'], ['6011', 'Wellington']],
  GB: [['W1D', 'Soho'], ['M1', 'Manchester']],
  IE: [['D02', 'Dublin 2']],
  US: [['10003', 'East Village, NYC'], ['94110', 'Mission, SF']],
  CA: [['M5V', 'Toronto'], ['V6B', 'Vancouver']],
  SG: [['018956', 'Marina Bay']],
};
const DEPTHS = {
  quick: ['Quick', 'About 4 requests per search. Cheapest, shows the most popular places.'],
  standard: ['Standard', 'About 15 requests per search. Popular and nearest places across major cuisines.'],
  deep: ['Deep', 'Up to ~27 requests per search. Tiles the radius to find more places in busy areas.'],
};

const freshFilters = () => ({
  openNow: false, healthy: false, diets: new Set(), prices: new Set(), unknownPrice: true,
  cuisines: new Set(), categories: new Set(), meals: new Set(), features: new Set(), minRating: 0,
});

const state = {
  mode: null, key: '', country: 'AU', radius: 2000, depth: 'standard',
  center: null, areaLabel: '', keyword: '',
  venues: [], filtered: [], loading: false, searched: false,
  filters: freshFilters(),
  sort: 'recommended', view: 'split',
  shortlistOnly: false, shortlist: new Map(),
  shown: PAGE, activeId: null, hoverId: null,
  map: null, detailOpen: null, lb: null, wiz: null,
};
window.forkcast = state; // handy for debugging in the browser console

// =====================================================================
// Boot (init() is called at the bottom of this file, after all consts exist)
// =====================================================================
async function init() {
  hydrateIcons(document);
  if (location.hash.startsWith('#place/')) history.replaceState(null, '', location.pathname + location.search);

  state.key = (store.get('apiKey', '') || cfg.googleMapsApiKey || '').trim();
  state.country = store.get('country', cfg.defaultCountry || 'AU');
  const r = +store.get('radius', cfg.defaultRadius || 2000);
  state.radius = RADII.includes(r) ? r : 2000;
  state.depth = store.get('depth', 'standard');
  state.sort = store.get('sort', 'recommended');
  state.shortlist = new Map((store.get('shortlist', []) || []).map(v => [v.id, v]));

  const last = store.get('last', null);
  $('#q').value = last?.query || '';
  $('#kw').value = last?.keyword || '';
  $('#radius').value = String(state.radius);
  $('#sort').value = state.sort;
  setView(store.get('view', 'split'));

  bindUI();
  renderQuickChips();
  updateCounts();

  window.addEventListener('fc:authfail', () => {
    showToast(G.AUTH_HELP, true, 12000);
    // Pending Google requests may never settle after an auth failure, so stop waiting on them.
    if (state.loading) { searchSeq++; state.loading = false; renderEmpty('error', G.AUTH_HELP); }
  });
  setInterval(refreshOpenStatus, 5 * 60 * 1000);

  const wantDemo = store.get('demo', false);
  if (state.key && !wantDemo) await startLive(last);
  else if (wantDemo || !state.key && store.get('seenWelcome', false)) await startDemo();
  else { renderEmpty('welcome'); showWelcome(); }
}

async function startLive(last) {
  state.mode = 'live';
  setBanner('');
  setLoading('Loading Google Maps…');
  try {
    await G.loadGoogle(state.key);
    state.map = new GoogleMapView();
    await state.map.init($('#map'));
  } catch (e) {
    console.error(e);
    showToast(e.message || String(e), true, 10000);
    renderEmpty('error', e.message);
    return;
  }
  if (last?.query) runSearch();
  else if (last?.center) { state.center = last.center; state.areaLabel = last.label || 'your location'; $('#q').placeholder = '📍 Your current location'; runSearch(); }
  else renderEmpty('start');
}

async function startDemo() {
  state.mode = 'demo';
  setBanner(`<b>Demo mode</b> — these are made-up sample venues so you can try the app. <button data-action="settings">Connect Google Maps</button> to search real restaurants and cafés by postcode.`);
  setLoading('Loading the map…');
  try {
    await loadLeaflet();
    state.map = new LeafletMapView();
    await state.map.init($('#map'));
  } catch (e) {
    console.warn(e); state.map = null;
  }
  runSearch();
}

// =====================================================================
// Search
// =====================================================================
let searchSeq = 0;
async function runSearch() {
  const seq = ++searchSeq;
  const query = $('#q').value.trim();
  const keyword = $('#kw').value.trim();
  const radius = +$('#radius').value;
  state.radius = radius; state.keyword = keyword;
  store.set('radius', radius);
  if (!state.mode) { showWelcome(); return; }
  closePreview();

  try {
    let raw, center, label;
    if (state.mode === 'demo') {
      setLoading('Loading sample venues…');
      center = DEMO_CENTER; label = 'Demo Town (sample data)';
      raw = demoVenues();
      await sleep(250);
    } else {
      if (query) {
        setLoading('Finding the area…');
        const g = await G.geocode(query, state.country);
        center = { lat: g.lat, lng: g.lng };
        label = String(g.label || query).replace(/,\s*(Australia|New Zealand|UK|United Kingdom|Ireland|USA|United States|Canada|Singapore)$/i, '');
      } else if (state.center) {
        center = state.center; label = state.areaLabel || 'your location';
      } else {
        renderEmpty('start'); showToast('Enter a postcode or suburb to search.'); $('#q').focus();
        return;
      }
      setLoading('Searching restaurants & cafés…');
      raw = await G.searchArea({ center, radius, keyword, depth: state.depth }, (d, n) => {
        if (seq === searchSeq) setLoading(`Searching restaurants & cafés… ${d}/${n}`);
      });
    }
    if (seq !== searchSeq) return;

    state.center = center; state.areaLabel = label;
    state.venues = raw
      .filter(v => v.businessStatus !== 'CLOSED_PERMANENTLY')
      .map(v => enrich(v, center))
      .filter(v => v.distance <= radius * 1.05);
    for (const v of state.venues) {
      v.score = scoreVenue(v, radius);
      if (state.shortlist.has(v.id)) state.shortlist.set(v.id, snapshot(v));
    }
    saveShortlist();
    state.searched = true; state.loading = false;
    state.shown = PAGE; state.shortlistOnly = false; state.activeId = null;
    $('#sl-btn').classList.remove('on');
    // Only remember a raw location for live "use my location" searches — never the demo's fake centre.
    const keepCenter = state.mode === 'live' && !query;
    store.set('last', { query: state.mode === 'live' ? query : '', keyword, center: keepCenter ? center : null, label: keepCenter ? label : null });
    state.map?.setArea(center, radius);
    renderAll();
    if (!state.venues.length) showToast('No places found here. Try a bigger radius or a different postcode.');
  } catch (e) {
    if (seq !== searchSeq) return;
    console.error(e);
    state.loading = false;
    showToast(e.message || String(e), true, 10000);
    if (state.venues.length) renderAll(); else renderEmpty('error', e.message);
  }
}

function setLoading(msg) {
  state.loading = true;
  $('#results-head').innerHTML = `<div class="loading"><span class="spinner"></span>${esc(msg)}</div>`;
  $('#active-filters').innerHTML = '';
  $('#more').innerHTML = '';
  if (!$('#cards .skel')) $('#cards').innerHTML = Array.from({ length: 6 }, () => '<div class="skel"><div class="sm"></div><div class="sl" style="width:40%"></div><div class="sl" style="width:75%"></div><div class="sl" style="width:55%"></div></div>').join('');
}

function locate() {
  if (state.mode !== 'live') { showToast('“Use my location” needs Google Maps connected — demo mode always shows the sample area.'); return; }
  if (!navigator.geolocation) { showToast('Location isn’t available in this browser.'); return; }
  showToast('Finding your location…');
  navigator.geolocation.getCurrentPosition(pos => {
    state.center = { lat: pos.coords.latitude, lng: pos.coords.longitude };
    state.areaLabel = 'your location';
    $('#q').value = ''; $('#q').placeholder = '📍 Your current location';
    runSearch();
  }, err => showToast(`Couldn’t get your location: ${err.message}`, true), { enableHighAccuracy: true, timeout: 10000 });
}

function refreshOpenStatus() {
  for (const v of state.venues) { v.open = openStatus(v.hours); v.score = scoreVenue(v, state.radius); }
  if (!state.loading && state.searched) renderResults();
}

// =====================================================================
// Filtering & sorting
// =====================================================================
function keywordMatch(v) {
  const kw = state.keyword.toLowerCase();
  if (!kw || v.keywordHit) return true;
  return kw.split(/\s+/).every(t => v.searchText.includes(t));
}

function getBase() {
  if (state.shortlistOnly) {
    return [...state.shortlist.values()].map(v => ({
      ...v,
      distance: state.center ? haversine(state.center, v) : v.distance,
      open: openStatus(v.hours),
    }));
  }
  return state.venues.filter(keywordMatch);
}

function passes(v, f = state.filters) {
  if (f.openNow && v.open?.open !== true) return false;
  if (f.healthy && v.health.score < 65) return false;
  for (const d of f.diets) if (!dietOk(v, d)) return false;
  if (f.prices.size) {
    if (v.priceLevel == null) { if (!f.unknownPrice) return false; }
    else if (!f.prices.has(v.priceLevel)) return false;
  }
  if (f.cuisines.size && !v.cuisines.some(c => f.cuisines.has(c))) return false;
  if (f.categories.size && !f.categories.has(v.category)) return false;
  for (const m of f.meals) if (v.meals[m] !== true) return false;
  for (const ft of f.features) if (v.features?.[ft] !== true) return false;
  if (f.minRating && (v.rating || 0) < f.minRating) return false;
  return true;
}

const SORTERS = {
  recommended: (a, b) => b.score - a.score,
  rating: (a, b) => (b.rating || 0) - (a.rating || 0) || b.reviews - a.reviews,
  distance: (a, b) => a.distance - b.distance,
  'price-asc': (a, b) => (a.priceLevel ?? 9) - (b.priceLevel ?? 9) || b.score - a.score,
  'price-desc': (a, b) => (b.priceLevel ?? -1) - (a.priceLevel ?? -1) || b.score - a.score,
  reviews: (a, b) => b.reviews - a.reviews,
  healthy: (a, b) => b.health.score - a.health.score || b.score - a.score,
};

function getFiltered() {
  return getBase().filter(v => passes(v)).sort(SORTERS[state.sort] || SORTERS.recommended);
}

function activeFilterList() {
  const f = state.filters, out = [];
  if (f.openNow) out.push(['open', '', 'Open now']);
  if (f.healthy) out.push(['healthy', '', 'Healthy picks']);
  for (const d of f.diets) out.push(['diet', d, DIETS.find(x => x.key === d)?.label || d]);
  for (const p of [...f.prices].sort()) out.push(['price', p, '$'.repeat(p)]);
  for (const c of f.categories) out.push(['cat', c, c]);
  for (const c of f.cuisines) out.push(['cuisine', c, c]);
  for (const m of f.meals) out.push(['meal', m, MEALS.find(x => x.key === m)?.label || m]);
  for (const ft of f.features) out.push(['feature', ft, FEATURES.find(x => x.key === ft)?.label || ft]);
  if (f.minRating) out.push(['rating', f.minRating, `${f.minRating}+ ★`]);
  return out;
}

function toggleSet(set, v) { set.has(v) ? set.delete(v) : set.add(v); }

function applyFilter(group, val) {
  const f = state.filters;
  switch (group) {
    case 'open': f.openNow = !f.openNow; break;
    case 'healthy': f.healthy = !f.healthy; break;
    case 'diet': toggleSet(f.diets, val); break;
    case 'price': toggleSet(f.prices, +val); break;
    case 'unknownPrice': f.unknownPrice = !f.unknownPrice; break;
    case 'cat': toggleSet(f.categories, val); break;
    case 'cuisine': toggleSet(f.cuisines, val); break;
    case 'meal': toggleSet(f.meals, val); break;
    case 'feature': toggleSet(f.features, val); break;
    case 'rating': f.minRating = f.minRating === +val ? 0 : +val; break;
  }
  state.shown = PAGE;
  renderAll();
  if (!$('#drawer').hidden) renderDrawer();
}

// =====================================================================
// Rendering — results
// =====================================================================
function renderAll() {
  renderQuickChips();
  renderResults();
  updateCounts();
}

function renderResults() {
  if (state.loading) return;
  if (!state.searched && !state.shortlistOnly) return;
  const list = getFiltered();
  state.filtered = list;
  const total = getBase().length;
  const n = list.length;

  const where = state.shortlistOnly
    ? 'Your saved places'
    : `within ${fmtDistance(state.radius)} of <b>${esc(state.areaLabel)}</b>`;
  const extra = [
    !state.shortlistOnly && n !== total ? `filtered from ${total}` : '',
    state.keyword && !state.shortlistOnly ? `matching “${esc(state.keyword)}”` : '',
  ].filter(Boolean).join(' · ');
  $('#results-head').innerHTML = `
    <div>
      <h1>${n} ${state.shortlistOnly ? 'shortlisted' : ''} place${n === 1 ? '' : 's'} to eat</h1>
      <p>${where}${extra ? ' · ' + extra : ''}</p>
    </div>
    ${state.shortlistOnly
      ? `<button class="btn-outline" data-action="shortlist">${icon('chevronL')} Back to results</button>`
      : `<button class="btn-primary" data-action="wizard">${icon('sparkles')} Help me choose</button>`}`;

  const af = activeFilterList();
  $('#active-filters').innerHTML = af.length
    ? af.map(([g, v, label]) => `<button class="afchip" data-action="unfilter" data-f="${g}" data-v="${esc(v)}">${esc(label)} ${icon('x')}</button>`).join('') +
      `<button class="btn-link" data-action="clear-filters" style="font-size:13px;margin-left:4px">Clear all</button>`
    : '';

  if (!n) {
    $('#cards').innerHTML = state.shortlistOnly && !state.shortlist.size
      ? emptyHTML('💛', 'No saved places yet', 'Tap the heart on any listing to add it to your shortlist, then compare your options here.')
      : emptyHTML('🔍', 'No matches', 'Nothing fits all of those filters. Try removing one or two, or widening the radius.',
        af.length ? `<button class="btn-outline" data-action="clear-filters">Clear filters</button>` : '');
    $('#more').innerHTML = '';
  } else {
    $('#cards').innerHTML = list.slice(0, state.shown).map(cardHTML).join('');
    const left = n - state.shown;
    $('#more').innerHTML = left > 0 ? `<button class="btn-outline" data-action="more">Show ${Math.min(left, PAGE)} more (${left} left)</button>` : '';
  }
  state.map?.setMarkers(list.slice(0, 400), onPinClick);
  if (state.activeId) state.map?.highlight(state.activeId);
}

function emptyHTML(emoji, title, text, actions = '') {
  return `<div class="empty"><div class="big">${emoji}</div><h2>${title}</h2><p>${text}</p><div class="row">${actions}</div></div>`;
}

function renderEmpty(kind, msg = '') {
  $('#results-head').innerHTML = '';
  $('#active-filters').innerHTML = '';
  $('#more').innerHTML = '';
  let html = '';
  if (kind === 'welcome') {
    html = emptyHTML('🍜', 'Find somewhere great to eat', 'Search a postcode to see every restaurant and café nearby — then filter by budget, diet, cuisine and healthy options.',
      `<button class="btn-primary" data-action="settings">Connect Google Maps</button><button class="btn-outline" data-action="demo">Try the demo</button>`);
  } else if (kind === 'start') {
    const ex = EXAMPLES[state.country] || [];
    html = emptyHTML('📍', 'Where are you eating?', 'Enter a postcode or suburb above, or use your current location. You’ll see every restaurant and café within the radius, listing-style.',
      ex.map(([q, l]) => `<button class="fchip" data-action="example" data-q="${esc(q)}">${esc(q)} · ${esc(l)}</button>`).join('') +
      `<button class="fchip" data-action="locate">${icon('locate')} Use my location</button>`);
  } else if (kind === 'error') {
    html = emptyHTML('⚠️', 'Something went wrong', esc(msg || 'The search failed.'),
      `<button class="btn-primary" data-action="retry">Try again</button><button class="btn-outline" data-action="settings">Check settings</button>`);
  }
  $('#cards').innerHTML = html;
}

function priceText(v) { return v.priceLevel ? '$'.repeat(v.priceLevel) : ''; }

function money(n, cur) {
  if (n == null) return '';
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency: cur || 'AUD', currencyDisplay: 'narrowSymbol', maximumFractionDigits: 0 }).format(n); }
  catch { return '$' + Math.round(n); }
}
function rangeText(v) {
  const r = v.priceRange;
  if (!r || (r.min == null && r.max == null)) return '';
  // Google's lowest bucket starts at 1 (e.g. "$1–$20"), which reads oddly — say "Under $20".
  if (r.min != null && r.max != null) return r.min <= 1 ? `Under ${money(r.max, r.currency)} pp` : `${money(r.min, r.currency)}–${money(r.max, r.currency)} pp`;
  return r.min != null ? `${money(r.min, r.currency)}+ pp` : `up to ${money(r.max, r.currency)} pp`;
}

function priceLineHTML(v) {
  const lvl = v.priceLevel, range = rangeText(v);
  if (lvl) return `<strong>${'$'.repeat(lvl)}</strong><span class="ghost">${'$'.repeat(4 - lvl)}</span><span class="pname">${esc(range || PRICE_NAMES[lvl])}</span>`;
  if (range) return `<strong>${esc(range)}</strong>`;
  return `<strong class="na">Price not listed</strong>`;
}

function ratingHTML(v) {
  if (!v.rating) return '<span class="muted">No ratings yet</span>';
  return `<span class="rating"><span class="star">${icon('star')}</span><b>${v.rating.toFixed(1)}</b><span class="muted">(${fmtCount(v.reviews)})</span></span>`;
}

function placeholderHTML(v, cls = '') {
  return `<div class="ph ${cls}" style="--h:${hashHue(v.name)}"><span>${emojiFor(v)}</span></div>`;
}

function statusBadge(v) {
  if (v.businessStatus === 'CLOSED_TEMPORARILY') return '<span class="badge b-closed">Temporarily closed</span>';
  if (!v.open) return '';
  if (v.open.open) return `<span class="badge ${v.open.soon ? 'b-soon' : 'b-open'}">${v.open.soon ? 'Closing soon' : 'Open now'}</span>`;
  return '<span class="badge b-closed">Closed</span>';
}

function cardHTML(v) {
  const saved = state.shortlist.has(v.id);
  const ph = v.photos?.[0];
  const badges = [statusBadge(v)];
  if (v.health.score >= 65) badges.push(`<span class="badge b-healthy">${icon('leaf')} Healthy pick</span>`);
  if (dietStatus(v, 'vegan') === 'confirmed') badges.push('<span class="badge b-diet">Vegan</span>');
  const feats = FEATURES.filter(f => ['dineIn', 'takeout', 'delivery', 'reservable'].includes(f.key) && v.features?.[f.key] === true)
    .map(f => `<li title="${esc(f.label)}">${icon(f.icon)}<span>${esc(f.short || f.label)}</span></li>`);
  if (dietOk(v, 'vegetarian')) feats.push(`<li title="Vegetarian options">${icon('leaf')}<span>Veg</span></li>`);
  const tags = [v.category, ...v.cuisines.slice(0, 2)].filter((x, i, a) => x && a.indexOf(x) === i);
  const id = esc(v.id);
  return `<article class="card${state.activeId === v.id ? ' active' : ''}" data-action="open" data-id="${id}" tabindex="0">
    <div class="card-media">
      ${ph ? `<img src="${esc(ph.thumb)}" alt="" loading="lazy">` : placeholderHTML(v)}
      <div class="badges">${badges.join('')}</div>
      ${v.photos?.length > 1 ? `<span class="photo-count">${icon('camera')} ${v.photos.length}</span>` : ''}
    </div>
    <button class="heart${saved ? ' on' : ''}" data-action="save" data-id="${id}" aria-pressed="${saved}" aria-label="Save ${esc(v.name)} to shortlist">${icon('heart')}</button>
    <div class="card-body">
      <div class="price-line">${priceLineHTML(v)}</div>
      <h3>${esc(v.name)}</h3>
      <p class="addr">${esc(v.address)}</p>
      <div class="meta">${ratingHTML(v)}<span class="muted">·</span><span>${fmtDistance(v.distance)} away</span></div>
      ${feats.length ? `<ul class="feats">${feats.join('')}</ul>` : ''}
    </div>
    <div class="card-foot">${tags.map(t => `<span class="tag">${esc(t)}</span>`).join('')}<span class="walk">${icon('walk')} ${walkMins(v.distance)} min walk</span></div>
  </article>`;
}

const QUICK = [
  ['open', '', 'Open now', f => f.openNow],
  ['healthy', '', 'Healthy', f => f.healthy, 'leaf'],
  ['diet', 'vegetarian', 'Vegetarian', f => f.diets.has('vegetarian')],
  ['diet', 'vegan', 'Vegan', f => f.diets.has('vegan')],
  ['diet', 'glutenFree', 'Gluten-free', f => f.diets.has('glutenFree')],
  ['price', 1, '$', f => f.prices.has(1)],
  ['price', 2, '$$', f => f.prices.has(2)],
  ['price', 3, '$$$', f => f.prices.has(3)],
  ['price', 4, '$$$$', f => f.prices.has(4)],
  ['cat', 'Cafe', 'Cafés', f => f.categories.has('Cafe')],
  ['meal', 'breakfast', 'Breakfast', f => f.meals.has('breakfast')],
  ['meal', 'dinner', 'Dinner', f => f.meals.has('dinner')],
];
function renderQuickChips() {
  const f = state.filters;
  $('#quick-chips').innerHTML = QUICK.map(([g, v, label, on, ic]) =>
    `<button class="qchip${on(f) ? ' on' : ''}" data-action="f" data-f="${g}" data-v="${v}" aria-pressed="${on(f)}">${ic ? icon(ic) : ''}${label}</button>`).join('');
}

function updateCounts() {
  const n = activeFilterList().length;
  $('#filter-count').textContent = n ? String(n) : '';
  $('#sl-count').textContent = state.shortlist.size ? String(state.shortlist.size) : '';
}

// =====================================================================
// Filters drawer
// =====================================================================
function tally(arr) {
  const m = new Map();
  for (const x of arr) m.set(x, (m.get(x) || 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
}

function renderDrawer() {
  const prevScroll = $('#drawer .drawer-body')?.scrollTop || 0;
  const f = state.filters;
  const base = getBase();
  const n = getFiltered().length;
  const chip = (g, val, label, on, c) =>
    `<button class="fchip${on ? ' on' : ''}" data-action="f" data-f="${g}" data-v="${esc(val)}" aria-pressed="${on}">${label}${c != null ? ` <span class="c">${c}</span>` : ''}</button>`;
  const count = fn => base.filter(fn).length;
  const cats = CATEGORIES.map(c => [c, count(v => v.category === c)]).filter(([, c]) => c);
  const cuisines = tally(base.flatMap(v => v.cuisines));

  $('#drawer').innerHTML = `<div class="drawer-panel" role="dialog" aria-modal="true" aria-label="Filters">
    <header><h2>Filters</h2><button class="icon-btn" data-action="close-drawer" aria-label="Close">${icon('x')}</button></header>
    <div class="drawer-body">
      <div class="fsec"><h3>Quick</h3><div class="fchips">
        ${chip('open', '', 'Open now', f.openNow, count(v => v.open?.open))}
        ${chip('healthy', '', `${icon('leaf')} Healthy picks`, f.healthy, count(v => v.health.score >= 65))}
      </div></div>
      <div class="fsec"><h3>Budget</h3><div class="fchips">
        ${[1, 2, 3, 4].map(p => chip('price', p, `${'$'.repeat(p)} <span class="c">${PRICE_NAMES[p]}</span>`, f.prices.has(p), count(v => v.priceLevel === p))).join('')}
      </div>
      <label class="toggle fhint" style="margin-top:10px"><input type="checkbox" data-action="f" data-f="unknownPrice" ${f.unknownPrice ? 'checked' : ''}> Include places with no price listed (${count(v => v.priceLevel == null)})</label></div>
      <div class="fsec"><h3>Dietary</h3><div class="fchips">
        ${DIETS.map(d => chip('diet', d.key, d.label, f.diets.has(d.key), count(v => dietOk(v, d.key)))).join('')}
      </div><p class="fhint">Vegetarian comes from Google’s listing where available. Vegan, gluten-free and halal are matched from the venue type, name and description, so always check the menu.</p></div>
      <div class="fsec"><h3>Meal</h3><div class="fchips">
        ${MEALS.map(m => chip('meal', m.key, m.label, f.meals.has(m.key), count(v => v.meals[m.key] === true))).join('')}
      </div></div>
      <div class="fsec"><h3>Type of place</h3><div class="fchips">
        ${cats.map(([c, k]) => chip('cat', c, esc(c), f.categories.has(c), k)).join('')}
      </div></div>
      <div class="fsec"><h3>Cuisine</h3><div class="fchips">
        ${cuisines.length ? cuisines.map(([c, k]) => chip('cuisine', c, esc(c), f.cuisines.has(c), k)).join('') : '<span class="fhint">No cuisines detected in these results.</span>'}
      </div></div>
      <div class="fsec"><h3>Minimum rating</h3><div class="fchips">
        ${[3.5, 4, 4.5].map(r => chip('rating', r, `${r}+ ★`, f.minRating === r, count(v => (v.rating || 0) >= r))).join('')}
      </div></div>
      <div class="fsec"><h3>Features</h3><div class="fchips">
        ${FEATURES.map(ft => chip('feature', ft.key, `${icon(ft.icon)} ${ft.label}`, f.features.has(ft.key), count(v => v.features?.[ft.key] === true))).join('')}
      </div></div>
    </div>
    <footer><button class="btn-link" data-action="clear-filters">Clear all</button><button class="btn-primary" data-action="close-drawer">Show ${n} place${n === 1 ? '' : 's'}</button></footer>
  </div>`;
  $('#drawer .drawer-body').scrollTop = prevScroll;
}
function openDrawer() { $('#drawer').hidden = false; renderDrawer(); }
function closeDrawer() { $('#drawer').hidden = true; $('#drawer').innerHTML = ''; }

// =====================================================================
// Map interactions
// =====================================================================
function onPinClick(id) {
  state.activeId = id;
  state.map?.highlight(id);
  $$('.card.active').forEach(c => c.classList.remove('active'));
  const card = $(`.card[data-id="${CSS.escape(id)}"]`);
  if (card) {
    card.classList.add('active');
    if (document.body.classList.contains('view-split') && innerWidth > 900) {
      card.scrollIntoView({ behavior: 'smooth', block: 'center' });
      card.classList.remove('flash'); void card.offsetWidth; card.classList.add('flash');
    }
  }
  showPreview(id);
}

function showPreview(id) {
  const v = findVenue(id); if (!v) return;
  const ph = v.photos?.[0];
  const el = $('#map-preview');
  el.innerHTML = `<div class="mp-img">${ph ? `<img src="${esc(ph.thumb)}" alt="">` : placeholderHTML(v)}</div>
    <div class="mp-body" data-action="open" data-id="${esc(v.id)}">
      <div class="price-line">${priceLineHTML(v)}</div>
      <h3>${esc(v.name)}</h3>
      <div class="meta">${ratingHTML(v)}<span class="muted">·</span><span>${fmtDistance(v.distance)}</span></div>
      <div class="meta">${v.open ? `<span class="${v.open.open ? (v.open.soon ? 'st-soon' : 'st-open') : 'st-closed'}">${esc(v.open.label)}</span>` : `<span class="muted">${esc(v.category)}</span>`}</div>
    </div>
    <button class="x" data-action="preview-close" aria-label="Close preview">${icon('x')}</button>`;
  el.dataset.id = v.id;
  el.hidden = false;
}
function closePreview() { $('#map-preview').hidden = true; }

function setView(v) {
  state.view = ['list', 'split', 'map'].includes(v) ? v : 'split';
  document.body.classList.remove('view-list', 'view-split', 'view-map');
  document.body.classList.add('view-' + state.view);
  $$('#view-seg button').forEach(b => b.classList.toggle('on', b.dataset.v === state.view));
  store.set('view', state.view);
  state.map?.resize();
}

function toggleMobileMap(force) {
  const on = force ?? !document.body.classList.contains('mobile-map');
  document.body.classList.toggle('mobile-map', on);
  $('#fab').innerHTML = on ? `${icon('list')} <span>List</span>` : `${icon('map')} <span>Map</span>`;
  state.map?.resize();
}

// =====================================================================
// Detail page
// =====================================================================
function findVenue(id) { return state.venues.find(v => v.id === id) || state.shortlist.get(id); }

function openDetail(id, push = true) {
  const v = findVenue(id); if (!v) return;
  if (!v.open && v.hours) v.open = openStatus(v.hours);
  const el = $('#detail');
  el.innerHTML = detailHTML(v);
  el.hidden = false; el.scrollTop = 0;
  document.body.classList.add('no-scroll');
  if (push) history.pushState({ detail: id }, '', `#place/${encodeURIComponent(id)}`);
  state.detailOpen = id;
  const mm = $('#mini-map', el);
  if (mm) {
    Promise.resolve()
      .then(() => state.mode === 'live' ? GoogleMapView.mini(mm, v) : window.L ? LeafletMapView.mini(mm, v) : null)
      .catch(e => { console.warn(e); mm.remove(); });
  }
}

function closeDetail(fromPop = false) {
  if (!state.detailOpen) return;
  state.detailOpen = null;
  $('#detail').hidden = true; $('#detail').innerHTML = '';
  document.body.classList.remove('no-scroll');
  if (!fromPop && history.state?.detail) history.back();
}

function yesNo(val, yes = 'Yes', no = 'No') {
  if (val === true) return `<span class="yes">${icon('check')} ${yes}</span>`;
  if (val === false) return `<span class="no">${no}</span>`;
  return '<span class="unk">Not listed</span>';
}

function dietCell(v, key) {
  const s = dietStatus(v, key);
  if (s === 'confirmed') return '<span class="yes">Yes</span><small>From Google listing / venue type</small>';
  if (s === 'likely') return '<span class="likely">Likely</span><small>Mentioned in name or description</small>';
  if (s === 'no') return '<span class="no">No</span><small>Per Google listing</small>';
  return '<span class="unk">Not listed</span><small>Check the menu</small>';
}

function detailHTML(v) {
  const L = v.links, id = esc(v.id);
  const saved = state.shortlist.has(v.id);
  const photos = v.photos || [];
  const shown = photos.slice(0, 5);
  const authors = [...new Map(photos.filter(p => p.author).map(p => [p.author, p])).values()].slice(0, 4);
  const gallery = photos.length
    ? `<div class="gallery g${shown.length}">
        ${shown.map((p, i) => `<button class="g-item" data-action="photo" data-id="${id}" data-i="${i}" aria-label="View photo ${i + 1}"><img src="${esc(i === 0 ? p.url : p.thumb)}" alt="" loading="${i ? 'lazy' : 'eager'}"></button>`).join('')}
        ${photos.length > 1 ? `<button class="g-more" data-action="photo" data-id="${id}" data-i="0">${icon('camera')} View ${photos.length} photos</button>` : ''}
      </div>
      ${authors.length ? `<p class="attrib">Photos via Google Maps: ${authors.map(p => p.authorUri ? `<a href="${esc(p.authorUri)}" target="_blank" rel="noopener">${esc(p.author)}</a>` : esc(p.author)).join(', ')}</p>` : ''}`
    : `<div class="gallery g0"><div class="g-item">${placeholderHTML(v, 'lg')}</div></div>`;

  const statusCls = v.open ? (v.open.open ? (v.open.soon ? 'st-soon' : 'st-open') : 'st-closed') : '';
  const statusTxt = v.businessStatus === 'CLOSED_TEMPORARILY' ? '<span class="st-closed">Temporarily closed</span>'
    : v.open ? `<span class="${statusCls}">${icon('clock')} ${esc(v.open.label)}</span>` : '<span class="muted">Hours not listed</span>';
  const about = v.summary || `${v.category}${v.cuisine ? ` serving ${v.cuisine} food` : ''}${v.suburb ? ` in ${v.suburb}` : ''}.`;
  const todayIdx = (new Date().getDay() + 6) % 7;
  const highlights = [
    v.health.score >= 65 ? `<span class="tag">${icon('leaf')} Healthy pick</span>` : '',
    v.comfort ? '<span class="tag">Comfort food</span>' : '',
    ...v.cuisines.map(c => `<span class="tag">${esc(c)}</span>`),
    v.primaryTypeLabel && !v.cuisines.some(c => v.primaryTypeLabel.toLowerCase().includes(c.toLowerCase())) &&
      !/^(restaurant|cafe|café)$/i.test(v.primaryTypeLabel) ? `<span class="tag">${esc(v.primaryTypeLabel)}</span>` : '',
  ].filter(Boolean).join('');

  const others = state.venues.filter(o => o.id !== v.id);
  const similar = others
    .filter(o => o.cuisines.some(c => v.cuisines.includes(c)) || o.category === v.category)
    .sort((a, b) => b.score - a.score).slice(0, 3);
  if (similar.length < 3) {
    const near = others.filter(o => !similar.includes(o) && (o.rating || 0) >= 4)
      .sort((a, b) => haversine(v, a) - haversine(v, b));
    similar.push(...near.slice(0, 3 - similar.length));
  }

  return `
  <div class="d-top"><div class="d-top-in">
    <button class="btn-ghost" data-action="close-detail">${icon('chevronL')} Back to results</button>
    <span class="sp"></span>
    <button class="btn-ghost" data-action="share" data-id="${id}">${icon('share')}<span class="hide-sm">Share</span></button>
    <button class="btn-ghost heart-btn${saved ? ' on' : ''}" data-action="save" data-id="${id}">${icon('heart')}<span class="hide-sm">${saved ? 'Saved' : 'Save'}</span></button>
  </div></div>
  <div class="d-wrap">
    ${gallery}
    <div class="d-grid">
      <div class="d-main">
        <div class="d-title">
          <div class="crumbs"><span>${esc(v.category)}</span>${v.cuisine ? `<span>·</span><span>${esc(v.cuisine)}</span>` : ''}${v.suburb ? `<span>·</span><span>${esc(v.suburb)}</span>` : ''}</div>
          <h1>${esc(v.name)}</h1>
          <p class="addr">${icon('pin')} ${esc(v.address)}</p>
          <div class="status-line">${statusTxt}${v.rating ? `<a href="${esc(L.maps)}" target="_blank" rel="noopener" style="text-decoration:none">${ratingHTML(v)} <span class="muted">Google reviews</span></a>` : ''}</div>
        </div>

        <div class="stats">
          <div class="stat"><b>${priceText(v) || '—'}</b><span>${esc(rangeText(v) || (v.priceLevel ? PRICE_NAMES[v.priceLevel] : 'Price not listed'))}</span></div>
          <div class="stat"><b>${v.rating ? v.rating.toFixed(1) + ' ★' : '—'}</b><span>${v.reviews ? fmtCount(v.reviews) + ' reviews' : 'No reviews yet'}</span></div>
          <div class="stat"><b>${fmtDistance(v.distance)}</b><span>~${walkMins(v.distance)} min walk</span></div>
          <div class="stat"><b>${v.health.score}<span style="font-size:13px;font-weight:600">/100</span></b><span>${esc(v.health.label)}</span></div>
        </div>
        ${highlights ? `<div class="chiprow">${highlights}</div>` : ''}

        <section class="d-sec"><h2>About</h2><p>${esc(about)}</p></section>

        <section class="d-sec"><h2>Dietary options</h2>
          <div class="kv">${DIETS.map(d => `<div class="kv-item" style="align-items:flex-start">${icon('leaf')}<div class="lbl"><b>${d.label}</b> · ${dietCell(v, d.key)}</div></div>`).join('')}</div>
          <p class="note">${icon('info')} Allergies or strict requirements? Always confirm with the venue directly.</p>
        </section>

        <section class="d-sec"><h2>What they serve</h2>
          <div class="kv">${MEALS.map(m => `<div class="kv-item"><div class="lbl">${m.label}</div>${yesNo(v.meals[m.key])}</div>`).join('')}</div>
        </section>

        <section class="d-sec"><h2>Good to know</h2>
          <div class="kv">${FEATURES.map(f => `<div class="kv-item">${icon(f.icon)}<div class="lbl">${f.label}</div>${yesNo(v.features?.[f.key])}</div>`).join('')}</div>
        </section>

        <section class="d-sec"><h2>Opening hours</h2>
          ${v.hours?.weekdayText?.length
            ? `<ul class="hours">${v.hours.weekdayText.map((line, i) => { const [d, ...rest] = line.split(': '); return `<li class="${i === todayIdx ? 'today' : ''}"><span>${esc(d)}${i === todayIdx ? ' (today)' : ''}</span><span>${esc(rest.join(': '))}</span></li>`; }).join('')}</ul>`
            : '<p class="muted">Opening hours aren’t listed. Check the website or call ahead.</p>'}
        </section>

        <section class="d-sec"><h2>How healthy?</h2>
          <div class="health"><div class="health-num">${v.health.score}</div><div class="health-bar"><i style="width:${v.health.score}%"></i></div><b>${esc(v.health.label)}</b></div>
          ${v.health.reasons.length ? `<div class="reasons">${v.health.reasons.slice(0, 8).map(r => `<span class="tag">${esc(r)}</span>`).join('')}</div>` : ''}
          <p class="note">${icon('info')} A rough estimate from the venue type, name and description, not a nutrition rating. Most places have lighter and heavier options.</p>
        </section>

        <section class="d-sec"><h2>Location</h2>
          <p>${esc(v.address)} · ${fmtDistance(v.distance)} from ${esc(state.areaLabel || 'search centre')}</p>
          <div id="mini-map"></div>
        </section>

        ${similar.length ? `<section class="d-sec similar"><h2>You might also like</h2><div class="cards">${similar.map(cardHTML).join('')}</div></section>` : ''}
      </div>

      <aside class="contact-card">
        <div class="cc-head"><div class="cc-price">${priceText(v) || '<span class="muted" style="font-size:15px">Price n/a</span>'}</div>${statusTxt}</div>
        <a class="btn-primary block" href="${esc(L.directions)}" target="_blank" rel="noopener">${icon('nav')} Get directions</a>
        <div class="cc-grid">
          ${L.website ? `<a class="cc-btn" href="${esc(L.website)}" target="_blank" rel="noopener">${icon('globe')} Website</a>` : ''}
          <a class="cc-btn" href="${esc(L.menuSearch)}" target="_blank" rel="noopener">${icon('book')} Find menu</a>
          <a class="cc-btn" href="${esc(L.instagram || L.instagramSearch)}" target="_blank" rel="noopener">${icon('instagram')} ${L.instagram ? 'Instagram' : 'Find Instagram'}</a>
          ${L.phone ? `<a class="cc-btn" href="${esc(L.phone)}">${icon('phone')} ${esc(v.phone)}</a>` : ''}
          <a class="cc-btn" href="${esc(L.maps)}" target="_blank" rel="noopener">${icon('pin')} Google Maps</a>
          <button class="cc-btn" data-action="share" data-id="${id}">${icon('share')} Share</button>
        </div>
        <button class="btn-outline block heart-btn${saved ? ' on' : ''}" data-action="save" data-id="${id}">${icon('heart')} ${saved ? 'Saved to shortlist' : 'Save to shortlist'}</button>
        <p class="cc-note">Google doesn’t publish menus or Instagram handles, so “Find” runs a targeted web search. The Google Maps page often has a Menu tab, and the venue website usually links to its menu.</p>
      </aside>
    </div>
  </div>`;
}

// =====================================================================
// Shortlist, share, lightbox
// =====================================================================
function snapshot(v) { return JSON.parse(JSON.stringify(v)); }
function saveShortlist() { store.set('shortlist', [...state.shortlist.values()]); }

function toggleSave(id) {
  const v = findVenue(id); if (!v) return;
  const on = !state.shortlist.has(id);
  if (on) state.shortlist.set(id, snapshot(v)); else state.shortlist.delete(id);
  saveShortlist();
  for (const b of $$(`[data-action="save"][data-id="${CSS.escape(id)}"]`)) {
    b.classList.toggle('on', on);
    b.setAttribute('aria-pressed', on);
    if (b.classList.contains('btn-outline')) b.innerHTML = `${icon('heart')} ${on ? 'Saved to shortlist' : 'Save to shortlist'}`;
    else if (b.classList.contains('btn-ghost')) b.innerHTML = `${icon('heart')}<span class="hide-sm">${on ? 'Saved' : 'Save'}</span>`;
  }
  updateCounts();
  showToast(on ? `Saved ${v.name} to your shortlist` : `Removed ${v.name} from your shortlist`);
  if (state.shortlistOnly) renderResults();
}

function toggleShortlistView() {
  closeDetail();
  state.shortlistOnly = !state.shortlistOnly;
  state.shown = PAGE;
  $('#sl-btn').classList.toggle('on', state.shortlistOnly);
  if (!state.shortlistOnly && !state.searched) { renderEmpty(state.mode ? 'start' : 'welcome'); state.map?.setMarkers([], onPinClick); return; }
  renderResults();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function share(id) {
  const v = findVenue(id); if (!v) return;
  const text = `${v.name} — ${v.address}`;
  try {
    if (navigator.share) { await navigator.share({ title: v.name, text, url: v.links.maps }); return; }
    await navigator.clipboard.writeText(`${text}\n${v.links.maps}`);
    showToast('Copied name, address and map link');
  } catch { /* user cancelled */ }
}

function openLightbox(id, i) {
  const v = findVenue(id); if (!v?.photos?.length) return;
  state.lb = { v, i: +i || 0 };
  renderLightbox();
  $('#lightbox').hidden = false;
}
function renderLightbox() {
  const { v, i } = state.lb, p = v.photos[i];
  const by = p.author ? ` · Photo by ${p.authorUri ? `<a href="${esc(p.authorUri)}" target="_blank" rel="noopener">${esc(p.author)}</a>` : esc(p.author)}` : '';
  $('#lightbox').innerHTML = `
    <button class="lb-btn lb-close" data-action="lb-close" aria-label="Close">${icon('x')}</button>
    ${v.photos.length > 1 ? `<button class="lb-btn lb-prev" data-action="lb-step" data-v="-1" aria-label="Previous">${icon('chevronL')}</button>` : ''}
    <figure><img src="${esc(p.url)}" alt="${esc(v.name)} photo ${i + 1}"><figcaption>${esc(v.name)} · ${i + 1} / ${v.photos.length}${by}</figcaption></figure>
    ${v.photos.length > 1 ? `<button class="lb-btn lb-next" data-action="lb-step" data-v="1" aria-label="Next">${icon('chevronR')}</button>` : ''}`;
}
function stepLightbox(d) {
  if (!state.lb) return;
  const n = state.lb.v.photos.length;
  state.lb.i = (state.lb.i + d + n) % n;
  renderLightbox();
}
function closeLightbox() { state.lb = null; $('#lightbox').hidden = true; $('#lightbox').innerHTML = ''; }

// =====================================================================
// "Help me choose" wizard
// =====================================================================
function wizardQuestions() {
  const topCuisines = tally(state.venues.flatMap(v => v.cuisines)).slice(0, 8).map(([c]) => [c, c]);
  return [
    { key: 'meal', q: 'What are you after?', opts: [['breakfast', '🍳 Breakfast / brunch'], ['lunch', '🥪 Lunch'], ['dinner', '🍝 Dinner'], ['coffee', '☕ Coffee & a bite'], ['dessert', '🍨 Something sweet'], ['any', '🎲 Surprise me']] },
    { key: 'budget', q: 'What’s the budget?', opts: [['1', '$ Cheap eats'], ['2', '$$ Mid-range'], ['3', '$$$ Treat yourself'], ['4', '$$$$ Splurge'], ['any', 'Any budget']] },
    { key: 'diet', q: 'Any dietary needs?', opts: [['none', 'None'], ...DIETS.map(d => [d.key, d.label])] },
    { key: 'vibe', q: 'How are you feeling?', opts: [['healthy', '🥗 Something healthy'], ['comfort', '🍔 Comfort food'], ['any', 'Don’t mind']] },
    { key: 'cuisine', q: 'Craving a cuisine?', opts: [['any', 'Anything'], ...topCuisines] },
    { key: 'dist', q: 'How far will you go?', opts: [['800', '🚶 Walking distance'], ['2000', 'Within 2 km'], ['any', 'Anywhere in the area']] },
  ];
}

function openWizard() {
  if (!state.venues.length) { showToast('Search an area first, then I can help you choose.'); $('#q').focus(); return; }
  closeDetail();
  const h = new Date().getHours();
  state.wiz = {
    a: {
      meal: h < 11 ? 'breakfast' : h < 15 ? 'lunch' : h < 17 ? 'coffee' : 'dinner',
      budget: 'any', diet: [...state.filters.diets][0] || 'none', vibe: state.filters.healthy ? 'healthy' : 'any',
      cuisine: 'any', dist: 'any', openNow: true,
    },
    ranked: null, idx: 0,
  };
  renderWizard();
  $('#modal').hidden = false;
}

function rankForWizard(a) {
  let pool = state.venues.filter(v => v.businessStatus !== 'CLOSED_TEMPORARILY');
  if (a.diet !== 'none') pool = pool.filter(v => dietOk(v, a.diet));
  if (a.budget !== 'any') pool = pool.filter(v => v.priceLevel == null || v.priceLevel <= +a.budget);
  if (a.dist !== 'any') pool = pool.filter(v => v.distance <= +a.dist);
  if (a.openNow) pool = pool.filter(v => v.open?.open !== false);
  if (a.cuisine !== 'any') pool = pool.filter(v => v.cuisines.includes(a.cuisine));
  if (a.meal !== 'any') pool = pool.filter(v => v.meals[a.meal] !== false);
  const mealLabel = { breakfast: 'breakfast', lunch: 'lunch', dinner: 'dinner', coffee: 'coffee', dessert: 'dessert' }[a.meal];
  const dietLabel = DIETS.find(d => d.key === a.diet)?.label;
  return pool.map(v => {
    let s = v.score; const why = [];
    if (a.meal !== 'any' && v.meals[a.meal] === true) { s += 12; why.push(`Serves ${mealLabel}`); }
    if (a.vibe === 'healthy') { s += (v.health.score - 50) * 0.7; if (v.health.score >= 65) why.push('Healthy pick'); }
    if (a.vibe === 'comfort') { if (v.comfort) { s += 15; why.push('Comfort food'); } else s -= 6; }
    if (a.budget !== 'any' && v.priceLevel === +a.budget) s += 4;
    if (a.cuisine !== 'any') why.push(`${a.cuisine} food`);
    if (dietLabel) why.push(dietStatus(v, a.diet) === 'confirmed' ? `${dietLabel} options` : `${dietLabel} (likely, check the menu)`);
    if (v.rating) why.push(`${v.rating.toFixed(1)}★ from ${v.reviews.toLocaleString()} reviews`);
    why.push(`${fmtDistance(v.distance)} away (~${walkMins(v.distance)} min walk)`);
    if (v.open?.open) why.push(v.open.label);
    if (a.budget !== 'any' && v.priceLevel == null) why.push('Price not listed');
    return { v, s, why };
  }).sort((x, y) => y.s - x.s);
}

function renderWizard(spun = false) {
  const w = state.wiz;
  let html;
  if (!w.ranked) {
    html = `<h2>Help me choose ✨</h2><p class="sub">Answer a few quick questions and I’ll pick from the ${state.venues.length} places in ${esc(state.areaLabel)}.</p>
      ${wizardQuestions().map(q => `<div class="wq"><h3>${q.q}</h3><div class="fchips">
        ${q.opts.map(([val, label]) => `<button class="fchip${w.a[q.key] === val ? ' on' : ''}" data-action="w-opt" data-k="${q.key}" data-v="${esc(val)}">${esc(label)}</button>`).join('')}
      </div></div>`).join('')}
      <div class="w-foot">
        <label class="toggle"><input type="checkbox" data-action="w-open" ${w.a.openNow ? 'checked' : ''}> Only places open now (or hours unknown)</label>
        <button class="btn-primary" data-action="w-go">${icon('sparkles')} Show my picks</button>
      </div>`;
  } else if (!w.ranked.length) {
    html = `<h2>Hmm, nothing fits all of that</h2><p class="sub">Try relaxing the budget, distance or “open now”.</p>
      <div class="w-foot"><button class="btn-outline" data-action="w-back">${icon('chevronL')} Change answers</button></div>`;
  } else {
    const top = w.ranked[w.idx];
    const others = w.ranked.filter((_, i) => i !== w.idx).slice(0, 2);
    const ph = top.v.photos?.[0];
    html = `<h2>${spun ? 'How about…' : 'Your top pick'}</h2><p class="sub">${w.ranked.length} place${w.ranked.length === 1 ? '' : 's'} match. Here’s the best fit.</p>
      <div class="pick${spun ? ' spin' : ''}" data-action="w-open-venue" data-id="${esc(top.v.id)}">
        <div class="pick-media">${ph ? `<img src="${esc(ph.thumb)}" alt="">` : placeholderHTML(top.v)}</div>
        <div class="pick-body">
          <div class="eyebrow">${esc([top.v.category, top.v.cuisine].filter(Boolean).join(' · '))}</div>
          <h3>${esc(top.v.name)}</h3>
          <div class="price-line">${priceLineHTML(top.v)}</div>
          <ul class="why">${top.why.slice(0, 6).map(r => `<li>${icon('check')}<span>${esc(r)}</span></li>`).join('')}</ul>
        </div>
      </div>
      ${others.length ? `<h3 style="font-size:15px;margin:18px 0 0">Also worth a look</h3><div class="runners">${others.map(o => `
        <button class="runner" data-action="w-open-venue" data-id="${esc(o.v.id)}">
          <div class="thumb">${o.v.photos?.[0] ? `<img src="${esc(o.v.photos[0].thumb)}" alt="">` : placeholderHTML(o.v)}</div>
          <div><b>${esc(o.v.name)}</b><span>${esc([priceText(o.v), o.v.rating ? o.v.rating.toFixed(1) + '★' : '', fmtDistance(o.v.distance)].filter(Boolean).join(' · '))}</span></div>
        </button>`).join('')}</div>` : ''}
      <div class="w-foot" style="margin-top:18px">
        <button class="btn-outline" data-action="w-back">${icon('chevronL')} Change answers</button>
        <span style="display:flex;gap:8px;flex-wrap:wrap">
          ${w.ranked.length > 1 ? `<button class="btn-outline" data-action="w-spin">${icon('dice')} Spin again</button>` : ''}
          <button class="btn-primary" data-action="w-apply" title="Applies these answers as filters on the main list">Filter the list like this</button>
        </span>
      </div>`;
  }
  $('#modal').innerHTML = `<div class="modal-card" role="dialog" aria-modal="true" aria-label="Help me choose">
    <button class="icon-btn x" data-action="close-modal" aria-label="Close">${icon('x')}</button>${html}</div>`;
}

function spinWizard() {
  const w = state.wiz;
  const top = w.ranked.slice(0, Math.min(8, w.ranked.length));
  const max = top[0].s;
  const weights = top.map((r, i) => i === w.idx ? 0 : Math.exp((r.s - max) / 10));
  let x = Math.random() * weights.reduce((a, b) => a + b, 0);
  let pick = 0;
  for (let i = 0; i < weights.length; i++) { x -= weights[i]; if (x <= 0 && weights[i] > 0) { pick = i; break; } }
  w.idx = pick;
  renderWizard(true);
}

function applyWizardAsFilters() {
  const a = state.wiz.a;
  const f = freshFilters();
  if (a.diet !== 'none') f.diets.add(a.diet);
  if (a.budget !== 'any') for (let p = 1; p <= +a.budget; p++) f.prices.add(p);
  if (a.vibe === 'healthy') f.healthy = true;
  if (a.cuisine !== 'any') f.cuisines.add(a.cuisine);
  if (a.openNow) f.openNow = true;
  if (['breakfast', 'lunch', 'dinner', 'coffee', 'dessert'].includes(a.meal)) f.meals.add(a.meal);
  state.filters = f;
  state.shortlistOnly = false;
  state.shown = PAGE;
  closeModal();
  renderAll();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

// =====================================================================
// Settings & welcome
// =====================================================================
function showWelcome() {
  store.set('seenWelcome', true);
  $('#modal').innerHTML = `<div class="modal-card" role="dialog" aria-modal="true" aria-label="Welcome" data-sticky>
    <button class="icon-btn x" data-action="close-modal" aria-label="Close">${icon('x')}</button>
    <h2>Welcome to Forkcast 🍴</h2>
    <p class="sub">Forkcast finds every restaurant and café around a postcode, shows them listing-style, and helps you decide by budget, diet, cuisine and how healthy you’re feeling.</p>
    <div class="welcome-opts">
      <div class="opt">
        <h3>Connect Google Maps</h3>
        <p>Paste a Google Maps Platform API key with <b>Maps JavaScript API</b> and <b>Places API (New)</b> enabled.</p>
        <input id="welcome-key" class="input" type="password" placeholder="AIza…" autocomplete="off" spellcheck="false">
        <button class="btn-primary push" data-action="connect">Connect &amp; search</button>
        <a class="fine" href="README.md" target="_blank" rel="noopener">How do I get a key?</a>
      </div>
      <div class="opt">
        <h3>Just looking?</h3>
        <p>Explore the app with made-up sample venues. No key needed.</p>
        <button class="btn-outline push" data-action="demo">Try the demo</button>
      </div>
    </div>
    <p class="fine">Your key is stored only in this browser and is sent only to Google.</p>
  </div>`;
  $('#modal').hidden = false;
}

function openSettings() {
  const key = store.get('apiKey', '') || cfg.googleMapsApiKey || '';
  const demo = state.mode === 'demo';
  $('#modal').innerHTML = `<div class="modal-card" role="dialog" aria-modal="true" aria-label="Settings" data-sticky>
    <button class="icon-btn x" data-action="close-modal" aria-label="Close">${icon('x')}</button>
    <h2>Settings</h2>
    <p class="sub">Connect Google Maps to search real places. Settings are saved in this browser.</p>
    <div class="form-row">
      <label for="set-key">Google Maps Platform API key</label>
      <div style="display:flex;gap:8px"><input id="set-key" class="input" type="password" value="${esc(key)}" placeholder="AIza…" autocomplete="off" spellcheck="false">
      <button class="btn-outline" data-action="toggle-key" type="button">Show</button></div>
      <ol class="steps">
        <li>In Google Cloud Console, create a project and turn on billing (Google includes a monthly free allowance).</li>
        <li>Enable <b>Maps JavaScript API</b>, <b>Places API (New)</b> and, optionally, <b>Geocoding API</b>.</li>
        <li>Create an API key and restrict it to HTTP referrer <code>http://localhost:8000/*</code>.</li>
      </ol>
    </div>
    <div class="form-row">
      <label for="set-country">Country for postcode search</label>
      <select id="set-country" class="input">${Object.entries(COUNTRIES).map(([c, n]) => `<option value="${c}" ${c === state.country ? 'selected' : ''}>${n}</option>`).join('')}</select>
    </div>
    <div class="form-row">
      <label>Search depth</label>
      <div class="radios">${Object.entries(DEPTHS).map(([k, [name, desc]]) => `<label><input type="radio" name="depth" value="${k}" ${k === state.depth ? 'checked' : ''}><span><b>${name}</b><small>${desc}</small></span></label>`).join('')}</div>
      <p class="fine">Each Places request returns up to 20 places, so deeper searches find more but use more of your Google quota. Set a budget alert in Google Cloud to be safe.</p>
    </div>
    <div class="form-row"><label class="toggle"><input type="checkbox" id="set-demo" ${demo ? 'checked' : ''}> Use demo data instead of Google</label></div>
    <div class="w-foot"><span class="fine">Changes apply when you click Save.</span><button class="btn-primary" data-action="save-settings">Save settings</button></div>
  </div>`;
  $('#modal').hidden = false;
  // A newly typed key almost always means "use Google now", so switch demo off for them.
  $('#set-key').addEventListener('input', e => { if (e.target.value.trim()) $('#set-demo').checked = false; });
}

function saveSettings() {
  const key = $('#set-key').value.trim();
  const country = $('#set-country').value;
  const depth = $('input[name="depth"]:checked')?.value || 'standard';
  const demo = $('#set-demo').checked;
  if (!demo && !key) { showToast('Add an API key, or tick “Use demo data”.', true); return; }
  const keyChanged = key !== state.key;
  const modeChanged = demo !== (state.mode === 'demo');
  store.set('apiKey', key); store.set('country', country); store.set('depth', depth); store.set('demo', demo);
  if (keyChanged || modeChanged) { location.reload(); return; }
  const countryChanged = country !== state.country;
  state.country = country; state.depth = depth;
  closeModal();
  showToast('Settings saved');
  if (countryChanged && $('#q').value.trim()) runSearch();
  else if (!state.searched && state.mode === 'live') renderEmpty('start');
}

function connectFromWelcome() {
  const key = $('#welcome-key').value.trim();
  if (!key) { showToast('Paste your API key first.', true); return; }
  store.set('apiKey', key); store.set('demo', false);
  location.reload();
}

async function chooseDemo() {
  store.set('demo', true);
  if (state.mode === 'live') { location.reload(); return; }
  closeModal();
  if (state.mode !== 'demo') await startDemo();
}

function closeModal() { $('#modal').hidden = true; $('#modal').innerHTML = ''; state.wiz = null; }

// =====================================================================
// Misc UI
// =====================================================================
function setBanner(html) { const b = $('#banner'); b.innerHTML = html; b.hidden = !html; }

let toastTimer;
function showToast(msg, isErr = false, ms = 3500) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.toggle('err', isErr);
  t.setAttribute('role', isErr ? 'alert' : 'status');
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, ms);
}

function hydrateIcons(root) {
  for (const el of $$('i[data-icon]', root)) el.outerHTML = icon(el.dataset.icon);
}

function bindUI() {
  $('#search-form').addEventListener('submit', e => { e.preventDefault(); $('#q').blur(); runSearch(); });
  $('#q').addEventListener('input', () => { if ($('#q').value) $('#q').placeholder = 'e.g. 2010 or Surry Hills'; });
  $('#radius').addEventListener('change', () => { if (state.searched) runSearch(); });
  $('#sort').addEventListener('change', e => { state.sort = e.target.value; store.set('sort', state.sort); state.shown = PAGE; renderResults(); });

  document.addEventListener('click', e => {
    if (e.target === $('#drawer')) return closeDrawer();
    // Settings/welcome hold typed input, so a stray click outside shouldn't throw it away.
    if (e.target === $('#modal')) return $('#modal [data-sticky]') ? undefined : closeModal();
    if (e.target === $('#lightbox')) return closeLightbox();
    const t = e.target.closest('[data-action]');
    if (!t || t.tagName === 'INPUT') return;
    const a = t.dataset.action, id = t.dataset.id;
    switch (a) {
      case 'home': e.preventDefault(); closeDetail(); if (state.shortlistOnly) toggleShortlistView(); window.scrollTo({ top: 0, behavior: 'smooth' }); break;
      case 'wizard': openWizard(); break;
      case 'shortlist': toggleShortlistView(); break;
      case 'settings': openSettings(); break;
      case 'locate': locate(); break;
      case 'example': $('#q').value = t.dataset.q; runSearch(); break;
      case 'retry': runSearch(); break;
      case 'filters': openDrawer(); break;
      case 'close-drawer': closeDrawer(); break;
      case 'clear-filters': state.filters = freshFilters(); state.shown = PAGE; renderAll(); if (!$('#drawer').hidden) renderDrawer(); break;
      case 'f': applyFilter(t.dataset.f, t.dataset.v); break;
      case 'unfilter': applyFilter(t.dataset.f, t.dataset.v); break;
      case 'view': setView(t.dataset.v); break;
      case 'toggle-mobile-view': toggleMobileMap(); break;
      case 'open': openDetail(id); break;
      case 'save': e.stopPropagation(); toggleSave(id); break;
      case 'more': state.shown += PAGE; renderResults(); break;
      case 'preview-close': closePreview(); break;
      case 'close-detail': closeDetail(); break;
      case 'photo': openLightbox(id, t.dataset.i); break;
      case 'lb-step': stepLightbox(+t.dataset.v); break;
      case 'lb-close': closeLightbox(); break;
      case 'share': share(id); break;
      case 'close-modal': closeModal(); break;
      case 'connect': connectFromWelcome(); break;
      case 'demo': chooseDemo(); break;
      case 'save-settings': saveSettings(); break;
      case 'toggle-key': { const k = $('#set-key'); k.type = k.type === 'password' ? 'text' : 'password'; t.textContent = k.type === 'password' ? 'Show' : 'Hide'; break; }
      case 'w-opt': state.wiz.a[t.dataset.k] = t.dataset.v; renderWizard(); break;
      case 'w-go': state.wiz.ranked = rankForWizard(state.wiz.a); state.wiz.idx = 0; renderWizard(); break;
      case 'w-back': state.wiz.ranked = null; renderWizard(); break;
      case 'w-spin': spinWizard(); break;
      case 'w-apply': applyWizardAsFilters(); break;
      case 'w-open-venue': { closeModal(); openDetail(id); break; }
    }
  });

  // Checkbox-style controls use change events.
  document.addEventListener('change', e => {
    const t = e.target;
    if (t.dataset?.action === 'f') applyFilter(t.dataset.f, t.dataset.v);
    if (t.dataset?.action === 'w-open' && state.wiz) state.wiz.a.openNow = t.checked;
  });

  // Hovering a card highlights its pin.
  const cards = $('#cards');
  cards.addEventListener('mouseover', e => {
    const c = e.target.closest('.card');
    const id = c?.dataset.id || null;
    if (id !== state.hoverId) { state.hoverId = id; state.map?.hover(id); }
  });
  cards.addEventListener('mouseleave', () => { state.hoverId = null; state.map?.hover(null); });

  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
      if (!$('#lightbox').hidden) return closeLightbox();
      if (!$('#modal').hidden) return closeModal();
      if (!$('#drawer').hidden) return closeDrawer();
      if (state.detailOpen) return closeDetail();
    }
    if (state.lb && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) stepLightbox(e.key === 'ArrowLeft' ? -1 : 1);
    if (e.key === 'Enter' && e.target.classList?.contains('card')) openDetail(e.target.dataset.id);
    if (e.key === 'Enter' && e.target.id === 'welcome-key') connectFromWelcome();
    if (e.key === 'Enter' && e.target.id === 'set-key') saveSettings();
  });

  window.addEventListener('popstate', e => {
    if (e.state?.detail) openDetail(e.state.detail, false);
    else closeDetail(true);
  });
}

init();
