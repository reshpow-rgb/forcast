# ASX Value Finder (Android)

An Android app that helps you find ASX shares that may be undervalued. It pulls live market data,
runs several valuation models, ranks stocks by a value score, and can have Claude write a summarised
equity research note for any company.

> General information only, not financial advice.

## What it does

**Screener**: scans the ASX top 50 or ~100 large and mid caps (or your watchlist). A quick valuation
appears straight away from batch quotes. The app then loads each company's detailed financials,
best candidates first, and refines the numbers. You can sort by value score, upside, P/E, yield or
market cap, filter by sector, and show only undervalued stocks.

**Stock page** (search any ASX code with the magnifier):
- *Overview*: share price chart (1M to Max) with a moving average and fair-value line, drag to
  inspect. Also return, volatility, max drawdown, 52-week range and key figures.
- *Valuation*: five models side by side against the price, the blended fair value, margin of safety,
  confidence, signals (positive and warning) and analyst targets.
- *Research*: a quick research summary built from the app's data (works without a key), the latest
  news, and an optional **AI equity research note** from Claude. Claude searches the web for recent
  results, guidance, broker views and catalysts, then writes a broker-style summary with sources.

**AI value ideas**: Claude reviews the screener's 15 top-ranked stocks with current market context.
It suggests which are worth researching and which may be value traps.

**Watchlist**: star stocks to track them, then screen just those.

## How valuation works

| Model | Method |
|---|---|
| Discounted cash flow | FCF per share grown 5 years at the analyst/earnings growth rate (capped −5%…15%), fading to terminal growth over years 6–10, discounted at CAPM cost of equity. Skipped for banks/insurers. |
| Dividend discount | Gordon growth: DPS × (1+g) / (r − g), with g = ROE × retention (capped at 4.5%). |
| Graham number | √(22.5 × EPS × book value per share). |
| Sector P/E | EPS × median P/E of the stock's ASX sector peers (live from the screener). |
| Analyst consensus | Mean broker 12-month target (needs 2+ analysts). |

- **Fair value** is the median of the models that ran. Any model result above 4× or below 0.2× the
  price is excluded as an outlier.
- **Verdict**: undervalued at 20%+ upside (adjustable in Settings), slightly undervalued at 5–20%,
  fair from −10% to 5%, otherwise overvalued.
- **Value score (0–100)** blends five measures:
  - upside to fair value
  - P/E and P/B against peers
  - analyst upside
  - dividend yield
  - where the price sits in its 52-week range

  It applies penalties for losses, high debt, payout ratios above 100% and low confidence.

You can change the risk-free rate, equity risk premium, terminal growth and the undervalued threshold
in Settings.

## Data sources

- **Yahoo Finance** public endpoints (no key): price history, quotes, fundamentals, analyst targets and
  news. Data can be delayed, and coverage of small caps is patchy.
- **Claude** (optional): set your Anthropic API key in Settings. Research uses Claude Opus 5.5 by
  default, or Sonnet 5.5 if you choose it, with the web search tool. The server-side refusal
  fallback is enabled. Calls are billed to your Anthropic account.

## Install

Each push builds an APK with GitHub Actions:

1. Open the repo on GitHub, then **Actions → ASX Value Finder (Android)** and the latest green run.
2. Download the **asx-value-finder-apk** artifact and unzip it.
3. Copy `app-debug.apk` to your phone and open it. Allow installing from unknown sources when asked.

## Build it yourself

You need Android Studio (or JDK 17 plus the Android SDK). Open the `asx-value` folder, or run:

```
cd asx-value
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # valuation and parser tests
```

## Code layout

```
app/src/main/java/com/forcast/asxvalue/
  core/data/       Yahoo Finance client, JSON parsers, ASX stock list   (pure Kotlin, unit tested)
  core/valuation/  valuation models, value score, price statistics      (pure Kotlin, unit tested)
  core/research/   rule-based research summary + Claude research notes
  data/            settings/watchlist storage, repository and screening pipeline
  ui/              Jetpack Compose screens: screener, stock detail, ideas, watchlist, settings
```

## Limitations

- Yahoo's endpoints are unofficial and can change or rate-limit without notice.
- The stock list is fixed in code (`AsxUniverse.kt`), and index membership drifts over time.
- Models are deliberately simple. Use them to shortlist ideas, not to make decisions.
- Franking credits aren't included in yields or the dividend model.
