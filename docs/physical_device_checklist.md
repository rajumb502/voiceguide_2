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

## 4. GMS SMS User Consent Auto-Click, Dual-SIM Headers & OTP Extraction

1. **Trigger OTP Flow & Dual-SIM / Indian Header Handling**
   - On a login/verification page (e.g., BESCOM or Indian portal), request an SMS OTP.
   - Test with carrier SMS sent from typical Indian 6-character sender headers (e.g., `VM-BESCOM`, `AD-HDFCBK`, `AX-SBIINB`, `BP-KOTAKB`).
   - Message containing mixed numbers: *"Your BESCOM electricity bill account #9876543210 has generated OTP 482910 for bill payment of Rs. 1450. Valid for 10 mins. Do not share with anyone."*
   - **Expected:**
     - `SmsOtpHelper` initiates `startSmsUserConsent(null)` and opens the 60s bounded consent window.
     - `SmsOtpHelper.extractOtp()` accurately extracts the 6-digit OTP (`482910`) and does **not** pick the 10-digit account number (`9876543210`) or bill amount (`1450`).
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

## 5. Cross-App Privacy, Opt-In Native Apps (WhatsApp) & Banking Handoff Checks

1. **Opt-In Native App Assistance (WhatsApp Summarization / Navigation)**
   - In Voice Companion **Settings**, navigate to **Allowed native apps**.
   - Speak *"Allow WhatsApp"* or toggle `com.whatsapp` to **Allowed** in the Settings UI.
   - Switch to **WhatsApp** and open a group or 1-on-1 chat.
   - Double-tap the **Voice Companion** accessibility floating button or speak *"summarize this screen"* / *"read messages"*.
   - **Expected:**
     - `BackgroundVoiceService` confirms `SharingConsent.isPackageAllowed("com.whatsapp")` is `true`.
     - `NativeAppDriver` inspects `getRootInActiveWindow()` (up to 150 nodes), extracts chat text, redacts sensitive inputs, and Gemini/OpenAI speaks a concise summary of visible messages.
   - Say *"Revoke WhatsApp"* in Settings.
   - Return to WhatsApp and re-trigger.
   - **Expected:** Prompt asks *"Allow sharing screen details from WhatsApp with [Provider]? Say Yes or No"*; if declined, screen reading aborts immediately with zero inspection.
2. **Zero Background Scraping & Unapproved App Block**
   - Switch to an unapproved non-banking app (e.g., **Telegram** or **System Settings**).
   - **Expected:** `BackgroundVoiceService` drops all accessibility events immediately; no screen nodes or screenshots are captured or sent.
3. **Permanent Banking App Block (PhonePe, GPay, SBI YONO, Paytm, BHIM)**
   - In Settings, attempt to say *"Allow PhonePe"* or enter `com.phonepe.app`.
   - **Expected:** Assistant rejects approval with a spoken security warning: *"Banking and payment apps cannot be shared with AI providers for your financial security."*
   - Launch **PhonePe**, **Google Pay**, or **SBI YONO**.
   - **Expected:** `BackgroundVoiceService` encounters the package in `STRICT_BANKING_PACKAGES`, bails immediately from `onAccessibilityEvent()`, and drops all node inspection.
4. **Pause Assistant for Banking**
   - Say *"pause assistant for banking"* in Voice Companion.
   - **Expected:** `BackgroundVoiceService.disableSelf()` runs (Android 7.0+) and TTS confirms the service is paused so banking apps with strict RASP checks launch unobstructed.
5. **Zero-Accessibility Share Sheet (`ACTION_SEND`)**
   - In WhatsApp, export a chat without media (*Export Chat → Without Media*) or share an article link, selecting **Voice Companion** from the Android system share sheet.
   - **Expected:** `VoiceBrowserActivity.handleIncomingIntent()` receives the text directly and speaks the content or opens the URL without requiring Accessibility permissions.
