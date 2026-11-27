### Phase 5: Post-Implementation Audit & Pre-Pilot Hardening
*Goal: Address real-world hardware edge cases, Accessibility Service upgrades, and pilot readiness.*

#### P0 — Critical Real-World Blockers
- [x] **Task 5.1: Activate `AccessibilityService` to Auto-Approve SMS User Consent Dialogs (`BackgroundVoiceService.java` & `SmsOtpHelper.java`)**
  - Enable `canRetrieveWindowContent="true"` in `res/xml/accessibility_service_config.xml`.
  - Set a short-lived `awaitingSmsConsent` flag in `SmsOtpHelper` when listening for an OTP.
  - In `BackgroundVoiceService.onAccessibilityEvent()`, detect `TYPE_WINDOW_STATE_CHANGED` from `com.google.android.gms` containing the SMS consent prompt, locate the `"Allow"` node (`com.google.android.gms:id/positive_button` or text `"Allow"`), and click it automatically.
  - Maintain mandatory `ActionSafetyGuard` hardware Volume Up double-press before entering OTP.
  - *Status:* **Completed.** `accessibility_service_config.xml` updated with window state/content events; `SmsOtpHelper` tracks `isAwaitingSmsConsent()` with bounded timeout; `BackgroundVoiceService` inspects active window, locates `positive_button` / `"Allow"` node and auto-clicks. Verified in `BackgroundVoiceServiceTest.java`.
- [x] **Task 5.2: Resolve Payment `<iframe>` Screenshot Suppression Conflict (`VoiceBrowserActivity.java` & `page.js`)**
  - Allow consented screenshots when a tagged payment `<iframe>` is present on page while keeping main-document passwords and raw card inputs redacted/suppressed.
  - Ensure `window.scrollY` / `window.scrollX` stability between snapshot capture and `dispatchCoordinateTap()`.
  - *Status:* **Completed.** `page.js` tags `payment_frame` and keeps `sensitive` false for iframes unless actual password/PIN fields exist in main document; `AssistantClient.java` retains `payment_frame` iframe nodes in clean snapshot and sends screenshots for visual coordinate detection; `VoiceBrowserActivity.java` tracks scroll deltas to prevent tap misalignments across zoom/soft-keyboard popups. Verified in `AssistantClientTest.java`.
- [x] **Task 5.3: Migrate Global Volume Key Interception to `AccessibilityService.onKeyEvent()` (`BackgroundVoiceService.java` & `res/xml/accessibility_service_config.xml`)**
  - Add `android:canRequestFilterKeyEvents="true"` to `accessibility_service_config.xml` and request key filtering flag.
  - Route volume key events via `BackgroundVoiceService` to `ActionSafetyGuard` so hardware confirmation works even if an external system dialog or payment bottom-sheet overlays the activity.
  - Filter `repeatCount == 0` to preserve TalkBack's dual-volume 3-second hold shortcut.
  - *Status:* **Completed.** Added `canRequestFilterKeyEvents="true"` and `flagRequestFilterKeyEvents` to `accessibility_service_config.xml`. Configured `FLAG_REQUEST_FILTER_KEY_EVENTS` dynamically in `BackgroundVoiceService.onServiceConnected()`. Implemented `onKeyEvent()` global delegation via `KeyEventListener` routing to `VoiceBrowserActivity.dispatchKeyEvent()` with `repeatCount == 0` filtering and volume-stream suppression. Verified in `BackgroundVoiceServiceTest.java`.

#### P1 — High-Priority Indian UX & Hardware Reality Fixes
- [x] **Task 5.4: Guide Users Past Android 13+ "Restricted Settings" During Onboarding (`SettingsActivity.java`)**
  - Detect when accessibility service activation is restricted on Android 13+ sideloaded builds.
  - Speak step-by-step instructions and launch `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` to direct users to "Allow restricted settings".
  - *Status:* **Completed.** Detects Android 13+ (`Build.VERSION.SDK_INT >= 33`) with failed initial accessibility activation in `advanceSetup()`; speaks step-by-step guidance directing user to App Info top-right menu; automatically launches `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` targeting package URI. Verified with `SettingsActivityTest.java`.
- [x] **Task 5.5: Harden React / Angular Synthetic Input Dispatching (`page.js`)**
  - Ensure native prototype property descriptors (`HTMLInputElement.prototype.value.set`) and bubbling `input`, `change`, and `blur` events are dispatched for input fields.
  - *Status:* **Completed.** Centralized input dispatching in `dispatchNativeInput()` using `Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, text)` across `TYPE`, `enterOtp()`, and `executeMacroStep()`. Dispatches full bubbling synthetic event cascade (`input`, `change`, and `blur`) ensuring full compatibility with React/Angular reactive change detection. Verified in `tests/agent-page.test.js`.
- [x] **Task 5.6: Expand Sensitive Field Redaction Regex (`page.js`)**
  - Expand redaction regex to cover Indian mobile patterns: `otp`, `pin`, `mpin`, `cvv`, `aadhaar`, `pan`, `password`, `secret` across `id`, `name`, `placeholder`, `aria-label`, and associated `<label>`.
  - *Status:* **Completed.** Expanded redaction regex in `page.js` and `AssistantClient.java` to encompass Indian portal patterns (`mpin`, `aadhaar`, `pan`, `secret`, `otp`, `pin`, `cvv`) across `id`, `name`, `placeholder`, `aria-label`, and `<label>`. Verified in `tests/agent-page.test.js` and `AssistantClientTest.java`.
- [x] **Task 5.7: Add Semantic Anchor Fallbacks to Local Macros (`MacroStore.java` & `page.js`)**
  - Store and match accessible labels, aria-labels, and input placeholders alongside CSS selectors in `MacroStore` and `page.js` during replay.
  - *Status:* **Completed.** `MacroStore.Step` gains an `ariaLabel` field (11-arg constructor; 10-arg compat overload keeps all existing call sites unchanged). `toJson`/`fromJson` persist `aria-label`. `VoiceBrowserActivity` records `aria-label` from the live DOM snapshot node. `page.js` snapshot emits `aria-label` per node; `findTarget()` checks aria-label before label/name/placeholder. Verified in `MacroStoreTest.testAriaLabelPersistedAndRoundTripped` and `page.test.js` aria-label fallback assertions.

#### P2 — Documentation, Cleanup & Physical Device Validation
- [x] **Task 5.8: Sync `README.md` with Completed Phases 1–4 (`tasks.md`)**
  - Document Volume Up double-press hardware confirmation, Audio Landmark Briefs, SMS User Consent auto-read, Encrypted Local Macros, and AccessibilityService integration.
  - *Status:* **Completed.** `README.md` rewritten to cover Audio Landmark Briefs, Volume Up double-press safety gate, SMS User Consent auto-read, Encrypted Local Macros (save/replay/delete voice commands), AccessibilityService anti-scraping guardrails, banking handoffs, `FLAG_SECURE` handling, and cross-app denylist. Build and verification section updated.
- [x] **Task 5.9: Archive Unused Prototype Files (`backend/server.py`)**
  - Move legacy `backend/server.py` to `prototypes/` or document its archival.
  - *Status:* **Completed.** `backend/server.py` moved to `prototypes/server.py` via `git mv`; file header updated with archival notice; `tests/test_backend.py` import updated to `prototypes.server`; `backend/` directory removed. All 7 Python tests still pass.
- [x] **Task 5.10: Document Physical Device Verification Suite Checklist (With TalkBack Enabled)**
  - Document step-by-step manual test protocols for TalkBack coexistence, Wikipedia landmark brief, BESCOM React billing portal, and GMS SMS sheet auto-click.
  - *Status:* **Completed.** `docs/physical_device_checklist.md` created with 5 sections: (1) TalkBack coexistence & Android 13+ Restricted Settings onboarding, (2) Wikipedia Audio Landmark Brief & basic navigation, (3) BESCOM React portal synthetic input / payment iframe / safety gate / macros, (4) GMS SMS User Consent auto-click & OTP entry, (5) cross-app privacy / denylist / banking handoff checks. Each section lists expected outcomes for pass/fail.

---

## P0 / P1 — Post-Muse Industry Hardening (Prevent Cross-App Leaks & OS Permission Blocks)
*Context: Following recent industry investigations into AI agents (e.g., Meta Muse) accidentally scraping encrypted messengers via broad Accessibility/Notification permissions and executing unauthorized transactions, Apple and Google are tightening OS-level AI agent guardrails. Add these defensive boundaries to stay Play-Store compliant and user-safe.*

- [x] **11. Enforce Sensitive App & Package Denylist (`BackgroundVoiceService.java` & `SharingConsent.java`)**
  - **Why:** Encrypted messengers (Signal, WhatsApp) and Indian banking/UPI apps (SBI YONO, HDFC, PhonePe, Google Pay) must never be scraped or screenshotted by default via `AccessibilityService` or `NotificationListener`.
  - **Action:**
    - Maintain a default-blocked package denylist (`org.thoughtcrime.securesms`, `com.whatsapp`, `com.phonepe.app`, `com.google.android.apps.nbu.paisa.user`, `net.one97.paytm`, `com.sbi.lotusintouch`, etc.) that immediately aborts `onAccessibilityEvent()` inspection and screenshot capture.
    - Respect `WindowManager.LayoutParams.FLAG_SECURE` signals by never attempting workarounds on flagged windows.
  - **Status:** **Completed.** Defined `SharingConsent.BLOCKED_PACKAGES` denylist and `isPackageBlocked(pkg)`. Enforced immediate event abort in `BackgroundVoiceService.onAccessibilityEvent()` when any denylisted package is received. Verified in `BackgroundVoiceServiceTest.java`.

- [x] **12. Bound `AccessibilityService` Window Inspection to Short User-Initiated Windows (`BackgroundVoiceService.java` & `SmsOtpHelper.java`)**
  - **Why:** Always-on background window polling across third-party apps triggers Google Play Accessibility policy rejections and OS-level permission revocation.
  - **Action:**
    - Restrict active window-node inspection strictly to time-bounded, user-initiated states (e.g., `awaitingSmsConsent == true` with an automatic `60,000ms` expiry timer, targeting only `com.google.android.gms`).
    - Automatically reset `awaitingSmsConsent = false` as soon as the `"Allow"` button is clicked, the user cancels, or 60 seconds elapse.
  - **Status:** **Completed.** Bounded `awaitingSmsConsent` window to 60,000ms (`CONSENT_TIMEOUT_MS = 60 * 1000L`). Auto-resets upon click in `handleSmsConsentDialog()`, on `stopListening()`, or upon 60s timeout expiration. Verified in `BackgroundVoiceServiceTest.java`.

- [x] **13. Guard Against Cross-Session Context Bleed & Hallucinated Balances (`ConversationMemory.java` & `AssistantClient.java`)**
  - **Why:** Prevent financial figures, biller IDs, or personal facts from one site/session from leaking into unrelated tasks or being hallucinated into responses.
  - **Action:**
    - Add a unit test in `ConversationMemoryTest` verifying zero cross-hostname context injection (e.g., a BESCOM bill amount saved on `bescom.co.in` is never injected into prompts on `irctc.co.in` or `wikipedia.org`).
    - Reinforce the `AssistantClient` system prompt rule: *"Never state account balances, bill amounts, or booking confirmations unless explicitly present in the current page's live DOM snapshot."*
  - **Status:** **Completed.** Reinforced `AssistantClient.INSTRUCTIONS` to strictly prohibit stating balances, bill amounts, or booking confirmations unless present in live DOM snapshot. Verified zero cross-site memory leakage in `ConversationMemoryTest.java` and `HostChecksTest`.

- [x] **14. Prepare Google Play Accessibility Declaration Artifacts (`docs/play_store_accessibility.md`)**
  - **Why:** Google Play requires strict proof that `AccessibilityService` is used solely to assist users with disabilities—not for covert data collection.
  - **Action:**
    - Document the exact bounded scope (GMS SMS User Consent auto-approval + hardware Volume Up confirmation), per-domain explicit spoken consent, and hardware double-press financial gating for Play Console review.
  - **Status:** **Completed.** Formulated comprehensive Google Play Store declaration artifact in `docs/play_store_accessibility.md` covering prominent in-app disclosures, bounded SMS consent dialog automation, ActionSafetyGuard hardware key filtering, anti-scraping guarantees, and Play Console review questions.

---

## Phase 6: Controlled Local App Interfacing & Accessibility Driver (P2 — Cross-App Assistance)
*Goal: Allow visually impaired users to summarize and navigate explicitly approved Android apps (e.g., WhatsApp, Contacts, Settings) on demand, even with system notifications disabled—while enforcing strict opt-in consent, zero background scraping, and safe handoffs on banking/UPI screens.*

- [x] **Task 6.1: Per-App Allowlist Storage & Consent UI (`SharingConsent.java` & `SettingsActivity.java`)**
  - Extend `SharingConsent` (backed by `KeystoreCryptoHelper`) with per-package approval methods (`isPackageAllowed(String pkg)`, `setPackageAllowed(String pkg, boolean allowed)`, `getAllowedPackages()`), isolated per LLM provider (`Gemini` vs `OpenAI`).
  - Default every installed package to **Denied (`false`)**; exclude strict Indian banking/UPI packages (`com.phonepe.app`, `com.google.android.apps.nbu.paisa.user`, `net.one97.paytm`, `in.org.npci.upiapp`, `com.sbi.lotusintouch`) from automatic suggestion with a spoken security warning.
  - Add an **"Allowed Apps" Consent Screen** in `SettingsActivity` (accessible via both TalkBack UI toggles and voice commands like *"Allow WhatsApp"* / *"Revoke WhatsApp"*), plus an in-flow spoken prompt if the user asks to read an unapproved app (*"Allow sharing screen details from WhatsApp with [Provider]? Say Yes or No"*).
  - *Status:* **Completed.** `SharingConsent` updated with provider-isolated package allowlists, default-denied behavior, and permanent banking exclusion. `AssistantSettings` and `SettingsActivity` expose TalkBack UI input and voice command toggles (*"Allow WhatsApp"*, *"Revoke WhatsApp"*). Verified in `SharingConsentUnitTest.java`.

- [x] **Task 6.2: Activate Event-Driven `BackgroundVoiceService` & Global Volume Keys (`BackgroundVoiceService.java` & `res/xml/accessibility_service_config.xml`)**
  - Enable `android:canRetrieveWindowContent="true"` and `android:canRequestFilterKeyEvents="true"` in the Accessibility Service XML config, and set `AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS` at runtime.
  - Enforce **Zero Background Scraping**: `BackgroundVoiceService` must immediately return and ignore `onAccessibilityEvent()` whenever `isUserTriggeredSessionActive` is `false` or `!SharingConsent.isPackageAllowed(packageName)` (except during bounded `60,000ms` SMS consent approval).
  - Route global `onKeyEvent()` Volume Up/Down presses through `ActionSafetyGuard` (`1,200ms` double-press confirmation, `repeatCount == 0` guard, preserving the 3-second dual-volume TalkBack shortcut) so confirmations work even when external apps or system dialogs are in the foreground.
  - Update `SettingsActivity.guideSetup()` to detect Android 13+ **"Restricted Settings"** locks on sideloaded APKs and speak step-by-step instructions to enable *"Allow restricted settings"* in App Info.
  - *Status:* **Completed.** `BackgroundVoiceService` enforces `isUserTriggeredSessionActive` gate and package approval check on every accessibility event. Volume keys routed via `KeyEventListener` with repeat filtering. Verified in `BackgroundVoiceServiceTest.java`.

- [x] **Task 6.3: Auto-Approve SMS User Consent Bottom Sheet (`BackgroundVoiceService.java` & `SmsOtpHelper.java`)**
  - Add a bounded `awaitingSmsConsent` window (`60,000ms` auto-expiry) in `SmsOtpHelper` when `startSmsUserConsent()` is active.
  - When `BackgroundVoiceService` receives `TYPE_WINDOW_STATE_CHANGED` from `com.google.android.gms` matching the SMS consent prompt, locate the `"Allow"` node (`com.google.android.gms:id/positive_button` or text `"Allow"`) and execute `performAction(AccessibilityNodeInfo.ACTION_CLICK)` so blind users never have to hunt for the visual bottom-sheet button.
  - Keep `ActionSafetyGuard` **Volume Up double-press** mandatory before speaking/entering the retrieved OTP.
  - *Status:* **Completed.** Bounded 60s window auto-clicks GMS positive consent button, auto-expires on timeout, and requires hardware Volume Up double-press prior to OTP entry. Verified in `BackgroundVoiceServiceTest.java` and `IndianAccessibilityTest.java`.

- [x] **Task 6.4: On-Demand Native App UI Tree Driver (`NativeAppDriver.java` & `AgentSession.java`)**
  - Implement `NativeAppDriver` plugged into the existing decoupled `AgentSession` loop, mirroring `page.js` capabilities for native Android apps:
    - **Snapshot:** Traverse `getRootInActiveWindow()` (`AccessibilityNodeInfo`) up to 150 visible nodes, extracting `text`, `contentDescription`, `viewIdResourceName`, `className`, `isClickable`, `isEditable`, `isScrollable`, and `boundsInScreen`.
    - **Sensitive Field Masking:** Mask `node.isPassword()` and any text/ID matching `/otp|pin|mpin|cvv|aadhaar|pan|password|secret/i` as `"[REDACTED]"`.
    - **Actions:** Support `CLICK` (`ACTION_CLICK` walking up clickable parents, with `dispatchGesture()` coordinate fallback via `boundsInScreen`), `TYPE` (`ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`), `SCROLL` (`ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD`), and `BACK` (`performGlobalAction(GLOBAL_ACTION_BACK)`).
  - Gate high-stakes native UI actions (`Send`, `Pay`, `Transfer`, `Delete`, `Call`) behind `ActionSafetyGuard` Volume Up double-press.
  - *Status:* **Completed.** `NativeAppDriver.java` implements node extraction (up to 150 nodes), sensitive field redaction, `CLICK` with gesture coordinate fallback, `TYPE` via `ACTION_SET_TEXT`, `SCROLL`, and `BACK`. High-stakes action gating integrated with `ActionSafetyGuard`. Verified in `NativeAppDriverTest.java`.

- [x] **Task 6.5: Android Share-Sheet Receiver & Safe Banking / UPI Handoffs (`VoiceBrowserActivity.java` & `NativeAppDriver.java`)**
  - Register an `ACTION_SEND` (`text/plain`) intent filter so users can also share exported WhatsApp `.txt` chats or articles directly to Voice Companion for zero-accessibility summarization.
  - **Banking / `FLAG_SECURE` Handoff:** Detect empty/blocked node trees or all-black bitmaps (`FLAG_SECURE`), drop image uploads automatically, speak *"This screen restricts screen reading for security"*, and pause AI automation at NPCI UPI PIN screens.
  - Add a **"Pause assistant for banking"** voice command that calls `BackgroundVoiceService.disableSelf()` (API 24+) so strict bank RASP checks (e.g., SBI YONO) do not block the user at launch.
  - *Status:* **Completed.** `AndroidManifest.xml` registered with `ACTION_SEND` (`text/plain`), handled via `handleSharedText()`. Banking and `FLAG_SECURE` detection incorporated into `NativeAppDriver`. Added *"Pause assistant for banking"* voice command calling `BackgroundVoiceService.disableSelf()`. Verified in unit tests.

- [x] **Task 6.6: Phase 5 Verification Tests (`NativeAppDriverTest.java` & `BackgroundVoiceServiceTest.java`)**
  - Unit-test `SharingConsent` package allowlist defaults (all `false`), provider isolation, and banking package warnings.
  - Verify `BackgroundVoiceService` drops `AccessibilityEvent`s when `isUserTriggeredSessionActive == false` or when an unapproved package is active, and expires `awaitingSmsConsent` after `60,001ms`.
  - Verify `NativeAppDriver` serializes mock `AccessibilityNodeInfo` trees (including WhatsApp-style message lists), redacts `isPassword()`/OTP nodes, and triggers `ActionSafetyGuard` before clicking a `"Send"` button.
  - *Status:* **Completed.** Added `NativeAppDriverTest.java`, updated `BackgroundVoiceServiceTest.java`, and expanded `SharingConsentUnitTest.java`. All JVM unit tests pass deterministically.

---

## Phase 7: Voice-First Settings & Utility Upgrades (P1 — Hands-Free Voice Control)
*Goal: Allow visually impaired users to set, unset, and inspect all settings and preferences completely hands-free via voice, while borrowing high-utility patterns from `vg_flutter` (TTS speech rate control, facts/profile memory, system deep links, and session resets).*

- [x] **Task 7.1: Voice-Controlled Speech Rate & Audio Earcon Toggles (`VoiceEngineManager.java`, `AssistantSettings.java`, & `VoiceBrowserActivity.java`)**
  - Add voice commands to adjust TTS speech rate: "speak faster", "speak slower", "set speech speed to 1.5", "normal speech speed" (mapping to `tts.setSpeechRate(...)` within 0.5x–3.0x range).
  - Add voice commands to toggle earcons/tones: "turn off tones", "turn on audio tones" (`settings.saveAudioTones(...)`).
  - Persist speech rate and audio preferences in `AssistantSettings` so they are restored across app launches.
  - *Status:* **Completed.** `VoiceEngineManager` gained `setSpeechRate(float)`/`getSpeechRate()` clamped to 0.5×–3.0×, applied to TTS on `initialize()` and whenever set. `AssistantSettings` persists `speech_rate` as a float (migrated cleanly). Both `VoiceBrowserActivity.preferenceCommand()` and `SettingsActivity.updateSettingByVoice()` handle *"speak faster"*, *"speak slower"*, *"normal speech"*, and *"set speech rate to N"*. Speech rate and tones are both reloaded from settings on `onCreate`/`onResume`. Verified with new `speechRateDefaultsToOneAndClampsToRange` test in `VoiceEngineManagerTest`; all 20 unit tests pass.

- [x] **Task 7.2: Voice-Controlled Privacy & AI Feature Toggles (`AssistantSettings.java`, `VoiceBrowserActivity.java`, & `VoiceCommands.java`)**
  - Enable toggling all boolean settings by voice without requiring visual navigation to `SettingsActivity`:
    - "Enable/disable page sharing" (`settings.saveSharingEnabled()`)
    - "Enable/disable screenshots" (`settings.saveScreenshotsEnabled()`)
    - "Enable/disable remembering preferences" (`settings.saveRememberPreferences()`)
  - Add voice query to inspect active settings: "Read my settings" or "What are my settings?" to speak the state of toggles, provider, and speech rate.
  - *Status:* **Completed.** `AssistantSettings.summary()` provides the full spoken summary (provider, model, language, speech speed, page sharing, screenshots, remember preferences, audio tones). Both `VoiceBrowserActivity.preferenceCommand()` and `SettingsActivity.updateSettingByVoice()` toggle `sharingEnabled`, `screenshotsEnabled`, `rememberPreferences`, and `audioTonesEnabled` and respond to *"read my settings"* / *"what are my settings"* (without intercepting *"open my settings"*). Verified in `SettingsActivityTest.java` and `Phase7VoiceTest.java`.

- [x] **Task 7.3: Form Auto-Fill Facts & Profile Preferences ("Remember my Consumer ID") (`UserPreferences.java` & `AssistantClient.java`)**
  - Extend `UserPreferences` to capture and store key-value user facts (e.g., "remember my consumer ID is 12345", "remember my voter ID is ABC...", "remember my email is user@example.com").
  - Encrypt facts locally via `KeystoreCryptoHelper` and inject into `user_preferences` system prompt.
  - Instruct `AssistantClient` to utilize remembered user facts when filling forms on matching portals without reprompting the user.
  - Add voice commands to inspect or clear stored facts: "What facts do you remember?", "Forget my facts".
  - *Status:* **Completed.** Added `UserPreferences.extractFact()`, `loadFacts()`, `saveFacts()`, `putFact()`, `clearFacts()`, and `formatFacts()` backed by `KeystoreCryptoHelper` AES-GCM encryption (rejecting sensitive keys like password/otp/cvv/pin). Updated `AssistantClient.INSTRUCTIONS` to fill matching form fields using saved profile facts without reprompting. `VoiceBrowserActivity` injects saved facts into `user_preferences` when `rememberPreferences()` is enabled and handles *"what facts do you remember"* and *"forget my facts"*. Verified in `tests/UserPreferencesTest.java` and `Phase7VoiceTest.java`.

- [x] **Task 7.4: Voice Session Reset / "Start Fresh" (`VoiceCommands.java` & `VoiceBrowserActivity.java`)**
  - Add voice aliases ("start fresh", "new session", "reset assistant") to `VoiceCommands.java` alongside "clear conversation".
  - Wipes short-term page task history (`agent.stop()`), clears `ConversationMemory`, clears pending states, and speaks verbal confirmation: *"Started fresh. What would you like to do?"*
  - *Status:* **Completed.** Added `VoiceCommands.isSessionReset()` matching *"start fresh"*, *"new session"*, *"reset assistant"*, *"start a new session"*, *"start over"*, *"clear conversation"*, and *"forget conversation"*, plus `AgentSession.reset()`. `VoiceBrowserActivity.localCommand()` cancels pending states, resets `AgentSession`, clears `currentRunSteps` and `ConversationMemory`, and speaks *"Started fresh. What would you like to do?"*. Verified in `tests/VoiceCommandsTest.java` and `Phase7VoiceTest.java`.

- [x] **Task 7.5: Native Intent Deep-Link Handling (`VoiceCommands.java` & `VoiceBrowserActivity.java`)**
  - Expand `VoiceCommands` to detect dialer, map, and mail intent schemes (`tel:`, `geo:`, `mailto:`, e.g., "call 9876543210", "navigate to Bangalore City Railway Station").
  - Launch standard Android intents (`Intent.ACTION_DIAL`, `Intent.ACTION_VIEW`) directly rather than sending them to the LLM browser loop.
  - Gate outgoing phone calls or direct communications behind `ActionSafetyGuard` confirmation if they trigger immediate dialing/action.
  - *Status:* **Completed.** Added `VoiceCommands.deepLink()` parsing `tel:`, `geo:`, and `mailto:` URIs as well as spoken call, navigation, and email phrases (deferring web hostnames to `navigationUrl()`). `VoiceBrowserActivity` launches `Intent.ACTION_VIEW` directly for `geo:` maps and gates `tel:` (`Intent.ACTION_DIAL`) and `mailto:` (`Intent.ACTION_VIEW`) behind `ActionSafetyGuard` Volume Up double-press confirmation. Verified in `tests/VoiceCommandsTest.java` and `Phase7VoiceTest.java`.

- [x] **Task 7.6: Automated Unit & Integration Tests for Phase 7**
  - Unit-test voice setting parsing (speech rate adjustments, toggling sharing/screenshots/audio tones).
  - Unit-test fact extraction and Keystore encryption round-trip in `UserPreferences`.
  - Unit-test deep-link intent resolution in `VoiceCommands`.
  - *Status:* **Completed.** Expanded `SettingsActivityTest.java`, `tests/VoiceCommandsTest.java`, and `tests/UserPreferencesTest.java`, and added `Phase7VoiceTest.java` covering settings toggles/summary, encrypted facts round-trip, session reset, and `tel:`/`geo:`/`mailto:` deep-link intent resolution with hardware safety gating. All 78 unit tests across 14 suites pass via `./gradlew testDebugUnitTest`.
