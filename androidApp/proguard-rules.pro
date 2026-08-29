# Add project specific ProGuard rules here.

# LevelPlay / IronSource SDK
-keep class com.unity3d.mediation.** { *; }
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-keep class com.ironsource.adapters.** { *; }
-keep class com.ironsource.mediationsdk.** { *; }
-keep class com.ironsource.sdk.** { *; }
-dontwarn com.ironsource.**
-dontwarn com.unity3d.**
-dontwarn android.webkit.**