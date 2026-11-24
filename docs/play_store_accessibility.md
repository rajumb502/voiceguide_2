# Google Play Store Accessibility Declaration & Policy Compliance Artifact

**App Name:** Voice Companion  
**Package:** `com.accessibility.voicecompanion`  
**Target Category:** Accessibility / Tools for Users with Visual Disabilities (Blind & Low Vision)  
**Declared Capability:** `AccessibilityService` (`com.accessibility.voicecompanion.BackgroundVoiceService`)

---

## 1. Core Purpose & Prominent Disclosure

Voice Companion is an audio-first accessibility launcher and web/app assistant designed specifically for visually impaired users in India. The `AccessibilityService` is utilized strictly to provide access to interactive controls and system dialogues that TalkBack or screen readers cannot autonomously actuate or that require non-visual tactile confirmation.

### Prominent In-App Disclosure
Before enabling the service, users are informed via TTS and on-screen dialogs:
> *"Voice Companion uses the Accessibility Service exclusively to: (1) automatically tap 'Allow' on system SMS verification prompts on your behalf when you request an OTP, and (2) capture hardware Volume Up double-presses to safely authorize high-risk actions without sending passwords or financial details to AI services. Voice Companion never scrapes your screen in the background or collects personal data."*

---

## 2. Declared Bounded Capabilities & Justifications

### A. Auto-Approval of Google Play Services SMS User Consent Dialogs
* **Capability:** `canRetrieveWindowContent="true"`, `typeWindowStateChanged`, `typeWindowContentChanged`
* **Limitation / Scope:**
  - Active only within a strict, user-initiated, 60-second window (`awaitingSmsConsent == true`).
  - Restricted exclusively to package `com.google.android.gms`.
  - Targets only the positive confirmation control (`com.google.android.gms:id/positive_button` or text `"Allow"`).
  - Automatically resets to dormant immediately upon click, cancellation, or expiration.
* **Accessibility Justification:** Blind users using the official SMS User Consent API cannot easily locate or tap the visual modal bottom sheet presented by Google Play Services. Auto-tapping `"Allow"` on verified consent sheets allows hands-free, accessible OTP retrieval without requiring broad, sensitive `READ_SMS` permissions.

### B. Hardware Key Interception for Tactile Safety Gate (`ActionSafetyGuard`)
* **Capability:** `canRequestFilterKeyEvents="true"`, `flagRequestFilterKeyEvents`
* **Limitation / Scope:**
  - Filters strictly `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` with `repeatCount == 0`.
  - Preserves TalkBack's accessibility shortcut (3-second simultaneous volume hold).
  - Active only when high-stakes actions (`pay`, `submit`, `confirm`, `transfer`, `delete`, `enter otp`) are queued for confirmation.
* **Accessibility Justification:** Users with visual disabilities are vulnerable to voice spoofing, TV/radio background speech, or AI hallucination during financial transactions. To ensure absolute user intent, high-stakes actions require a tactile physical hardware double-press (Volume Up twice within 1,200 ms). Spoken confirmation is strictly rejected for sensitive operations.

---

## 3. Strict Privacy Guarantees & Anti-Scraping Defenses

1. **Zero Background Scraping:** `BackgroundVoiceService` executes no continuous polling. Any event outside active user-initiated flows or outside an explicitly allowed package is dropped immediately.
2. **Default Denylist:** Encrypted messengers (Signal, WhatsApp) and Indian banking/UPI apps (SBI YONO, HDFC, PhonePe, Google Pay, BHIM, Paytm, PayZapp) are permanently blocked from automated accessibility capture.
3. **Sensitive Field Redaction:** Passwords, PINs, MPINs, CVVs, Aadhaar numbers, PAN cards, and OTPs are redacted (`"[REDACTED]"`) locally on-device before any DOM or accessibility node information is processed.
4. **Keystore-Backed Consent:** Cloud AI sharing requires explicit per-domain and per-app user opt-in, stored securely using Android Keystore AES-256 encryption.
5. **Respect for `FLAG_SECURE`:** Windows with `WindowManager.LayoutParams.FLAG_SECURE` are never bypassed.

---

## 4. Play Console Form Declaration Answers

| Field | Declaration Response |
| :--- | :--- |
| **Is your app designed for people with disabilities?** | Yes. |
| **Primary user group:** | Blind or visually impaired individuals. |
| **Why does your app need the Accessibility API?** | To allow visually impaired users to approve system SMS consent dialogs and use physical volume keys for tactile confirmation of critical actions. |
| **Does your app collect or share sensitive personal data?** | No. Node inspection is strictly local, bounded to user consent, and sensitive fields (passwords, PINs, banking info) are masked or blocked. |
