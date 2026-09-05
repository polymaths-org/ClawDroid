# Interpole Review — Desktop Control + Visual Feedback

Branch: `feat/ver2-feature-work`. All new desktop work respects the master
kill-switch: **Settings → Interpole → Enable Interpole OFF = every desktop tool
returns `interpole_disabled` and touches nothing.**

## What was built

| # | Feature | Where | Status |
|---|---------|-------|--------|
| 1 | Interpole master switch + Hyprland easy-mode toggle | `AppConfigManager`, `InterpoleConfig.kt`, `InterpoleConfigScreen` | ✅ (task 6) |
| 2 | Desktop tools: mouse_move, left/right/double click, drag, scroll, key_press, type_text, open_app, screenshot | `DesktopControlTools.kt` + `ToolExecutor.kt` + `ToolSchemaRegistry.kt` | ✅ |
| 3 | `smart_desktop_action`: batch + per-step retry (0–3) + before/after screenshots | `DesktopControlTools.smartAction` | ✅ |
| 4 | Visual proof: ✅/❌ step card + screenshot paths; 🖼 preview rows → fullscreen dialog | `ChatScreen.kt` (`Desktop` step type, `extractFilePaths`), `ChatModels.kt` | ✅ |
| 5 | Screenshots saved to `Documents/ClawDroid/Output/interpole-<ts>.png` | `DesktopControlTools.fetchScreenshot` | ✅ |
| 6 | Quote card + `thinking.gif` loader (55KB optimized, spinner fallback) | `QuoteCard.kt`, `CustomProcessingLoader.kt`, Coil + `coil-gif` deps | ✅ |
| 7 | OS target picker: auto/hyprland/wayland/x11/debian/windows | `desktopOsTarget` pref + Interpole screen | ✅ |
| 8 | Theme prompt (professional × simple) | `docs/THEME_PROMPT.md` | ✅ |
| 9 | Arch Hyprland setup script | `scripts/interpole-hyprland-setup.sh` | ✅ |

## Backend matrix

| Target | Move/click | Keys/type | Screenshot | Notes |
|--------|-----------|-----------|------------|-------|
| Hyprland (easy ON) | `ydotool mousemove/click` | `ydotool key` (evdev map) / `wtype` | `grim` | Needs `ydotoold` running; `hyprctl dispatch exec` for apps |
| Wayland generic | same as Hyprland minus hyprctl | same | `grim` | No `hyprctl` calls at all |
| X11 | `xdotool` | `xdotool key/type` | `scrot`/`import` fallback | Most reliable for drag |
| Debian | auto chain | auto chain | `grim → scrot → import` | Same tools, apt-installable |
| Windows | PowerShell `mouse_event`/`SetCursorPos` | `SendKeys` | GDI+ capture | Best-effort; needs companion shell |

Scroll on Wayland = PageUp/PageDown/arrow keys (no universal scroll injection).
Drag on Wayland needs a new-enough `ydotool` (`mousedown`/`mouseup`).

## Transport

- Host empty → commands run **locally** (Arch demo mode).
- Host set → wrapped in `ssh -o BatchMode=yes -p PORT HOST 'cmd'`, screenshots
  pulled back with `scp`. Key-auth required (never password prompts).

## Arch Hyprland demo script (2 min)

1. On the desktop: `bash scripts/interpole-hyprland-setup.sh`
2. In app: Sidebar → **Interpole** → Enable ON, Hyprland easy-mode ON, target Auto.
3. Ask: *"Open kitty, type `htop`, take a screenshot"* — agent should use ONE
   `smart_desktop_action` call, then you get ✅ + before/after 🖼 previews.
4. Tap any preview → fullscreen proof. PNG also in `Documents/ClawDroid/Output/`.
5. Kill-switch demo: toggle Enable OFF and repeat — clean `interpole_disabled`.

## Known limits (honest)

- No on-device build here (no Android SDK): brace/paren + reference checks pass,
  but run `./gradlew :app:assembleDebug` on your machine before the demo.
- Wayland security model: `ydotoold` daemon must run or input is refused by design.
- `fifanim.gif` (untracked 1.4MB in old `app/src/drawable-nodpi/`) was removed
  with that non-packaged folder; restore from your copy if still needed.
- Screenshot liveness check is file-exists + non-empty (no pixel diff yet).
- Windows path is command-correct but untested against a real host.
