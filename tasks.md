### P0 — Critical Real-World Blockers
- [x] **Task 5.1: Activate `AccessibilityService` to Auto-Approve SMS User Consent Dialogs (`BackgroundVoiceService.java` & `SmsOtpHelper.java`)**
  - Enable `canRetrieveWindowContent="true"` in `res/xml/accessibility_service_config.xml`.
  - Set a short-lived `awaitingSmsConsent` flag in `SmsOtpHelper` when listening for an OTP.
  - In `BackgroundVoiceService.onAccessibilityEvent()`, detect `TYPE_WINDOW_STATE_CHANGED` from `com.google.android.gms` containing the SMS consent prompt, locate the `"Allow"` node (`com.google.android.gms:id/positive_button` or text `"Allow"`), and click it automatically.
  - Maintain mandatory `ActionSafetyGuard` hardware Volume Up double-press before entering OTP.
  - *Status:* **Completed.** `accessibility_service_config.xml` updated with window state/content events; `SmsOtpHelper` tracks `isAwaitingSmsConsent()` with 5-minute timeout; `BackgroundVoiceService` inspects active window, locates `positive_button` / `"Allow"` node and auto-clicks. Verified in `BackgroundVoiceServiceTest.java`.
- [ ] **Task 5.2: Resolve Payment `<iframe>` Screenshot Suppression Conflict (`VoiceBrowserActivity.java` & `page.js`)**