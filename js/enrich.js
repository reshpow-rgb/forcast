// Turns raw venue data (from Google or the demo set) into the fields the UI filters on:
// category, cuisines, diet, health score, meals, open status and handy links.
import { haversine, fmtTime } from './util.js';

export const PRICE_NAMES = ['Free', 'Inexpensive', 'Moderate', 'Expensive', 'Very expensive'];
const PRICE_ENUM = { FREE: 0, INEXPENSIVE: 1, MODERATE: 2, EXPENSIVE: 3, VERY_EXPENSIVE: 4 };

export function priceLevelFrom(x) {
  if (x == null || x === '') return null;
  if (typeof x === 'number') return x;
  const k = String(x).toUpperCase().replace(/^PRICE_LEVEL_/, '');
  return k in PRICE_ENUM ? PRICE_ENUM[k] : null;
}

export const DIETS = [
  { key: 'vegetarian', label: 'Vegetarian' },
  { key: 'vegan', label: 'Vegan' },
  { key: 'glutenFree', label: 'Gluten-free' },
  { key: 'halal', label: 'Halal' },
];

export const MEALS = [
  { key: 'breakfast', label: 'Breakfast' },
  { key: 'lunch', label: 'Lunch' },
  { key: 'dinner', label: 'Dinner' },
  { key: 'coffee', label: 'Coffee' },
  { key: 'dessert', label: 'Dessert' },
  { key: 'drinks', label: 'Drinks' },
];

export const FEATURES = [
  { key: 'dineIn', label: 'Dine-in', icon: 'utensils' },
  { key: 'takeout', label: 'Takeaway', icon: 'bag' },
  { key: 'delivery', label: 'Delivery', icon: 'truck' },
  { key: 'reservable', label: 'Takes bookings', short: 'Bookings', icon: 'calendar' },
  { key: 'outdoor', label: 'Outdoor seating', icon: 'sun' },
  { key: 'kids', label: 'Kid-friendly', icon: 'smile' },
  { key: 'groups', label: 'Good for groups', icon: 'users' },
  { key: 'dogs', label: 'Dog-friendly', icon: 'paw' },
];

export const CATEGORIES = ['Restaurant', 'Cafe', 'Bakery', 'Dessert', 'Juice & smoothies', 'Fast food', 'Deli & sandwiches', 'Bar & pub', 'Fine dining', 'Food court'];

const CATEGORY_BY_TYPE = {
  cafe: 'Cafe', coffee_shop: 'Cafe', tea_house: 'Cafe', cat_cafe: 'Cafe', dog_cafe: 'Cafe',
  bakery: 'Bakery', bagel_shop: 'Bakery', donut_shop: 'Bakery',
  dessert_shop: 'Dessert', dessert_restaurant: 'Dessert', ice_cream_shop: 'Dessert', chocolate_shop: 'Dessert', confectionery: 'Dessert', candy_store: 'Dessert',
  juice_shop: 'Juice & smoothies', acai_shop: 'Juice & smoothies',
  fast_food_restaurant: 'Fast food', meal_takeaway: 'Fast food', meal_delivery: 'Fast food',
  bar: 'Bar & pub', pub: 'Bar & pub', wine_bar: 'Bar & pub', bar_and_grill: 'Bar & pub',
  food_court: 'Food court', sandwich_shop: 'Deli & sandwiches', deli: 'Deli & sandwiches',
  fine_dining_restaurant: 'Fine dining',
};

function categoryOf(primary, types) {
  if (CATEGORY_BY_TYPE[primary]) return CATEGORY_BY_TYPE[primary];
  if (primary && /restaurant|steak_house|diner/.test(primary)) return 'Restaurant';
  for (const t of types) if (CATEGORY_BY_TYPE[t] && t !== 'meal_takeaway' && t !== 'meal_delivery') return CATEGORY_BY_TYPE[t];
  return 'Restaurant';
}

const CUISINE_BY_TYPE = {
  afghani_restaurant: 'Afghan', african_restaurant: 'African', american_restaurant: 'American', asian_restaurant: 'Asian',
  barbecue_restaurant: 'BBQ', brazilian_restaurant: 'Brazilian', chinese_restaurant: 'Chinese', french_restaurant: 'French',
  greek_restaurant: 'Greek', hamburger_restaurant: 'Burgers', indian_restaurant: 'Indian', indonesian_restaurant: 'Indonesian',
  italian_restaurant: 'Italian', japanese_restaurant: 'Japanese', korean_restaurant: 'Korean', lebanese_restaurant: 'Lebanese',
  mediterranean_restaurant: 'Mediterranean', mexican_restaurant: 'Mexican', middle_eastern_restaurant: 'Middle Eastern',
  pizza_restaurant: 'Pizza', ramen_restaurant: 'Ramen', seafood_restaurant: 'Seafood', spanish_restaurant: 'Spanish',
  steak_house: 'Steakhouse', sushi_restaurant: 'Sushi', thai_restaurant: 'Thai', turkish_restaurant: 'Turkish',
  vietnamese_restaurant: 'Vietnamese', bar_and_grill: 'Grill', diner: 'Diner', sandwich_shop: 'Sandwiches', bagel_shop: 'Bagels', deli: 'Deli',
};
const CUISINE_PARENT = { Ramen: 'Japanese', Sushi: 'Japanese', Pizza: 'Italian' };
const NOT_CUISINE = new Set(['restaurant', 'fast_food_restaurant', 'fine_dining_restaurant', 'buffet_restaurant', 'brunch_restaurant',
  'breakfast_restaurant', 'dessert_restaurant', 'vegan_restaurant', 'vegetarian_restaurant', 'family_restaurant']);
const CUISINE_KEYWORDS = [
  [/\bpho\b|banh mi|vietnam/i, 'Vietnamese'], [/ramen|udon|soba/i, 'Ramen'], [/sushi|izakaya|japan|teppan|yakitori|bento/i, 'Japanese'],
  [/\bthai\b/i, 'Thai'], [/pizz/i, 'Pizza'], [/trattoria|osteria|pasta|italian|nonna/i, 'Italian'],
  [/taco|taqueria|burrito|mexic|cantina/i, 'Mexican'], [/dumpling|yum cha|dim sum|chinese|szechuan|sichuan/i, 'Chinese'],
  [/curry|tandoor|masala|india|biryani|dosa/i, 'Indian'], [/kebab|turk|gozleme|pide/i, 'Turkish'], [/burger/i, 'Burgers'],
  [/korea|bibimbap/i, 'Korean'], [/greek|souvlaki|gyros/i, 'Greek'], [/leban|falafel|shawarma|mezze/i, 'Lebanese'],
  [/\bpok[eé]\b/i, 'Hawaiian'], [/seafood|\bfish\b|oyster/i, 'Seafood'],
];

function cuisinesOf(v) {
  const out = [];
  const add = c => { if (c && !out.includes(c)) out.push(c); };
  for (const t of [v.primaryType, ...v.types]) {
    if (!t) continue;
    if (CUISINE_BY_TYPE[t]) add(CUISINE_BY_TYPE[t]);
    else if (t.endsWith('_restaurant') && !NOT_CUISINE.has(t)) add(t.replace(/_restaurant$/, '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()));
  }
  if (!out.length) {
    const text = `${v.name} ${v.summary || ''}`;
    for (const [re, c] of CUISINE_KEYWORDS) if (re.test(text)) add(c);
  }
  for (const c of [...out]) if (CUISINE_PARENT[c]) add(CUISINE_PARENT[c]);
  return out;
}

// ---- Diet --------------------------------------------------------------
// 'confirmed' = Google attribute or venue type; 'likely' = mentioned in name/description; 'no' = Google says no.
export function dietStatus(v, key) {
  const t = new Set(v.types || []);
  const text = `${v.name} ${v.summary || ''}`;
  switch (key) {
    case 'vegetarian':
      if (t.has('vegan_restaurant') || t.has('vegetarian_restaurant') || v.serves?.vegetarian === true) return 'confirmed';
      if (/vegetarian|vegan|plant[- ]based/i.test(text)) return 'likely';
      return v.serves?.vegetarian === false ? 'no' : null;
    case 'vegan':
      if (t.has('vegan_restaurant')) return 'confirmed';
      return /\bvegan|plant[- ]based|dairy[- ]free/i.test(text) ? 'likely' : null;
    case 'glutenFree':
      return /gluten[- ]?free|coeliac|celiac|\bgf\b/i.test(text) ? 'likely' : null;
    case 'halal':
      return /halal/i.test(text) ? 'likely' : null;
  }
  return null;
}
export const dietOk = (v, key) => ['confirmed', 'likely'].includes(dietStatus(v, key));

// ---- Health estimate ----------------------------------------------------
const HEALTH_TYPES = {
  vegan_restaurant: 22, vegetarian_restaurant: 18, juice_shop: 18, acai_shop: 10, sushi_restaurant: 10, japanese_restaurant: 8,
  vietnamese_restaurant: 10, mediterranean_restaurant: 12, greek_restaurant: 9, lebanese_restaurant: 10, middle_eastern_restaurant: 8,
  korean_restaurant: 4, thai_restaurant: 4, seafood_restaurant: 8, indian_restaurant: 2,
  fast_food_restaurant: -24, hamburger_restaurant: -16, donut_shop: -24, dessert_shop: -18, dessert_restaurant: -18,
  ice_cream_shop: -18, candy_store: -24, confectionery: -20, chocolate_shop: -18, pizza_restaurant: -12, barbecue_restaurant: -8,
  bar: -10, pub: -10, buffet_restaurant: -8, steak_house: -4, diner: -6, meal_takeaway: -5,
};
const HEALTHY_KW = [
  [/\bsalads?\b/i, 'salads'], [/\bpok[eé]\b/i, 'poke'], [/\bbowls?\b/i, 'bowls'], [/organic/i, 'organic'], [/whole ?foods?/i, 'wholefoods'],
  [/healthy|nourish|wellness/i, 'health-focused'], [/plant[- ]based|\bvegan\b/i, 'plant-based'], [/juice|smoothie|cold[- ]press/i, 'juices & smoothies'],
  [/a[cç]a[ií]/i, 'açaí'], [/grilled|charcoal/i, 'grilled'], [/\bfresh\b/i, 'fresh food'], [/sashimi|rice paper/i, 'light dishes'],
];
const UNHEALTHY_KW = [
  [/fried chicken|\bfried\b/i, 'fried food'], [/burger/i, 'burgers'], [/do(ugh)?nuts?/i, 'doughnuts'], [/pizza/i, 'pizza'],
  [/\bfries\b|\bchips\b|loaded/i, 'fries'], [/gelato|ice cream|dessert|waffle|pancake|milkshake|churro|cake|macaron|pastr/i, 'sweets'],
];

function healthOf(v) {
  let score = 50; const reasons = [];
  const seen = new Set();
  for (const t of [v.primaryType, ...v.types]) {
    if (!t || seen.has(t)) continue; seen.add(t);
    const d = HEALTH_TYPES[t];
    if (d) { score += d; reasons.push(`${d > 0 ? '+' : '−'} ${t.replace(/_/g, ' ')}`); }
  }
  const text = `${v.name} ${v.summary || ''}`;
  let plus = 0, minus = 0;
  for (const [re, label] of HEALTHY_KW) if (re.test(text) && plus < 24) { plus += 8; reasons.push(`+ ${label}`); }
  for (const [re, label] of UNHEALTHY_KW) if (re.test(text) && minus < 24) { minus += 8; reasons.push(`− ${label}`); }
  score += plus - minus;
  if (v.serves?.vegetarian === true) { score += 4; reasons.push('+ vegetarian options'); }
  // Compress toward the middle so only clearly health-focused places get near the top.
  score = Math.max(0, Math.min(100, Math.round(50 + (score - 50) * 0.8)));
  const label = score >= 65 ? 'Healthy pick' : score >= 48 ? 'Balanced' : 'Treat';
  return { score, label, reasons };
}

const COMFORT_TYPES = new Set(['pizza_restaurant', 'hamburger_restaurant', 'fast_food_restaurant', 'barbecue_restaurant', 'diner', 'ramen_restaurant',
  'pub', 'bar_and_grill', 'mexican_restaurant', 'dessert_shop', 'dessert_restaurant', 'ice_cream_shop', 'donut_shop', 'bakery', 'steak_house']);
const comfortOf = v => v.types.some(t => COMFORT_TYPES.has(t)) || COMFORT_TYPES.has(v.primaryType) ||
  /burger|fried|pizza|ramen|dumpling|kebab|\bpie|schnitzel|pancake|gelato/i.test(v.name + ' ' + (v.summary || ''));

// ---- Hours, open status, meals -------------------------------------------
const WEEK = 10080;
const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export function openStatus(hours) {
  if (!hours?.periods?.length) return null;
  const offset = hours.utcOffsetMinutes ?? -new Date().getTimezoneOffset();
  const now = new Date(Date.now() + offset * 60000);
  const nowMin = now.getUTCDay() * 1440 + now.getUTCHours() * 60 + now.getUTCMinutes();
  let next = null;
  for (const p of hours.periods) {
    if (!p.open) continue;
    if (!p.close) return { open: true, soon: false, label: 'Open 24 hours' };
    const o = p.open.day * 1440 + p.open.hour * 60 + (p.open.minute || 0);
    let c = p.close.day * 1440 + p.close.hour * 60 + (p.close.minute || 0);
    if (c <= o) c += WEEK;
    for (const m of [nowMin, nowMin + WEEK]) {
      if (m >= o && m < c) {
        const soon = c - m <= 60;
        return { open: true, soon, label: `${soon ? 'Closes soon' : 'Open'} · until ${fmtTime(p.close.hour, p.close.minute || 0)}` };
      }
    }
    const diff = (o - nowMin + WEEK) % WEEK;
    if (!next || diff < next.diff) next = { diff, p };
  }
  if (!next) return { open: false, soon: false, label: 'Closed' };
  const { p } = next;
  const when = next.diff < 1440 - (nowMin % 1440) ? '' : next.diff < 2880 - (nowMin % 1440) ? 'tomorrow ' : `${DAY_NAMES[p.open.day]} `;
  return { open: false, soon: false, label: `Closed · opens ${when}${fmtTime(p.open.hour, p.open.minute || 0)}` };
}

function hourWindows(hours) {
  if (!hours?.periods?.length) return null;
  let earliest = 1440, latest = 0, lunch = false;
  for (const p of hours.periods) {
    if (!p.open) continue;
    if (!p.close) return { earliest: 0, latest: 1440, lunch: true };
    const o = p.open.hour * 60 + (p.open.minute || 0);
    let c = p.close.hour * 60 + (p.close.minute || 0);
    if (p.close.day !== p.open.day || c <= o) c += 1440;
    earliest = Math.min(earliest, o); latest = Math.max(latest, c);
    if (o <= 750 && c >= 810) lunch = true;
  }
  return { earliest, latest, lunch };
}

function mealsOf(v, cat) {
  const s = v.serves || {}; const t = new Set(v.types); const w = hourWindows(v.hours);
  const pick = (...vals) => { for (const x of vals) if (x != null) return x; return null; };
  const cafeLike = cat === 'Cafe' || cat === 'Bakery';
  return {
    breakfast: pick(s.breakfast, s.brunch, (t.has('breakfast_restaurant') || t.has('brunch_restaurant') || cafeLike) ? true : null, w ? w.earliest <= 570 : null),
    lunch: pick(s.lunch, w ? w.lunch : null, cat === 'Bar & pub' ? null : true),
    dinner: pick(s.dinner, w ? w.latest >= 1200 : null, (cat === 'Restaurant' || cat === 'Fine dining') ? true : null),
    coffee: pick(s.coffee, cafeLike ? true : null),
    dessert: pick(s.dessert, (cat === 'Dessert' || t.has('dessert_restaurant')) ? true : null),
    drinks: pick((s.beer || s.wine) ? true : null, cat === 'Bar & pub' ? true : null),
  };
}

// ---- Links ----------------------------------------------------------------
function suburbOf(address) {
  const parts = String(address || '').split(',').map(s => s.trim()).filter(Boolean);
  if (parts.length < 2) return '';
  const last = parts[parts.length - 1];
  const cand = /\d/.test(last) || parts.length === 2 ? parts[parts.length - 1] : parts[parts.length - 2];
  return cand.replace(/\b[A-Z]{2,3}\b\s*\d{3,5}$/, '').replace(/\s\d{3,5}$/, '').trim();
}

function linksOf(v) {
  const q = encodeURIComponent;
  const area = v.suburb ? ` ${v.suburb}` : '';
  const isInsta = /instagram\.com/i.test(v.website || '');
  const place = `${v.name} ${v.address || ''}`.trim();
  return {
    website: isInsta ? '' : v.website,
    instagram: isInsta ? v.website : '',
    instagramSearch: `https://www.google.com/search?q=${q(`site:instagram.com "${v.name}"${area}`)}`,
    menuSearch: `https://www.google.com/search?q=${q(`"${v.name}"${area} menu`)}`,
    maps: v.mapsUrl || `https://www.google.com/maps/search/?api=1&query=${q(place)}${v.placeId ? `&query_place_id=${v.placeId}` : ''}`,
    directions: `https://www.google.com/maps/dir/?api=1&destination=${q(place)}${v.placeId ? `&destination_place_id=${v.placeId}` : ''}`,
    phone: v.phone ? `tel:${v.phone.replace(/[^\d+]/g, '')}` : '',
  };
}

const EMOJI = {
  Japanese: '🍣', Ramen: '🍜', Sushi: '🍣', Italian: '🍝', Pizza: '🍕', Chinese: '🥟', Vietnamese: '🍜', Thai: '🍛', Indian: '🍛',
  Mexican: '🌮', Korean: '🍖', Seafood: '🦞', Greek: '🥙', Lebanese: '🧆', Turkish: '🥙', Steakhouse: '🥩', Burgers: '🍔', Hawaiian: '🥗',
  Cafe: '☕', Bakery: '🥐', Dessert: '🍨', 'Juice & smoothies': '🥤', 'Fast food': '🍔', 'Bar & pub': '🍺', 'Deli & sandwiches': '🥪', 'Fine dining': '🍷',
};
export const emojiFor = v => (v.types?.includes('vegan_restaurant') && '🥗') || EMOJI[v.cuisine] || EMOJI[v.category] || '🍽️';

// ---- Main entry -------------------------------------------------------------
export function enrich(v, center) {
  v.types = v.types || [];
  v.distance = haversine(center, v);
  v.suburb = v.suburb || suburbOf(v.address);
  v.category = categoryOf(v.primaryType, v.types);
  v.cuisines = cuisinesOf(v);
  v.cuisine = v.cuisines[0] || null;
  if (v.priceLevel == null && v.priceRange?.min != null) {
    const mid = v.priceRange.max != null ? (v.priceRange.min + v.priceRange.max) / 2 : v.priceRange.min;
    v.priceLevel = mid <= 15 ? 1 : mid <= 35 ? 2 : mid <= 70 ? 3 : 4;
  }
  if (v.priceLevel === 0) v.priceLevel = 1;
  v.health = healthOf(v);
  v.comfort = comfortOf(v);
  v.meals = mealsOf(v, v.category);
  v.open = openStatus(v.hours);
  v.links = linksOf(v);
  v.searchText = [v.name, v.category, v.primaryTypeLabel, v.summary, ...v.cuisines, ...v.types.map(t => t.replace(/_/g, ' '))].join(' ').toLowerCase();
  return v;
}

// "Recommended" ranking: rating (shrunk toward average for few reviews), popularity, closeness, open now.
export function scoreVenue(v, radius) {
  const n = v.reviews || 0, r = v.rating || 3.8;
  const bayes = (r * n + 4.0 * 25) / (n + 25);
  let s = (bayes - 3) * 35;
  s += Math.min(10, Math.log10(n + 1) * 3.3);
  s += 12 * (1 - Math.min(1, v.distance / Math.max(radius, 1)));
  if (v.open) s += v.open.open ? 5 : -8;
  if (v.businessStatus === 'CLOSED_TEMPORARILY') s -= 40;
  if (!v.photos?.length) s -= 3;
  return s;
}
