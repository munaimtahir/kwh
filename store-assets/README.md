# Play Console Store Listing Assets

Everything needed for the Play Console store listing lives here, separate from app source
(`app/src/main/res`) and from developer docs (`docs/`). This folder is the single place to check
what's ready vs. still needed before submitting `com.vexel.kwh` for review.

## Status

| Asset | Status | Location |
|---|---|---|
| Privacy Policy document | Drafted, needs hosting | `privacy-policy.md` |
| Short description (≤80 chars) | Drafted | `descriptions/short-description.txt` |
| Full description (≤4000 chars) | Drafted | `descriptions/full-description.txt` |
| Feature graphic (1024×500 PNG/JPG) | **Not created** — needs actual graphic design | `graphics/feature-graphic/` |
| Screenshots (2–8 per form factor, phone required) | **Not created** — needs real device/emulator captures | `graphics/screenshots/` |
| App icon (512×512 PNG, 32-bit w/ alpha) | **Not created** — export from `app/src/main/res` launcher icon source | `graphics/` |
| Data Safety form answers | Drafted | `data-safety.md` |

Items marked "Not created" are visual assets that need actual design/screenshot work — they
can't be produced from the codebase alone. The rest are text ready to paste into Play Console
once reviewed.

## Privacy Policy hosting

Play Console requires a **live URL**, not just a document. Easiest option: enable GitHub Pages on
this repo (or a docs branch) and publish `privacy-policy.md` there, then put that URL in Play
Console's "Privacy Policy" field and in `README.md`.

## Feature graphic spec

- 1024 × 500 px, PNG or JPEG (no alpha channel), no transparency.
- Should not include the app icon or excessive text — it's the banner shown at the top of the
  store listing.

## Screenshots spec

- Minimum 2, maximum 8 per device type (phone is required for phones-only apps like this one).
- JPEG or 24-bit PNG (no alpha).
- Min dimension 320px, max 3840px; the longer side must not exceed 2× the shorter side.
- Suggested shots for this app: empty state, meter list with readings, add-reading dialog,
  history/trend chart, environment grouping + switch-recommendation banner (once M6–M10 is
  visible), settings screen.

## App icon export

512×512 PNG export of the launcher icon — regenerate from the adaptive icon source in
`app/src/main/res/drawable/ic_launcher_foreground.xml` / `mipmap-anydpi-v26/ic_launcher.xml`
using Android Studio's Image Asset tool or a vector rasterizer, rather than hand-recreating it.
