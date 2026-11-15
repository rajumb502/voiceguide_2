# Codebase Audit & OpenDroid Upgrades
**Target Package:** `com.accessibility.voicecompanion`  
**Instructions for Antigravity Agent:** Audit the 13 existing Java files in `java/com/accessibility/voicecompanion/` against the checklist below, fix any threading/lifecycle bugs, and implement the OpenDroid upgrades.

---

## 1. Critical Fixes to Check in Existing Classes

### A. `VoiceBrowserActivity.java` & `AssistantClient.java`
* **Main-Thread Network & Bitmap Freeze:** Ensure `AssistantClient` never executes Gemini HTTP calls on the Android UI thread, and scale down the captured `WebView` `Bitmap` (e.g., max width `720px`, JPEG quality `70`) before base64 encoding to prevent `OutOfMemoryError` and slow 4G/5G uploads.
* **Coordinate Scaling Mismatch:** Verify that `(x, y)` coordinates returned by JavaScript (`getBoundingClientRect()`) are multiplied by `webView.getScale()` (device pixel density) before dispatching `MotionEvent` taps. Without density scaling, taps will miss targets on high-DPI Android screens.
* **Memory Leaks:** Ensure `VoiceEngineManager.destroy()` and `webView.destroy()` are called in `onDestroy()`.

### B. `BackgroundVoiceService.java` & `VoiceEngineManager.java`
* **Android 14+ Foreground Service Rules:** Ensure `BackgroundVoiceService` calls `startForeground()` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` (or `specialUse`) so the OS does not kill the voice listener in the background.
* **SpeechRecognizer Threading:** Android's `SpeechRecognizer` must strictly be created, started, and destroyed on the **Main Looper (`Handler(Looper.getMainLooper())`)**, or it will throw `RuntimeException` crashes.

### C. `ActionSafetyGuard.java`, `AgentPolicy.java` & `SharingConsent.java`
* **Consent Enforcement Before Screenshot Upload:** Before `AssistantClient` transmits any screen `Bitmap` or DOM text to Gemini, verify that `SharingConsent` is explicitly granted for the current domain and that password/OTP input fields (`input[type="password"]`) are masked out of the scraped DOM JSON.
* **Pending Action Timeout:** Add a 15-second expiration timer to `ActionSafetyGuard` so a stale `"Yes"` command later does not accidentally trigger an old payment tap.

---

## 2. Best Practices & Features to Borrow from OpenDroid

### A. LLM Failover & Offline Fallback (`AssistantClient.java`)
* **Why:** Blind users in India frequently face patchy mobile data or API quota limits.
* **Upgrade:** Refactor `AssistantClient` into a provider chain:
  1. **Primary:** Gemini Flash-Lite (multimodal vision + DOM).
  2. **Secondary Fallback:** Fast text-only LLM endpoint (e.g., Groq / Gemini text-only) using just the scraped DOM JSON when image upload times out.
  3. **Offline Mode:** On-device Android `TextToSpeech` + local `VoiceCommands` intent matching (scroll, go back, read page text, open app) so basic navigation works with zero internet.

### B. Encrypted Credential Storage (`AssistantSettings.java` & `UserPreferences.java`)
* **Why:** Hardcoding or storing the NGO's shared Gemini API key in plain `SharedPreferences` risks key theft if the APK is decompiled.
* **Upgrade:** Wrap `AssistantSettings` and `UserPreferences` using `EncryptedSharedPreferences` backed by the **Android Keystore System** (`androidx.security:security-crypto`).

### C. Persistent 4-Tier Memory (`ConversationMemory.java` & `AgentSession.java`)
* **Why:** Visually impaired users should not have to spell out long consumer IDs, Aadhaar/ration card numbers, or frequent utility URLs repeatedly.
* **Upgrade:** Upgrade `ConversationMemory` from in-memory session lists to a lightweight local **SQLite / Room database** storing:
  1. *Short-term:* Current page step history inside `AgentSession` (prevents infinite scroll/click loops).
  2. *Long-term:* Encrypted user profile snippets (saved biller IDs, preferred language `en-IN` / `hi-IN`) injected into Gemini's system prompt only when `SharingConsent` allows.

### D. Audio-First Permission Onboarding (`SettingsActivity.java`)
* **Why:** Enabling Accessibility Service, Microphone, and Default Launcher permissions in Android Settings is difficult without sight.
* **Upgrade:** Add an interactive, voice-guided setup wizard in `SettingsActivity` that checks missing permissions on launch, speaks clear step-by-step instructions, and opens the exact Android Settings intent (`Settings.ACTION_ACCESSIBILITY_SETTINGS`, `Settings.ACTION_HOME_SETTINGS`) automatically.
