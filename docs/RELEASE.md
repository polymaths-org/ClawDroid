# ClawDroid Release Guide

This guide covers the stable APK release flow for the transferred repository:

https://github.com/polymaths-org/ClawDroid

## Local Build Checks

Run these before publishing:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease
```

## Local Release Signing

Release signing is intentionally local-only. The keystore and signing properties live under `signing/`, which is ignored by git.

Expected local file:

```properties
storeFile=signing/clawdroid-release.jks
storePassword=...
keyAlias=clawdroid-release
keyPassword=...
```

Back up `signing/` securely. If this keystore is lost, future APK updates cannot be signed with the same identity.

## Verify A Signed APK

Use the newest Android SDK build tools available locally:

```bash
SDK_DIR="$(sed -n 's/^sdk.dir=//p' local.properties)"
BUILD_TOOLS="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
"$BUILD_TOOLS/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

## GitHub Release

The workflow in `.github/workflows/build.yml` creates a GitHub Release when a tag matching `v*.*.*` is pushed.

Before pushing a release tag, configure these repository secrets:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Create `RELEASE_KEYSTORE_BASE64` from the local keystore:

```bash
base64 -w 0 signing/clawdroid-release.jks
```

Then tag and push:

```bash
git tag -a v0.1.0 -m "ClawDroid v0.1.0"
git push origin HEAD
git push origin v0.1.0
```

Do not push the tag until the secrets are configured, otherwise the release job will fail during signing.
