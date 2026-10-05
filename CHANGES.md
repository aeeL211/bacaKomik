# Stage 0 Fixes & Verification Guide

This document describes the fixes applied for the 3 regressions introduced during the Compose UI migration, explaining how each fix resolves the underlying root cause and how to verify the fix on a physical Android device.

---

## 1. Web-Page Dialogs Positioning (Centered Modals)

### Symptom
Dialogs and modals created by websites (HTML/CSS using viewport units, `inset: 0`, or `top: 50%`) were displayed near the top of the screen instead of centered on the visible display.

### Root Cause
1. `useWideViewPort` and `loadWithOverviewMode` were set to `true` in `MainActivity.kt` (`configureWebSettings`). This caused the WebView to calculate its CSS viewport using a desktop-like layout width (980px) and scale down the rendered web content. The viewport units (`vh`/`vw`) and fixed offsets were computed relative to the scaled desktop layout dimensions, pushing centered modals upward.
2. Legacy `android:windowFullscreen = true` flag in `themes.xml` conflicted with `WindowCompat.setDecorFitsSystemWindows(window, false)` and `windowSoftInputMode = adjustResize`, causing WindowManager to dispatch mismatched visible layout frame metrics to the WebView.
3. In `MainScreen.kt`, `PullToRefreshBox` was padded at the top using `.windowInsetsPadding(WindowInsets.statusBars)`. Applying status bar top padding shifted the entire WebView top boundary down, causing a layout offset where the WebView viewport did not equal the visible display size.

### Fix
- Set `useWideViewPort = false` and `loadWithOverviewMode = false` in `MainActivity.kt` (`configureWebSettings`).
- Removed `android:windowFullscreen = true` from `AppTheme.FullScreen` in `themes.xml`.
- Removed `.windowInsetsPadding(WindowInsets.statusBars)` from `PullToRefreshBox` and popup container in `MainScreen.kt`, allowing the WebView to fill the screen (`fillMaxSize()`) edge-to-edge while keeping IME bottom padding for keyboard adjustments.

### How to Verify on Phone
1. Open the app on your phone and navigate to any page that displays a web modal/dialog (e.g., login modal, announcement pop-up, or site prompt).
2. Confirm that the modal appears vertically and horizontally centered in the visible screen area rather than hugging the top status bar.

---

## 2. White Box at the Bottom of Web Content

### Symptom
A plain white horizontal gap appeared at the bottom of the screen, situated between the bottom of the page content and the site's fixed bottom navigation bar.

### Root Cause
1. `Theme.kt` was using `isSystemInDarkTheme()` to toggle between `DarkColorScheme` and `LightColorScheme`. When the user's phone was set to system Light Mode, Compose rendered a default white background (`#FFFFFF`) behind the WebView container.
2. Because `useWideViewPort = true` and `loadWithOverviewMode = true` scaled down the webpage layout to fit the screen width, the vertical height of the web content shrunk, creating a gap above the fixed bottom navigation bar. The white background behind the container was exposed through this gap.

### Fix
- Updated `Theme.kt` (`AppTheme`) to force `DarkColorScheme` (`#121212` background / `#18181B` surface) so system Light Mode settings never draw white surfaces behind the WebView or system bar spacers.
- Disabling `useWideViewPort` and `loadWithOverviewMode` in `MainActivity.kt` ensured 1:1 viewport rendering matching the physical screen dimensions, eliminating overview scaling shortfalls.

### How to Verify on Phone
1. Ensure your phone is set to system Light Mode (or Dark Mode) and open the app.
2. Load a web page with a bottom navigation bar.
3. Scroll through the page and examine the area above the website's bottom navigation bar.
4. Confirm that no white box or gap appears, and that web page content fills the entire screen seamlessly without layout gaps.

---

## 3. Pull-To-Refresh Gesture & Reload Behavior

### Symptom
Pulling down at the top of the screen displayed the pull-to-refresh spinner, but the page did not reload from the network and the spinner remained stuck indefinitely. Pulling while scrolled down also triggered refresh unexpectedly.

### Root Cause
1. `AndroidView` holding `NestedScrollWebView` in `MainScreen.kt` was missing `Modifier.nestedScroll(rememberNestedScrollInteropConnection())`. As a result, nested scrolling touch events from `NestedScrollWebView` were not connected to Compose's `PullToRefreshBox`.
2. `onRefresh` relied on a state variable that could evaluate to `false` during overscroll/drag gestures at touch release (`ACTION_UP`), causing `onRefresh` to exit prematurely without calling `triggerManualRefresh` or `webView.reload()`, leaving `PullToRefreshBox` stuck in refreshing state.
3. In `MainScreen.kt`, `webView.clearCache(true)` was not called before `webView.reload()`, preventing forced network reloads.
4. In `DefaultWebViewClient.onReceivedError`, main frame load errors did not notify `viewModel.onPageFinished()`, leaving the spinner in a refreshing state if network requests failed.

### Fix
- Added `Modifier.nestedScroll(rememberNestedScrollInteropConnection())` to `AndroidView` in `MainScreen.kt`.
- Updated `onRefresh` in `MainScreen.kt` to inspect the WebView instance directly (`webView.scrollY == 0 || !webView.canScrollVertically(-1)`), ensuring reliable top scroll detection upon gesture release.
- Added `webView.clearCache(true)` before `webView.reload()` in `onRefresh`.
- Added `activityRef.get()?.viewModel?.onPageFinished()` call in `onReceivedError` for main frame errors to guarantee the refresh spinner clears on failure.

### How to Verify on Phone
1. Scroll to the top of any webpage in the app and pull down.
2. Verify that the pull-to-refresh spinner appears, the page reloads fresh content from the network, and the spinner disappears promptly upon completion.
3. Scroll down into the middle/bottom of a long page (or inside a scrollable div) and pull down: verify that normal page scrolling occurs without triggering pull-to-refresh or sticking the spinner.
