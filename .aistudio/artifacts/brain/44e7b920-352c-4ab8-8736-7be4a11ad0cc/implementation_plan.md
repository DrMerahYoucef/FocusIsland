# 50,000-Point Island Progression & Sky Fireworks Plan

## 1. Reorder Island Progression Curve (Max 50,000 Points)
We will update `IslandProgression.kt` and `IslandRenderer.kt` so that `FULL_GROWTH_UNITS = 50_000` and all 12 landmarks scale smoothly up to **50,000 points**:

| # | Landmark | Previous Points | New Points | Trees | Era |
|---|---|---|---|---|---|
| 1 | First Island Sprout | 100 | **100** | 1 | Era I (0+) |
| 2 | Timber Pier & Campfire | 500 | **400** | 4 | Era II (400+) |
| 3 | Island Dwelling | 1,500 | **1,000** | 8 | Era III (1,000+) |
| 4 | Seafarer's Vessel | 3,000 | **2,000** | 11 | Era III |
| 5 | Coastal Beacon | 5,500 | **3,500** | 14 | Era III |
| 6 | Soaring Bird Flock | 9,000 | **5,500** | 16 | Era IV (5,500+) |
| 7 | Highland Ridge & Hamlet | 14,000 | **8,500** | 18 | Era IV |
| 8 | Waterfall & Lagoon Bridge | 21,000 | **12,500** | 20 | Era V (12,500+) |
| 9 | Grand Palace Citadel | 32,000 | **18,000** | 22 | Era VI (18,000+) |
| 10 | Sky Vessel & Harbor Fleet | 48,000 | **26,000** | 24 | Era VI |
| 11 | Ancient Guardian Sanctuary | 70,000 | **36,000** | 26 | Era VII (36,000+) |
| 12 | Grand Sky Fireworks | 100,000 | **50,000** | 28 | Era VII |

- `IslandRenderer.radius(...)` will also adjust its early/late landmass expansion inflection to match `HIGHLAND_AT` (`8,500`) and `FULL_GROWTH` (`50,000`).

---

## 2. Replace Celestial Aurora Crown with Grand Sky Fireworks
We will replace the Aurora ribbons in `IslandLandmarksRenderer.kt` with a multi-burst **Sky Fireworks** celebration (`drawSkyFireworks`) unlocked at **50,000 points**:
- **Visual Design**:
  - 4 festive firework bursts across the sky above the island (`left`, `center-left high`, `center-right high`, and `right`) at varied sizes and heights so they frame the Grand Palace and highland peak cleanly.
  - Each firework burst renders:
    - A soft radial glow halo in the sky.
    - Rising launch trail arcs from behind the island ridge.
    - Concentric primary and secondary starburst rays (`12` outer rays + `8` inner rays) with rounded caps and sparkling tip droplets.
    - Theme-aware color palettes (Golden/Coral/Cyan for Palm & Treasure, Magenta/Cyan/Electric Gold for Neon, Sakura Pink/Gold/Warm White for Blossom).
- **Localization (`strings.xml` & `values-fr/strings.xml`)**:
  - Update `island_ms_12_title`, `island_ms_12_desc`, and `island_era_7_desc` in both English and French to describe the **Grand Sky Fireworks** (*Feux d'Artifice Royaux*) instead of the Celestial Aurora Crown.
- **Unit Tests (`ArchipelagoLayoutTest.kt`)**:
  - Update the progression unit test assertions to verify the new 50,000-point curve.
