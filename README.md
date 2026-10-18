# Pokémon Sold Tracker (Android)

Pokémon sold-price research for eBay UK, supporting independently saved searches combined into a single card's history.

## Included features
- Exact card-name, card-number and optional set identification
- PSA / ACE / TAG, grades 8 / 9 / 10, graded / ungraded selectors
- Multiple searches checked separately; deduplication by eBay item reference
- eBay UK sold/completed searches and a built-in browser to capture candidates
- Optional repeated checks every 15/30/60/120 minutes **only while the application is open**; notification of new candidates
- Separate unverified candidates versus manually verified actual sold prices
- 1-week / 1-month / 3-month / 6-month median, average and chart for **verified** sale prices only
- Manual exclusion of false matches and JSON backup/import

## Data limitations
Ordinary eBay sold-listing pages can show the original asking price when a Best Offer was accepted. Prices are not shown as actual sales until manually confirmed against eBay Product Research. eBay may restrict automated reading; this app does not guarantee unattended monitoring, background operation, six months of historical eBay records, or access to eBay's restricted sold-price API.

## Building
Open the Gradle project in Android Studio, or use GitHub Actions: **Actions → Build Android APK**. Download the `pokemon-sold-tracker-apk` build artifact.

App stores data locally in Android WebView. Export a backup before uninstalling the app.
