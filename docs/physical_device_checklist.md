# Physical Device Verification Suite Checklist (TalkBack Enabled)

Manual end-to-end verification protocol for Voice Companion on physical Android 8.0+ hardware (recommended: Android 13+ Indian market device with TalkBack, Google Play Services, and offline `en-IN` / `hi-IN` TTS voices installed).

---

## Pre-Flight Setup & Permissions

- [ ] Install debug APK (`./gradlew :app:installDebug`) via ADB or sideload.
- [ ] Enable **TalkBack** (`Settings → Accessibility → TalkBack`).
- [ ] Confirm offline `en-IN` TTS voice is installed and audible alongside TalkBack.

---

## 1. TalkBack Coexistence & Android 13+ Restricted Settings Onboarding

1. **Launch & Initial Focus**
   - Open **Voice Companion**.
   - Swipe right through controls using TalkBack gestures.
   - **Expected:** Every button (**Speak**, **Stop**, **Settings**, address bar, WebView) receives clean focus with readable labels; **Speak** activates reliably via double-tap.
2. **Restricted Settings Guidance (Android 13+ Sideloaded Builds)**
   - Open **Settings** and advance through the spoken setup sequence to Accessibility activation.
   - Attempt to toggle **Voice Companion** in Accessibility when restricted by Android 13+.
   - **Expected:** `SettingsActivity` detects the failed activation, speaks step-by-step instructions to tap the top-right overflow menu → **Allow restricted settings**, and launches `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for `com.accessibility.voicecompanion`.
   - Grant **Allow restricted settings**, return, and enable **Voice Companion** in Accessibility Settings.
3. **TalkBack Shortcut & Speech Ducking**
   - Hold **Volume Up + Volume Down simultaneously for 3 seconds**.
   - **Expected:** `BackgroundVoiceService.onKeyEvent()` only acts on `repeatCount == 0` when a pending `ActionSafetyGuard` prompt exists; TalkBack's 3-second dual-volume toggle shortcut still works cleanly.
   - Trigger TTS readout (e.g., *"read this page"*), then single-tap the WebView or tap **Speak** mid-utterance.
   - **Expected:** TTS stops immediately and transitions cleanly to listening without overlapping TalkBack focus announcements.

---

## 2. Wikipedia Audio Landmark Brief & Basic Navigation

1. **Cold Load Landmark Brief**
   - Launch Voice Companion or say *"go to en dot m dot wikipedia dot org"*.
   - **Expected:** Upon `onPageFinished`, `speakAudioLandmarkBrief` announces:
     `"<Title> loaded. Found <N> input fields and <M> interactive controls. Tap Speak and tell me what you want to do."`
2. **Local Reading & Search**
   - Say *"find search box"* or *"search Wikipedia for migrating birds and read the introduction"*.
   - **Expected:** If AI sharing is requested on `en.m.wikipedia.org` for the first time, spoken site-consent prompt names `en.m.wikipedia.org` and the active provider. Saying *"Yes"* persists consent, executes search, and speaks the introduction after page reload.
3. **Interrupt & Cancel**
   - Tap **Speak** mid-task and say *"stop"* (or tap **Stop**).
   - **Expected:** Active automation cancels immediately, pending uploads drop, and no further model calls fire.

---

## 3. BESCOM React Billing Portal (Synthetic Input, Payment Frame, Safety Gate & Macros)

> **Note:** Use a test/non-destructive consumer ID or stop before final real-money settlement unless intentionally paying a live bill.

1. **React/Angular Synthetic Input Dispatching**
   - Navigate to `bescom.co.in` (or equivalent React/Angular utility portal).
   - Ask the assistant to enter a consumer/account ID into the React input field.
   - **Expected:** `page.js` `dispatchNativeInput()` updates controlled React state (`input`, `change`, `blur` events bubble), submit/fetch button enables, and value is **not** echoed back if the field matches sensitive patterns (`password`, `pin`, `mpin`, `otp`, `cvv`, `aadhaar`, `pan`, `secret`).
2. **Payment `<iframe>` Handling & Scroll Stability**
   - Proceed to the payment summary / gateway step containing a payment `<iframe>`.
   - **Expected:**
     - `page.js` marks `payment_frame` without forcing main-document screenshot suppression unless main-document password/PIN fields are present.
     - Tap/scroll actions adjust for `scrollX` / `scrollY` deltas even when the soft keyboard opens or page zooms.
3. **Hardware Volume Up Double-Press Safety Gate**
   - Trigger a consequential action (e.g., *"Pay"* / *"Submit"* / *"Proceed"*).
   - **Expected:**
     - Assistant speaks the action summary and requests tactile confirmation.
     - Say *"Yes"* verbally → **Expected:** Rejected (*"Spoken confirmation is not accepted for sensitive actions..."*).
     - Press **Volume Down** → **Expected:** Action aborts immediately.
     - Re-trigger and **double-press Volume Up within 1,200 ms** (even if an external system overlay or bottom sheet is visible) → **Expected:** Success chime plays and the action executes.
4. **Local Macro Save & Replay**
   - After completing a multi-step check-bill flow, say *"save macro as check bescom bill"*.
   - Return to the initial URL and say *"run macro check bescom bill"*.
   - **Expected:** Macro replays locally from `MacroStore` without LLM round-trips; any consequential step in the macro pauses for Volume Up double-press.

---

## 4. GMS SMS User Consent Auto-Click & OTP Entry

1. **Trigger OTP Flow**
   - On a login/verification page, request an SMS OTP.
   - **Expected:** `SmsOtpHelper` calls `startSmsUserConsent(null)` and opens the 60-second `awaitingSmsConsent` window (`com.google.android.gms` only).
2. **Auto-Approve Bottom Sheet**
   - When the SMS arrives and Google Play Services displays the `"Allow <app> to read the message below?"` modal bottom sheet:
   - **Expected:** `BackgroundVoiceService.onAccessibilityEvent()` locates `com.google.android.gms:id/positive_button` (or `"Allow"`), clicks it automatically without requiring TalkBack touch exploration, and immediately resets `awaitingSmsConsent = false`.
3. **Readout & Hardware-Gated Entry**
   - **Expected:**
     - OTP digits are spoken clearly one digit at a time (e.g., `"4 8 2 9 1 0"`).
     - Entering the OTP into the page pauses for **Volume Up double-press**.
     - Double-pressing Volume Up fills the OTP field via `dispatchNativeInput()`.
4. **60-Second Expiry Check**
   - Wait >60 seconds without an SMS arriving, then trigger a GMS dialog manually.
   - **Expected:** `SmsOtpHelper.isAwaitingSmsConsent()` returns `false`; `BackgroundVoiceService` does **not** auto-click after expiry.

---

## 5. Cross-App Privacy, Denylist & Banking Handoff Checks

1. **Zero Background Scraping & Denylist**
   - Switch away from Voice Companion to **WhatsApp**, **PhonePe**, **Google Pay**, or **SBI YONO**.
   - **Expected:** `BackgroundVoiceService` drops all accessibility events immediately; no screen nodes or screenshots are captured or uploaded.
2. **Pause Assistant for Banking**
   - Say *"pause assistant for banking"* in Voice Companion.
   - **Expected:** `BackgroundVoiceService.disableSelf()` runs (Android 7.0+) and TTS confirms the service is paused so banking apps with strict RASP checks launch unobstructed.
3. **Zero-Accessibility Share Sheet (`ACTION_SEND`)**
   - Share plain text or a URL from another app (e.g., an article or exported chat) to **Voice Companion**.
   - **Expected:** `VoiceBrowserActivity.handleIncomingIntent()` opens URLs directly or speaks shared text without requiring Accessibility permissions.
