# Lissen (fork) — Audiobookshelf Player

An opinionated fork of [GrakovNe's Lissen](https://github.com/GrakovNe/lissen-android),
a clean Android client for [Audiobookshelf](https://github.com/advplyr/audiobookshelf).

### Why a fork?

Upstream Lissen is deliberately minimalist — a focused, uncluttered player, and
that focus is exactly what makes it good. This fork simply has different goals:
it's tuned for managing a large personal Audiobookshelf library, which means more
library-management surface (folders, bulk actions, download visibility) than fits
upstream's design. These changes were proposed upstream and respectfully declined,
so they live here instead.

If you want the polished, minimalist experience — use the original from
[Google Play](https://play.google.com/store/apps/details?id=org.grakovne.lissen) or
[F-Droid](https://f-droid.org/packages/org.grakovne.lissen). If you want the
power-user extras below, welcome.

### What's different

- **Download visibility** — full-width **All Audiobooks** / **Downloads** home
  tabs make the offline library one tap away; every library row shows whether a
  book is downloaded or downloading. "Downloaded first" works with every sorter
  and preserves series, author, and smart-author grouping.
- **Folders** — group books into local folders. Foldered books leave the main All
  Audiobooks catalog (including grouped children), while cached copies remain
  visible in Downloads so offline content never becomes unreachable.
- **Composite covers** — series, folders, and author fallbacks use persistent
  mosaics of up to four book covers.
- **Multi-select** — long-press to select books, then bulk download, mark as
  finished, or add to a folder.
- **Smarter author grouping** — small authors' books flatten inline (sorted by
  series); only prolific authors collapse into dropdowns, with a configurable
  threshold.
- **Opinionated player** — always-visible, scrollable chapter list (no
  expand/collapse gestures), one-tap full-book download, sleep-timer presets
  including 45 min.
- **Audiobooks only** — podcast support is removed to keep the app simple. If you
  listen to podcasts through Audiobookshelf, use the original Lissen.

See [FORK_PLAN.md](FORK_PLAN.md) for the compact operating brief: current behavior,
durable decisions, verification status, and maintenance notes.

### Install

This is a personal build; no signed GitHub Release or Obtainium feed is maintained.
Build the `personal` APK locally. It is signed with your local Android debug key and
uses its own application id, so it installs alongside the original app and updates
consistently on the same development machine.

### Building

1. Clone the repository:

```
git clone https://github.com/mcdaniel67/lissen-android.git
```

2. Set up the SDK path in your `local.properties`.

3. Build:

```
./gradlew assemblePersonal
```

Install `app/build/outputs/apk/personal/app-personal.apk`.

### Localization

Translations are inherited from upstream (managed there via
[Weblate](https://hosted.weblate.org/engage/lissen/)). New fork-only strings are
English-only and are not sent to Weblate — untranslated strings fall back to
English.

### Development

This fork is developed in the open, largely with AI agents working from
[FORK_PLAN.md](FORK_PLAN.md), with human review before merge.

### Credit & License

All credit for the app's foundation goes to
[GrakovNe](https://github.com/GrakovNe) and the upstream contributors.
Lissen is open-source under the MIT License — see the LICENSE file.
