# ClawDroid Theme Prompt — Professional × Simple

Use this prompt (as-is) with any designer or LLM to generate, review, or extend
the ClawDroid theme. It is written against the current **Obsidian Astra** dark
theme (`ui/theme/Color.kt`, `ui/theme/Theme.kt`).

---

## The prompt

> Design a mobile dark theme that is a perfect blend of **professionalism and
> simplicity** for ClawDroid, an AI agent chat app (Jetpack Compose, Material 3).
>
> **Mood:** calm, competent, quiet confidence. A senior engineer's terminal at
> midnight — not a gaming rig, not a bank form. No neon glow abuse, no gradients
> for decoration, no pure black, no pure saturated blue.
>
> **Palette (stay inside it):**
> - Background/surfaces: near-black blue-grey `#111416`, stepped containers
>   `#0C0F11 → #191C1E → #1D2022 → #272A2C → #323537` (use steps to show depth,
>   never shadows heavier than 4dp).
> - Primary accent: soft periwinkle `#D3E2FF` on dark `#0A315B`; container
>   `#A8C7FA`. Secondary: muted grey `#C5C7C5`.
> - Voice/thinking highlight only: soft lavender `#B0A2F8` (loading, speaking,
>   processing — nowhere else).
> - Glass overlays: 8–22% white fills, 12–20% white borders. Error: `#FFB4AB`.
> - Text: `#E1E2E5` primary, `#C3C6D0` secondary at 70–90% alpha, `#8D9199`
>   disabled.
>
> **Rules:**
> 1. One accent per screen. Everything else is grey.
> 2. Corners: 12dp cards, 22dp chat bubbles, full-round only for avatars/loaders.
> 3. Motion is information: 1500ms linear for continuous spinners, 1000ms
>    ease-in-out-reverse for breathing states. Nothing bounces, nothing shakes.
> 4. Empty/loading states show a short human line + one quiet visual — never a
>    bare spinner and never a wall of skeleton bars.
> 5. Touch targets ≥ 48dp. Contrast ≥ 4.5:1 for all body text. Dynamic
>    Material You color may tint the primary, but the surface steps stay.
> 6. Every new component must map to existing tokens — if you need a new color,
>    you must justify which rule above failed.
>
> **Deliver:** updated `Color.kt` tokens only (no new files), a 3-bullet reason
> for each changed value, and one screenshot-described check ("settings screen
> at 50% brightness still reads primary actions first").

---

## How we apply it in this repo

- Tokens live in `app/src/main/java/com/clawdroid/app/ui/theme/Color.kt`.
- `Theme.kt` wires the single dark scheme (`ObsidianAstraColors`).
- Processing/voice moments use `ActivePurple` (`#B0A2F8`); everything else uses
  Astra greys/blues. Keep it that way.
- Quote card (`QuoteCard.kt`) and loader (`CustomProcessingLoader.kt`) follow
  rules 2–4 above: 12dp glass card, italic secondary text, breathing animation.
