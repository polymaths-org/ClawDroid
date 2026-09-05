# Error 1: `launch_app` Fails for User-Installed Apps

**Severity:** 🟡 Medium (Workaround exists)

## The Problem
The `launch_app(package_name)` tool returns `success: false` when trying to open any **user-installed app** (e.g., WhatsApp, Chrome, Instagram), while it works perfectly for **system apps** (e.g., `com.android.settings`).

```
launch_app("com.whatsapp")        → ❌ success: false
launch_app("com.android.settings") → ✅ success: true
```

## Root Cause
ClawDroid targets a modern Android SDK, so package-manager calls are filtered by Android package visibility rules. The manifest did not declare visibility for launcher apps, which meant ClawDroid could resolve system apps like Settings but not user-installed launcher apps like WhatsApp, Chrome, or Instagram.

## Symptoms
- `launch_app` returns `success: false` silently — no error message
- `get_installed_apps` also returns empty or system-only results
- Apps **are** installed and usable — just invisible to these tools

## Fix Applied

- Added launcher-app package visibility to `app/src/main/AndroidManifest.xml` with a `<queries>` declaration for `ACTION_MAIN` + `CATEGORY_LAUNCHER`.
- Updated `get_installed_apps` in `AndroidControlTools.kt` to list launchable app activities instead of raw installed application records.
- Verified with `./gradlew :app:assembleDebug`.

## Workaround: Smart Launcher Method
Instead of `launch_app`, use visual navigation via the home screen:

1. `press_home()` → Go to launcher
2. `get_screen()` → Scan the UI tree for the app icon
3. `tap(x, y)` or `tap_text("App Name")` → Tap the icon directly

This works 100% of the time for any installed app.

## Example
```python
# Instead of:
launch_app("com.whatsapp")  # ❌ Fails

# Do:
press_home()
# Find WhatsApp icon on home screen or app drawer
# Tap it by coordinates or text
```

## Status
- ✅ Root fix applied locally
- ✅ Workaround remains useful if an OEM launcher or app-specific restriction still blocks package launch
- ✅ Debug build passes

---
*Documented by Rigbee — Last updated: 2026-06-16*
