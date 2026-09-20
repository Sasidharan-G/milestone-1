# APK distribution workflow

## Decision log

- Decision: keep one distributable APK at `apk-releases/kadaikutty-pos-v0.1.0-release.apk`.
- Alternatives considered: retain Gradle's output as the handoff file, or maintain root-level copies. Both create duplicate APKs and make the latest file unclear.
- Rationale: the `releaseApk` task cleanly rebuilds the signed release variant, then replaces the canonical file only after a successful build.

## Use

From `android-app`, run:

```powershell
.\gradlew.bat releaseApk
```

The task runs `clean` and `assembleRelease`, so the APK is built from the current source. The intermediate Gradle APK is removed after it is copied; the only distributable APK remains in `apk-releases`.
