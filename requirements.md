# Target System Blueprint: Voice-Driven Web Accessibility Agent
**Tech Stack:** Native Android (Java), Android WebView Component, Gemini Multimodal API

## 1. Core Architectural Engine
- **Host Layer:** Custom Android Launcher UI catching all target HTTP/HTTPS navigation intents to bypass external mobile browsers.
- **Background Layer:** High-Privilege Android Accessibility Service mapping native system window focus tracking loops.
- **Web Parsing Layer:** Android WebView running an intentional Chromium prototype injection intercepting `Element.prototype.attachShadow` at `onPageStarted`. This mutates `{ mode: 'closed' }` declarations to `{ mode: 'open' }` dynamically before internal layout trees finish compiling.

## 2. Dynamic Cognitive Loop
1. User supplies natural verbal commands (e.g., in conversational Hinglish or local phrasing).
2. The custom container triggers a lower-level window screenshot layout capture (`Bitmap`) and queries the newly exposed text-node mapping matrix via a native JavaScript interface bridge.
3. The collected data is piped directly to the Gemini Multimodal API via a secure backend endpoint using context caching profiles.
4. Gemini evaluates the intent against the combined visual framework, returning targeted action coordinate maps (X, Y) and spoken feedback strings.

```
{
  "target_found": true,
  "requires_confirmation": true,
  "action_summary": "submit your electricity bill payment of 1,450 rupees",
  "inferred_label": "Pay Now Button",
  "action": "CLICK",
  "target_coordinates": {
    "x": 512.4,
    "y": 940.5
  },
  "spoken_confirmation": "Found the payment button."
}
```
5. The container translates the response layout parameters into hardware emulated touch actions via `MotionEvent` injection.

## 3. Reference Implementations

### Android Manifest Gateway Configuration (`AndroidManifest.xml`)
```xml
<manifest xmlns:android="http://android.com" package="com.accessibility.voicecompanion">
    <application android:theme="@style/Theme.AppCompat.NoActionBar">
        <!-- Home Launcher Hook -->
        <activity android:name=".LauncherActivity" android:launchMode="singleInstance" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
        <!-- Intent Browser Hijack -->
        <activity android:name=".VoiceBrowserActivity" android:exported="true" android:launchMode="singleTop">
            <intent-filter android:priority="999">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" /><data android:scheme="https" />
            </intent-filter>
        </activity>
        <!-- Background Monitoring Service -->
        <service android:name=".BackgroundVoiceService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true">
            <intent-filter><action android:name="android.view.accessibility.AccessibilityService" /></intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

### Unpacker & Scraper Lifecycle Hook (`VoiceBrowserActivity.java`)
```java
package com.accessibility.voicecompanion;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class VoiceBrowserActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_browser);

        webView = findViewById(R.id.embedded_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebBridgeReceiver(), "AndroidVoiceBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Pierces closed shadow DOM declarations dynamically before page runtime execution
                String shadowPatchScript = "(function() {" +
                        "  if (Element.prototype._isUnpacked) return;" +
                        "  Element.prototype._isUnpacked = true;" +
                        "  const nativeAttach = Element.prototype.attachShadow;" +
                        "  Element.prototype.attachShadow = function(options) {" +
                        "    if (options && options.mode === 'closed') { options.mode = 'open'; }" +
                        "    return nativeAttach.call(this, options);" +
                        "  };" +
                        "})();";
                view.evaluateJavascript(shadowPatchScript, null);
            }
        });
    }

    private class WebBridgeReceiver {
        @JavascriptInterface
        public void sendExtractedMatrix(String jsonWebTree) {
            // Target entry point processing coordinates and forwarding payloads to the Gemini controller
        }
    }
}
```

## 4. Native OS Accessibility Bridge Config
To operationalize background monitoring loops, the system requires an explicit OS-level accessibility permission declaration bundle.

### Service Rules Descriptor (`res/xml/accessibility_service_config.xml`)
- Enforces `canRetrieveWindowContent="true"` to extract on-screen application hierarchies.
- Enforces `canPerformGestures="true"` to allow coordinate-based `MotionEvent` injection layers to fire hardware touches.
- Leverages `flagIncludeNotImportantViews` to bypass lazy third-party app structural tags, exposing unlabelled structural layouts to the processing thread.

```
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://android.com"
    <!-- Capture window state updates, button clicks, and text inputs across all apps -->
    android:accessibilityEventTypes="typeWindowStateChanged|typeViewClicked|typeViewFocused|typeViewTextChanged"
    
    <!-- Listen to every third-party application on the user's phone -->
    android:packageNames=""
    
    <!-- Request structural layout node traversal capabilities -->
    android:accessibilityFeedbackType="feedbackSpoken"
    
    <!-- System flags: Allow touch exploration and flag layout grid coordinate inspection -->
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRequestTouchExplorationMode"
    
    android:notificationTimeout="100"
    
    <!-- Essential capability: Allows your app to simulate hardware taps on behalf of the user -->
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    
    android:description="@string/accessibility_service_description" />
```

## 5. Native Voice Input & Output Loop (`VoiceEngineManager.java`)
To keep latency low and costs near zero, voice capture and speech synthesis run natively on the Android device before passing clean text strings to Gemini.

### Key Architectural Behaviors
- **Locale Tuning:** Defaults to `en-IN` (Indian English) with fallback support for Hindi (`hi-IN`) and regional Indian languages so accents and Hinglish phrases are transcribed accurately.
- **Audio Feedback Loop Prevention:** Automatically pauses speech recognition while `TextToSpeech` is speaking so the app does not transcribe its own voice.
- **Trigger Mechanism:** Activated via hardware volume-button double-press, a full-screen tap gesture, or a wake-word listener inside `BackgroundVoiceService`.

### Core Java Implementation (`VoiceEngineManager.java`)
```java
package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceEngineManager {
    private final SpeechRecognizer speechRecognizer;
    private final Intent recognizerIntent;
    private TextToSpeech textToSpeech;
    private final VoiceCommandCallback callback;
    private boolean isSpeaking = false;

    public interface VoiceCommandCallback {
        void onCommandRecognized(String spokenText);
        void onVoiceError(String errorMessage);
    }

    public VoiceEngineManager(Context context, VoiceCommandCallback callback) {
        this.callback = callback;

        // 1. Initialize Native Text-To-Speech (TTS)
        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(new Locale("en", "IN"));
                textToSpeech.setSpeechRate(1.05f); // Slightly brisk pace preferred by screen-reader users
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                isSpeaking = true;
            }

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false;
            }

            @Override
            public void onError(String utteranceId) {
                isSpeaking = false;
            }
        });

        // 2. Initialize Native Speech-To-Text (STT)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    callback.onCommandRecognized(matches.get(0));
                }
            }

            @Override
            public void onError(int error) {
                callback.onVoiceError("Could not hear clearly. Please try again.");
            }

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void startListening() {
        if (isSpeaking) {
            textToSpeech.stop();
            isSpeaking = false;
        }
        speechRecognizer.startListening(recognizerIntent);
    }

    public void speak(String message) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "VOICE_FEEDBACK");
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, params, "VOICE_FEEDBACK");
    }

    public void destroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
    }
}
```

## 6. High-Stakes Action Confirmation Layer (`ActionSafetyGuard.java`)
To prevent accidental payments, irreversible form submissions, or unintended deletions, the execution pipeline enforces a two-step verbal confirmation state machine before dispatching `MotionEvent` taps on sensitive UI elements.

### Risk Classification Rules
1. **Prompt Augmentation:** Instruct Gemini to include a boolean flag `"requires_confirmation": true` and a clear `"action_summary"` string whenever the target element involves payments, money transfers, OTP/form submissions, deletions, or logouts.
2. **Local Keyword Fallback:** Even if Gemini marks an action as safe, `ActionSafetyGuard` scans the target element's extracted DOM text and inferred label against high-risk keywords (`pay`, `submit`, `confirm`, `transfer`, `delete`, `proceed`, `checkout`, `otp`). If matched, confirmation is forced locally.
3. **Pending Action State:** When confirmation is required, the `(x, y)` coordinates are held in memory rather than tapped immediately. The app reads the action summary aloud and switches the voice recognizer to listen for `"Yes"` / `"Haan"` / `"Confirm"` or `"No"` / `"Cancel"`.

### Core Java Implementation (`ActionSafetyGuard.java`)
```java
package com.accessibility.voicecompanion;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ActionSafetyGuard {
    private static final List<String> HIGH_RISK_KEYWORDS = Arrays.asList(
            "pay", "submit", "confirm", "transfer", "delete",
            "remove", "proceed", "checkout", "book now", "send"
    );

    public static class PendingAction {
        public final float x;
        public final float y;
        public final String description;

        public PendingAction(float x, float y, String description) {
            this.x = x;
            this.y = y;
            this.description = description;
        }
    }

    private PendingAction pendingAction = null;

    public boolean hasPendingAction() {
        return pendingAction != null;
    }

    public boolean isHighRiskAction(boolean geminiFlag, String inferredLabel, String domText) {
        if (geminiFlag) return true;

        String combined = ((inferredLabel != null ? inferredLabel : "") + " " +
                (domText != null ? domText : "")).toLowerCase(Locale.ROOT);

        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (combined.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public void queueForConfirmation(float x, float y, String actionDescription, VoiceEngineManager tts) {
        this.pendingAction = new PendingAction(x, y, actionDescription);
        tts.speak("Safety check. I am about to " + actionDescription + ". Say Yes to confirm, or No to cancel.");
    }

    public PendingAction evaluateConfirmationResponse(String userSpokenReply, VoiceEngineManager tts) {
        if (pendingAction == null) return null;

        String reply = userSpokenReply.toLowerCase(Locale.ROOT).trim();
        PendingAction actionToExecute = pendingAction;

        if (reply.contains("yes") || reply.contains("yeah") || reply.contains("confirm") || reply.contains("haan") || reply.contains("ok")) {
            pendingAction = null;
            tts.speak("Confirmed. Executing now.");
            return actionToExecute;
        } else {
            pendingAction = null;
            tts.speak("Action cancelled.");
            return null;
        }
    }

    public void clear() {
        pendingAction = null;
    }
}
```
