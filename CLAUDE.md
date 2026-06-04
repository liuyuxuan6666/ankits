# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew assembleDebug                                    # Build debug APK
./deploy.sh                                                # Build, install, and launch on emulator/device
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Manual install
adb shell am start -n com.example.ankits/.MainActivity     # Manual launch
```

There are no tests in this project.

## Architecture

Single-module (`:app`) Android app — a local-first toolbox. Currently ships one tool: a **Markdown-to-image renderer** that draws styled text onto a `Bitmap` via Android's `Canvas` 2D API, exporting PNGs sized for social media platforms.

All source lives in `com.example.ankits` (no sub-packages). No third-party libraries — only AndroidX, AppCompat, and Material 3. No Jetpack Navigation, ViewModel, coroutines, or DI framework.

## Key Classes

- **`MainActivity.kt`** — Launcher. A `RecyclerView` of tool cards. Add tools by appending to the `tools` list.
- **`TextToImageActivity.kt`** — The renderer tool (~523 lines). Real-time debounced preview (300ms `Handler`), two-pass `Canvas` measurement/drawing, full-screen preview dialog, and `MediaStore` export.
- **`MarkdownParser.kt`** — Regex-based parser. Splits on double-newlines into `MarkdownSection` objects (`HERO`, `SUB`, `BODY`). Inline formatting via `SpannableStringBuilder`.
- **`Template.kt`** — `Template` + `SectionStyle` data classes. Three built-in presets: `SIMPLE`, `TECH`, `CARTOON`.
- **`ImageSize.kt`** — Width presets for WeChat, Instagram, Twitter, Xiaohongshu.

## Patterns

- **ViewBinding** everywhere. Activities use generated binding classes with no synthetic imports.
- **No coroutines.** Debouncing uses `Handler.postDelayed`/`removeCallbacks`.
- **Edge-to-edge** via `WindowInsetsCompat` in every activity.
- **Collapsible config cards** — manual toggle via `View.OnClickListener` on headers (chevron ▾/▸).
- **All rendering is local.** No network calls, no WebView, no HTML intermediary. `StaticLayout` + `TextPaint` + `Canvas` draw directly to `Bitmap`.
