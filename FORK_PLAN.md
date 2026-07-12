# Lissen Fork — decisions, status, and maintenance

This is the operating brief for the permanently diverged personal fork of
[GrakovNe/lissen-android](https://github.com/GrakovNe/lissen-android). Git history
contains the implementation archaeology; this file records the behavior and decisions
that future work must preserve.

The fork targets one power user's large Audiobookshelf library. Design priorities are,
in order: **reliability → simplicity → ownership**. Prefer fewer moving parts and delete
obsolete code when a feature replaces it.

## Engineering rules

- Architecture: `channel/` → `content/LissenMediaProvider.kt` → `viewmodel/` →
  Compose screens. Persistent downloads and metadata live under
  `content/cache/persistent/`; preferences live in
  `persistence/preferences/LissenSharedPreferences.kt`.
- Preferences use the existing `KEY_X` + save/get + `asFlow` pattern and surface
  through the relevant ViewModel.
- New user-facing strings go in `app/src/main/res/values/strings.xml`. Do not manually
  edit inherited `values-*` Weblate locales; English fallback is intentional.
- Room changes are additive: bump the database version, add a migration, and commit the
  exported schema JSON.
- Match existing Compose/Kotlin style and add stable `testTag`s to interactive UI.
- Before handoff run:

  ```bash
  ./gradlew formatKotlin lintKotlin testDebugUnitTest
  ./gradlew assembleDebugAndroidTest lintDebug
  ./gradlew assembleRelease assemblePersonal
  ```

- Run focused connected tests when UI/device behavior changes, then the full suite when
  its live demo dependency is relevant.
- Do not rebase public `main`. Do not push, publish, sign, or create releases unless Kyle
  explicitly requests it.

## Current product behavior

| Area | Durable behavior |
|---|---|
| Identity | Base `applicationId io.github.mcdaniel67.lissen`; the installed personal build adds `.personal`. Namespace remains `org.grakovne.lissen`; label is **Lissen Fork**; version scheme is `X.Y.Z-fork.N`. OAuth remains the hardcoded `lissen://oauth` flow. |
| Distribution | Keep the debug-signed `personal` APK. Stable signing, GitHub Releases, and Obtainium distribution are out of scope unless this stops being a personal app. |
| Libraries | Audiobook libraries only. Server enum/`UNKNOWN` handling remains for compatibility; podcast channels and UI are deleted. |
| Home | Full-width **All Audiobooks** and **Downloads** tabs replace the library-title dropdown. The multi-library picker lives in Quick Settings. |
| Folders | Local folders are exclusive in **All Audiobooks**: foldered books are removed from flat rows, grouped standalone rows, expanded series/author children, search results, and Continue Listening. |
| Folder ownership | Folder data belongs to the current server host. A successful login to a different host wipes it; same-host relogin and routine token expiry do not. Backup schema 2 stores denormalized book snapshots plus `foldersHost`. |
| Downloads | Downloads is persisted force-cache mode. It hides folder rows and intentionally lists every cached book, including foldered books, so downloaded content never becomes unreachable. Reads stay local. |
| Download state | Shared `BookDownloadState`: not downloaded, downloading with progress, downloaded. Library, recent cards, groups, and player controls consume the same state. |
| Downloaded first | In **All Audiobooks**, works in flat, `SERIES`, `AUTHOR`, and `AUTHOR_SMART` modes. Grouping remains intact: groups containing downloaded visible books lead; downloaded children lead inside expanded groups; existing server/secondary order is stable within each partition. Foldered books do not influence priority. In Downloads every item is already downloaded, so no partition is applied. |
| Paging | Flat folded-book and downloaded-first feeds refill from the global start before slicing logical pages, preventing gaps/duplicates. Grouped feeds with folder filtering or downloaded-first resolve and filter the full source set before slicing logical client pages, including flattened `AUTHOR_SMART` rows. |
| Player | One fixed layout: compact header, controls, permanently visible scrollable chapter list, and bottom navigation. No expand/collapse state machine or Chapters toggle. |
| Player download | One tap starts a full-book download; tapping progress stops it; tapping downloaded asks before deletion. Local files win even while online. |
| Timer/speed | Timer presets plus a 5-minute custom stepper; active timer and non-default speed use primary tint. |
| Bulk actions | Multi-select supports download, mark finished, and add/create folder. Mark-finished updates ABS and local cached progress; force-cache changes local state only. |
| Covers | A singleton Coil loader uses stable custom keys. Online covers use evictable short-term storage. Downloaded covers use app-specific persistent storage when available. Fast cache hits suppress shimmer; slow loads show it after 150ms. |
| Composite covers | Series/folder/author mosaics use up to four tiles. Complete and partial composites persist via temporary-file replacement under an ordered-ID hash, empty cache files are rejected, and ID/order changes invalidate the mosaic. |
| CI/metadata | Normal build CI uses GitHub-hosted runners. Instrumented tests are manually triggered on a hosted emulator. Fork-irrelevant store/funding art is removed; inherited phone screenshots remain reference assets only. |

### Cache and offline boundaries

- Ordinary online thumbnails are disposable: Android or the in-app clear-cache action may
  remove them, after which the next request refetches them.
- Downloaded audio, metadata, and covers normally live in app-specific external files.
  When that storage is unavailable, the implementation falls back to internal
  `cacheDir`, which Android may evict.
- **All Audiobooks** expects the server for catalog data. **Downloads** is the explicit
  local-only catalog; there is no automatic full-library offline fallback.
- A downloaded cover fetch/write failure fails the download honestly. Cover writes and
  composite writes use temporary siblings and rename-on-success to avoid corrupt final
  files.
- Group priority uses cached detailed metadata: stable ABS IDs are preferred, with a
  case-insensitive display-name fallback for older/incomplete metadata. Duplicate group
  names can therefore be ambiguous.
- Grouped downloaded-first intentionally reads the full group-header set and cached
  detailed metadata before stable partitioning and logical paging. This costs more than
  direct server paging but prevents downloaded groups from changing pages as the user
  scrolls.
- A valid partial composite is cached like a complete one. A tile that failed transiently
  is retried only after clear-cache, membership/order change, or future freshness
  invalidation.

## Completion ledger

| Package | Completed | Outcome |
|---|---:|---|
| WP-0 | 2026-07-07 | Consolidated upstream and fork branches onto `main`; enabled rerere and created `upstream-reviewed`. |
| WP-1 | 2026-07-08 | Fork identity, versioning, launcher label, and README. |
| WP-2 | 2026-07-07 | Shared three-state download model and icon. |
| WP-3 | 2026-07-07 | Fixed player layout and scrollable chapter queue. |
| WP-4 | 2026-07-08 | One-tap player download/stop/delete plus active speed/timer styling. |
| WP-5 | 2026-07-07 | Timer presets and stepper; wheel removed. |
| WP-6 | 2026-07-08 | ABS finished-state API plus local progress update. |
| WP-7 | 2026-07-08 | Bulk download, mark-finished, and folder actions. |
| WP-8 | 2026-07-08 | Safe folder wipe on actual server change. |
| WP-9 | 2026-07-09 | Downloaded-first verification and cached created/updated sorting fix. |
| WP-10 | 2026-07-09 | Dead-code, CI, resource, and metadata simplification sweep. |
| WP-11 | 2026-07-08 | Live download indicators across library/recent/group surfaces. |
| WP-12 | 2026-07-07 | Podcast implementation removed; book-library selection enforced. |
| WP-13 | 2026-07-08 | Folder backup/restore with denormalized book snapshots. |
| WP-14 | 2026-07-09 | All/Downloads home tabs, relocated library picker, correct folded paging. |
| WP-15 | 2026-07-10 | Cover shimmer/offline correctness and author-cover identity. |
| WP-16 | 2026-07-11 | Persistent composite mosaics, folder exclusivity in grouped feeds, and grouped downloaded-first ordering. |

## Verification environment and status

- Local AVD: `lissen_api34`, API 34 Google APIs x86_64, KVM accelerated. Use native
  Pixel 6 dimensions normally; set 1080px/480dpi for exact 360dp checks.
- The public E2E fixture is `https://demo.lissenapp.org/` with `demo` / `demo`.
- Tab, quick-settings toggle semantics, and shimmer component tests passed connected on
  the AVD after WP-16 (8 focused tests on 2026-07-11).
- The last full connected run (before WP-16) executed all 127 then-existing tests. Two
  chapter-list assertions can be order-dependent because they rely on live demo playback
  preparation; both pass alone. WP-16's grouped ordering, folder exclusion, paging, and
  composite persistence are covered by focused JVM tests, not a later full connected run.
- Physical-device checks are handled by living with the app, not as a release gate.

Operational checks when investigating a report:

- login/password/OAuth and install alongside upstream;
- all grouping modes, folders, folder exclusivity, and multi-select actions;
- flat and grouped downloaded-first ordering plus restart persistence;
- download/start/stop/delete and live ring-to-badge transitions;
- Downloads refresh after cache add/remove;
- short-screen player layout, long titles, chapter centering, timer, speed, playback,
  progress sync, and kill/relaunch restoration;
- folder backup/restore and same-server versus changed-server ownership;
- Android Auto audiobook browsing.

## Backlog and watch list

- Namespace Coil and thumbnail-file keys by canonical server/account, entity kind, and
  online/offline source to prevent cross-server stale artwork.
- Use ABS width-aware cover endpoints for list thumbnails instead of fetching originals.
- Add cover freshness/versioning and bounded retry/backoff for persistent failures.
- Add a real cached-book fixture for the deferred library download-badge E2E assertion.
- Revisit signing/distribution only if the app needs to leave this personal workflow.

## Someday

- Expand and harden the existing Android Auto audiobook support: exercise browse,
  playback, reconnect, and offline/Downloads behavior on a real head unit or emulator,
  then improve the current tree and controls where concrete gaps appear. This is not a
  from-zero integration.

## Upstream maintenance

Do not merge upstream wholesale. Cherry-pick or manually port fixes from layers the fork
still shares; skip conflicting product/UI direction.

Per upstream review:

1. `git fetch upstream`
2. Inspect `git log --oneline --no-merges upstream-reviewed..upstream/main`.
3. Prefer ABS API compatibility, playback/Media3, crash, security/dependency, and useful
   translation fixes.
4. Skip upstream UI redesigns, podcast work, CI/store metadata, and routine version bumps.
5. If a valuable fix conflicts heavily, port the behavior manually and reference its SHA.
6. Run the normal verification commands.
7. Advance the marker with `git tag -f upstream-reviewed upstream/main` after review.

`rerere` is enabled. Never rebase public `main`, and do not optimize fork work for
upstreamability.
