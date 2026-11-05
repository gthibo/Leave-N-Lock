# LEAVE/LOCK design system (Porcelain Grid)

This folder is the app's copy of the **Porcelain Grid** design system. The source of truth is the design system page on claude.ai (private to the maintainer):
https://claude.ai/artifact/MBB1pMEZ219qXmbJMCFqxp

If this folder and the page disagree, the page wins. Change the page first, then refresh this folder from it.

> **Status: reference only.** Nothing in this folder is wired into the build yet. `app/` still uses the old theme in `ui/theme/`. Moving the app onto this design system is a production-code change, so it only happens as a deliberate, reviewed change (see CONTRIBUTING.md).

## What's here

| Path | What it is |
| --- | --- |
| `tokens/tokens.json` | Every token (colour in both themes, type, spacing, radius, chamfer, layout). The single source for everything below. |
| `tokens/tokens.css` | The same tokens as CSS variables, for web pages. |
| `android/theme/*.kt` | Jetpack Compose theme, ready to drop into `app/src/main/java/io/github/gthibo/leavenlock/ui/theme/porcelain/`: `PorcelainColors.kt` (both themes plus the Material 3 mapping), `PorcelainType.kt`, `PorcelainShapes.kt` (the chamfered card shape plus spacing), `PorcelainTheme.kt`. |
| `android/res/font/` | Exo 2 (variable), Instrument Sans (variable), IBM Plex Mono Regular and Medium, from Google Fonts. Copy them to `app/src/main/res/font/`. Their SIL Open Font Licences are in `licenses/` (keep them out of `res/`, which only accepts font files). |
| `android/res/drawable/` | The app icon as adaptive-icon layers (`ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome`), the notification icon `ic_stat_leavelock`, and the 18 UI icons as `ic_pg_*`. The launcher files use the same names as the current ones, so they replace them one for one. |
| `android/res/values*/porcelain_colors.xml` | The colours as XML resources (light, and dark in `values-night`), for the widget, notification and splash screen. |
| `brand/` | Logo SVGs: mark on light and dark, compact versions, app icon (512 Play Store icon plus adaptive layers), favicon. |
| `icons/` | The 18 UI icons as SVG. |
| `web/` | The React component bundle (`window.Porcelain`) and its stylesheet, for web pages. Load `tokens/tokens.css`, then `web/bundle.css`, then React 18, then `web/bundle.js`. |

## Rules to follow when building UI

- **Theme:** two themes, Porcelain (light) and Graphite (dark), chosen in Settings as System default / Light / Dark. **Dynamic colour off.**
- **Colour:** use tokens only, never raw hex in UI code. `teal` is the only action colour. `teal-deep` is for one or two hero status cards per screen, always flat. `mint` and amber `signal` appear only as single lit pixels. `danger` is for risky actions on the real car (Unlock now).
- **Shape:** cards get one 45° chamfer on the top-right corner (`ChamferShape`: 16dp for cards, 24dp for hero cards, 8dp for tiles). Buttons, inputs and switch tracks get 4dp corners. Nothing rounder: no pills, no circles, no Material rounded defaults.
- **Type:** Exo 2 for headings, the status word and numbers; Instrument Sans for everything people read; IBM Plex Mono only for small uppercase labels, units and data. Use the `PorcelainText` styles.
- **No** gradients, glows, blurred textures or heavy shadows. Cards are separated by 1dp hairlines (`line`), not elevation.
- **Layout:** 16dp screen margins, 16dp between cards, 48dp minimum touch targets, 64dp top app bar.
- **Words:** short, factual, present tense, sentence case, second person. No exclamation marks or emoji. Status always has words. The name is always written **LEAVE/LOCK**.
- **Brand:** never use any carmaker's logos, badges, wordmarks or photos.

The full guidelines, component specs and live previews are on the design system page.
