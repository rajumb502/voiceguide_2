# Architectural & Upgrade Blueprint: Voice-Driven Accessibility Launcher & Browser
**Target Package:** `com.accessibility.voicecompanion`  
**Target Audience:** Visually Impaired Users in India  
**Instructions for Antigravity Agent:** Preserve existing worker threads, bitmap scaling, API key encryption, resource cleanup, DOM-click execution, and dormant `BackgroundVoiceService` state. Implement the remaining OpenDroid upgrades, Indian real-world UX enhancements, and automated test suites below.

---

## 1. Existing Verified Baseline (Do Not Regress)
* **Threading & Media:** HTTP calls to Gemini run on background workers; `WebView` screenshots are bounded before encoding.
* **Security & Lifecycle:** API keys are encrypted via Android Keystore; `WebView` and `SpeechRecognizer` instances are cleanly destroyed on exit.
* **Execution Model:** Standard web actions execute via DOM clicks (`element.click()`), and `BackgroundVoiceService` remains dormant without an always-on background microphone.

---

## 2. Core OpenDroid & Security Upgrades (Pending Implementation)

### A. Per-Domain Consent & Scraper Privacy (`SharingConsent.java`, `AssistantClient.java`, `VoiceBrowserActivity.java`)
* **Sensitive Input Redaction:** Update the injected JS scraper to replace values/placeholders of `input[type="password"]`, `input[type="hidden"]`, and `autocomplete` OTP/card fields with `"[REDACTED]"`.
* **Domain Gate:** Pass `window.location.hostname` to `AndroidVoiceBridge`. Before `AssistantClient` sends any screenshot or DOM JSON to Gemini, verify `SharingConsent.isDomainAllowed(hostname)`. If unknown, pause and ask via TTS: *"Allow sharing screen details on [hostname] with the AI assistant? Say Yes or No."*

### B. 30-Second Action Timeout (`ActionSafetyGuard.java`)
* Record `System.currentTimeMillis()` when `queueForConfirmation()` holds a high-risk action (`pay`, `submit`, `confirm`, `transfer`, `delete`).
* Automatically expire and clear pending actions older than `30,000ms`, speaking: *"Confirmation timed out for your safety. Please repeat your request."*

### C. Encrypted User Preferences (`UserPreferences.java`)
* Migrate plaintext `SharedPreferences` (saved consumer IDs, URLs, profile snippets) to `EncryptedSharedPreferences` (`androidx.security:security-crypto`), including a one-time migration that deletes old plaintext XML files.

### D. Persistent 4-Tier Memory (`ConversationMemory.java` & `AgentSession.java`)
* **Short-Term (`AgentSession`):** Track per-page action history in memory to prevent infinite scroll/click loops.
* **Long-Term (`ConversationMemory`):** Persist bounded cross-session context (last 20 turns and saved biller IDs) in local encrypted storage or Room DB, injected into Gemini prompts only when `SharingConsent` allows.

### E. Text-Only & Offline Failover (`AssistantClient.java` & `VoiceCommands.java`)
* **Primary:** Gemini Flash-Lite multimodal (bounded screenshot + scraped DOM JSON).
* **Secondary Fallback:** On network timeout, HTTP 429/5xx, or low bandwidth, retry with text-only DOM JSON + user command.
* **Offline Fallback:** Route basic commands (`scroll down`, `go back`, `read page`) to local `VoiceCommands` + Android `TextToSpeech` with zero network dependency.

### F. Audio-First Permission Onboarding (`SettingsActivity.java` & `AndroidManifest.xml`)
* Add a voice-guided setup wizard in `SettingsActivity` that checks `RECORD_AUDIO`, Accessibility Service status, and `RoleManager.ROLE_HOME` (Default Launcher), speaks clear instructions, and opens the exact Android Settings screen automatically.

---

## 3. Real-World Indian Accessibility Upgrades

### A. Auto "Audio Landmark Brief" on Page Load (`VoiceBrowserActivity.java`)
* Immediately when `onPageFinished` fires, trigger a lightweight Gemini summary (or local heading/form parser) that speaks a 2-sentence orientation within 3 seconds (e.g., *"BESCOM Portal loaded. Found Consumer ID box, Password box, and Login button."*).

### B. Audio Earcons & Tap-to-Interrupt Barge-In (`VoiceEngineManager.java`)
* **Audio Earcons:** Use Android `ToneGenerator` or `SoundPool` to play subtle audio cues: a soft pulse while Gemini is thinking, a crisp chime on successful action, and a low tone on error.
* **Instant Barge-In:** Map a single-finger screen tap or Volume Up press to immediately call `textToSpeech.stop()` and open `SpeechRecognizer`, so users never wait for long readouts to finish.

### C. Cross-Origin `<iframe>` & Payment Gateway Handling (`VoiceBrowserActivity.java`)
* Upgrade prototype injection to use `WebViewCompat.addDocumentStartJavaScript` (`androidx.webkit:webkit`) so `Element.prototype.attachShadow` overrides apply reliably across frames.
* When a cross-origin payment `<iframe>` (e.g., Razorpay, BillDesk, Aadhaar e-KYC) blocks DOM access and `element.click()`, fall back to **Gemini Visual Coordinates + DPI-scaled `MotionEvent` hardware taps** to click straight through the iframe boundary.

### D. Hands-Free SMS OTP Auto-Read (`SmsOtpHelper.java`)
* Integrate Android's **SMS User Consent API** (`SmsRetriever.getClient(context).startSmsUserConsent(null)`)—which requires zero `READ_SMS` permissions—to detect incoming 4-to-8-digit OTPs, read the code aloud, and ask: *"OTP received. Should I enter it?"*

### E. Regional Indian Languages & Code-Mixed Voice (`VoiceEngineManager.java` & `UserPreferences.java`)
* Let users select their preferred language (`en-IN`, `hi-IN`, `kn-IN`, `ta-IN`, `mr-IN`) in `SettingsActivity`.
* Pass the selected locale to both `SpeechRecognizer` and Gemini's system prompt so code-mixed commands (Hinglish/Kanglish) are understood and spoken back in the user's native regional TTS voice.

### F. "Teach Once, Replay Forever" Local Macros (`MacroStore.java`)
* After a multi-step flow completes successfully, offer to save the sequence of DOM selectors/labels as a named local macro (e.g., *"Check Electricity Bill"*).
* On future runs, replay steps locally using instant DOM clicks, calling Gemini **only** if an expected element is missing or the layout changed.

---

## 4. Security, Privacy & Hardware Confirmation Guardrails
- **Encrypted Storage:** Both API credentials (`AssistantSettings.java`) and user profile preferences (`UserPreferences.java`) use `EncryptedSharedPreferences` backed by the Android Keystore System.
- **Persistent Conversation Memory (`ConversationMemory.java` & `AgentSession.java`):** Stores bounded cross-session context (saved consumer IDs, preferred language) locally while tracking current-page steps in memory to prevent infinite action loops.
- **Mandatory Hardware Confirmation (`ActionSafetyGuard.java` & `VoiceBrowserActivity.java`):** High-stakes actions (`pay`, `submit`, `confirm`, `transfer`, `delete`, `proceed`, `checkout`, `otp`) **never execute via spoken `"Yes"`/`"Haan"`**. They require a **physical Volume Up double-press** (within `1,200ms`) and expire automatically after **30 seconds**. Pressing Volume Down or saying `"Cancel"`/`"Ruko"` aborts immediately.
- **Audio Focus Lock (`VoiceEngineManager.java`):** Requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` before opening `SpeechRecognizer` so background TV, YouTube, or speaker audio cannot inject commands.
- **Audio-First Permission Onboarding (`SettingsActivity.java`):** Guides visually impaired users step-by-step via TTS to grant `RECORD_AUDIO`, Accessibility Service, and Default Home Launcher permissions.

---

## 5. Automated Testing Strategy
To keep CI/CD deterministic, fast, and free of live microphone dependencies or Gemini API quota burn, automated tests are split across three layers:

### Layer 1: Fast JVM Unit Tests (`app/src/test/java/com/accessibility/voicecompanion/`)
Uses **JUnit 4 + Mockito + Robolectric** (`./gradlew testDebugUnitTest`):
- **`ActionSafetyGuardTest`:** Verifies high-risk keyword detection, rejection of spoken `"Yes"`/`"Haan"`, execution on two Volume Up presses within `1,200ms`, Volume Down cancellation, and automatic expiration after `30,001ms` via `ShadowSystemClock`.
- **`SharingConsentTest`:** Verifies unknown domains block cloud uploads until approved and sensitive input masking strips password/OTP values.
- **`AssistantClientFailoverTest`:** Uses OkHttp `MockWebServer` to verify automatic fallback from multimodal payload to text-only DOM payload on HTTP 503 or socket timeout.

### Layer 2: Instrumented WebView & Closed Shadow DOM Tests (`app/src/androidTest/`)
Uses **Espresso Web + MockWebServer** (`./gradlew connectedAndroidTest`):
- **`ShadowDomUnpackerTest`:** Serves a local hostile HTML page containing `element.attachShadow({ mode: 'closed' })` and `<input type="password">`. Asserts that `VoiceBrowserActivity` pierces the closed shadow root, redacts password values to `"[REDACTED]"`, and triggers DOM clicks accurately.

### Layer 3: End-to-End Voice Flow Verification
- Exposes a package-private test hook `simulateVoiceInput(String spokenText)` on `VoiceBrowserActivity` to bypass physical hardware microphone capture in CI and feed commands straight into `VoiceCommandCallback.onCommandRecognized()`.
- Verifies multi-step conversational flows (e.g., `"Pay bill"` → TTS hardware prompt → simulated double `KEYCODE_VOLUME_UP` → DOM click execution) against canned `MockWebServer` Gemini JSON responses.
