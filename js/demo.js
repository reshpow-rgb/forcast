// Made-up sample venues so the app can be explored without a Google API key.
// None of these places are real.
import { offsetPoint, seeded, fmtTime } from './util.js';

export const DEMO_CENTER = { lat: -33.8861, lng: 151.2111 };

const fill = slots => Array(7).fill(slots);
// Index 0 = Sunday. Times are minutes from midnight; > 1440 means past midnight.
const PATTERNS = {
  cafe: [[[480, 840]], [[420, 900]], [[420, 900]], [[420, 900]], [[420, 900]], [[420, 900]], [[450, 900]]],
  brunch: fill([[420, 900]]),
  rest: [[[720, 900], [1050, 1290]], [], [[720, 900], [1050, 1320]], [[720, 900], [1050, 1320]], [[720, 900], [1050, 1320]], [[720, 900], [1050, 1380]], [[720, 900], [1050, 1380]]],
  dinner: [[[1050, 1290]], [], [[1050, 1350]], [[1050, 1350]], [[1050, 1350]], [[1050, 1410]], [[1050, 1410]]],
  allday: fill([[660, 1320]]),
  late: [[[660, 1380]], [[660, 1440]], [[660, 1440]], [[660, 1440]], [[660, 1440]], [[660, 1560]], [[660, 1560]]],
  bakery: [[[420, 780]], [[360, 900]], [[360, 900]], [[360, 900]], [[360, 900]], [[360, 900]], [[360, 900]]],
  dessert: fill([[720, 1380]]),
  juice: fill([[420, 1020]]),
  pub: [[[660, 1320]], [[660, 1440]], [[660, 1440]], [[660, 1440]], [[660, 1440]], [[660, 1500]], [[660, 1500]]],
};
const DAY = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const t = m => fmtTime(Math.floor((m % 1440) / 60), m % 60);

function buildHours(name) {
  const days = PATTERNS[name];
  const periods = [];
  days.forEach((slots, day) => slots.forEach(([o, c]) => periods.push({
    open: { day, hour: Math.floor(o / 60), minute: o % 60 },
    close: { day: (day + Math.floor(c / 1440)) % 7, hour: Math.floor((c % 1440) / 60), minute: c % 60 },
  })));
  const weekdayText = [1, 2, 3, 4, 5, 6, 0].map(d => `${DAY[d]}: ${days[d].length ? days[d].map(([o, c]) => `${t(o)} – ${t(c)}`).join(', ') : 'Closed'}`);
  return { periods, weekdayText, utcOffsetMinutes: null };
}

const RANGES = { 1: [10, 20], 2: [20, 40], 3: [40, 70], 4: [90, 160] };
const STREETS = ['Fig Tree Ln', 'Market St', 'Station Rd', 'Harbour Pde', 'Mill St', 'Park Ave', 'Church St', 'Queen St', 'Wattle Rd', 'Rose Ln'];

// [name, primaryType, extraTypes, price, rating, reviews, summary, flags, hours]
const LIST = [
  ['Little Fig Kitchen', 'cafe', ['breakfast_restaurant'], 2, 4.6, 812, 'Bright corner café with seasonal brunch plates, house-baked sourdough and specialty coffee.', 'veg,dinein,takeout,outdoor,kids,dogs,coffee', 'cafe'],
  ['Greenhouse Bowl Co.', 'vegan_restaurant', ['restaurant'], 2, 4.7, 540, 'Plant-based poke and grain bowls, cold-pressed juices and smoothies. Gluten-free options marked on the menu.', 'veg,dinein,takeout,delivery', 'allday'],
  ['Nonna Rosa Trattoria', 'italian_restaurant', ['restaurant'], 3, 4.5, 1290, 'Family-run trattoria known for handmade pasta and wood-fired pizza.', 'veg,dinein,takeout,reserve,kids,groups,wine', 'rest'],
  ['Golden Lotus Dumpling House', 'chinese_restaurant', ['restaurant'], 1, 4.3, 2210, 'Busy dumpling house with xiao long bao, noodles and yum cha favourites.', 'veg,dinein,takeout,delivery,groups,kids', 'allday'],
  ['Pho Saigon Corner', 'vietnamese_restaurant', ['restaurant'], 1, 4.4, 980, 'Fragrant pho, banh mi and fresh rice paper rolls.', 'dinein,takeout,delivery', 'allday'],
  ['Saltbush Grill', 'steak_house', ['restaurant'], 4, 4.6, 640, 'Modern Australian grill with dry-aged steaks and an extensive wine list.', 'dinein,reserve,groups,wine', 'dinner'],
  ['Bun & Bird', 'hamburger_restaurant', ['fast_food_restaurant', 'restaurant'], 1, 4.1, 1530, 'Smash burgers, fried chicken and loaded fries.', 'dinein,takeout,delivery,kids', 'late'],
  ['Sakura Hand Roll Bar', 'sushi_restaurant', ['japanese_restaurant', 'restaurant'], 2, 4.5, 720, 'Counter-style hand roll bar with fresh sashimi and miso soup.', 'dinein,takeout', 'rest'],
  ['Baan Thai Street', 'thai_restaurant', ['restaurant'], 2, 4.2, 860, 'Street-style Thai curries, pad see ew and som tum. Vegan options available.', 'veg,dinein,takeout,delivery', 'allday'],
  ['Spice Route Tandoor', 'indian_restaurant', ['restaurant'], 2, 4.4, 1110, 'North Indian curries, biryani and tandoor breads. Halal certified.', 'veg,dinein,takeout,delivery,groups', 'dinner'],
  ['The Crumb Bakehouse', 'bakery', ['cafe'], 1, 4.7, 930, 'Sourdough, croissants and pastries baked on site daily.', 'veg,takeout,coffee', 'bakery'],
  ['Olive & Thyme Mezze', 'lebanese_restaurant', ['mediterranean_restaurant', 'restaurant'], 2, 4.6, 670, 'Charcoal-grilled skewers, falafel, hummus and fresh salads. Halal.', 'veg,dinein,takeout,delivery,outdoor,groups', 'allday'],
  ['Gelato Luna', 'ice_cream_shop', [], 1, 4.8, 1400, 'Small-batch gelato and sorbets, including dairy-free flavours.', 'veg,takeout,kids,dogs', 'dessert'],
  ['Taqueria El Sol', 'mexican_restaurant', ['restaurant'], 2, 4.3, 590, 'Tacos al pastor, burritos and margaritas. Gluten-free corn tortillas.', 'veg,dinein,takeout,outdoor,beer', 'late'],
  ['Seoul Kitchen BBQ', 'korean_restaurant', ['barbecue_restaurant', 'restaurant'], 3, 4.4, 1020, 'Table-top Korean barbecue, bibimbap and fried chicken.', 'dinein,reserve,groups', 'dinner'],
  ['Harbour Catch', 'seafood_restaurant', ['restaurant'], 3, 4.2, 450, 'Grilled fish, oysters and seafood platters.', 'dinein,reserve,outdoor,wine', 'rest'],
  ['Daily Press Juicery', 'juice_shop', [], 1, 4.5, 310, 'Cold-pressed juices, açaí bowls and protein smoothies.', 'veg,takeout', 'juice'],
  ['The Wattle Hotel', 'pub', ['bar', 'restaurant'], 2, 4.0, 1750, 'Classic pub with schnitzels, burgers and a sunny beer garden.', 'dinein,outdoor,kids,dogs,groups,beer,wine', 'pub'],
  ['Kumo Ramen', 'ramen_restaurant', ['japanese_restaurant', 'restaurant'], 2, 4.6, 1320, 'Rich tonkotsu ramen, gyoza and karaage.', 'dinein,takeout', 'rest'],
  ['Brew Lab Espresso', 'coffee_shop', ['cafe'], 1, 4.7, 480, 'Specialty coffee bar with single-origin pour-overs and toasties.', 'takeout,dogs,coffee', 'cafe'],
  ['Athena Souvlaki', 'greek_restaurant', ['restaurant'], 1, 4.3, 690, 'Souvlaki, gyros and Greek salads.', 'dinein,takeout,delivery', 'late'],
  ['Wild Grain Wholefoods Café', 'cafe', ['vegetarian_restaurant'], 2, 4.5, 390, 'Organic wholefoods café: salads, nourish bowls and vegan cakes. Gluten-free friendly.', 'veg,dinein,takeout,kids,outdoor,coffee', 'cafe'],
  ['Napoli Slice', 'pizza_restaurant', ['restaurant'], 1, 4.1, 870, 'Wood-fired Neapolitan pizza by the slice or whole.', 'veg,dinein,takeout,delivery,kids', 'late'],
  ['Maison Délice Patisserie', 'dessert_shop', ['bakery', 'cafe'], 2, 4.6, 560, 'French patisserie with macarons, tarts and éclairs.', 'veg,takeout,coffee', 'bakery'],
  ['Istanbul Grill & Kebab', 'turkish_restaurant', ['restaurant'], 1, 4.2, 730, 'Charcoal kebabs, pide and gozleme. Halal.', 'dinein,takeout,delivery,groups', 'late'],
  ['Fern & Fold', 'fine_dining_restaurant', ['restaurant'], 4, 4.8, 310, 'Seasonal tasting menu showcasing native Australian ingredients.', 'veg,dinein,reserve,wine', 'dinner'],
  ['Tokyo Bento Express', 'japanese_restaurant', ['meal_takeaway', 'restaurant'], 1, 4.0, 410, 'Quick bento boxes, teriyaki and sushi rolls.', 'takeout,delivery', 'allday'],
  ['Morning Glory Diner', 'breakfast_restaurant', ['diner', 'restaurant'], 2, 4.3, 620, 'All-day breakfast diner: pancakes, eggs benny and milkshakes.', 'dinein,takeout,kids,groups', 'brunch'],
  ['Poke Republic', 'restaurant', [], 2, 4.4, 520, 'Build-your-own poke bowls with fresh salmon, tuna and tofu.', 'veg,dinein,takeout,delivery', 'allday'],
  ['Chai & Chat Tea House', 'tea_house', ['cafe'], 1, 4.5, 260, 'Loose-leaf teas, chai and Indian street snacks.', 'veg,dinein,takeout,coffee', 'cafe'],
];

export function demoVenues() {
  const rnd = seeded(2010);
  return LIST.map(([name, primary, extra, price, rating, reviews, summary, flags, hours], i) => {
    const fl = new Set(flags.split(','));
    const p = offsetPoint(DEMO_CENTER, 120 + rnd() * 1750, rnd() * 360);
    const street = `${10 + Math.floor(rnd() * 290)} ${STREETS[Math.floor(rnd() * STREETS.length)]}`;
    const [min, max] = RANGES[price];
    return {
      id: 'demo_' + i, source: 'demo', name,
      address: `${street}, Demo Town`, suburb: 'Demo Town',
      lat: p.lat, lng: p.lng,
      types: [primary, ...extra, 'food', 'establishment'], primaryType: primary, primaryTypeLabel: '',
      rating, reviews, priceLevel: price, priceRange: { min, max, currency: 'AUD' },
      businessStatus: 'OPERATIONAL', summary, phone: '', website: '', mapsUrl: '', photos: [],
      hours: buildHours(hours),
      serves: { vegetarian: fl.has('veg') ? true : null, coffee: fl.has('coffee') ? true : null, beer: fl.has('beer') ? true : null, wine: fl.has('wine') ? true : null },
      features: {
        dineIn: fl.has('dinein'), takeout: fl.has('takeout'), delivery: fl.has('delivery'), reservable: fl.has('reserve'),
        outdoor: fl.has('outdoor'), kids: fl.has('kids'), groups: fl.has('groups'), dogs: fl.has('dogs'),
      },
    };
  });
}
