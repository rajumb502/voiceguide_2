# Voice Companion

An experimental native Android Java voice agent for browser and on-demand assistive tasks.
Users choose Gemini or OpenAI and provide their own API key. Requests go directly to
that provider; Python is not required. The original blueprint is in requirements.md.

## Summary of Capabilities

Voice Companion is an **audio-first, non-visual web browser and assistive companion** for Android, designed specifically for blind and low-vision users (with first-class Indian English and Hindi support). It uses your own Gemini or OpenAI API key directly on-device—no backend server or subscription required.

1. **Audio-First Web Browsing:**
   - **Audio Landmark Briefs:** Spoken layout orientation (page title, count of editable inputs, and interactive controls) on every page load before speaking a goal.
   - **Goal-Directed AI Automation:** Multimodal step-by-step browser loop with local validation and DOM element targeting.
   - **React/Angular Synthetic Inputs:** Native property descriptor setters and bubbling `input`/`change`/`blur` event cascade for dynamic single-page apps.
   - **Instant Touch Interrupt:** Tap anywhere on the WebView while TTS speaks to pause speech and immediately start listening.
2. **Physical & Financial Safety Guards (`ActionSafetyGuard`):**
   - **Hardware Double-Press Gate:** High-stakes actions (paying, transferring, deleting, submitting forms, filling OTPs) require a physical Volume Up double-press within 1,200 ms; spoken "Yes" or "Haan" is strictly rejected.
   - **TalkBack Coexistence:** Only single key presses (`repeatCount == 0`) are filtered, preserving TalkBack's 3-second simultaneous dual-volume shortcut.
   - **Sensitive Field Redaction:** Passwords, PINs, MPINs, CVVs, Aadhaar, PAN, and OTPs are masked to `[REDACTED]` locally before processing.
   - **Payment Frame Isolation:** Preserves `payment_frame` iframe nodes for coordinate taps while redacting main-document secrets.
3. **Hands-Free SMS OTP Auto-Read (`SmsOtpHelper`):**
   - **Zero-Permission SMS Consent:** Uses Google Play Services SMS User Consent API (zero `READ_SMS` permission).
   - **Auto-Approved Consent Sheet:** Auto-clicks "Allow" on GMS modal sheets within a bounded 60s window, speaks the OTP digit-by-digit, and gates entry behind Volume Up confirmation.
4. **Encrypted Local Macros ("Teach Once, Replay Forever"):**
   - **Save & Replay:** Save successful multi-step browser flows locally (`MacroStore`, AES-256 encrypted) and replay with zero API calls.
   - **Semantic Anchor Fallbacks:** Resilient replay checks CSS selectors → `aria-label` → accessible label → element name → placeholder.
5. **Controlled Native App Assistance (`NativeAppDriver`):**
   - **On-Demand App Reading:** Traverses Android's accessibility node tree (up to 150 nodes) for explicitly approved native apps.
   - **Zero Background Scraping & Banking Shield:** Default-denied package policy with hard permanent block on banking/UPI apps (PhonePe, GPay, Paytm, SBI YONO, BHIM, etc.).
   - **Banking Pause Command:** Saying "pause assistant for banking" disables the accessibility service via `disableSelf()` (API 24+) to bypass strict bank RASP checks.
   - **Share-Sheet Receiver:** Direct `ACTION_SEND` (`text/plain`) handler reads shared articles or exported chat text without accessibility permissions.
6. **Privacy & Context Architecture:**
   - **Per-Host & Per-Provider Consent:** Spoken per-domain approval required before sending data, backed by Android Keystore AES-256 encryption.
   - **Zero Cross-Site Memory Bleed:** Hostname-isolated conversation history prevents financial or context leakage across websites.
   - **Saved Feedback Preferences:** App-private local memory for voice guidance rules ("Keep answers short", "Prefer prices before features").

## Give the assistant a goal

Tap **Speak**, wait for the tone, and tell it what you want to accomplish. Examples:

- “On wikipedia.org, find an article about migrating birds and explain the introduction.”
- “Search this library website for books about gardening.”
- “Help me fill out this contact form. Ask me for any missing details.”
- “Find the opening hours on this museum website.”

There are no hardcoded site workflows.
The model chooses a next action from the current page; the app validates it, executes
it, observes the result, and asks the model what to do next. It retains the user's goal,
clarifications, recent spoken context, and local action outcomes throughout the task.

### Audio Landmark Briefs

When a web page finishes loading, Voice Companion automatically extracts a lightweight
DOM snapshot and speaks a concise **Audio Landmark Brief** containing the page title,
the count of editable input fields, and the count of interactive controls (buttons/links)
so blind and low-vision users immediately know the shape of the page before speaking a goal.

### Hardware Safety Gate (Volume Up Double-Press)

Available browser actions are CLICK, TYPE, SUBMIT, SCROLL, NAVIGATE, BACK, WAIT, ASK, and DONE.
ASK reads a question and listens for the user's reply. DONE speaks the result and ends
the task. Ordinary links and recognizable search inputs can be used automatically.
Unknown controls, ordinary form fields, non-search submissions, downloads, OTP entry,
and actions involving payments, sending, account changes, or deletion require confirmation.
Model risk flags and local rules (`ActionSafetyGuard` / `AgentPolicy`) both apply.

High-stakes actions **cannot** be confirmed by voice (protecting against background speech,
TV/radio audio, or AI hallucination). Instead:
- **Confirm:** Double-press the physical **Volume Up** button within 1,200 ms.
- **Cancel:** Press **Volume Down**, or say “cancel”, “stop”, “no”, or “ruko”.
- An unapproved action expires after 30 seconds and cannot be approved after navigation.

Volume key events are routed globally through `BackgroundVoiceService.onKeyEvent()` when
active, so tactile confirmation works even when a system dialog or payment sheet overlays
the browser. Only initial presses (`repeatCount == 0`) are intercepted, preserving TalkBack's
3-second simultaneous Volume Up + Down accessibility shortcut.

### SMS User Consent Auto-Read (Hands-Free OTP)

`SmsOtpHelper` integrates the Google Play Services **SMS User Consent API** with zero
`READ_SMS` permission. When an SMS arrives during an active OTP flow:
1. A bounded 60-second `awaitingSmsConsent` window opens.
2. `BackgroundVoiceService` detects the `com.google.android.gms` consent bottom sheet and
   automatically clicks **Allow** (`com.google.android.gms:id/positive_button` or `"Allow"`),
   then immediately resets the consent window.
3. The 4–8 digit OTP is extracted locally and read aloud digit-by-digit.
4. Filling the OTP into the page still requires a mandatory **Volume Up double-press**
   via `ActionSafetyGuard`.

### Encrypted Local Macros ("Teach Once, Replay Forever")

Multi-step browser workflows completed during an AI session can be saved locally in
`MacroStore` (AES-256 encrypted via `KeystoreCryptoHelper`) and replayed deterministically
without calling the cloud LLM:
- **Save:** Say “save macro as <name>” (or “save this as macro <name>”) after a workflow.
- **List:** Say “list macros” to hear saved macro names.
- **Replay:** Say “run macro <name>” (or “replay macro <name>”). High-stakes steps
  within a macro still require Volume Up double-press confirmation.
- **Delete:** Say “delete macro <name>”.

### Memory, Interruptions, and Local Commands

Conversation memory is separate from task history. Follow-ups retain up to 24 recent
user/assistant/page messages within a 16,000-character budget, including intermediate
spoken progress, final descriptions, local readouts, and page references. Each message
records its page title, site, path, and page ID; URL queries and fragments are omitted.
The model receives the current page separately and must check it before reusing old facts
or stating balances/confirmations. Memory is encrypted with Android Keystore and saved in
a bounded SQLite record across activity recreation and process exits. Only conversation from
the current site is shared under saved sharing approvals. Say “clear conversation” outside
a pending question to erase it explicitly.

After any operation the next request contains a fresh page snapshot. The loop stops after
16 model turns, two consecutive operation failures, or three repeats of the same operation.
Tap **Speak** (or touch the WebView while TTS is speaking) to interrupt and clarify, or **Stop**
to cancel. Manual page interaction, manual navigation, leaving the screen, settings, and
page-load failures cancel automation.

Local commands remain available outside an active question: “go to example dot com”,
“read this page”, “list links”, “list buttons”, “scroll down”, “scroll up”, “go back”,
“find search box”, “open app Calculator”, “pause assistant for banking”, and “help”.
With TalkBack, swipe to Speak and double-tap to activate it.

## Settings, Sharing, and AccessibilityService

1. Open Settings and choose Gemini or OpenAI.
2. Choose a suggested model or type a model ID available to your account (must support
   image input and structured JSON).
3. Enter your API key and save. Each provider retains its own key and model.
4. Optionally change speech recognition from `en-IN` to `hi-IN` or another language tag,
   and toggle audio feedback tones.

Settings starts a spoken setup sequence for microphone access, Accessibility settings,
and the default Home app (the app qualifies via its `HOME` intent filter). On Android 13+
sideloaded builds where **Restricted Settings** blocks Accessibility activation,
`SettingsActivity` speaks step-by-step instructions and opens App Info so the user can tap
**Allow restricted settings**.

### AccessibilityService & Anti-Scraping Guardrails

`BackgroundVoiceService` is declared with `canRetrieveWindowContent="true"`,
`canRequestFilterKeyEvents="true"`, and `canPerformGestures="true"` for:
- Global `ActionSafetyGuard` volume-key filtering (`FLAG_REQUEST_FILTER_KEY_EVENTS`).
- Bounded 60s SMS User Consent dialog auto-approval (`com.google.android.gms` only).
- On-demand native app interaction (`NativeAppDriver`) via explicit per-app opt-in.

Strict anti-scraping boundaries are enforced in `BackgroundVoiceService` and `SharingConsent`:
- **Zero background scraping:** Accessibility events are ignored unless a user-triggered
  session is active and the package is explicitly approved (`SharingConsent.isPackageAllowed`),
  except during the bounded 60s SMS consent prompt.
- **Permanent denylist:** Encrypted messengers and Indian banking/UPI packages (PhonePe,
  Google Pay, Paytm, SBI YONO, BHIM, etc.) are blocked from automatic inspection
  and suggestion.
- **Banking pause & `FLAG_SECURE`:** `NativeAppDriver` respects `FLAG_SECURE` / empty trees
  by aborting capture and speaking a security warning. Saying “pause assistant for banking”
  calls `disableSelf()` (Android 7.0+) so strict banking RASP checks are not triggered.
- **Share-sheet receiver:** Voice Companion accepts `ACTION_SEND` (`text/plain`) intents
  to read shared articles, links, or exported chat text without accessibility permissions.

For Play Store policy details, see [docs/play_store_accessibility.md](docs/play_store_accessibility.md).

### Page Sharing & Profile Notes

Page sharing, per-site approvals, per-app allowlists, and saved feedback preferences use
`EncryptedSharedPreferences` backed by Android Keystore. Unknown websites require spoken
per-site consent naming the host and selected provider before DOM/conversation data is sent.
Sharing is re-checked at the HTTP boundary (`AssistantClient`), and screenshots are bounded
to 720 px JPEG quality 70 and omitted for sensitive pages (main-document passwords, OTP,
PIN, MPIN, CVV, Aadhaar, PAN, or recognized sensitive inputs). Tagged `payment_frame` iframes
allow consented visual inspection while keeping main-document secrets redacted.

Feedback preferences (“Remember that I prefer prices before features”, “Keep answers short”,
“Read my preferences”, “Forget my preferences”) are stored locally up to 20 notes / 4,000 chars.

## Build and verification

Use JDK 17 and Android SDK 35. Open the project in Android Studio, or run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Instrumented tests leave the app installed to preserve emulator settings and keys.
Run on Android 8.0+ with speech recognition and TTS installed. No backend URL or server
token is needed; the original Phase 1 gateway prototype is archived at `prototypes/server.py`
and verified with `python3 -m unittest discover tests`.

JavaScript bridge checks can be run with:

```bash
node tests/page.test.js
node tests/agent-page.test.js
```

For physical device manual testing with TalkBack enabled, see
[docs/physical_device_checklist.md](docs/physical_device_checklist.md).
For the observed NVIDIA decoder emulator crash and its workaround, see
[docs/emulator-crash.md](docs/emulator-crash.md) and `scripts/emulator-debug.sh`.

## Current scope

The DOM bridge sees up to 150 visible labelled elements in the main document, open shadow
roots, and `payment_frame` markers, dispatching React/Angular-compatible native property
value setters and bubbling events. `NativeAppDriver` provides on-demand `AccessibilityNodeInfo`
snapshots (up to 150 nodes), sensitive field redaction, and `CLICK`/`TYPE`/`SCROLL`/`BACK`
actions for explicitly allowed apps.

Cross-origin iframe internals, canvas-only interfaces, pre-existing closed shadow roots,
CAPTCHAs, arbitrary custom select widgets, and file uploads are not fully supported.
Physical device validation with TalkBack and live Indian portals is tracked in
[docs/physical_device_checklist.md](docs/physical_device_checklist.md).

## References

- [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Gemini generateContent](https://ai.google.dev/api/generate-content)
- [AndroidX WebView](https://developer.android.com/reference/kotlin/androidx/webkit/WebViewCompat)
