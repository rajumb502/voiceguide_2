### P0 — Critical Real-World Blockers
- [x] **Task 5.1: Activate `AccessibilityService` to Auto-Approve SMS User Consent Dialogs (`BackgroundVoiceService.java` & `SmsOtpHelper.java`)**
  - Enable `canRetrieveWindowContent="true"` in `res/xml/accessibility_service_config.xml`.
  - Set a short-lived `awaitingSmsConsent` flag in `SmsOtpHelper` when listening for an OTP.
  - In `BackgroundVoiceService.onAccessibilityEvent()`, detect `TYPE_WINDOW_STATE_CHANGED` from `com.google.android.gms` containing the SMS consent prompt, locate the `"Allow"` node (`com.google.android.gms:id/positive_button` or text `"Allow"`), and click it automatically.
  - Maintain mandatory `ActionSafetyGuard` hardware Volume Up double-press before entering OTP.
  - *Status:* **Completed.** `accessibility_service_config.xml` updated with window state/content events; `SmsOtpHelper` tracks `isAwaitingSmsConsent()` with 5-minute timeout; `BackgroundVoiceService` inspects active window, locates `positive_button` / `"Allow"` node and auto-clicks. Verified in `BackgroundVoiceServiceTest.java`.
- [ ] **Task 5.2: Resolve Payment `<iframe>` Screenshot Suppression Conflict (`VoiceBrowserActivity.java` & `page.js`)**

## P0 / P1 — Post-Muse Industry Hardening (Prevent Cross-App Leaks & OS Permission Blocks)
*Context: Following recent industry investigations into AI agents (e.g., Meta Muse) accidentally scraping encrypted messengers via broad Accessibility/Notification permissions and executing unauthorized transactions, Apple and Google are tightening OS-level AI agent guardrails. Add these defensive boundaries to stay Play-Store compliant and user-safe.*

- [ ] **11. Enforce Sensitive App & Package Denylist (`BackgroundVoiceService.java` & `SharingConsent.java`)**
  - **Why:** Encrypted messengers (Signal, WhatsApp) and Indian banking/UPI apps (SBI YONO, HDFC, PhonePe, Google Pay) must never be scraped or screenshotted by default via `AccessibilityService` or `NotificationListener`.
  - **Action:**
    - Maintain a default-blocked package denylist (`org.thoughtcrime.securesms`, `com.whatsapp`, `com.phonepe.app`, `com.google.android.apps.nbu.paisa.user`, `net.one97.paytm`, `com.sbi.lotusintouch`, etc.) that immediately aborts `onAccessibilityEvent()` inspection and screenshot capture.
    - Respect `WindowManager.LayoutParams.FLAG_SECURE` signals by never attempting workarounds on flagged windows.

- [ ] **12. Bound `AccessibilityService` Window Inspection to Short User-Initiated Windows (`BackgroundVoiceService.java`)**
  - **Why:** Always-on background window polling across third-party apps triggers Google Play Accessibility policy rejections and OS-level permission revocation.
  - **Action:**
    - Restrict active window-node inspection strictly to time-bounded, user-initiated states (e.g., `awaitingSmsConsent == true` with an automatic `60,000ms` expiry timer, targeting only `com.google.android.gms`).
    - Automatically reset `awaitingSmsConsent = false` as soon as the `"Allow"` button is clicked, the user cancels, or 60 seconds elapse.

- [ ] **13. Guard Against Cross-Session Context Bleed & Hallucinated Balances (`ConversationMemory.java` & `AssistantClient.java`)**
  - **Why:** Prevent financial figures, biller IDs, or personal facts from one site/session from leaking into unrelated tasks or being hallucinated into responses.
  - **Action:**
    - Add a unit test in `ConversationMemoryTest` verifying zero cross-hostname context injection (e.g., a BESCOM bill amount saved on `bescom.co.in` is never injected into prompts on `irctc.co.in` or `wikipedia.org`).
    - Reinforce the `AssistantClient` system prompt rule: *"Never state account balances, bill amounts, or booking confirmations unless explicitly present in the current page's live DOM snapshot."*

- [ ] **14. Prepare Google Play Accessibility Declaration Artifacts (`docs/play_store_accessibility.md`)**
  - **Why:** Google Play requires strict proof that `AccessibilityService` is used solely to assist users with disabilities—not for covert data collection.
  - **Action:**
    - Document the exact bounded scope (GMS SMS User Consent auto-approval + hardware Volume Up confirmation), per-domain explicit spoken consent, and hardware double-press financial gating for Play Console review.
