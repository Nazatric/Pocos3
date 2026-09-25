# Mornye Design System

Mornye is the design language used by the PocoS3 Android UI. It is **not**
Material 3 with a different colour palette. Material 3's neutral greys,
rounded-corner card chrome, and Motion-easing tokens are intentionally not
used; the visual identity of the app is distinct.

This document records the design tokens so any contributor adding a new
screen produces something that looks like the rest of the app.

## Visual identity (one paragraph)

Mornye is a **dark-first, warm-cool dual-tone** design language: dark slate
backgrounds with subtle amber-gold accents and a single cool-teal signal
colour for interactive elements. Surfaces use a layered translucency stack
(glass blur over warm-noise texture) so depth reads as warm light through
tinted glass. Geometry is **large-radius rounded squares** (28 dp on cards,
24 dp on buttons, full-pill on chips), not Material's 16 dp. Typography is
high-contrast sans for headings (Inter Display weight 700) with a slightly
tightened optical size, paired with a wider humanist sans for body (Inter
weight 400). Animations favour **physical deceleration** (Material's
`FastOutSlowInElastic` is too soft; Mornye uses a cubic-bezier curve close
to `cubic-bezier(0.2, 0.6, 0.05, 1)` — fast attack, very long settle).

## Palette

The base palette is dark-only by default. A light variant exists but is
opt-in and not the primary identity.

### Dark (default)

| Token                | Hex       | Usage                                  |
|----------------------|-----------|----------------------------------------|
| `bg.deep`            | `#0A0B0F` | App background behind all surfaces.    |
| `bg.surface.0`       | `#111319` | Bottom of the translucency stack.      |
| `bg.surface.1`       | `#161A22` | Cards.                                 |
| `bg.surface.2`       | `#1C2230` | Elevated cards, dialogs.              |
| `bg.surface.glass`   | `#161A22AA` | Glass overlay; alpha-over-noise.   |
| `border.subtle`      | `#2A2F3D` | 1 dp borders on cards and dividers.    |
| `border.active`      | `#3A4258` | Hovered/selected card border.          |
| `text.primary`       | `#F4F1EA` | Body and headings. Warm-tinted white. |
| `text.secondary`     | `#A6AAB8` | Captions, labels.                      |
| `text.tertiary`      | `#6B7080` | Metadata, debug numbers.               |
| `accent.amber`       | `#F5B14C` | Brand colour. Logo, primary CTAs.      |
| `accent.amber.dim`   | `#A87833` | Pressed state of amber CTA.            |
| `accent.teal`        | `#3CC4D0` | Interactive elements: links, toggles, sliders. |
| `accent.teal.dim`    | `#1F6F78` | Pressed state.                         |
| `signal.success`     | `#5CC974` | Status OK.                              |
| `signal.warning`     | `#E0A040` | Thermal warning.                       |
| `signal.danger`      | `#E0556A` | Errors, crashes.                        |

### Light (opt-in, secondary)

| Token                | Hex       |
|----------------------|-----------|
| `bg.deep`            | `#F6F4ED` |
| `bg.surface.0`       | `#FAF8F2` |
| `bg.surface.1`       | `#FFFFFF` |
| `bg.surface.glass`   | `#FFFFFFCC` |
| `text.primary`       | `#15171C` |
| `text.secondary`     | `#5C606A` |
| `accent.amber`       | `#C6852D` |
| `accent.teal`        | `#1F8A95` |

## Typography

PocoS3 ships `Inter` (variable, weight 400–700) for both display and body.
For numeric/HUD readouts, `JetBrains Mono` (weight 500) is used so digits
align in tabular columns.

| Style                    | Family | Weight | Size  | Line | Tracking | Usage |
|--------------------------|--------|--------|-------|------|----------|-------|
| `display.xl`              | Inter  | 700    | 48 dp | 1.10 | -0.02 em | Onboarding hero. |
| `display.l`               | Inter  | 700    | 36 dp | 1.15 | -0.01 em | Screen titles. |
| `display.m`               | Inter  | 600    | 28 dp | 1.20 |  0.00 em | Section headers. |
| `body.l`                  | Inter  | 400    | 18 dp | 1.40 |  0.00 em | Primary body. |
| `body.m`                  | Inter  | 400    | 16 dp | 1.40 |  0.00 em | Default body. |
| `body.s`                  | Inter  | 400    | 14 dp | 1.40 |  0.00 em | Captions. |
| `label.button`            | Inter  | 600    | 15 dp | 1.00 |  0.01 em | Button text. |
| `label.chip`              | Inter  | 500    | 13 dp | 1.00 |  0.04 em | Chips, small tags. |
| `mono.hud`                | JetBrains Mono | 500 | 13 dp | 1.20 |  0.00 em | Performance HUD numbers. |
| `mono.hud.l`               | JetBrains Mono | 500 | 16 dp | 1.20 |  0.00 em | HUD primary metrics. |

## Geometry

| Token                | Value    | Usage |
|----------------------|----------|-------|
| `radius.card`         | 28 dp    | Game cards, settings panels. |
| `radius.button`       | 24 dp    | Buttons. |
| `radius.chip`         | 999 dp   | Chips, pills. |
| `radius.field`        | 16 dp    | Text fields, search bars. |
| `spacing.xxs`         | 4 dp     | Tight internal spacing. |
| `spacing.xs`          | 8 dp     | Default inner spacing of small elements. |
| `spacing.s`           | 12 dp    | Default grid unit. |
| `spacing.m`           | 16 dp    | Default card padding. |
| `spacing.l`           | 24 dp    | Section gap. |
| `spacing.xl`           | 32 dp    | Screen edge padding. |
| `spacing.xxl`          | 48 dp    | Hero spacing. |

## Motion

| Token                          | Duration | Easing |
|--------------------------------|----------|--------|
| `motion.tap`                    | 90 ms    | `cubic-bezier(0.2, 0.6, 0.05, 1)` |
| `motion.fadeIn.short`           | 120 ms   | `cubic-bezier(0.2, 0.6, 0.05, 1)` |
| `motion.fadeIn.default`         | 220 ms   | `cubic-bezier(0.2, 0.6, 0.05, 1)` |
| `motion.fadeIn.slow`             | 380 ms   | `cubic-bezier(0.2, 0.6, 0.05, 1)` |
| `motion.slideUp.default`         | 280 ms   | `cubic-bezier(0.2, 0.6, 0.05, 1)` |
| `motion.sheet.expand`            | 320 ms   | `cubic-bezier(0.2, 0.6, 0.05, 1)` |
| `motion.glass.shimmer`           | 1200 ms  | linear, looping |

## Surface hierarchy

From back to front:

1. `bg.deep` — the app background. A subtle warm-noise texture (alpha 4%) is
   baked in to break up flatness.
2. `bg.surface.0` — the lowest glass layer. `bg.surface.glass` over
   `bg.surface.0` produces the diffuse glass look.
3. `bg.surface.1` — opaque cards.
4. `bg.surface.2` — elevated cards, modal sheets.
5. `border.subtle` — 1 dp borders that define card edges without high contrast.
6. Content. Text, icons, controls.
7. `accent.teal` — interactive elements live *above* content but do not
   dominate.
8. `accent.amber` — the brand colour is reserved for the logo, primary CTA,
   and the active game card. Using it on anything else cheapens it.

## Iconography

Icons are simple line icons (stroke 1.75 dp at 24 dp nominal size). PocoS3
uses the `Lucide` icon set as the base and extends it where needed. No
filled icons except for the active state of segmented controls. No
emoji-as-icon.

## Touch overlay (in-game)

Touch controls are not Mornye-themed in the same way as the rest of the
app. They live *over* a rendered PS3 frame, so they must read against
arbitrary game art. The visual treatment is:

- White outline, 2 dp stroke, drop shadow (alpha 50%, blur 8 dp, y offset 2 dp).
- Fill: `bg.surface.glass` (alpha 70%) — same glass as app surfaces.
- When active (pressed): fill `accent.teal.dim` (alpha 50%).
- When hidden: opacity 0, hit-box still active (so a "hide overlay" toggle
  is a visual toggle, not a "stop sending input" toggle).

Analog sticks are a circular well (radius 32 dp) with a thumb (radius 16 dp).
The thumb glides with `motion.tap` easing. The well has a 1 dp
`border.subtle` ring; the thumb has a 2 dp white ring.

## What this design language deliberately avoids

- **Material 3 default chrome.** No Material 3 colour roles (`primary`,
  `onPrimary`, `primaryContainer`, etc.). PocoS3 uses its own token names.
- **High-saturation accent gradients.** Gradients are reserved for the
  onboarding hero and the loading spinner; the rest of the app uses solid
  fills.
- **Filled chips on filled surfaces.** Chips are outlined in
  `border.subtle`; filled chips only on `accent.amber` brand use.
- **Drop shadows on cards.** Cards get their depth from translucency and
  border, not from elevation shadows.

## Implementation

The token values above are implemented in:

- `android/pocos3-ui/app/src/main/java/com/pocos3/ui/theme/MornyColors.kt`
- `android/pocos3-ui/app/src/main/java/com/pocos3/ui/theme/MornyShapes.kt`
- `android/pocos3-ui/app/src/main/java/com/pocos3/ui/theme/MornyMotion.kt`
- `android/pocos3-ui/app/src/main/java/com/pocos3/ui/theme/MornyTheme.kt`
- `android/pocos3-ui/app/src/main/res/values/morny_colors.xml` (for XML consumers, e.g. splash screen)
- `android/pocos3-ui/app/src/main/res/font/inter_variable.ttf` (variable font)
- `android/pocos3-ui/app/src/main/res/font/jetbrains_mono_variable.ttf`
