# Turbo Bar — native Android build (Compose + Room)

This is the native rewrite — no WebView, no static JSON lookup table.
Architecture:

- **UI**: Jetpack Compose, hosted in a `ComposeView` inside the IME's
  `onCreateInputView()`. Since `InputMethodService` doesn't provide the
  `Lifecycle` / `ViewModelStore` / `SavedStateRegistry` Compose expects
  from an Activity, `LifecycleInputMethodService.kt` builds and drives
  those by hand, bound to the IME's own callbacks
  (`onCreate` -> `onCreateInputView` -> `onStartInputView` <->
  `onFinishInputView` -> `onDestroy`) rather than an Activity's.
- **Data**: Room, one unified table (`PrefixEntry`) for both shipped
  dictionary words and macros -- the "scrap the lookup table, consolidate
  into the database" change. Schema follows the `original_text` /
  `current_text` design from our earlier conversation: an app update can
  safely refresh shipped word data without ever touching a user's edits,
  and "reset" is always the same operation (clear the override) regardless
  of whether the row started as a dictionary word or a from-scratch macro.
- **QR**: uses ZXing (`com.google.zxing:core`), a real Gradle dependency,
  rather than a hand-ported encoder -- lower risk now that this is a real
  Gradle project and can just depend on a mature library.
- **Images**: real `InputConnection.commitContent()` + `FileProvider`,
  gated by checking `EditorInfo.contentMimeTypes` for image support before
  ever attempting a commit -- same approach as before, ported to be called
  directly from Compose callbacks instead of a JS bridge.

## What I could NOT verify

I have no Kotlin/Android toolchain in my environment -- no `kotlinc`, no
Android SDK, no emulator. Every file here is a first draft built from
established, well-known Android patterns, reviewed carefully by eye for
obvious mistakes (missing imports, type errors I could reason through by
hand) -- but **none of it has been compiled**. Specific things worth
extra scrutiny on your first build:

- **The Lifecycle/ViewModelStore/SavedStateRegistry bridge**
  (`LifecycleInputMethodService.kt`) -- the pattern is standard for hosting
  Compose outside an Activity, but this exact combination inside an IME
  hasn't been run.
- **Showing `AlertDialog` (the macro editor) from inside an IME's input
  view window** -- IME windows are a special window type, and dialogs
  sometimes need extra handling to display correctly from one. If the
  macro dialog doesn't appear right, the fallback is replacing
  `MacroDialog`'s `AlertDialog` internals with a plain in-line Composable
  overlay drawn as part of the same `ComposeView`, instead of a separate
  system dialog window.
- **KSP + Room annotation processing setup** in `build.gradle.kts` -- the
  plugin/dependency versions are a reasonable current baseline, not
  guaranteed to be exactly right; Android Studio will likely prompt for
  updates on sync, which should be fine to accept.
- **The seeding step** (`SeedLoader.kt`) parses your reviewed
  `Turbo_Bar_Prefix_Tables.csv` and `preloaded_macros.json` (both bundled
  as assets) into Room on first launch only -- deliberately NOT using
  Room's `createFromAsset()` prepackaged-database feature, since that
  requires the asset database's schema hash to exactly match what Room
  generates at compile time from the `@Entity` annotations, which I have
  no way to verify without a compiler. Runtime parsing is slower on first
  launch but has no such hidden failure mode.

## What's carried over from the web prototype, and what isn't (yet)

**Ported**: dynamic top row reading live from the database, full QWERTY,
shift/caps, macro create/edit/reset via long-press, QR macros with
field-support graying-out, the 22 preloaded messages, the demo QR macro.

**Not yet ported in this pass** (flagging explicitly rather than silently
dropping):
- **Drag-to-reorder** -- the web prototype's slot-reordering feature isn't
  in this build yet.
- **Real draft-text reading for the macro dialog.** The web prototype and
  this native build both still track "what have you typed" as a local
  mirror rather than reading the actual focused field -- but a *native*
  build can genuinely do better here via
  `InputConnection.getTextBeforeCursor()`, which a WebView structurally
  could not. That upgrade isn't implemented in this pass; the dialog
  currently prefills with an empty draft rather than the WebView
  version's `output` mirror.

## Setup

Same as before -- open this folder directly in Android Studio (or your
chosen cloud environment; see the note about Firebase Studio/Project IDX
being sunset if that was the plan), let Gradle sync (accept version-update
prompts), connect a physical device via USB debugging, run, then enable
"Turbo Bar" under Settings -> System -> Languages & input -> On-screen
keyboard -> Manage keyboards.

Budget real debugging time for the first build specifically -- this is a
much larger jump from "known working" than any previous step in this
project, precisely because I have no way to compile-check any of it
myself this time.

## Building via GitHub Actions (no local/cloud IDE needed for compiling)

`.github/workflows/build.yml` builds a debug APK entirely on GitHub's
servers, not your machine:

1. Create a GitHub repo and push this project to it (`git init`, `git add .`,
   `git commit`, add a remote, `git push`).
2. Every push to `main` triggers a build automatically, or trigger one
   manually anytime from the repo's "Actions" tab (Run workflow button).
3. When it finishes, open that run and download the `turbobar-debug-apk`
   artifact — that's your installable APK.
4. Get it onto your phone (download link works if you're on the phone's
   browser, or transfer the file another way), enable "install from unknown
   sources" if prompted, and install it directly — no `adb`, no emulator,
   no local build tooling required at all.

One thing this workflow deliberately does differently from the most common
tutorials: it doesn't rely on `./gradlew` (the Gradle wrapper), since that
needs a committed `gradle-wrapper.jar` binary I had no safe way to produce
without Gradle already installed somewhere to generate it. Instead it
installs Gradle directly on the runner and calls `gradle` by name — fully
equivalent for this purpose, just a different bootstrapping path. See the
comments in the workflow file for how to switch to a real wrapper later if
you generate one locally.
