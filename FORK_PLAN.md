# FORK_PLAN.md — Lissen fork: consolidation + feature build plan

This document is the single source of truth for turning this repository into a
standalone, permanently-diverged fork of
[GrakovNe/lissen-android](https://github.com/GrakovNe/lissen-android). It is written
for implementation agents: each work package below is self-contained, names the exact
files, and ends with acceptance checks. Read this whole file before starting any
package.

**Why a fork:** upstream and this project have different goals. Upstream is a
deliberately minimalist player; this fork optimizes for one power user's large
personal Audiobookshelf library (folders, bulk actions, download visibility,
opinionated player UX). No shade — the changes were offered and declined, so they
live here.

**Design priorities, in order: reliability → simplicity → ownership.** When two
implementations are equally good, take the one with fewer moving parts. Deleting
code is a feature. Every package should leave the codebase *simpler* than it found
it, not just bigger.

---

## 1. Ground rules for agents

- **Architecture flow:** `channel/` (Audiobookshelf REST client) →
  `content/LissenMediaProvider.kt` (routing between server channel and local cache)
  → `viewmodel/` (Hilt ViewModels) → `ui/screens/` (Compose). Local data is Room in
  `content/cache/persistent/` (`LocalCacheStorage`, DAOs, `Migrations.kt`).
  Preferences are `persistence/preferences/LissenSharedPreferences.kt`.
- **Preferences pattern:** every new setting follows the existing
  `KEY_X` constant + `saveX()` + `getX()` + `val xFlow = asFlow(KEY_X, ::getX)`
  pattern in `LissenSharedPreferences.kt`, surfaced through `SettingsViewModel`.
- **Strings:** new user-facing strings go in `app/src/main/res/values/strings.xml`
  (English) only. **Never** edit other `values-*/strings.xml` locales — they are
  inherited from upstream's Weblate and stay as-is. Missing translations fall back
  to English; that is fine.
- **Room migrations:** additive only. Bump the version in `LocalCacheStorage.kt`,
  add a `Migration` to `Migrations.kt`, and commit the exported schema JSON under
  `app/schemas/org.grakovne.lissen.content.cache.persistent.LocalCacheStorage/`.
- **Formatting/lint:** `./gradlew formatKotlin lintKotlin` (kotlinter). Run before
  every commit.
- **Tests:** `./gradlew testDebugUnitTest` must pass. E2E/instrumented tests live in
  `app/src/androidTest/`; update any that reference UI you change (they find views
  by `testTag`). Add `testTag`s to new interactive elements.
- **Build check:** `./gradlew assembleDebug` after each package.
- **Branching:** cut a short-lived branch per work package off `main`, merge back
  when acceptance passes. No upstreaming constraints anymore — commits just need to
  be coherent.
- **Match the local style.** Compose files here use trailing-comma style, explicit
  `Modifier` params, `colorScheme`/`typography` imports. Copy the idiom of the file
  you are editing.

---

## 2. Current state (historical — WP-0 has landed; everything below now lives on `main`)

Branch map before consolidation (branches since deleted; upstream's
`feature/mark-as-completed` still exists and matters for WP-6):

| Branch | Base | Contents |
|---|---|---|
| `main` | upstream `b084573c` | stale — 9 commits behind upstream/main |
| `feat/download-visibility` | upstream/main tip (`5c9da074`) | cleaned-up download badge commit `4d5e2d56` |
| `feat/folders-multiselect` | old main | local folders + multi-select (`281fb4b9`) |
| `feat/smart-author-grouping` | old main | `AUTHOR_SMART` grouping (`c6428e69`) |
| `wip/all-features` | old main | **everything combined** + fixes: downloaded-first ordering (`5282976d`), shared `cachedBookIds` badge flow + `personal` build type (`ae822f14`), hide-foldered-books (`4e5fd4f2`) |
| upstream `feature/mark-as-completed` | upstream | unmerged: ABS progress/finished API plumbing (`ChangeListenedStateRequest`, client endpoints, `BookActionsComposable`) — we port the API layer in WP-6 |

Key files you will touch repeatedly:

- `ui/screens/player/PlayerScreen.kt`, `composable/NavigationBarComposable.kt`,
  `composable/PlayingQueueComposable.kt`, `composable/TimerComposable.kt`,
  `composable/DownloadsComposable.kt`
- `ui/screens/library/LibraryScreen.kt`, `composables/BookComposable.kt`,
  `composables/FolderComposable.kt`, `composables/QuickSettingsComposable.kt`
- `viewmodel/CachingModelView.kt`, `viewmodel/LibraryViewModel.kt`,
  `viewmodel/PlayerViewModel.kt`
- `content/LissenMediaProvider.kt`, `content/folder/*`,
  `content/cache/persistent/*`
- `channel/common/MediaChannel.kt`,
  `channel/audiobookshelf/common/client/AudiobookshelfApiClient.kt`

---

## 3. Work packages

Dependency order. WP-0 blocks everything. WP-12 (drop podcasts) runs early so the
player packages never have to handle `LibraryType.PODCAST` branches. WP-2
(download-state backbone) blocks WP-4 and WP-11. WP-6 (progress API) blocks WP-7.
Everything else is parallel-safe after its listed dependencies.

```
WP-0 consolidate
 ├─ WP-1 identity + README
 ├─ WP-12 drop podcast support ──┬─ WP-3 player restructure
 │                               ├─ WP-4 nav-bar download/tints (also needs WP-2)
 │                               └─ WP-5 sleep timer
 ├─ WP-2 DownloadState backbone ─┬─ WP-4
 │                               └─ WP-11 library/recent row indicators
 ├─ WP-6 progress API ─────────── WP-7 selection actions
 ├─ WP-8 folder wipe on server change
 ├─ WP-13 folders in config backup/restore
 ├─ WP-9 downloaded-first verification
 └─ WP-10 simplification sweep (last)
```

---

### WP-0 — Consolidate all branches onto one `main` ✅ DONE 2026-07-07

> Completed: main = upstream `5c9da074` + merge `6b2b1b9d` + README. 788 unit
> tests green, debug APK builds, rerere enabled, `upstream-reviewed` tag pushed,
> local + remote feature branches deleted. Residual: on-device smoke pass.

**Goal:** one `main` containing upstream/main tip + every fork feature. All other
local branches deleted afterward.

**Steps:**
1. `git checkout main && git merge upstream/main` — fast-forward to `5c9da074`.
2. `git merge wip/all-features`. Expect conflicts concentrated in:
   - `QuickSettingsComposable.kt` — upstream redesigned the sheet into
     `ToggleRow`/`PickerHeaderRow`/`OptionRow`. Re-express the fork's additions
     (downloaded-first toggle, author-grouping-threshold stepper, `AUTHOR_SMART`
     grouping entry) in the **new** idiom. Keep upstream's structure; layer fork
     rows into it.
   - `LibraryScreen.kt` — upstream added accessibility semantics; fork added
     folders section, selection top bar, `FolderComposable` items. Keep both.
   - `LissenMediaProvider.kt` — take the fork's downloaded-first +
     hide-foldered-books version (`5282976d` rewrote `fetchLibraryDownloadedFirst`)
     and reapply any upstream-side changes around it.
   - `BookComposable.kt`, `LibraryViewModel.kt`, `strings.xml`,
     `LissenSharedPreferences.kt` — additive on both sides; merge both.
3. Reconcile the badge implementation: `feat/download-visibility` commit `4d5e2d56`
   is a cleaner version of what `wip` already contains (it adds an a11y
   `contentDescription`, `library_item_downloaded_indicator` string, and
   `testTag("downloadedIndicator_<id>")`). After the merge run
   `git diff HEAD feat/download-visibility` and port anything the merge result is
   missing — prefer the badge branch's `BookComposable` indicator block.
4. `./gradlew formatKotlin testDebugUnitTest assembleDebug`.
5. Manual smoke on device/emulator against the real server: library loads, folders
   render, grouping modes work, downloaded badge shows, playback works.
6. Delete merged branches (`feat/download-visibility`, `feat/folders-multiselect`,
   `feat/smart-author-grouping`, `wip/all-features`) and stale worktrees
   (`git worktree list` → `git worktree remove <path>` → `git worktree prune`).
7. `git tag upstream-reviewed upstream/main` — the starting marker for the
   cherry-pick playbook in §6. Also `git config rerere.enabled true`.
8. Push `main` (fast-forward) and the tag to origin.

**Acceptance:** unit tests green; app runs; `git diff main feat/download-visibility`
(pre-deletion) shows no fork-feature content missing from main.

---

### WP-1 — Fork identity + README ✅ DONE 2026-07-08

> Landed on `main` (`d46e9fe5` build/identity, `afa3966c` README, both from the
> planning session; verified 2026-07-08). `applicationId io.github.mcdaniel67.lissen`,
> `namespace` unchanged, `versionName 1.11.4-fork.1` (versionCode 11104), `personal`
> build type kept. `app_name` = "Lissen Fork". OAuth is unaffected: the redirect scheme
> is the hardcoded `lissen://oauth` (`channel/audiobookshelf/common/oauth/OAuthScheme.kt`
> + manifest intent filter), not derived from applicationId. `assembleRelease` +
> `aapt2 dump badging` confirm identity. Residual: on-device install-alongside + OAuth
> round-trip need a physical device.

**Goal:** the fork ships under its own identity and its README says what it is.

**Steps:**
1. In `app/build.gradle.kts`:
   - Change `applicationId` to `io.github.mcdaniel67.lissen` (decided 2026-07-07).
     Keep `namespace` `org.grakovne.lissen` so no source/resource churn.
   - **Keep the `personal` build type for now** (decided 2026-07-07). No release
     keystore is configured yet, and `personal` signs with the debug keystore so
     it stays the working on-device install. Deleting it (so signed `release` *is*
     the personal build) is deferred to a dedicated release-setup task that first
     generates a stable fork keystore — required anyway for GitHub-releases +
     Obtainium updates, whose signature check the debug key would break.
   - Version scheme: `versionName = "1.11.4-fork.1"` (drop upstream's `-release`
     suffix, mark fork builds with `-fork.N`). `versionCode` unchanged (11104) —
     the new `applicationId` is a distinct app identity.
2. Change the launcher label (`app_name` string / manifest `android:label`) to
   **"Lissen Fork"** (decided 2026-07-07).
3. Grep for hardcoded `org.grakovne.lissen` in `AndroidManifest.xml`, widget XML,
   and OAuth redirect/intent-filter schemes. The OAuth flow (`channel/common/Pkce.kt`,
   manifest intent filters) may embed a scheme — verify login (both password and
   OAuth if configured) still round-trips after the id change.
4. Replace `README.md` per the outline in §5. *(README and the deletion of
   `KYLE_PLAN.md` were already done in the planning session — verify, don't redo.)*

**Acceptance:** `./gradlew assembleRelease` installs alongside the Play Store
Lissen on a device; login + playback work; README renders correctly on GitHub.

---

### WP-2 — DownloadState backbone (needed by WP-4, WP-11) ✅ DONE 2026-07-07

> Landed on `main` (`d65fcb28`). Added `domain/BookDownloadState.kt` (kept separate
> from the persistence-layer `CacheState`), `CachingModelView.downloadState()`
> combining live progress + `cachedBookIds`, and shared
> `ui/components/DownloadStateIcon.kt` (size-parameterized). `NavigationBarComposable`
> now renders via the shared icon but still only maps ring/cloud (no "completed"
> nav-bar icon) — consumers of the full 3-state model arrive in WP-4/WP-11. 4 new
> `CachingModelViewTest` cases; no visible UI change.


**Goal:** one shared model answering "what is the download state of book X?" that
every surface (library rows, group rows, player nav bar) consumes. Today this is
scattered: `CachingModelView.getProgress(bookId): StateFlow<CacheState>` (live
session progress) and `cachedBookIds: StateFlow<Set<String>>` (completed set).

**Steps:**
1. Add a sealed type in `domain/` (or reuse-and-extend
   `content/cache/persistent/CacheState.kt` if cleaner):
   ```kotlin
   sealed interface BookDownloadState {
     data object NotDownloaded : BookDownloadState
     data class Downloading(val progress: Double) : BookDownloadState
     data object Downloaded : BookDownloadState
   }
   ```
2. In `CachingModelView`, add
   `fun downloadState(bookId: String): Flow<BookDownloadState>` combining
   `getProgress(bookId)` (Caching → `Downloading(progress)`) with membership in
   `cachedBookIds` (→ `Downloaded`), else `NotDownloaded`.
3. Extract the player's `DownloadProgressIcon` (currently private in
   `NavigationBarComposable.kt`) into a shared composable, e.g.
   `ui/components/DownloadStateIcon.kt`: `NotDownloaded` → outlined cloud,
   `Downloading` → circular progress ring, `Downloaded` → `CloudDone`/check tinted
   `colorScheme.primary`. Parameterize size so it works at row scale (16–24dp).
4. Unit-test the combine logic (extend `CachingModelViewTest`).

**Acceptance:** tests green; no UI change yet (consumers arrive in WP-4/WP-11).

---

### WP-3 — Player restructure: fixed layout, scrollable chapters ✅ DONE 2026-07-07

> Landed on `main` (`0be31c92`). Expand/collapse state machine deleted from
> `PlayerViewModel`, `PlayingQueueComposable`, `PlayerScreen`, `NavigationBarComposable`
> (QueueMusic item gone), and the `forceExpanded` plumbing removed. Compact header is
> a **sibling** `PlayerCompactHeader` (not a `compact` flag on the wide variant).
> Auto-scroll-to-current gated on `!listState.isScrollInProgress` — a chapter change
> mid-drag won't recenter until the scroll settles / next change (tap-to-jump still
> recenters). E2E tests updated but not run here (no device). **Needs on-device pass:**
> short screens (<600dp), centered-scroll feel, long-title ellipsize.


> **Agent note: Opus-class (or better) model required.** This package deletes a
> state machine woven through four files and involves real layout/UX judgment
> (compact header design, auto-scroll vs user-scroll arbitration). Do not hand
> this to a small model.

**Goal:** kill the expand/collapse playing-queue state machine. The player becomes
a single fixed layout: compact header (cover + title/author + controls), the
chapter list is the one scrollable element, bottom nav below. The "Chapters" nav
button dies with the toggle it drove. (Vault items 5 + 7.)

Target (phone portrait):

```
┌──────────────────────┐
│ ← Player        ⓘ 🔖 │
│  [cover]  Title      │
│           Author     │
│  ◄◄  ▶  ►►  ───────  │
│ Chapters        🔍   │
│  1. Chapter One   ✓  │
│ ▸2. Chapter Two      │   ← list scrolls; auto-scrolls to current
│  ⋮                   │
│ [DL] [Speed] [Timer] │
└──────────────────────┘
```

**Steps:**
1. `PlayerViewModel`: delete `playingQueueExpanded`, `expandPlayingQueue()`,
   `collapsePlayingQueue()`, `togglePlayingQueue()`. Keep `searchRequested` /
   `searchToken` (chapter search stays).
2. `PlayingQueueComposable`: strip the two `NestedScrollConnection`s, fling
   thresholds, collapse FAB, `collapsedPlayingQueueHeight` measurement, and the
   expanded/collapsed padding fork. What remains: header text, `LazyColumn` with
   scrollbar, auto-scroll-to-current-chapter (`scrollPlayingQueue` simplifies —
   drop its `playingQueueExpanded` early-return; only auto-scroll when the user
   isn't actively scrolling, e.g. gate on `!listState.isScrollInProgress`).
3. `PlayerScreen.kt` (portrait branch): remove the `AnimatedVisibility` that hides
   artwork/controls. Replace `PlayerArtworkAndControls` usage with a **compact
   header**: `Row`(small cover ~96dp, `Column`(title, author)) above
   `TrackControlComposable`. The existing wide-layout
   `PlayerArtworkAndControlsWide` shows how to compose cover/title/controls — make
   the compact variant a sibling, or generalize one composable with a `compact`
   flag if it stays readable. The chapter search icon in the top bar becomes
   always-visible (drop `queueControlsVisible` gating on expansion).
4. `NavigationBarComposable`: delete the first `NavigationBarItem` (QueueMusic /
   chapters toggle) and its `playingQueueExpanded` usage.
5. Landscape/two-pane already renders queue permanently (`forceExpanded = true`) —
   remove the now-meaningless `forceExpanded` plumbing and let both layouts use the
   same always-visible queue.
6. Back handling in `PlayerScreen.stepBack()`: drop the `playingQueueExpanded`
   branch.
7. Update `PlayerE2ETest` / `LandscapeE2ETest` and placeholders
   (`PlayingQueuePlaceholderComposable`, `NavigationBarPlaceholderComposable`) to
   match. Delete `a11y_collapse_queue` string if now unused.

**Risks:** chapter-list auto-scroll fighting user scrolls (test with a playing
book); very long titles in the compact header (ellipsize, `maxLines = 2`);
screens under ~600dp tall — verify controls + a few chapter rows fit.

**Acceptance:** no gesture changes layout state anywhere on the player; chapter
list scrolls freely both directions; current chapter auto-centers on
chapter change; search still filters; E2E tests updated and green.

---

### WP-4 — Nav bar: one-tap download + active-state tints ✅ DONE 2026-07-08

> Landed on `main`. `NavigationBarComposable` now drives the download item off
> `downloadState(book.id)`: NotDownloaded tap → `cache(book, pos, AllItemsDownloadOption)`
> (no sheet; disabled in force-cache), Downloading tap → `stopCaching`, Downloaded tap →
> `AlertDialog` → `dropCache` + clear-playing-book + back to library. Speed item shows the
> live speed (e.g. "1.25×") tinted primary when ≠ 1.0×; timer item tints primary when set.
> `DownloadsComposable.kt` deleted; 7 orphaned sheet strings removed (`ChaptersCountStepper`
> + `DownloadOption` machinery kept for auto-cache settings). Lint + unit tests + assembleDebug green.

**Goal:** vault items 4, 7b, 8. The download button acts by state; active features
tint their nav button `colorScheme.primary`.

**Steps (all in `NavigationBarComposable.kt` unless noted):**
1. Consume `cachingModelView.downloadState(book.id)` (WP-2).
2. **Download item:**
   - `NotDownloaded` → tap immediately runs
     `contentCachingModelView.cache(book, currentPosition, AllItemsDownloadOption)`.
     No sheet.
   - `Downloading(p)` → icon is the progress ring (from `DownloadStateIcon`); tap
     stops caching (`stopCaching`).
   - `Downloaded` → icon is the tinted done-icon (this **is** the "download
     indicator on details page", item 4); tap opens a small `AlertDialog` confirming
     cache deletion (`dropCache`, then the existing clear-playing-book navigation).
   - With WP-12 landed there is no podcast path left, so **delete
     `DownloadsComposable.kt` entirely** along with its now-unused strings.
     Careful: `ChaptersCountStepper` and the `DownloadOption` types are still used
     by the auto-cache settings (`AutoCacheSettingsComposable`) — keep those.
3. **Speed item:** when `playbackSpeed != 1.0f`, tint icon + label
   `colorScheme.primary` and show the speed as the label (e.g. "1.25×") instead of
   the static caption.
4. **Timer item:** when `timerOption != null`, tint icon + label primary (the
   countdown label already swaps in).
5. Respect `isForceCache` (offline mode): download tap is disabled the same way the
   sheet options are today.

**Acceptance:** tap-to-download starts full download with visible ring; tap
mid-download stops; tap when done asks to delete. Speed and timer buttons visibly
light up when non-default. Works offline-gracefully. No reference to
`DownloadsComposable` remains.

---

### WP-5 — Sleep timer: presets + stepper, no wheel ✅ DONE 2026-07-07

> Landed on `main` (`9701af74`). Presets `[Off, 10, 15, 30, 45, 60, end-of-chapter]`
> in a `FlowRow` (7×56dp don't fit 360dp → wraps 5+2). `SleepTimerSlider.kt` deleted;
> `CommonSlider.kt` kept. Custom `[−] 25m [+]` stepper (range 5–120, step 5) is a
> hand-rolled row matching `ChaptersCountStepper`'s style rather than reusing it;
> displayed value derives from `currentOption` so custom values survive sheet reopen
> with no local state. 4 new English strings. `libraryType` stays live as the
> end-of-chapter icon's a11y `contentDescription` (LIBRARY→chapter; else→episode).


**Goal:** vault item 6. Presets Off / 10 / 15 / 30 / 45 / 60 / end-of-chapter plus
a custom stepper; delete the wheel.

```
┌────────────────────────────┐
│        Sleep Timer         │
│ (✕)(10)(15)(30)(45)(60)(⏭) │
│    Custom:  [−]  25m  [+]  │
└────────────────────────────┘
```

**Steps:**
1. `TimerComposable.kt`: extend `OptionPresets` with `DurationTimerOption(45)` and
   `CurrentEpisodeTimerOption` (the ⏭ "end of chapter" preset). Buttons stay the
   existing `FilledTonalButton` circles — check
   they still fit one row on a 360dp screen; wrap to two rows if not
   (`FlowRow`).
2. Replace the `SleepTimerSlider` block with a custom row: `[−]` / current value /
   `[+]` stepping ±5 min in range 5–120, styled like `ChaptersCountStepper`
   (reuse it if it fits). Changing the value applies the timer immediately (same
   contract as the old `onUpdate`).
3. Delete `ui/components/slider/SleepTimerSlider.kt`. **Keep `CommonSlider.kt`** —
   playback-speed, seek and volume-boost sliders still use it.
4. The selected state must reflect custom values too (a 25-min timer shows in the
   stepper, no preset highlighted).

**Acceptance:** 45-min timer is two taps from the player; no wheel anywhere;
custom 25-min via stepper works and survives sheet reopen (shows current
remaining option).

---

### WP-6 — Server progress API (backbone for WP-7) ✅ DONE 2026-07-08

> Landed on `main` (`b2e6d957`). API-only, no UI. Collapsed upstream's WIP
> complete/uncomplete pair into one call: `LissenMediaProvider.markAsListened(itemId,
> isFinished): OperationResult<Unit>` — **this is what WP-7 calls.** Chain:
> `AudiobookshelfApiClient.updateListenedState` (`PATCH api/me/progress/{itemId}`,
> `ChangeListenedStateRequest(isFinished)`) → `AudioBookshelfRepository` →
> `MediaChannel`/`AudiobookshelfChannel`; on success also writes local
> `CachedBookRepository.updateFinishedState` (preserves `currentTime`) so hide-completed
> reflects offline. Under `isForceCache()` it skips the network and updates cache only.
> Room untouched (v21, reuses `media_progress`). +8 unit tests, 772 green, assembleDebug OK.
> Residual: live-ABS `isFinished` flip needs the homelab. Note for WP-7: a pre-existing
> `runBlocking`-as-last-statement footgun silently skips 3 old `LissenMediaProviderTest`
> cases — don't copy that shape.

**Goal:** the app can mark a book finished/unfinished on the ABS server. Port the
API layer from upstream's unmerged `feature/mark-as-completed` branch
(`git diff upstream/main...upstream/feature/mark-as-completed`), **not** its UI.

**Steps:**
1. Port: `ChangeListenedStateRequest` model, the two `AudiobookshelfApiClient`
   endpoints, `AudioBookshelfRepository` methods, `AudiobookshelfChannel` +
   `MediaChannel` interface additions, `LissenMediaProvider.markAsListened(...)` /
   equivalent. Review as you port — that branch is WIP; tighten naming and error
   handling to local conventions (`OperationResult`).
2. Also update the **local** cached progress on success (via
   `LocalCacheRepository`) so "hide completed" reflects immediately and offline
   state stays consistent.
3. Unit-test the provider routing (server vs cache) like the existing
   `LissenMediaProviderTest` cases.

**Acceptance:** calling the new provider function against the homelab ABS flips
`isFinished` (verify with `abs` CLI or ABS web UI); local library reflects it
after refresh without app restart.

---

### WP-7 — Multi-select actions: mark finished, download, add-to-folder ✅ DONE 2026-07-08

> Landed on `main` (`95e4da40`). `SelectionTopBar` gains Download / Mark-finished /
> Add-to-folder alongside the existing create-folder. Download →
> `CachingModelView.cacheByIds` (new): one full-book caching task per selected id via the
> existing `ContentCachingService` queue — the service fetches each book itself, so no
> `fetchBook` loop is needed and per-book progress surfaces through the WP-2/WP-11
> `runningDownloads`/`downloadState` flows. Mark-finished →
> `LibraryViewModel.markSelectionFinished` calls WP-6's `markAsListened` per book, refreshes,
> and emits a one-shot `markFinishedFailures` count (shown as a toast) without aborting the
> batch. Add-to-folder → `AddToFolderDialog` lists existing folders (tap-to-add, not radio —
> simpler) with a "New folder…" row falling through to the create-folder dialog;
> `addSelectionToFolder` calls `folderRepository.addBooks`. All actions `clearSelection()`.
> +6 `LibraryViewModelTest` cases. Residual: on-device 360dp top-bar crowding (4 actions +
> title) and live ABS finished-flip.

**Goal:** vault items 1 + 2. The existing selection mode (long-press → checkboxes
→ `SelectionTopBar` with count + create-folder) gains three actions.

**Steps:**
1. `SelectionTopBar` (in `LibraryScreen.kt`): add icon actions —
   **Download** (cloud icon), **Mark finished** (check icon), **Add to folder**
   (folder-plus icon), keeping the existing create-folder and close. Overflow into
   a dropdown if five icons crowd the bar on 360dp.
2. `LibraryViewModel`:
   - `downloadSelection()` — for each selected book: `fetchBook(id)` then
     `cachingModelView.cache(item, 0, AllItemsDownloadOption)` (add a small
     `CachingModelView.cacheByIds(ids)` helper so the loop lives in one place).
     Fire-and-forget with the normal per-book progress surfacing from WP-2/WP-11.
   - `markSelectionFinished()` — call WP-6's provider function per book;
     refresh the library afterward so hide-completed filtering applies. If any
     call fails, surface one toast/snackbar with the failure count — don't abort
     the rest.
   - both actions end with `clearSelection()`.
3. **Add to folder:** dialog listing `folderRepository.observeFolders()` (radio
   list, reuse the create-folder dialog styling) + a "New folder…" row that falls
   through to the existing create dialog. On confirm:
   `folderRepository.addBooks(folderId, selectedBooks)`.
4. Selection already stores `Book` objects — `addBooks` needs them; keep it that
   way.
5. Strings for all new labels; `testTag`s on the new actions.

**Acceptance:** select 3 books → download: all three cache with visible progress;
→ mark finished: they disappear when hide-completed is on and show finished on the
ABS web UI; → add to folder: they appear in the folder and (per
hide-foldered-books) leave the flat list.

---

### WP-8 — Wipe folders on server change (bug fix) ✅ DONE 2026-07-08

> Landed on `main`. `FolderRepository`/`LocalFolderRepository` gained `clear()` +
> `folderCount()`; `FolderDao` got transactional `clear()` (`DELETE FROM folder_items`
> then `folders`). `LissenSharedPreferences` gained `KEY_FOLDERS_HOST` +
> `saveFoldersHost()`/`getFoldersHost()`; `LocalFolderRepository.createFolder` records the
> current host. Wipe hook: `LissenMediaProvider.onPostLogin` → `wipeFoldersOnServerChange`
> (only when `host != getFoldersHost() && folderCount() > 0`) — covers password + OAuth
> login; same-host re-login preserves folders. +7 unit tests.
>
> **Plan self-conflict resolved (Kyle decided 2026-07-08):** step 4 assumed
> `clearCredentials()` was the explicit disconnect button, but it's the automatic
> token-refresh-failure path. The agent's first cut wiped folders there, which would drop
> them on routine same-server session expiry. **Removed** that wipe
> (`AudioBookShelfApiService`) and the now-unused `FolderRepository` injection — the
> login-time host-change guard is the sole, sufficient wipe trigger. Folders now survive
> same-server token expiry; still wiped when the server actually changes at next login.

**Goal:** folders created against server A must not survive pointing the app at
server B (dead book ids). Decision: **wipe, not scope** — simplest correct
behavior.

**Steps:**
1. Add `suspend fun clear()` to `FolderRepository` /
   `LocalFolderRepository` (+`FolderDao` `DELETE` queries for both tables).
2. Track ownership with a pref: `KEY_FOLDERS_HOST` in `LissenSharedPreferences`,
   set to the current host whenever a folder is created.
3. On successful login/connection (find the post-login point — `LoginViewModel` /
   wherever `saveHost` is invoked after auth): if `getHost() != getFoldersHost()`
   and folders exist → `folderRepository.clear()` and update the pref. Re-login to
   the **same** host must NOT wipe.
4. Also clear on explicit logout (the `clearCredentials()` flow — hook the caller,
   not the prefs class; prefs must not touch Room).
5. Unit-test `LocalFolderRepository.clear()` and the host-comparison logic.

**Acceptance:** create folder → logout → login same server: folder intact.
Point at demo server: folders gone. No orphan rows in `folder_item`
(verify via the DAO in the test).

---

### WP-9 — Downloaded-first sort with other sorters (verification)

**Goal:** vault item 9 — already implemented in `wip` commit `5282976d`
(`fetchLibraryDownloadedFirst` in `LissenMediaProvider` + `LissenMediaProviderTest`).
This package just proves it survived the WP-0 merge.

**Steps:**
1. Confirm `LissenMediaProviderTest` downloaded-first cases pass post-merge.
2. Manual matrix on device: downloaded-first ON × sort by title/author/date,
   ascending + descending, with and without grouping. Downloaded items lead in
   every combination, and the secondary sort holds within each partition.
3. Confirm the toggle reads correctly in the redesigned quick-settings sheet
   (post-WP-0 idiom) and persists across restart.

**Acceptance:** the matrix above; fix anything broken as part of this package.

---

### WP-11 — Download indicators on library + recent rows ✅ DONE 2026-07-08

> Landed on `main` (`00b944be`). List-wide, no per-row flow: `CachingModelView` gained
> `runningDownloads: StateFlow<Map<String, Double>>` (maintained in the existing
> `statusFlow` collector) + pure `downloadStateOf(bookId, cachedIds, running)`.
> `LibraryScreen` collects `cachedBookIds` + `runningDownloads` once and threads a
> `(String) -> BookDownloadState` resolver into rows. `BookComposable` swaps
> `DownloadForOffline` for the shared `DownloadStateIcon` (keeps `downloadedIndicator_<id>`
> testTag; shows nothing when not-downloaded). Group headers (Series/Author/Folder) show
> the badge only when all children cached (no ring). Recent cards get a scrim-backed corner
> overlay (distinct `recentDownloadedIndicator_<id>` testTag to avoid dup matches).
> `DownloadStateIcon` gained an optional `contentDescription` (nav-bar call unchanged) +
> an a11y label on the ring. New string `library_item_downloading_indicator`. +tests in
> `CachingModelViewTest`; lint + unit tests + assemblePersonal green. Residual: on-device
> ring→badge live transition, recent-overlay legibility, and `LibraryE2ETest` badge
> assertion (deferred — needs a real cached-book fixture).

**Goal:** vault item 3 ("better download statuses"): library rows show three
states — nothing / downloading ring / downloaded badge — and the Recent Books
strip gets the same treatment. Depends on WP-2.

**Steps:**
1. `LibraryScreen.kt` already passes `downloaded: Boolean` into `BookComposable`
   from the shared `cachedBookIds` set. Replace that boolean with the WP-2
   `BookDownloadState` (keep the "observe one set for the whole list" pattern —
   do **not** give every row its own DB flow; combine `cachedBookIds` with the
   in-session progress map exposed by `CachingModelView`).
2. `BookComposable`: swap the hardcoded `DownloadForOffline` icon for the shared
   `DownloadStateIcon` (WP-2), preserving the existing
   `testTag("downloadedIndicator_<id>")` and a11y `contentDescription`; add a
   distinct contentDescription for the downloading state.
3. Group rows (`SeriesComposable`, `AuthorComposable`, `FolderComposable`): show
   the downloaded badge when **all** children are cached (the wip branch already
   sketched this — verify it survived WP-0) and nothing otherwise. No ring at
   group level — per-group progress isn't worth the bookkeeping.
4. `RecentBooksComposable`: overlay a small `DownloadStateIcon` on the cover
   corner (recent cards are cover-only, so a corner overlay rather than a
   trailing icon).
5. Extend `LibraryE2ETest` with a downloaded-badge assertion if the harness makes
   it cheap.

**Acceptance:** start a book download from the player, jump back to the library:
that row shows a ring, then flips to the badge on completion without refresh;
recent strip matches; fully-cached series/author/folder groups show the badge.

---

### WP-12 — Drop podcast support (run before WP-3/4/5) ✅ DONE 2026-07-07

> Completed on branch `wp12-drop-podcasts`. Deleted the 9-file podcast channel +
> `AudioBookshelfPodcastSyncService` + 4 podcast converter tests; removed the
> podcast-only methods/endpoints from `AudioBookshelfRepository` /
> `AudiobookshelfApiClient`; unregistered the podcast channel in
> `AudiobookshelfChannelProvider` (now always returns the library channel).
> `meaningfulTypes = [LIBRARY]`. Every `when (libraryType)` UI branch collapsed to
> `LIBRARY -> book wording; else -> generic "item" wording` (kept params + the
> genuinely-live UNKNOWN state — a podcast-only server now yields UNKNOWN since
> podcast libs aren't selectable). 765 unit tests green; kotlinter + Android
> `lintVitalPersonal` clean; personal APK builds. Residual: on-device Android Auto
> browse check.
>
> **Deviations from the steps below:**
> - The library picker was **not** actually filtered by `meaningfulTypes` (the plan
>   assumed it was). Added the filter in `SettingsViewModel.fetchLibraries()`
>   (`response.data.filter { it.type in meaningfulTypes }`) so podcast libraries
>   never appear or become the `firstOrNull` default — this is what satisfies the
>   "library picker only offers book libraries" acceptance.
> - **Kept** the auto-download-by-library-type plumbing
>   (`LibraryTypeAutoCacheSettingsComposable`, `getAutoDownloadLibraryTypes`,
>   `ContentAutoCachingService`) rather than ripping it out — it feeds real caching
>   behavior and now degenerates to `[LIBRARY]`. Only de-podcasted its `when` arms.
> - **Kept** the podcast-named English strings in `values/strings.xml` (now unused).
>   Deleting them fails release `lintVital` (`MissingDefaultResource`) because 26
>   Weblate locale files still declare those keys; per the strings ground rule we
>   don't hand-edit locales. Unused-string cleanup (and the locale question) is
>   deferred to **WP-10**.

**Goal:** this fork is an audiobook app. Deleting the podcast path removes a
whole parallel channel implementation and `when (libraryType)` branching from
every screen the other packages are about to touch. Decision made deliberately —
podcasts on the homelab ABS are not used by this app.

**Scope found by audit:** 10 dedicated files (`channel/audiobookshelf/podcast/**`
+ `common/api/podcast/AudioBookshelfPodcastSyncService.kt`) and ~17 files with
`LibraryType.PODCAST` branches.

**Steps:**
1. Delete `channel/audiobookshelf/podcast/` (channel, converters, models) and
   `AudioBookshelfPodcastSyncService`. Unregister the podcast channel in
   `AudiobookshelfChannelProvider` (and its Hilt wiring).
2. `domain/LibraryType.kt`: shrink `meaningfulTypes` to `listOf(LIBRARY)` so
   podcast libraries vanish from the library picker
   (`PreferredLibrarySettingComposable`). **Keep the enum values** — the server
   still reports the type and `UNKNOWN` remains the fallback; we just never
   select podcast libraries.
3. Collapse `when (libraryType)` UI branches to the LIBRARY wording:
   `NavigationBarComposable`, `DownloadsComposable` (dies fully in WP-4),
   `TimerComposable`/`SleepTimerSlider` (dies in WP-5), `TrackDetailsComposable`,
   `PlayingQueueFallbackComposable`, `ProvideNowPlayingTitle`,
   `DownloadOptionFormat`, `LibraryFallbackComposable`, `LibraryScreen`,
   placeholders, `DefaultTimerSettingsComposable`,
   `LibraryTypeAutoCacheSettingsComposable` (merge podcast auto-cache prefs into
   one), `MediaLibraryTree` (Android Auto tree). Where a composable's only
   parameter use was wording selection, drop the `libraryType` parameter
   entirely.
4. Remove podcast-only strings from `values/strings.xml` (leave locale files
   alone; orphaned translations are harmless).
5. Delete/adjust tests that exercised podcast conversion.

**Risks:** `UNKNOWN` library type paths must still behave (someone connecting to
a server with only podcast libraries should get the existing "no libraries"
fallback, not a crash — test this against the demo server by deselecting);
Android Auto tree (`MediaLibraryTree`) needs a manual check.

**Acceptance:** compiles with zero `PODCAST` references outside the enum
declaration and converters of server responses; library picker only offers book
libraries; player, downloads, timer all work; Android Auto browse works.

---

### WP-13 — Folders in config backup/restore ✅ DONE 2026-07-08

> Landed on `main` (`46a6419b`). `SettingsBackup` gains `folders` (id/name/createdAt +
> denormalized book snapshots) and `foldersHost`; `SCHEMA_VERSION` → 2. **Deviation from the
> plan's `bookIds: [...]`:** folder items store denormalized book metadata (title/author/…)
> so folders render offline — storing bare ids would lose that, so the backup snapshots the
> full `Book` fields. `FolderRepository.exportFolders`/`importFolders` (+ `FolderDao.folders()`);
> import replaces by id, preserving id + createdAt. `LissenConfigProvider` is now `suspend`
> (embeds folders on export, restores on import, all on `Dispatchers.IO`);
> `ConfigBackupSettingsScreen` launches the export/import in a `rememberCoroutineScope`.
> `foldersHost` round-trips as a plain preference (in `exportSettings`/`importSettings`) so
> WP-8's server-change wipe doesn't clear restored folders. Nullable `folders` default means
> an upstream backup (no `folders` key) restores unchanged. +2 `LocalFolderRepositoryTest`,
> rewrote `LissenConfigProviderTest` (folder round-trip + upstream-format), `SettingsViewModelTest`
> updated for the suspend methods. Note: a pre-existing `CachedBookGroupingTest` androidTest
> non-exhaustive-`when` break is unrelated (needs a device; not in the unit-test CI).

**Goal:** folders are the only fork-local data with no server copy. Upstream's
Configuration Backup & Restore (#456) exports preferences — extend it to carry
folders so a reinstall or new phone doesn't lose them.

**Steps:**
1. Find the backup writer/reader (`ConfigBackupSettingsScreen.kt` →
   its ViewModel/service; E2E: `ConfigBackupE2ETest`). Add a `folders` section to
   the exported JSON: `[{id, name, createdAt, bookIds: [...]}]` via
   `FolderRepository`.
2. On restore: upsert folders + items (replace-by-id, don't duplicate), then set
   `KEY_FOLDERS_HOST` (WP-8) to the restored host so the wipe logic doesn't
   immediately clear them.
3. Version the backup payload the same way upstream does (check how the existing
   format handles unknown fields — restore of an upstream backup without a
   `folders` key must still work).
4. Extend `ConfigBackupE2ETest` or add a unit test for round-tripping.

**Acceptance:** export → wipe app data → login to same server → import: folders
and their contents are back; importing an upstream-format backup (no folders key)
succeeds.

---

### WP-10 — Simplification sweep (run last)

**Goal:** the "leave it simpler" pass once features are in.

**Checklist:**
1. `rg`-audit for orphans left by WP-3/4/5: unused strings
   (`a11y_collapse_queue`, book-specific download-sheet strings), unused
   `PlayerViewModel` fields, unused imports, the `forceExpanded` param if any
   caller remains.
2. Delete dead composables/placeholders no longer referenced
   (`gradlew lintDebug` unused-resource report helps).
3. CI: upstream's `instrumented_tests.yml` targets their self-hosted runner
   (`github_runner_provisioning.sh`) — it can never pass on this fork. Trim CI to
   `app_build.yml` (assemble + unit tests on GitHub-hosted runners); make
   instrumented tests a manually-triggered workflow or delete it.
4. Metadata: remove Play-Store/F-Droid publishing leftovers that don't apply
   (badges handled in README; keep `metadata/` screenshots — they're harmless and
   the README uses them).
5. Re-run the full verification: `./gradlew formatKotlin lintKotlin
   testDebugUnitTest assembleRelease` + on-device smoke of every feature in this
   plan.

---

## 4. Global verification (after each package, full pass at the end)

```
./gradlew formatKotlin lintKotlin testDebugUnitTest assembleDebug
```

On-device smoke against the homelab ABS server (and the public demo server
`https://demo.lissenapp.org/` demo/demo for a second library shape): login,
library browse in all grouping modes, folder create/expand/delete, multi-select
actions, download / stop / delete from the player, sleep timer, speed change,
playback + progress sync, kill-and-relaunch state restore.

## 5. README outline (WP-1)

Keep it factual and friendly toward upstream:

- **Title:** Lissen (fork) — one-liner: an opinionated fork of GrakovNe's Lissen.
- **Why this fork exists:** upstream is intentionally minimalist and that's a
  valid design goal — this fork simply has different ones: power-user library
  management for large personal Audiobookshelf servers. Link upstream prominently;
  recommend upstream for users who want the Play-Store app.
- **What's different:** folders, multi-select (download / mark finished / folders),
  download visibility everywhere, one-tap full-book download, fixed player layout
  with always-visible chapters, smarter author grouping, downloaded-first sort,
  sleep-timer presets, audiobooks-only (no podcasts).
- **Install:** GitHub releases APK (Obtainium-friendly); no Play/F-Droid badges.
- **Building:** same gradle instructions as upstream.
- **Localization:** translations inherited from upstream; new fork strings are
  English-only (not wired to Weblate).
- **Credit + license:** MIT, full credit to GrakovNe and upstream contributors.

## 6. Upstream strategy — cherry-pick fixes, don't merge

Decided 2026-07-07. The short version: **feature parity with upstream is dead;
a thin pipe for fixes stays open.**

**Why not standing merges:** the moment WP-12 (podcast deletion) and WP-3 (player
rewrite) land, this fork deletes and rewrites files upstream actively develops.
Every wholesale `git merge upstream/main` after that is modify/delete conflict
archaeology across dozens of files, and the tax grows with every upstream
release. Merge-based syncing is the right call for lightly-diverged forks; this
is not one.

**Why not full detach:** upstream still produces things we want that land in
layers this fork barely touches — Audiobookshelf API compatibility fixes
(`channel/`), playback/Media3 bug fixes (`playback/`), dependency and security
bumps, and Weblate translation updates (locale files the fork never edits).
Cherry-picks from those layers apply nearly clean.

**The playbook (per upstream release, or whenever something breaks):**

1. `git fetch upstream`
2. `git log --oneline --no-merges upstream-reviewed..upstream/main` — triage each
   commit:
   - `channel/` / `playback/` / ABS-API / crash fixes → **cherry-pick**
   - UI features, redesigns, podcast anything → **skip**
   - Weblate commits → batch cherry-pick when convenient (clean applies)
   - version bumps, CI, store metadata → skip
3. If a wanted fix conflicts heavily with fork-rewritten files, don't fight the
   cherry-pick — read the diff and port the fix by hand as a normal fork commit
   referencing the upstream SHA.
4. Move the marker: `git tag -f upstream-reviewed upstream/main` (create it at
   the WP-0 merge point initially).
5. Normal verification (§4) after any batch.

**One-time setup:** `git config rerere.enabled true` (records conflict
resolutions for the shapes that recur). Never rebase the public `main`, and
don't spend effort keeping commits "upstreamable" — that constraint died with
the upstreaming plan.
