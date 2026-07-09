# ClawDroid APK Deployment

## Debug APK For Testing

Build:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signed Release APK

Build:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

This APK is signed only when `signing/release-signing.properties` exists locally. The signing directory is ignored by git.

## Release Verification

```bash
SDK_DIR="$(sed -n 's/^sdk.dir=//p' local.properties)"
BUILD_TOOLS="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
"$BUILD_TOOLS/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

## Smoke Test Checklist

- Launch app from a clean install.
- Confirm onboarding and setup complete.
- Send a normal chat message and confirm streaming response.
- Trigger a tool call and confirm collapsible activity steps render.
- Use `Continue in new chat` after a provider/context error and confirm the new run continues previous work.
- Tap a ClawDroid notification and confirm it opens the app without opening the overlay.
- Open the overlay, type with the keyboard, and confirm the dock stays near the keyboard instead of jumping to the top.
- Complete an overlay task and confirm current task plus latest response remain visible.
- Test voice input, TTS, stop, and close controls.
- Reopen app after backgrounding and confirm the latest chat is retained.

## GitHub Release

Repository:

```text
https://github.com/polymaths-org/ClawDroid
```

Configure the release signing secrets in GitHub before pushing a release tag:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Then push a semantic version tag:

```bash
git tag -a v0.1.0 -m "ClawDroid v0.1.0"
git push origin HEAD
git push origin v0.1.0
```

The workflow will upload the signed APK and checksum to the GitHub Release.
