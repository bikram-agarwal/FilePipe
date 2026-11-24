# R8 rules for the androidTest APK when it targets devRelease (-PscreenshotsOnDevRelease=true,
# ../capture_screenshots.ps1). AGP minifies the test APK against the app's mapping in that case.

# Annotation-only references from errorprone (compile-time) — not present at runtime.
-dontwarn javax.lang.model.element.Modifier

# JUnit finds @Test / @Rule / @Before by reflection, and the capture script runs the screenshot
# tests by class#method name, so keep them (and their annotations) as written.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class dev.bikram.filepipe.screenshots.** { *; }
