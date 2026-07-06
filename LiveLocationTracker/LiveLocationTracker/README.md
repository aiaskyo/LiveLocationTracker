# Live Location Tracker (Android / Kotlin)

A production-ready, MVVM live location tracking app: Firebase Auth + Firestore,
Google Maps SDK, Fused Location Provider, a foreground service for
continuous background tracking, and full Android 14+ background-location
compliance.

This project is complete and ready to open in Android Studio, but it ships
with **placeholder Firebase/Maps credentials** (real API keys can't be
generated on your behalf). You must plug in your own before it will run.
Follow the setup steps below — they take about 10 minutes.

---

## 1. Project structure

```
LiveLocationTracker/
├── app/
│   ├── build.gradle.kts              # Module Gradle config, all dependencies
│   ├── google-services.json          # PLACEHOLDER — replace with your own
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/livelocationtracker/
│       │   ├── LiveLocationApp.kt              # Application: Firebase + notification channel init
│       │   ├── data/
│       │   │   ├── model/LocationData.kt       # Firestore document schema
│       │   │   └── repository/
│       │   │       ├── AuthRepository.kt
│       │   │       └── LocationRepository.kt
│       │   ├── service/
│       │   │   └── LocationTrackingService.kt  # Foreground service, FLP updates, Firestore writes
│       │   ├── receiver/BootReceiver.kt        # Resumes tracking after reboot
│       │   ├── ui/
│       │   │   ├── splash/SplashActivity.kt
│       │   │   ├── auth/ (LoginActivity, AuthViewModel)
│       │   │   ├── permission/PermissionActivity.kt
│       │   │   └── map/ (MapActivity, MapViewModel)
│       │   └── utils/
│       │       ├── Constants.kt
│       │       ├── PermissionHelper.kt
│       │       ├── BatteryOptimizationHelper.kt
│       │       └── NetworkConnectivityObserver.kt
│       └── res/                                 # Material 3 layouts, strings, vector icons
├── firestore.rules                              # Security rules (deploy via Firebase CLI)
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── README.md
```

## 2. Firebase setup

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project (or reuse one).
2. Add an Android app to the project:
   - Package name: `com.example.livelocationtracker` (or change `applicationId` in `app/build.gradle.kts` to match whatever you register).
   - Add your debug (and later release) SHA-1 fingerprint. Get it with:
     ```
     ./gradlew signingReport
     ```
3. Download the generated `google-services.json` and **replace**
   `app/google-services.json` in this project with it.
4. In the Firebase Console, enable:
   - **Authentication** → Sign-in method → enable **Email/Password** and **Anonymous**.
   - **Firestore Database** → Create database (start in production mode).
5. Deploy the included security rules (recommended) so only a signed-in user
   can read/write their own location document:
   ```
   npm install -g firebase-tools
   firebase login
   firebase init firestore   # point it at this project, reuse firestore.rules
   firebase deploy --only firestore:rules
   ```
   Or paste the contents of `firestore.rules` directly into the Firestore
   Console's Rules tab.

## 3. Google Maps SDK setup

1. In the [Google Cloud Console](https://console.cloud.google.com/) (same
   project Firebase created, or a new one), enable **Maps SDK for Android**.
2. Create an API key restricted to Android apps, using the same package
   name and SHA-1 fingerprint as above.
3. Open `app/src/main/res/values/strings.xml` and replace:
   ```xml
   <string name="google_maps_key" translatable="false">YOUR_GOOGLE_MAPS_API_KEY_HERE</string>
   ```
   with your real key.

## 4. Open and run

1. Open the `LiveLocationTracker/` folder in Android Studio (Koala/Ladybug or newer recommended).
2. Let Gradle sync — it will download the Firebase, Play Services, and Maps dependencies declared in `app/build.gradle.kts`.
3. Run on a real device or an emulator with Google Play services (a Play Store emulator image — plain AOSP images lack Play Services/Maps).
4. On first launch:
   - Sign in (email/password, "Create Account", or "Continue without an account" for a quick anonymous session).
   - Walk through the four permission steps: foreground location, notifications, "Allow all the time" background location, and the battery optimization exemption.
   - Tap **Start Tracking**. A persistent notification appears and your marker begins moving on the map as fixes come in.
5. Minimize the app, or swipe it away from Recents — tracking keeps running because it's backed by a genuine foreground service, not an Activity-bound listener. Reopen the app and the map picks the live marker back up from Firestore.

## 5. How the requirements map to the code

| Requirement | Where |
|---|---|
| MVVM | `ui/*/‑ViewModel.kt` classes expose `StateFlow`; Activities only observe and render |
| Firebase Auth | `data/repository/AuthRepository.kt` |
| Firestore | `data/repository/LocationRepository.kt`, `data/model/LocationData.kt` |
| Google Maps SDK | `ui/map/MapActivity.kt` (`SupportMapFragment`, marker, camera) |
| Fused Location Provider | `service/LocationTrackingService.kt` |
| Foreground service | `service/LocationTrackingService.kt`, declared `foregroundServiceType="location"` in the manifest |
| Runtime permissions incl. background "Allow all the time" | `ui/permission/PermissionActivity.kt`, `utils/PermissionHelper.kt` |
| Continues when minimized/removed from Recents | Foreground service + `START_STICKY`, independent of any Activity |
| Update every 15s OR 10m moved | `LocationTrackingService.handleNewLocation()` — samples the FLP every 5s and gates the actual Firestore write on the 15s/10m OR condition (see in-code comment for why FLP's own `minUpdateDistanceMeters` doesn't express an OR condition) |
| Fields saved (userId, lat, lng, accuracy, speed, bearing, timestamp) | `data/model/LocationData.kt` |
| Live location displayed on map | `MapViewModel` listens to the user's own Firestore doc in real time; `MapActivity` renders it |
| Reconnect after network loss | Firestore's persistent local cache (enabled in `LiveLocationApp.kt`) queues writes offline and replays them automatically; `NetworkConnectivityObserver` drives an "offline" banner in the UI |
| Battery optimization guidance | `utils/BatteryOptimizationHelper.kt`, surfaced as a step in `PermissionActivity` |
| Material Design 3 | `res/values/themes.xml` (`Theme.Material3.DayNight.NoActionBar`), Material 3 components throughout the layouts |
| Android 14+ background location rules | `FOREGROUND_SERVICE_LOCATION` permission + `foregroundServiceType="location"` declared; background location requested in an isolated runtime request per Google's guidance |

## 6. Known limitations / next steps for a real production rollout

- **Icons**: the launcher icon and in-app icons are simple vector placeholders. Swap them via Android Studio's Image Asset tool (`File > New > Image Asset`) for a polished look.
- **Sharing with others**: the current Firestore rules only let a user read their own location. If you want a "share my location with a friend" feature, you'll need a sharing/permissions model (e.g. an `authorizedViewers` array on each user's doc) and adjusted rules.
- **Accuracy vs. battery tradeoff**: `PRIORITY_HIGH_ACCURACY` is used throughout for best GPS accuracy; consider `PRIORITY_BALANCED_POWER_ACCURACY` if battery life matters more than precision for your use case.
- **Play Store policy**: apps requesting `ACCESS_BACKGROUND_LOCATION` must complete Google's [Background Location permission declaration form](https://support.google.com/googleplay/android-developer/answer/9799150) and justify the use case (live tracking clearly qualifies, but you must still fill out the form) before publishing.
- This project was authored and validated for structural/reference correctness in a sandboxed environment without network access to actually run `./gradlew build`. Do a full Gradle sync and build in Android Studio as your first step — if a dependency version has been yanked or bumped since this was written, Android Studio will surface it immediately and a version bump is a one-line fix.
