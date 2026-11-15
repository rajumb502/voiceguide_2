# Implementation Roadmap & Priority Task Breakdown
**Target Package:** `com.accessibility.voicecompanion`  
**Reference Blueprint:** `@requirements.md`  
**Rule for Agent:** Execute **one Phase at a time**. Do not move to the next Phase until all tasks and verification tests in the current Phase compile and pass. Always preserve existing worker threads, bitmap scaling, API key encryption, DOM clicks, and dormant `BackgroundVoiceService`.

---

## Phase 1: Safety, Privacy & Core Guardrails (P0 — Must Have Before Any Real User Testing)
*Goal: Ensure user passwords, OTPs, and money are completely protected before putting the app on a blind user's phone.*

- [ ] **Task 1.1: Gradle Dependencies (`app/build.gradle`)**
  - Add `androidx.security:security-crypto` (for encrypted user preferences).
  - Add `androidx.webkit:webkit` (for reliable document-start script injection).
  - Add JUnit 4, Robolectric, Mockito, and OkHttp `MockWebServer` test dependencies.
- [ ] **Task 1.2: Sensitive Field Masking & Hostname Extraction (`VoiceBrowserActivity.java`)**
  - Update the injected JS DOM scraper to mask `input[type="password"]`, `input[type="hidden"]`, and OTP/card fields as `"[REDACTED]"`.
  - Pass `window.location.hostname` to `AndroidVoiceBridge`.
- [x] **Task 1.3: Per-Domain Consent Gate (`SharingConsent.java` & `AssistantClient.java`)**
  - Block Gemini uploads for unapproved domains and prompt the user via TTS (*"Allow sharing screen details on [hostname]? Say Yes or No"*).
  - Spoken approval is now saved per exact hostname and provider in encrypted settings. Settings can edit/clear approvals or enable an all-sites override for that provider (default off). The master sharing switch, sensitive-field protections, and action confirmations still apply.
- [x] **Task 1.4: 30-Second Confirmation Timeout (`ActionSafetyGuard.java`)**
  - Track `queuedTimestampMs` when holding high-risk actions (`pay`, `submit`, `transfer`, `delete`) and auto-expire after `30,000ms` with a spoken timeout alert.
- [ ] **Task 1.5: Encrypt Saved User Preferences (`UserPreferences.java`)**
  - Migrate plaintext `SharedPreferences` (saved consumer IDs, URLs) to `EncryptedSharedPreferences` and delete old plaintext files.
- [x] **Task 1.6: Phase 1 Verification Tests**
  - Write and run `ActionSafetyGuardTest` and `SharingConsentTest` via `./gradlew testDebugUnitTest`.
  - Existing `SafetyGuardTest` and `SharingConsentTest` run through JUnit `HostChecksTest`; Android `StorageTest` covers encrypted consent persistence, provider isolation, revocation, overrides, and the HTTP gate.

**Phase 1 review:** The APK build, lint, 16 JVM tests, eight Android 14 emulator tests, and both JavaScript suites pass. Phase 1 remains open: Task 1.1 has the runtime dependencies and JUnit, but the proposed Robolectric/Mockito/MockWebServer stack is not installed or used. Task 1.2 already suppresses sensitive nodes entirely and returns hostname through the existing snapshot callback; it does not emit literal `[REDACTED]` nodes or introduce `AndroidVoiceBridge`. Task 1.5 already encrypts and migrates profile notes in `AssistantSettings`, where they are actually stored; successful migration clears the old preferences but leaves an empty legacy XML file. Those differences remain explicit rather than marking the exact roadmap tasks complete. This update stays within Phase 1; later phases were reviewed but no additional later-phase work was started.

---

## Phase 2: Blind-First UX & Reliability (P1 — Makes the App Usable Without Sight)
*Goal: Eliminate silent waiting screens, support low-network Indian conditions, and simplify first-time setup.*

- [ ] **Task 2.1: Audio Earcons & Tap-to-Interrupt Barge-In (`VoiceEngineManager.java`)**
  - Add `ToneGenerator` / `SoundPool` audio cues (thinking pulse, click chime, error tone).
  - Map single-finger screen tap or Volume Up press to immediately stop TTS and start `SpeechRecognizer`.
- [ ] **Task 2.2: Auto "Audio Landmark Brief" (`VoiceBrowserActivity.java`)**
  - On `onPageFinished`, speak a concise 2-sentence summary of the page title, inputs, and main buttons so the user is never left on a silent screen.
- [ ] **Task 2.3: Text-Only & Offline Failover (`AssistantClient.java` & `VoiceCommands.java`)**
  - Add automatic fallback from multimodal (image + DOM) to text-only DOM when network times out or returns 429/5xx, plus offline local commands (`scroll down`, `go back`, `read page`).
- [ ] **Task 2.4: Audio-First Permission Onboarding (`SettingsActivity.java`)**
  - Build a spoken step-by-step setup wizard guiding users through `RECORD_AUDIO`, Accessibility Service, and Default Home Launcher (`ROLE_HOME`) settings.
- [ ] **Task 2.5: Phase 2 Verification Tests**
  - Write and run `AssistantClientFailoverTest` (using `MockWebServer`) and `ShadowDomUnpackerTest` (instrumented test verifying closed Shadow DOM piercing + password redaction).

---

## Phase 3: Indian Web Realities (P2 — Solves Banking, Utility & Language Friction)
*Goal: Handle payment gateways, SMS OTPs, and regional Indian languages seamlessly.*

- [ ] **Task 3.1: Cross-Origin `<iframe>` & Payment Gateway Support (`VoiceBrowserActivity.java`)**
  - Switch script injection to `WebViewCompat.addDocumentStartJavaScript`.
  - Add DPI-scaled `MotionEvent` coordinate tap fallback when cross-origin `<iframe>` gateways (Razorpay, BillDesk, Aadhaar e-KYC) block `element.click()`.
- [ ] **Task 3.2: Hands-Free SMS OTP Auto-Read (`SmsOtpHelper.java`)**
  - Integrate Android's **SMS User Consent API** (zero `READ_SMS` permission needed) to detect incoming OTPs, read them aloud, and fill them upon verbal `"Yes"`.
- [ ] **Task 3.3: Regional Languages & Code-Mixed Voice (`VoiceEngineManager.java` & `UserPreferences.java`)**
  - Add language selection (`en-IN`, `hi-IN`, `kn-IN`, `ta-IN`, `mr-IN`) in `SettingsActivity` and pass locale context to both `SpeechRecognizer` and Gemini prompts.

---

## Phase 4: Memory & Zero-Cost Speedups (P3 — Post-Pilot Polish)
*Goal: Reduce repeat typing and cut Gemini API token costs for recurring tasks.*

- [ ] **Task 4.1: Persistent 4-Tier Memory (`ConversationMemory.java` & `AgentSession.java`)**
  - Keep per-page step history in `AgentSession` to prevent action loops, and persist the last 20 conversation turns + saved biller IDs locally.
- [ ] **Task 4.2: "Teach Once, Replay Forever" Local Macros (`MacroStore.java`)**
  - Allow saving successful multi-step workflows (e.g., *"Check BESCOM Bill"*) and replaying them locally via DOM clicks, invoking Gemini only if a selector breaks.
