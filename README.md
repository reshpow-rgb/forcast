# Forkcast — find where to eat

A listing-style app (think Domain / realestate.com.au, but for food) that finds every restaurant and café
within a radius of a postcode. You can then filter and choose by **budget, diet, cuisine, healthy options**
and more.

## Run it

Double-click **`start.bat`**. It starts a small local web server (PowerShell, nothing to install) and
opens http://localhost:8000. Close the window to stop it.

The first time, choose **Try the demo** to explore with made-up sample venues, or connect Google Maps
to search real places.

> You must open the app through `start.bat` (http://localhost:8000), not by double-clicking
> `index.html`. Google API keys don't work from `file://` pages.

## Use it on your phone (GitHub Pages)

The app is plain static files, so GitHub Pages can host it for free at
`https://YOUR-USERNAME.github.io/forkcast/`.

1. Upload the files to a **public** GitHub repo. The empty `.nojekyll` file makes Pages serve the files as-is.
2. In the repo: **Settings → Pages → Deploy from a branch → `main` / `(root)` → Save**. It's live within a minute or two.
3. In Google Cloud Console, edit your API key's **Websites** restriction and add
   `https://YOUR-USERNAME.github.io/*` (keep `http://localhost:8000/*` for local use).
4. Open the site on your phone, tap the gear icon, paste your key, and tap **Save settings**.
   Each browser keeps its own copy of the key.
5. Optional: use **Share → Add to Home Screen** to get an app icon.

> Don't put your key in `config.js` in a public repo. Paste it into Settings on each device instead.

## Connect Google Maps (real data)

1. Go to https://console.cloud.google.com/ and create a project.
2. Turn on **billing** for the project. Google Maps Platform includes a monthly free usage allowance.
   Check current pricing at https://mapsplatform.google.com/pricing/ and set a **budget alert**.
3. Under **APIs & Services → Library**, enable:
   - **Maps JavaScript API** (the map)
   - **Places API (New)** (finding restaurants and cafés, photos, hours, ratings, prices)
   - **Geocoding API** (postcode → location). Optional: without it the app falls back to Places search.
4. Under **APIs & Services → Credentials**, click **Create credentials → API key**.
5. Restrict the key: **Application restrictions → Websites**, add `http://localhost:8000/*`.
   Under **API restrictions**, allow the three APIs above.
6. In Forkcast, open **Settings** (gear icon) and paste the key, or put it in `config.js`.

### How much does a search cost?

Google Places returns at most 20 places per request, so one search makes several requests.
You can change the depth in Settings:

| Depth    | Requests per search | What you get |
|----------|--------------------|--------------|
| Quick    | ~4                 | The most popular restaurants, cafés, bakeries/dessert and takeaway spots |
| Standard | ~15                | Popular and nearest places, plus a sweep across major cuisines (default) |
| Deep     | up to ~27          | Also splits the radius into tiles to find more places in busy areas |

Requests that include ratings, hours, and dine-in/vegetarian details are billed at Google's higher
"Enterprise + Atmosphere" Places rate. Photos are billed when they load.

## Features

- **Postcode + radius search** (500 m – 10 km) and "use my location". Optional "craving" keyword
  (e.g. *ramen*, *vegan*, *pho*).
- **Listing cards**: photo, price ($–$$$$ and per-person range where Google has it), rating,
  distance, walk time, open/closing-soon badge, healthy-pick and vegan badges, dine-in/takeaway/
  delivery/bookings icons.
- **Split list + map view** with price pins. Hover a card to highlight its pin; click a pin to see a
  preview. List-only and map-only views are also available, and a map toggle on phones.
- **Filters**: open now, healthy picks, budget, vegetarian/vegan/gluten-free/halal, meal (breakfast,
  lunch, dinner, coffee, dessert, drinks), type of place, cuisine, minimum rating, features (outdoor
  seating, kid-friendly, dog-friendly, groups, bookings…). Each option shows a live count.
- **Sort**: recommended, highest rated, nearest, price, most reviewed, healthiest.
- **Detail page**: photo gallery and lightbox, key stats, about, dietary options (with confidence),
  what they serve, good-to-know features, opening hours (today highlighted), health estimate, a
  location map, and similar places. Buttons for directions, website, menu, Instagram, call, Google Maps
  and share.
- **Help me choose**: answer six quick questions (meal, budget, diet, healthy vs comfort, cuisine,
  distance). It gives a top pick with reasons, two runner-ups and **Spin again**.
- **Shortlist**: heart any place to save it in your browser and compare later.

## Important limitations (be honest with users)

- **Menus and Instagram**: Google's Places API doesn't provide menu links or social handles. If a
  venue's website *is* its Instagram page, Forkcast links to it directly. Otherwise **Find menu** and
  **Find Instagram** run a targeted Google search. The Google Maps link often has a Menu tab.
- **Dietary info**: Google only publishes "serves vegetarian food". Vegan, gluten-free and halal are
  inferred from the venue type, name and description, and marked *Likely*. Always check with the venue
  for allergies.
- **Health score**: a rough 0–100 estimate from venue type and keywords (e.g. salad/poke/juice vs
  burgers/doughnuts). It is not a nutrition rating.
- **"All" restaurants**: Google caps each request at 20 results. Standard/Deep depth gets most places
  in a typical suburb, but very dense areas (a CBD at 5 km+) may not return every single venue.
  Use a smaller radius or Deep mode there.
- Google's terms require Places results to be shown on a Google map. Forkcast does this in live mode.
  Demo mode uses OpenStreetMap with fake data.

## Files

```
index.html     page layout
styles.css     all styling
config.js      optional API key / default country / default radius
js/app.js      UI: search, listings, filters, detail page, wizard, shortlist, settings
js/google.js   Google Maps loading, postcode geocoding, Places search and normalising results
js/enrich.js   cuisine, category, diet, health score, meals, opening-hours logic, links
js/mapview.js  Google map and Leaflet (demo) map with price pins
js/demo.js     made-up sample venues for demo mode
serve.ps1      tiny local web server used by start.bat
```
