This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

## Unity LevelPlay Ads SDK

Unity LevelPlay (mediation SDK `9.4.0`) is integrated through a shared `AdsManager`
(common code in `shared/src/commonMain/kotlin/.../ads`), with expect/actual
`PlatformAdsManager` implementations for Android and iOS and a `LevelPlayAdsKitBridge`
Swift wrapper on iOS.

Key components:

- `AdsManager` – common interface (`init`, `loadInterstitial`, `showInterstitial`,
  `loadRewarded`, `showRewarded`, `is*Ready`) plus `StateFlow`s for init, interstitial,
  rewarded and banner states.
- `AdsBanner` – a Compose banner slot filled by a platform banner view
  (`LevelPlayBannerAdView` on Android, `LPMBannerAdView` via the Swift bridge on iOS).
- `AdsCoordinator` + `AdPlacementPolicy` – placement gating: an **interstitial is shown
  automatically after every 3rd completed level, but never more often than once per 60 s**,
  and a **rewarded ad is only shown before a reward action, and never more often than once
  per 30 s**. Rewards are granted from the platform `onAdRewarded`/`didRewardAd` callback.
- `initAdsKoin()` – idempotently starts Koin and registers `AdsManager` as a singleton
  (Koin 4.0.0, common code only).

### Prerequisites

- Unity LevelPlay dashboard app with an App Key and Ad Unit IDs for Interstitial,
  Rewarded and Banner placements. The repo ships with real Ad Unit IDs already set in
  `shared/src/commonMain/kotlin/.../ads/LevelPlayAdUnits.kt`:
  interstitial `gxxgiao5omzaqz95`, rewarded `8ow6cxhi3gcopyd9`, banner `0shs04ti2gakqd9k`.
  Only the App Key needs configuring below.
- SKAdNetwork: the IDs below are added to the iOS `Info.plist`. For a full list (AdMob,
  AppLovin, etc.) generate `SKAdNetwork.xml` from the LevelPlay dashboard >> SKAdNetwork
  ID Manager.
  - ironSource: `su67r6k2v3.skadnetwork`
  - Unity Ads: `4dzt52r2t5.skadnetwork`

### Android setup

1. Add your App Key to `gradle.properties` (or `~/.gradle/gradle.properties` to keep
   secrets out of the repo). The value is injected into `BuildConfig` in
   `androidApp/build.gradle.kts` per build type:
   ```properties
   LEVELPLAY_APP_KEY_DEBUG=...
   LEVELPLAY_APP_KEY_RELEASE=...
   ```
   Ad Unit IDs are NOT configured here - they live in `LevelPlayAdUnits.kt` (shared
   module).
2. `MainActivity` pushes these values into `AndroidAdsConfigHolder` (in `shared` androidMain)
   and exposes the raw activity to `AndroidAdsHost` so `showInterstitial`/`showRewarded`
   have a presenter.
3. `AndroidManifest.xml` already declares `INTERNET`, `ACCESS_NETWORK_STATE` and the
   `com.google.android.gms.permission.AD_ID` permissions.
4. Build: `./gradlew :androidApp:assembleDebug`.

### iOS setup

1. Install CocoaPods dependencies (IronSource SDK + Unity Ads adapter):
   ```bash
   cd iosApp
   pod install
   ```
   The bridge Swift file `iosApp/iosApp/LevelPlayAdsKit/LevelPlayAdsKit.swift` needs
   `import IronSource`, so build the app from Xcode **after** `pod install`.
2. The wrapper is linked into the app via the `Shared` framework (Kotlin sees only the
   self-contained `LevelPlayAdsKit.h`, so the Kotlin cinterop/CocoaPods build order does
   not matter).
3. Set your App Key in `iosApp/Configuration/Config-Debug.xcconfig` and
   `Config-Release.xcconfig` (they `#include "Config.xcconfig"`); the Project-level
   **Debug** build configuration points at `Config-Debug.xcconfig` and **Release** at
   `Config-Release.xcconfig`. The value is expanded into `Info.plist`
   (`LevelPlayAppKey`) which `AdsConfig` reads at runtime via `NSBundle`. Ad Unit IDs
   come from `LevelPlayAdUnits.kt`, not the plist.

### Recommended build order (first time)

1. `./gradlew :androidApp:assembleDebug`
2. `cd iosApp && pod install`
3. Open `iosApp.xcworkspace` in Xcode (not the project) and run.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…