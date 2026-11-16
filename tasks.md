# Implementation Roadmap & Priority Task Breakdown
**Target Package:** `com.accessibility.voicecompanion`  
**Reference Blueprint:** `@requirements.md`  
**Rule for Agent:** Execute **one Phase at a time**. Do not move to the next Phase until all tasks and verification tests in the current Phase compile and pass. Always preserve existing worker threads, bitmap scaling, API key encryption, DOM clicks, and dormant `BackgroundVoiceService`.

---

## Phase 1: Safety, Privacy & Hardware Confirmation (P0 — Must Have First)
*Goal: Protect user credentials, OTPs, and money against bystander spoofing and accidental taps.*

- [x] **Task 1.1: Gradle Dependencies (`app/build.gradle`)**
  - Add `androidx.webkit:webkit` (for reliable document-start script injection across frames).
  - Add JUnit 4, Robolectric, Mockito, and OkHttp `MockWebServer` test dependencies. *(Do NOT add deprecated `androidx.security:security-crypto`.)*
  - *Status:* **Completed.** Mockito 5.14.2 and MockWebServer 4.12.0 added alongside Robolectric and JUnit 4; webkit 1.12.1 preserved.
- [x] **Task 1.2: Sensitive Field Masking & Hostname Extraction (`VoiceBrowserActivity.java`)**
  - Update the injected JS DOM scraper to mask `input[type="password"]`, `input[type="hidden"]`, and OTP/card fields as `"[REDACTED]"`, and pass `window.location.hostname` to `AndroidVoiceBridge`.
  - *Status:* **Completed.** `page.js` updated with `[REDACTED]` masks for labels, names, placeholders, and values on sensitive nodes, and notifies `window.AndroidVoiceBridge.setHostname(hostname)`. JavaScript test suite verifies redaction.
- [x] **Task 1.3: Per-Domain Consent Gate (`SharingConsent.java` & `AssistantClient.java`)**
  - Block Gemini uploads for unapproved domains and prompt the user via TTS (*"Allow sharing screen details on [hostname]? Say Yes or No"*).
  - *Status:* **Completed.** `SharingConsent.isDomainAllowed()` implemented, unapproved hosts blocked from upload, and unit tests verify approval and rejection flows.
- [x] **Task 1.4: Mandatory Hardware Volume Confirmation & 30s Timeout (`ActionSafetyGuard.java` & `VoiceBrowserActivity.java`)**
  - Enforce **physical Volume Up double-press** (within `1,200ms`) as the sole confirmation trigger for all high-stakes transactions (`pay`, `submit`, `confirm`, `transfer`, `delete`, `proceed`, `checkout`, `otp`).
  - Explicitly reject spoken `"Yes"`/`"Haan"` confirmations while a sensitive action is pending; allow Volume Down or spoken `"Cancel"`/`"Ruko"` to abort immediately.
  - Auto-expire pending actions after `30,000ms` with a spoken timeout alert.
  - Request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` when `SpeechRecognizer` opens so background media/TV audio pauses automatically.
  - *Status:* **Completed.** `ActionSafetyGuard` updated with double Volume Up within 1,200ms window, rejection of voice affirmations, immediate cancellation on Volume Down/spoken cancel, and 30-second timeout. `VoiceBrowserActivity` wires `dispatchKeyEvent`, and `VoiceEngineManager` requests/abandons `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`.
- [x] **Task 1.5: Encrypt Saved User Preferences (`UserPreferences.java` & `KeystoreCryptoHelper.java`)**
  - Reuse the existing **Android Keystore (`AES/GCM/NoPadding`)** encryption pattern from `AssistantSettings.java` (or extract a shared `KeystoreCryptoHelper`) to encrypt saved consumer IDs, URLs, and profile snippets inside standard `SharedPreferences`, migrating and wiping any old plaintext values.
  - *Status:* **Completed.** `KeystoreCryptoHelper` extracted with AES/GCM/NoPadding. `AssistantSettings` and `UserPreferences` encrypt values and migrate legacy plaintext.
- [x] **Task 1.6: Phase 1 Verification Tests (`ActionSafetyGuardTest.java` & `SharingConsentTest.java`)**
  - Assert spoken `"Yes"`/`"Haan"` does **not** execute a pending payment.
  - Assert two `KEYCODE_VOLUME_UP` presses within `1,200ms` execute the action, while presses > 30s apart expire safely.
  - *Status:* **Completed.** `ActionSafetyGuardTest`, `SharingConsentUnitTest`, and `SafetyGuardTest` assert rejection of voice confirmation, 1,200ms hardware double-press confirmation, immediate aborts, and 30s timeouts. `./gradlew testDebugUnitTest` and `node tests/*.js` pass cleanly (100% success).

---

## Phase 2: Blind-First UX & Reliability (P1 — Eliminate Silent Screens & Network Drops)
*Goal: Give immediate spoken orientation, tactile audio feedback, and offline/low-bandwidth resilience.*

- [ ] **Task 2.1: Audio Earcons & Tap-to-Interrupt Barge-In (`VoiceEngineManager.java`)**
  - Add `ToneGenerator` / `SoundPool` cues (soft pulse while Gemini thinks, crisp chime on action success, low tone on error/timeout).
  - Map single-finger screen tap or single Volume Up press (when no confirmation is pending) to immediately stop TTS and start `SpeechRecognizer`.
  - *Status:* **Partially Present Baseline.** Ready-for-speech beep (`TONE_PROP_BEEP`) and screen touch interrupt exist. Missing: thinking pulse, success chime, error/timeout earcon, and single Volume Up tap-to-interrupt mapping.
- [ ] **Task 2.2: Auto "Audio Landmark Brief" (`VoiceBrowserActivity.java`)**
  - On `onPageFinished`, speak a concise 2-sentence summary of the page title, input fields, and main buttons so the user never lands on a silent screen.
  - *Status:* **Partially Present Baseline.** Speaks basic `"Loaded [host]"` or schedules observation for active agent. Missing: immediate 2-sentence summary of headings, form fields, and main buttons.
- [x] **Task 2.3: Text-Only & Offline Failover (`AssistantClient.java` & `VoiceCommands.java`)**
  - Fall back automatically from multimodal (image + DOM) to text-only DOM on network timeout or HTTP 429/5xx, and route basic commands (`scroll down`, `go back`, `read page`) to local `VoiceCommands` offline.
  - *Status:* **Already Available & Verified.** `AssistantClient.withFallback()` automatically strips `image` and retries text-only DOM on `IOException` / 429 / 5xx; `VoiceCommands` routes scroll, go back, read page/links/buttons locally with zero network call.
- [x] **Task 2.4: Audio-First Permission Onboarding (`SettingsActivity.java`)**
  - Build a voice-guided setup wizard for `RECORD_AUDIO`, Accessibility Service, and Default Home Launcher (`ROLE_HOME`).
  - *Status:* **Already Available & Verified.** `SettingsActivity.advanceSetup()` / `guideSetup()` provides spoken step-by-step guidance for `RECORD_AUDIO`, Accessibility Service, and Default Launcher (`ACTION_HOME_SETTINGS`), launching the exact settings intents automatically.
- [ ] **Task 2.5: Phase 2 Verification Tests**
  - Run `AssistantClientFailoverTest` (`MockWebServer`) and `ShadowDomUnpackerTest` (instrumented test verifying closed Shadow DOM piercing + password redaction + Volume Up double-press confirmation).
  - *Status:* **In Progress.** `AssistantClientTest` unit-tests fallback; dedicated `AssistantClientFailoverTest` with `MockWebServer` and `ShadowDomUnpackerTest` to be added.

---

## Phase 3: Indian Web Realities (P2 — Solves Banking, Utility & Language Friction)
*Goal: Handle payment gateways, SMS OTPs, and regional Indian languages seamlessly.*

- [ ] **Task 3.1: Cross-Origin `<iframe>` & Payment Gateway Support (`VoiceBrowserActivity.java`)**
  - Switch script injection to `WebViewCompat.addDocumentStartJavaScript`.
  - Add DPI-scaled `MotionEvent` coordinate tap fallback when cross-origin `<iframe>` gateways (Razorpay, BillDesk, Aadhaar e-KYC) block `element.click()`.
  - *Status:* **Partially Present Baseline.** `WebViewCompat.addDocumentStartJavaScript` is already active in `VoiceBrowserActivity`. Missing: DPI-scaled `MotionEvent` coordinate click fallback for cross-origin iframes.
- [ ] **Task 3.2: Hands-Free SMS OTP Auto-Read (`SmsOtpHelper.java`)**
  - Integrate Android's **SMS User Consent API** (zero `READ_SMS` permission needed) to detect incoming OTPs, read them aloud, and fill/submit upon **physical Volume Up double-press** (preventing bystander OTP spoofing).
  - *Status:* **Not Started.**
- [ ] **Task 3.3: Regional Languages & Code-Mixed Voice (`VoiceEngineManager.java` & `UserPreferences.java`)**
  - Add language selection (`en-IN`, `hi-IN`, `kn-IN`, `ta-IN`, `mr-IN`) in `SettingsActivity` and pass locale context to both `SpeechRecognizer` and Gemini prompts.
  - *Status:* **Partially Present Baseline.** Locale (`en-IN`, `hi-IN`, etc.) is supported in `SettingsActivity` and passed to `SpeechRecognizer` & TTS; system prompt code-mixed language guidance and dedicated spinner choices to be finalized.

---

## Phase 4: Memory & Zero-Cost Speedups (P3 — Post-Pilot Polish)
*Goal: Reduce repeat typing and cut Gemini API token costs for recurring tasks.*

- [x] **Task 4.1: Persistent 4-Tier Memory (`ConversationMemory.java` & `AgentSession.java`)**
  - Keep per-page step history in `AgentSession` to prevent action loops, and persist the last 20 conversation turns + saved biller IDs encrypted via `KeystoreCryptoHelper` (injected only when `SharingConsent` allows).
  - *Status:* **Already Available & Verified.** `AgentSession` tracks per-page step history, loop detection (repeats >= 3, failures >= 2); `ConversationMemory` persists bounded SQLite database encrypted with Keystore AES/GCM and site-filtering; injected into payloads only when consent allows.
- [ ] **Task 4.2: "Teach Once, Replay Forever" Local Macros (`MacroStore.java`)**
  - Allow saving successful multi-step workflows (e.g., *"Check BESCOM Bill"*) and replaying them locally via DOM clicks, invoking Gemini only if a selector breaks.
  - *Status:* **Not Started.**
