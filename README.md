# Pokémon Sold Tracker — Android

Automated eBay UK sold-price tracking for Pokémon cards. Every card has a separate tracker with a printed number, set, grade and company filters. Add multiple independent searches and combine their matched sales in one 7/30/90/180-day price chart.

## Automatic price collection

**Recommended:** Save a [SoldComps](https://sold-comps.com/dashboard) API key under App → Settings. Its free tier advertises 100 requests/month. The app then makes its own eBay UK sold-listing requests, using domestic (UK-based) item location and GBP filtering. It automatically captures individual prices, sold dates and item links, and removes duplicates across searches.

Best Offer results count towards the graph only when the provider reports a successful actual-price resolution. Unresolved offers remain unverified. This external API is a third-party service, independent from eBay, and may be subject to quotas or service interruptions. This API method has not been tested with a real key in this project.

Without a provider key, the app can attempt to scan eBay UK sold pages in the built-in WebView. That method is unreliable (login, changing markup, restricted access) and its results are recorded as **candidates**, not actual-price verification.

You can exclude false matches, correct individual prices and manually add sales, but none of those steps are required to start an automatic search with a connected API key. A new tracker automatically creates its first card-name-and-number search. Tap **Find sold prices**; add more search phrases individually whenever necessary.

## Monitoring

When switched on under Settings and enabled per card, the app checks its saved searches periodically **while it is open**. It is not a guaranteed background service or an every-minute market alert. Each search may use 1 or 2 provider requests; set the check interval accordingly to avoid consuming monthly quota. Data and API credentials are stored locally on the device; API credentials are not exported in backup JSON. The app disables Android's automatic backup for local API credentials.

## Build

The debug APK is produced by GitHub Actions → Build Android APK → latest successful run → Artifacts → `pokemon-sold-tracker-apk`. Open this project in Android Studio with JDK 17 and the Android SDK to build locally.
