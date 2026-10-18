// Google Maps Platform: loading the JS API, geocoding a postcode, and finding every
// restaurant/café in a radius with Places API (New).
import { priceLevelFrom } from './enrich.js';
import { offsetPoint } from './util.js';

export const AUTH_HELP = 'Google rejected the API key. Check it is correct, billing is enabled on the project, and (if restricted) the key allows http://localhost:8000/* as an HTTP referrer.';

let loading = null;
export function loadGoogle(key) {
  if (window.google?.maps?.importLibrary) return Promise.resolve();
  if (loading) return loading;
  loading = new Promise((resolve, reject) => {
    window.__fcGoogleReady = () => resolve();
    window.gm_authFailure = () => window.dispatchEvent(new CustomEvent('fc:authfail'));
    const s = document.createElement('script');
    s.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&v=weekly&loading=async&callback=__fcGoogleReady`;
    s.async = true;
    s.onerror = () => { loading = null; reject(new Error('Could not load Google Maps. Check your internet connection.')); };
    document.head.appendChild(s);
  });
  return loading;
}

// Google sometimes never settles a request (e.g. after an auth failure), so never wait forever.
function withTimeout(promise, ms, what) {
  let t;
  return Promise.race([
    promise,
    new Promise((_, reject) => { t = setTimeout(() => reject(new Error(`Google didn’t respond while ${what}. Check your connection and API key, then try again.`)), ms); }),
  ]).finally(() => clearTimeout(t));
}

const COUNTRY_NAMES ={ AU: 'Australia', NZ: 'New Zealand', GB: 'United Kingdom', IE: 'Ireland', US: 'United States', CA: 'Canada', SG: 'Singapore' };

export async function geocode(query, country) {
  try {
    const { Geocoder } = await google.maps.importLibrary('geocoding');
    const req = { address: query };
    if (country) { req.componentRestrictions = { country }; req.region = country.toLowerCase(); }
    const { results } = await withTimeout(new Geocoder().geocode(req), 10000, 'looking up that postcode');
    if (results?.length) {
      const r = results[0];
      return { lat: r.geometry.location.lat(), lng: r.geometry.location.lng(), label: r.formatted_address };
    }
  } catch (e) {
    // Geocoding API is optional: any refusal here (often REQUEST_DENIED because it isn't enabled)
    // falls back to Places, which reports real key problems itself.
    console.warn('Geocoding API unavailable, falling back to Places text search:', e?.message || e);
  }
  const { Place } = await google.maps.importLibrary('places');
  // Phrase it as an area ("postcode 2010, Australia"); a bare "2010 Australia" can match street addresses.
  const looksLikePostcode = /\d/.test(query) && query.replace(/\s/g, '').length <= 8;
  const countryName = COUNTRY_NAMES[country] || '';
  const textQuery = `${looksLikePostcode ? 'postcode ' : ''}${query}${countryName ? ', ' + countryName : ''}`;
  let places;
  try {
    ({ places } = await withTimeout(Place.searchByText({
      textQuery,
      fields: ['location', 'formattedAddress', 'types'],
      maxResultCount: 3,
      ...(country ? { region: country } : {}),
    }), 15000, 'looking up that postcode'));
  } catch (e) { throw friendlyError(e); }
  if (!places?.length) throw new Error(`Couldn't find “${query}”. Try a postcode or suburb name.`);
  const AREA = /postal_code|locality|sublocality|neighborhood|administrative_area/;
  const best = places.find(p => (p.types || []).some(t => AREA.test(t))) || places[0];
  return { lat: best.location.lat(), lng: best.location.lng(), label: best.formattedAddress };
}

// Field tiers: if Google rejects a newer field name, fall back to a smaller set automatically.
const CORE_FIELDS = ['id', 'displayName', 'formattedAddress', 'location', 'types', 'primaryType', 'primaryTypeDisplayName', 'rating',
  'userRatingCount', 'priceLevel', 'businessStatus', 'photos', 'regularOpeningHours', 'utcOffsetMinutes', 'nationalPhoneNumber',
  'websiteURI', 'googleMapsURI', 'editorialSummary'];
const ATMOSPHERE_FIELDS = ['servesVegetarianFood', 'servesBreakfast', 'servesBrunch', 'servesLunch', 'servesDinner', 'servesCoffee',
  'servesDessert', 'servesBeer', 'servesWine', 'hasTakeout', 'hasDelivery', 'hasDineIn', 'isReservable', 'hasOutdoorSeating',
  'isGoodForChildren', 'isGoodForGroups', 'allowsDogs'];
const NEWER_FIELDS = ['priceRange'];
const FIELD_TIERS = [[...CORE_FIELDS, ...ATMOSPHERE_FIELDS, ...NEWER_FIELDS], [...CORE_FIELDS, ...ATMOSPHERE_FIELDS], CORE_FIELDS];
let tier = 0;

// Errors that mean every other request will fail too, so retrying is pointless (and costs quota).
const FATAL_RE = /api key|API_KEY|not been used|disabled|not enabled|ApiNotActivated|PERMISSION_DENIED|not authorized|billing|referer|referrer|REQUEST_DENIED/i;
const isFatal = e => FATAL_RE.test(e?.message || '');
const isFieldError = e => !isFatal(e) && /\bfields?\b|unknown field|property/i.test(e?.message || '');

async function withFields(fn) {
  for (let t = tier; t < FIELD_TIERS.length; t++) {
    try {
      const r = await fn(FIELD_TIERS[t]);
      tier = Math.max(tier, t);
      return r;
    } catch (e) {
      if (t === FIELD_TIERS.length - 1 || !isFieldError(e)) throw e;
      console.warn(`Retrying with fewer fields (tier ${t + 1}):`, e.message);
    }
  }
}

const BASE_GROUPS = [['restaurant'], ['cafe', 'coffee_shop'], ['bakery', 'dessert_shop', 'ice_cream_shop', 'juice_shop'], ['fast_food_restaurant', 'meal_takeaway', 'sandwich_shop']];
const CUISINE_GROUPS = [
  ['italian_restaurant', 'pizza_restaurant'],
  ['japanese_restaurant', 'sushi_restaurant', 'ramen_restaurant'],
  ['chinese_restaurant', 'korean_restaurant', 'vietnamese_restaurant', 'thai_restaurant'],
  ['indian_restaurant', 'middle_eastern_restaurant', 'lebanese_restaurant', 'turkish_restaurant', 'mediterranean_restaurant', 'greek_restaurant'],
  ['vegan_restaurant', 'vegetarian_restaurant'],
  ['breakfast_restaurant', 'brunch_restaurant'],
  ['mexican_restaurant', 'american_restaurant', 'hamburger_restaurant', 'seafood_restaurant', 'steak_house', 'barbecue_restaurant'],
];

export function planJobs({ center, radius, keyword, depth }) {
  const jobs = [];
  const nearby = (types, c, r, rank) => jobs.push({ kind: 'nearby', types, center: c, radius: Math.min(r, 50000), rank });
  if (keyword) jobs.push({ kind: 'text', query: keyword, center, radius });
  for (const g of BASE_GROUPS) nearby(g, center, radius, 'POPULARITY');
  if (depth !== 'quick') {
    for (const g of BASE_GROUPS) nearby(g, center, radius, 'DISTANCE');
    for (const g of CUISINE_GROUPS) nearby(g, center, radius, 'POPULARITY');
  }
  if (depth === 'deep' && radius >= 1200) {
    // Each request returns at most 20 places, so tile the circle to dig deeper in busy areas.
    for (let i = 0; i < 6; i++) {
      const c = offsetPoint(center, radius * 0.55, i * 60);
      nearby(['restaurant'], c, radius * 0.5, 'POPULARITY');
      nearby(['cafe', 'coffee_shop'], c, radius * 0.5, 'POPULARITY');
    }
  }
  return jobs;
}

export async function searchArea(opts, onProgress) {
  const { Place, SearchNearbyRankPreference } = await google.maps.importLibrary('places');
  const jobs = planJobs(opts);
  const results = new Map();
  let done = 0, firstError = null, anyOk = false;

  const run = async job => {
    const places = await withTimeout(withFields(async fields => {
      if (job.kind === 'text') {
        const r = await Place.searchByText({ textQuery: job.query, fields, locationBias: { center: job.center, radius: job.radius }, maxResultCount: 20 });
        return r.places || [];
      }
      const r = await Place.searchNearby({
        fields,
        locationRestriction: { center: job.center, radius: job.radius },
        includedTypes: job.types,
        maxResultCount: 20,
        rankPreference: SearchNearbyRankPreference?.[job.rank] ?? job.rank,
      });
      return r.places || [];
    }), 20000, 'searching for places');
    for (const p of places) {
      const v = fromGoogle(p);
      if (!v) continue;
      if (!results.has(v.id)) results.set(v.id, v);
      if (job.kind === 'text') results.get(v.id).keywordHit = true;
    }
  };

  // Small worker pool: 4 requests in flight at once.
  const queue = [...jobs];
  await Promise.all(Array.from({ length: 4 }, async () => {
    while (queue.length) {
      const job = queue.shift();
      try { await run(job); anyOk = true; } catch (e) {
        firstError ??= e; console.warn('Places request failed', job, e);
        if (isFatal(e)) { done += queue.length; queue.length = 0; }
      }
      onProgress?.(++done, jobs.length);
    }
  }));
  if (!anyOk) throw friendlyError(firstError);
  return [...results.values()];
}

function friendlyError(e) {
  const msg = e?.message || String(e);
  if (/api key not valid|API_KEY_INVALID|invalid api key/i.test(msg))
    return new Error('Google says this API key isn’t valid. Open Settings and paste the whole key again (Google Cloud Console → APIs & Services → Credentials).');
  if (/not been used|disabled|not enabled|ApiNotActivated|PERMISSION_DENIED|not authorized/i.test(msg))
    return new Error('Places API (New) isn’t enabled for this key. In Google Cloud Console → APIs & Services, enable “Places API (New)” and “Maps JavaScript API”.');
  if (/billing/i.test(msg)) return new Error('Google says billing isn’t enabled for this project. Add a billing account in Google Cloud Console.');
  if (/referer|referrer/i.test(msg)) return new Error('This API key doesn’t allow this website. Add http://localhost:8000/* to the key’s HTTP referrer restrictions.');
  return new Error(`Google Places error: ${msg}`);
}

function get(p, k) { try { return p[k]; } catch { return undefined; } }
const pt = x => x ? { day: x.day, hour: x.hour, minute: x.minute || 0 } : null;
const money = m => m ? Number(m.units ?? 0) + (m.nanos ? m.nanos / 1e9 : 0) : null;
const bool = x => (x === true || x === false) ? x : null;

function fromGoogle(p) {
  const loc = get(p, 'location');
  if (!loc) return null;
  const hours = get(p, 'regularOpeningHours');
  const range = get(p, 'priceRange');
  const photos = (get(p, 'photos') || []).slice(0, 10).map(ph => {
    const a = ph.authorAttributions?.[0];
    return {
      url: ph.getURI({ maxWidth: 1200, maxHeight: 900 }),
      thumb: ph.getURI({ maxWidth: 560, maxHeight: 420 }),
      author: a?.displayName || '',
      authorUri: a?.uri || '',
    };
  });
  return {
    id: 'g_' + p.id, placeId: p.id, source: 'google',
    name: get(p, 'displayName') || 'Unnamed place',
    address: get(p, 'formattedAddress') || '',
    lat: loc.lat(), lng: loc.lng(),
    types: get(p, 'types') || [],
    primaryType: get(p, 'primaryType') || '',
    primaryTypeLabel: get(p, 'primaryTypeDisplayName') || '',
    rating: get(p, 'rating') ?? null,
    reviews: get(p, 'userRatingCount') ?? 0,
    priceLevel: priceLevelFrom(get(p, 'priceLevel')),
    priceRange: range ? { min: money(range.startPrice), max: money(range.endPrice), currency: range.startPrice?.currencyCode || range.endPrice?.currencyCode || '' } : null,
    businessStatus: get(p, 'businessStatus') || 'OPERATIONAL',
    summary: get(p, 'editorialSummary') || '',
    phone: get(p, 'nationalPhoneNumber') || '',
    website: get(p, 'websiteURI') || '',
    mapsUrl: get(p, 'googleMapsURI') || '',
    photos,
    hours: hours ? {
      periods: (hours.periods || []).map(pr => ({ open: pt(pr.open), close: pt(pr.close) })),
      weekdayText: hours.weekdayDescriptions || [],
      utcOffsetMinutes: get(p, 'utcOffsetMinutes') ?? null,
    } : null,
    serves: {
      vegetarian: bool(get(p, 'servesVegetarianFood')), breakfast: bool(get(p, 'servesBreakfast')), brunch: bool(get(p, 'servesBrunch')),
      lunch: bool(get(p, 'servesLunch')), dinner: bool(get(p, 'servesDinner')), coffee: bool(get(p, 'servesCoffee')),
      dessert: bool(get(p, 'servesDessert')), beer: bool(get(p, 'servesBeer')), wine: bool(get(p, 'servesWine')),
    },
    features: {
      dineIn: bool(get(p, 'hasDineIn')), takeout: bool(get(p, 'hasTakeout')), delivery: bool(get(p, 'hasDelivery')),
      reservable: bool(get(p, 'isReservable')), outdoor: bool(get(p, 'hasOutdoorSeating')), kids: bool(get(p, 'isGoodForChildren')),
      groups: bool(get(p, 'isGoodForGroups')), dogs: bool(get(p, 'allowsDogs')),
    },
  };
}
