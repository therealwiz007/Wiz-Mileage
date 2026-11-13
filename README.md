# Wiz Mileage

**Every litre. Every rupee. Every km.**

Offline Android fuel log by Wiz Labs. For each fill-up you type the odometer reading, the amount paid and the RSP. The app works out litres, mileage, cost per km and a set of insights. Kotlin + Jetpack Compose.

## Identity
- App name (under the icon): **Wiz Mileage**
- Play Store title: **Wiz Mileage: Fuel & Petrol Log**
- Application ID: `com.wizlabs.mileage`
- Version: 1.1.0 (versionCode 4). Raise versionCode by 1 for every Play upload
- Minimum Android: 8.0 / API 26 · Compile / target SDK: 36
- No INTERNET permission. The only permission is the camera, requested when you scan a share QR code

## Features

**Fill-ups**
- Odometer (km), amount (₹), RSP (₹ per L, or per kg for CNG). The RSP is pre-filled from the last fill-up
- Quick amount chips (₹100–₹1,000 for two-wheelers, ₹500–₹3,000 for cars)
- Optional **Full tank** toggle for exact full-to-full mileage
- Optional fuel brand: **IOCL, BPCL, HPCL, Nayara, Jio-bp, Shell** (the last one is pre-selected)
- Optional date (defaults to now) and note
- Live preview while typing: litres, km since the last fill-up, mileage for this tank
- Odometer check against the readings before and after the chosen date
- Edit any entry; deleting one can be undone

**Vehicles**
- Any number of vehicles (Bike, Scooter, Car, SUV, Other) with fuel type, registration and tank capacity
- Switch vehicles from the top bar; each has its own history and insights

**Dashboard (Home)**
- "Cockpit" card: animated speedometer gauge for average mileage (red → green arc, sprung needle, scale ends on round numbers), last-tank trend chip, odometer / last tank / RSP
- This-month spend ring against last month, cost-per-km card with sparkline
- Swipeable KPI strip: km per day (and its fuel cost), per visit, fuel bought, tank range, yearly pace, distance
- "For you" insight carousel with page dots
- 17-week fill-up activity heatmap (cells shaded by money spent)
- Brand donut ("Where you fill up") with mileage per brand
- Personal records: best tank, longest stretch, cheapest RSP, biggest fill-up
- Count-up numbers and drawing animations throughout

**Share with another phone (offline)**
- Share logs: pick this vehicle or all vehicles; the app shows a QR code (bigger logs cycle through several QR frames automatically) at full screen brightness
- Send code: the same log as a text share code (`WZM1:…`) through WhatsApp, Messages, email…
- Receive logs: scan the QR (CameraX + ZXing, on-device), paste a code, or share the message straight into the app from WhatsApp. Also offered on the first-run screen
- Preview before importing: vehicles, fill-up count, date range, and how many are new or already present. Vehicles with the same name are merged; duplicates are skipped
- Codes are compact (≈300 characters for 23 fill-ups), checksummed (CRC32) and versioned, so a damaged code is rejected with a clear message

**Insights**
- Average mileage: exact (full tank to full tank, counting partial fills in between) or estimated when no full tanks are marked
- Cost per km, km per day, spend per visit, days between fill-ups
- Charts: mileage trend with average line, monthly spend (12 months), RSP history. Tap or drag a chart to read any point
- Period filter: 1M, 3M, 6M, 1Y, All
- Fuel brand breakdown: share of spend, average RSP and mileage per brand
- Compare vehicles: spend, mileage and cost per km side by side
- Smart tips: mileage drop or rise on the last tank, spending pace against last month (from the 10th of the month), best brand for mileage, RSP changes and what ₹1,000 now buys in km, daily running cost, fill-up rhythm, full-tank range, yearly projection, and a warning when a fill-up is bigger than the tank (likely typo)

**Data**
- Stored as one JSON file in private app storage, written atomically. A file that can't be read is kept aside instead of being overwritten
- Export to CSV (opens in Excel / Google Sheets) and import the same CSV back. Duplicates are skipped and vehicles are matched by name
- Included in Android's own device backup

**Look**
- Black-and-yellow Wiz design, light and dark themes (System / Dark / Light in Garage)
- Launcher icon: speedometer with an orange→yellow gradient arc, white needle, glowing hub and fuel drop on a dark radial background; themed (monochrome) icon and matching splash

## Project layout
```
app/src/main/java/com/wizlabs/mileage/
  model/Models.kt           Vehicle, FillUp, Brand, FuelType
  data/Store.kt             JSON persistence
  data/Csv.kt               CSV export/import
  data/ShareCodec.kt        Share codes: binary pack + deflate + CRC + Base64, QR framing, merge
  insights/Insights.kt      Mileage segments, stats, smart tips (pure Kotlin, unit-tested)
  insights/Format.kt        Indian number formatting (₹1,23,456)
  ui/                       Theme, components, charts, screens
  AppViewModel.kt, MainActivity.kt
```

## Build
Open in Android Studio (JDK 17), or:
```
gradle :app:testDebugUnitTest :app:assembleDebug
```
Release builds use the same GitHub Actions workflow as the other Wiz apps (`.github/workflows/android.yml`). Add the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets, then push to `main`. The workflow outputs a signed APK and AAB.

## Play Store
- Privacy policy: `privacy-policy.html` (host it, e.g. on GitHub Pages, and paste the URL into Play Console). The same file is bundled at `app/src/main/assets/privacy-policy.html` and opens offline from Garage → "Read the privacy policy"; copy it there again whenever you edit it
- App icon (512×512, opaque PNG, ready for Play Console): `store-assets/wiz-mileage-play-icon-512.png`. It's drawn from the same geometry as the launcher vectors; to re-render at another size run
  `cd store-assets && javac Icon.java && java -Djava.awt.headless=true Icon out.png 1024`
- Data safety: no data collected; sharing is user-initiated, device-to-device, and never sent to the developer. Camera: used only for on-device QR scanning
- Short description (76/80): *Petrol & diesel mileage tracker: km/L, cost per km, spend insights. Offline.*
- Privacy policy URL: https://therealwiz007.github.io/Wiz-Mileage/privacy-policy.html

Fuel brand names are trademarks of their owners and are used only as labels.
