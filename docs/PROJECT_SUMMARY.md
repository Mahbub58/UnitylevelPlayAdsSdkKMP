# Session Summary — LevelPlay Ads KMP (iOS + Android)

## Objective
- Render a **LevelPlay (ironsource) banner** at the **bottom of the screen, full-width** in the KMP app `UnitylevelPlayAdsSdkKMP` (iOS + Android).
- End persistent `Code 509 "Mediation No fill"` on the production **banner** ad unit.
- Implement ads "perfectly" per the official `ironsource-mobile/Mediation-Demo-Apps` reference (cloned at `/var/folders/bf/992n0s313117r6mv__124t8h0000gn/T/opencode/Mediation-Demo-Apps`).
- Consent/regulation must use the **SDK's own consent APIs** (no custom UI): `LevelPlayPrivacySettings` (Android) and `LPMPrivacySettings` (iOS).

## Current State (latest session)
- **SDK consent dialog (Google UMP) now actually renders in debug builds on both platforms.** Root blocker was missing Google Mobile Ads App IDs: UMP had no app identity → consent info request always failed (this was also the trigger for the earlier iOS NSLog crash path). Now:
  - iOS: `GADApplicationIdentifier` in Info.plist via `$(ADMOB_APP_ID)` from `iosApp/Configuration/*.xcconfig` (Debug+Release both set to Google's sample test App ID `ca-app-pub-3940256099942544~1458002511`).
  - Android: `com.google.android.gms.ads.APPLICATION_ID` in AndroidManifest via `manifestPlaceholders["admobApplicationId"]`, overridable with `./gradlew -PADMOB_APP_ID=...`, defaulting to Google's sample test ID `ca-app-pub-3940256099942544~3347511713`.
  - Sample IDs carry a pre-configured consent message so the dialog works NOW; swap for YOUR IDs + publish a CMP message in AdMob for production (single place each: xcconfig `ADMOB_APP_ID`, `-PADMOB_APP_ID`).
- **Debug consent-region switch** added: `AdsCredentials.DEBUG_CONSENT_GEOGRAPHY` ("EEA" default | "US" | "NONE"). **Gated on the new `AdsCredentials.DEBUG_FORCE_CONSENT_DIALOG` flag (default `false`), NOT `ENABLE_TEST_SUITE`, so the two debug features are independent.** Maps to UMP debug geography: iOS `UMPDebugGeographyEEA=1` / `RegulatedUSState=3` (DebugSettings via `RequestParameters.debugSettings`, chosen by raw value); Android `DEBUG_GEOGRAPHY_EEA` / `DEBUG_GEOGRAPHY_REGULATED_US_STATE` (NOTE: these are Int constants; the whole "enum" is an IntDef, so Kotlin fun returns `Int`).
- **"Yesterday" defaults restored (user-facing fix for: banner hidden + fullscreen no-fill after Test Suite was added):** `ENABLE_TEST_SUITE=false` (SDK runs in real ad-serving mode, no `is_test_suite` metadata, no auto-launched Test Suite overlay covering the app UI) and `ENABLE_COPPA=false` (restores bidding/personalized demand). Verified fresh-install iOS run: no consent dialog (real region BD → `canRequestAds` true), **no Test Suite overlay, banner visible at bottom**, interstitial + rewarded both `didLoad … BID`. Android verified parity on emulator-5554: `Interstitial loaded`, `Rewarded loaded`, `Banner loaded`, `topResumedActivity = MainActivity` (no Test Suite). Screenshot of iOS banner at `/var/folders/bf/992n0s313117r6mv__124t8h0000gn/T/opencode/ios_banner_visible.png`.
- Opt-ins (each a single const flip + rebuild): `ENABLE_TEST_SUITE=true` → Test Suite mode + auto-launch; `DEBUG_FORCE_CONSENT_DIALOG=true` (+ `DEBUG_CONSENT_GEOGRAPHY`) → UMP dialog forced on first fresh launch; both can be on together (dialog gate runs before init, then Test Suite launches on init success).
- **CCPA (US states) flow enabled:** GEOGRAPHY="US" forces the US-regulated message (do-not-sell). Real do-not-sell is read from the IAB US Privacy String `IABUSPrivacy_String` written by UMP (2nd char 'Y' = opted out): Android reads default SharedPreferences (`<package>_preferences`), iOS reads NSUserDefaults via `usPrivacyDoNotSell` in the bridge; value flows into `ConsentResult.doNotSell` → `LevelPlayPrivacySettings.setCCPA` / `LPMPrivacySettings.setCCPA`. Fallback to `AdsCredentials.CCPA_DO_NOT_SELL` when no string. Caveat: Google's sample App IDs auto-consent for the US geography (observed `canRequestAds=true`), so to interactively test the CCPA form you must publish a US-states message under your own AdMob App ID; the EEA flow presents with the sample ID immediately.
- **iOS auth removed from requestConsent** (was unconditionally forced). Bridge is now `requestConsent(testMode:debugGeography:completion:)`, callback is `(BOOL canRequestAds, BOOL doNotSell, NSError*)`. If form presentation fails but `canRequestAds==true`, we proceed with real consent instead of dropping to defaults (prevents GDPR regression). Together with the INFO lines above, verified EEA flow: `UMP: consent info OK, canRequestAds=false, presenting SDK consent form` (iOS) and Android `status=2 privacyOptions=REQUIRED formAvailable=true canRequestAds=false` + `load_complete status=ok`.
- **Android**: REMOVED `setTagForUnderAgeOfConsent(AdsCredentials.ENABLE_COPPA)` from UMP request (UMP child-tag suppresses the consent dialog). COPPA keeps being passed to LevelPlay via `LevelPlayPrivacySettings.setCOPPA` / `LPMPrivacySettings.setCOPPA` before init (the ad-network signal, unchanged). UMP iOS never set the tag.
- **iOS no-fill root cause (user: "works yesterday, broke after we added the Test Suite") — CONFIRMED + FIXED.** The previous run's 509 "Mediation No fill" on iOS interstitial/rewarded was **NOT dashboard config** — it was the **Test Suite dev mode** (`is_test_suite=enable` metadata) plus `ENABLE_COPPA=true`, which switched the SDK's mediated configuration and suppressed fullscreen fill. After the default restore below, on the SAME account/ad units, sim 2FCAA47D… shows **ALL THREE iOS formats loading via bidding**: `LPMInterstitialAdDelegate didLoadAdWithAdInfo … mm99t0zi6liu8caw … ironsourceads Bidding 25265407 precision BID`, `LPMRewardedAdDelegate didLoadAd … vu8wedrf3cr1kc7z … Bidding 25265406 precision BID`, banner `vkgmtcqo3vjyslms … Bidding 25265408` loads+displays repeatedly (`country: BD`). NO dashboard change needed. (Prior "activate a bidding instance in dashboard" advice is superseded.)
- Screenshot of the iOS UMP consent dialog on-screen at `/var/folders/bf/992n0s313117r6mv__124t8h0000gn/T/opencode/ios_consent_dialog.png` (this model cannot view images; verified via logs + init gate instead).

## Missing Unity Ads adapter on Android — fixed (earlier session)
- Confirmed locally on the running emulator (`emulator-5554`, Android 16, Pixel 9a): `LevelPlay.launchTestSuite` threw `ClassNotFoundException: com.unity3d.ads.UnityAds` → Test Suite never opened, and Unity Ads bidding data was never collected for the banner.
- Root cause: Android app had the **Unity Ads adapter** (`com.unity3d.ads-mediation:unityads-adapter:5.9.0`) but NOT the **Unity Ads core SDK** (`com.unity3d.ads:unity-ads`). Adapters declare the network SDK as `compileOnly`; the demo adds it explicitly. iOS was fine because the pod pulls UnityAds.
- Fix: added `unity-ads = "4.15.0"` → `levelplay-unityAds = { module = "com.unity3d.ads:unity-ads" }` in `libs.versions.toml` + `implementation(libs.levelplay.unityAds)` in `shared/build.gradle.kts` androidMain. `unity-ads` pulls `play-services-cronet → org.chromium.net:cronet-api` (Google Maven only) → added `includeGroupAndSubgroups("org.chromium")` to both `google()` `mavenContent` blocks in `settings.gradle.kts`.
- Verified on emulator: `IntegrationHelper: UnityAds - VERIFIED`; `UnityAds initialize 4.15.0`, `SDK init success`, `LevelPlay Test Suite launched` (topResumedActivity = `com.ironsource.mediationsdk.testSuite.TestSuiteActivity`), and **Banner/Interstitial/Rewarded all loaded** (`Banner loaded: 0shs04ti2gakqd9k`, `Interstitial loaded: gxxgiao5omzaqz95`, `Rewarded loaded: 8ow6cxhi3gcopyd9`). Process alive, zero crashes.
- Benign log: `error while setting consent of UnityAds: No static method setUserConsent(Boolean) in UnityAds` — legacy API removed in Unity Ads 4.x; adapter swallows it, consent still works via `setGDPRConsents`.
- Note: this model cannot view screenshots; device-state evidence used instead.

## Crash fixed (iOS) — latest session
- User: "my ios app is crashed". Root cause in `ConsentPlatform.ios.kt`: `NSLog("...%@", e.localizedDescription)` passed a Kotlin `String` into K/N's variadic `NSLog` → corrupted ObjC string pointer → `EXC_BAD_ACCESS` (`KERN_INVALID_ADDRESS at 0x...6c756f5e`, i.e. ASCII "ould n") whenever UMP errored. UMP always errored because no Google CMP message is published for the app. Hit on every launch → app dead on startup.
- Fix: interpolate the string into the format: `NSLog("ConsentUMP: consent flow failed: ${e.localizedDescription}")` (no varargs). Only such occurrence in Kotlin sources.
- Verified: fresh uninstall → rebuild (BUILD SUCCEEDED) → reinstall → launch → PID alive 30s+, no new `.ips` crash report. Fullscreen (interstitial/rewarded) logged 509 on that simulator run, but banner **loaded** via `ironsourceads Bidding 25265408` (BID precision). Note: prior launch-attempt errors (`--console-pty`, `FBSOpenApplicationServiceErrorDomain code=4`) were pty artifacts — plain `simctl launch <bundleID>` works.
- Learning: never pass Kotlin `String` scalars into variadic `NSLog`/`printf` in K/N — interpolate instead.

## Current State (latest session)
- **Google UMP (consent SDK) integrated** on both platforms — SDK-provided consent form for GDPR/CCPA/COPPA, triggered on app start before SDK init:
  - Android: `com.google.android.ump:user-messaging-platform:4.0.0` (Google Maven). Flow in `shared/src/androidMain/.../ads/ConsentPlatform.android.kt`: `requestConsentInfoUpdate` → `UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity)` → `canRequestAds()`. When `DEBUG_FORCE_CONSENT_DIALOG=true` forces `DEBUG_GEOGRAPHY_EEA`/`DEBUG_GEOGRAPHY_REGULATED_US_STATE` + `setForceTesting(true)` so the form always shows. (UMP `setTagForUnderAgeOfConsent` was REMOVED from the request — it suppresses the consent form; COPPA is only passed to LevelPlay.)
  - iOS: pod `GoogleUserMessagingPlatform ~> 3.1` (installed, headers at `Pods/GoogleUserMessagingPlatform/.../UserMessagingPlatform.framework/Headers`). Swift bridge `requestConsent(testMode:completion:)` in `LevelPlayAdsKit.swift` uses `ConsentInformation.shared` / `ConsentForm.loadAndPresentIfRequired(from:)` (Swift-renamed types: `RequestParameters`, `DebugSettings`, `ConsentInformation`, `ConsentForm`); test mode sets `.EEA` geography on `DebugSettings`.
  - Kotlin common `AdsConsent.kt` (expect `requestConsent(onComplete:)`) + `ConsentResult` (canRequestAds/doNotSell/childDirected); iOS actual at `shared/src/iosMain/.../ads/ConsentPlatform.ios.kt` (requires `@OptIn(ExperimentalForeignApi::class)`).
- Init is gated on consent: `AdsCoordinator.start` calls `requestConsent` first, falls back to `ConsentResult.default()` (from `AdsCredentials`) if the flow fails (e.g., no AdMob CMP message configured), then calls `adsManager.init(appKey, consent)`. `AdsManager.init` signature now takes `ConsentResult`.
- Consent applied via SDK privacy APIs (no deprecated `setConsent`/`setMetaData`):
  - Android: `LevelPlayPrivacySettings.setGDPRConsents(mapOf("UnityAds" to canRequestAds, "IronSource" to canRequestAds))`, `setCCPA(doNotSell)`, `setCOPPA(childDirected)`.
  - iOS: `LPMPrivacySettings.setGDPRConsent(_)`, `setCCPA(_)`, `setCOPPA(_)` in `applyPrivacyConsent:doNotSell:childDirected:`.
- **Custom consent dialog REMOVED** (user rejected it). The SDK-provided UI is the UMP consent form + the LevelPlay Test Suite (`is_test_suite` + `launchTestSuite`).
- Demo-app pattern kept: `LevelPlay.validateIntegration(activity)` in debug (gated by `ENABLE_TEST_SUITE`), ad objects created after init success, adaptive banner in container.
- **UMP production requirement:** UMP only renders a consent form when the app has a published CMP message configured in the AdMob/Google dashboard for its App ID. Until then the form fails gracefully and the app falls back to `AdsCredentials` defaults.
- Important: consent is applied to LevelPlay **before init**; interstitial/rewarded loaded only after init success (demo pattern).

## Credentials (AdsCredentials.kt — single source of truth)
- `ENABLE_TEST_SUITE = false` (opt-in Test Suite; ON sets `is_test_suite` dev mode which previously caused the iOS fullscreen "Mediation No fill")
- `DEBUG_FORCE_CONSENT_DIALOG = false` (opt-in forced UMP dialog; pair with `DEBUG_CONSENT_GEOGRAPHY="EEA"/"US"/"NONE"`)
- `GDPR_CONSENT = true`, `CCPA_DO_NOT_SELL = false`, `ENABLE_COPPA = false` (COPPA ON previously suppressed fullscreen fill too)
- Android: APP_KEY `27d971f7d`; INTERSTITIAL `gxxgiao5omzaqz95`; REWARDED `8ow6cxhi3gcopyd9`; BANNER `0shs04ti2gakqd9k`
- iOS: APP_KEY `27d9bd7a5`; INTERSTITIAL `mm99t0zi6liu8caw`; REWARDED `vu8wedrf3cr1kc7z`; BANNER `vkgmtcqo3vjyslms`
- Android key also in `gradle.properties` (`LEVELPLAY_APP_KEY_DEBUG/RELEASE`); iOS in Info.plist `LevelPlayAppKey` + CLI `LEVELPLAY_APP_KEY=` override.

## IMPORTANT — Recent Regression Root Cause & Fix
- Earlier session set unconditional `do_not_sell=true` + `is_child_directed=true` before init → **killed ALL iOS demand** (that is why iOS "previously working" went no-fill).
- Removing those and using consent values from flags restored the previous behavior. `CCPA_DO_NOT_SELL=false` default keeps demand.
- **Second regression (user report: "banner hidden, interstitial/rewarded not loading after adding the Test Suite"):** the Test Suite path set `is_test_suite=enable` metadata for the whole SDK lifetime + auto-launched the Test Suite over the app UI (hiding the bottom banner), and `ENABLE_COPPA=true` suppressed fullscreen demand. **Fix applied:** `ENABLE_TEST_SUITE=false` + `ENABLE_COPPA=false` by default → all three formats load via bidding on both platforms (verified). Both remain available as deliberate opt-ins for testing.
- **COPPA caveat:** `ENABLE_COPPA=true` (child-directed) suppresses most bidding/personalized demand. Keep `false` unless the app truly targets children.

## Verified Findings (production dashboard, iOS sim)
- Banner unit only ever dispatches `IronSource_25265408` → no bidder → Code 509. Tested at 320×50, adaptive 393×50, with/without 3s warmup, retries. Banner needs a **Unity Ads (Bidding) instance activated on the banner ad unit** in the dashboard (user-side fix; code can't create demand).
- Interstitial (`ironsourceads Bidding 25265407`) and rewarded (`ironsourceads Bidding 25265406`) DO fill on production.
- Demo app key `25b63cf85` + ad unit `4fpetq4lhe5lsw3e` (banner) rendered bottom-centered when used — proof of concept; demo creds were reverted.
- Test Suite banner ≠ proof of live demand (test mode bypasses the real auction).

## Build/Verify Commands
- cinterop regen after header changes: `./gradlew :shared:clean`
- Android: `./gradlew :androidApp:assembleDebug`
- iOS Kotlin: `./gradlew :shared:compileKotlinIosSimulatorArm64`
- iOS full: xcodebuild `-workspace iosApp/iosApp.xcworkspace -scheme iosApp -configuration Debug -destination 'platform=iOS Simulator,id=2FCAA47D-B1E2-4E36-912C-50BDA58111F8' -derivedDataPath <fresh>` with `LANG=... LC_ALL=...`, `CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY="-" DEVELOPMENT_TEAM="" PROVISIONING_PROFILE_SPECIFIER=""`, `LEVELPLAY_APP_KEY=27d9bd7a5`. Fresh DerivedData each build. Project build alone fails (`Unable to find module dependency: 'IronSource'`) — must use workspace.
- No Android emulator/adb on this machine → Android runtime verification by user.
- iOS logs consent fields verified: `consent: 1`, `do_not_sell`, `is_child_directed` (old meta path, no longer used after latest change).

## Android Test Suite (user issue: "not opening")
- Requires `LevelPlay.setMetaData("is_test_suite","enable")` BEFORE init (message: `TestSuite cannot be launched because the setMetadata flag is not enabled...`).
- Launched on `onInitSuccess` via `launchTestSuite(activity)` posted to main thread with guards (`isFinishing`/`isDestroyed`) + try/catch Throwable.
- If it does not open, likely init is failing (invalid app key / no network) — check `adb logcat -s LevelPlayAds:E LevelPlay_CRASH:E`. Crash diagnostics: `installCrashLogger()` in MainActivity logs tag `LevelPlay_CRASH`.

## Consent APIs reference
- Android 9.4.0: `LevelPlayPrivacySettings` (`setGDPRConsents(Map<String,Boolean>)`, `setCCPA(boolean)`, `setCOPPA(boolean)`). `LevelPlay.setConsent` and `setMetaData do_not_sell/is_child_directed` are deprecated.
- iOS: `LPMPrivacySettings` (`setGDPRConsent:BOOL`, `setGDPRConsents:NSDictionary` deprecated, `setCCPA:BOOL` = do-not-sell YES, `setCOPPA:BOOL` must be before init). Header: `IronSource.framework/Headers/LPMPrivacySettings.h`.
- Consent SDK: **Google UMP** — Android `com.google.android.ump:user-messaging-platform` (Google Maven; current 4.0.0, minSdk 23), iOS pod `GoogleUserMessagingPlatform` (`~> 3.1`, current 3.1.0). UMP provides the actual SDK consent form (GDPR EEA + UK, US states/CCPA, `tagForUnderAgeOfConsent` for COPPA). LevelPlay ≥7.7.0 auto-passes TCF/GDPR consent from UMP to mediated networks.
- Signed/verified from the cloned demo repo: `DemoActivity.kt` (Android) and `DemoViewController.swift`/`AppDelegate.swift` (iOS Swift).

## Relevant Files
- `shared/src/commonMain/.../ads/AdsCredentials.kt` — all keys/IDs + `ENABLE_TEST_SUITE`, `GDPR_CONSENT`, `CCPA_DO_NOT_SELL`, `ENABLE_COPPA`.
- `shared/src/commonMain/.../ads/AdsConsent.kt` — `ConsentResult` + expect `requestConsent`.
- `shared/src/androidMain/.../ads/ConsentPlatform.android.kt` — UMP flow (debug EEA geography in test mode, COPPA tag, form load+show, `canRequestAds`).
- `shared/src/iosMain/.../ads/ConsentPlatform.ios.kt` — UMP bridge call (`@OptIn(ExperimentalForeignApi::class)`).
- `shared/src/androidMain/.../ads/PlatformAdsManager.android.kt` — init(appKey, consent) + `applyPrivacySettings(consent)` (SDK consent APIs), `validateIntegration` (debug), banner (adaptive + 3s warmup delay, no-fill retries 5×10s), `launchTestSuite`.
- `shared/src/iosMain/.../ads/PlatformAdsManager.ios.kt` — `setTestSuiteEnabled`, `applyPrivacyConsent(...)`, init bridge with consent, `launchTestSuite()` on init success.
- `iosApp/iosApp/LevelPlayAdsKit/LevelPlayAdsKit.h` + `.swift` — `requestConsentWithTestMode:completion:` (UMP), banner (adaptive + 3s delay), `applyPrivacyConsent:doNotSell:childDirected:` (LPMPrivacySettings), test-suite bridge, delegates.
- `iosApp/Podfile` — added `GoogleUserMessagingPlatform ~> 3.1`.
- `gradle/libs.versions.toml` + `shared/build.gradle.kts` — added `ump 4.0.0` androidMain dependency.
- `shared/src/commonMain/.../App.kt` — no consent UI; init on start (gated by UMP consent via coordinator); bottom banner when `AdsInitState.Ready`.
- `shared/src/commonMain/.../ads/AdsCoordinator.kt` — `start()` runs `requestConsent` then `adsManager.init(appKey, consent)`.
- `shared/src/androidMain/.../ads/AdsBannerView.android.kt`, `shared/src/iosMain/.../ads/AdsBannerView.ios.kt` — state-driven height, bottom slot, `onDispose { destroyBanner() }`.
- `androidApp/.../MainActivity.kt` — GAID thread (catches Throwable), `installCrashLogger()` (tag `LevelPlay_CRASH`), host/config holders.
- `iosApp/iosApp/Info.plist` — 78 SKAdNetwork IDs + `LevelPlayAppKey`.
- `gradle.properties`, `iosApp/Configuration/Config-*.xcconfig`, `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`.

## Open Items / Next Steps
1. Android: get user logcat (`adb logcat -s LevelPlayAds:E LevelPlay_CRASH:E`) for "test suite not opening" / crash.
2. AdMob dashboard (user-side): register the app and publish a CMP consent message so the UMP form actually renders; otherwise consent falls back to `AdsCredentials` defaults.
3. Dashboard (user-side): activate Unity Ads (Bidding) instance on the banner ad unit to end Code 509.
4. If fill drops with child-directed mode, flip `AdsCredentials.ENABLE_COPPA = false`.
5. Production: `ENABLE_TEST_SUITE = false`.