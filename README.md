# Voice Companion

An experimental native Android Java voice agent for browser tasks across websites.
Users choose Gemini or OpenAI and provide their own API key. Requests go directly to
that provider; Python is not required. The original blueprint is in requirements.md.

## Give the assistant a goal

Tap **Speak**, wait for the tone, and tell it what you want to accomplish. Examples:

- “On wikipedia.org, find an article about migrating birds and explain the introduction.”
- “Search this library website for books about gardening.”
- “Help me fill out this contact form. Ask me for any missing details.”
- “Find the opening hours on this museum website.”

There are no Amazon selectors, shopping workflows, or site-specific action sequences.
The model chooses a next action from the current page; the app validates it, executes
it, observes the result, and asks the model what to do next. It retains the user's goal,
clarifications, recent spoken context, and local action outcomes throughout the task.

Conversation memory is separate from task history. Follow-ups retain up to 24 recent
user/assistant/page messages within a 16,000-character budget, including intermediate
spoken progress, final descriptions, local readouts, and page references. Each message
records its page title, site, path, and page ID; URL queries and fragments are omitted.
A fresh task can therefore refer to an ad or item described during the previous task.
The model receives the current page separately and must check it before reusing old facts.
Memory is encrypted with Android Keystore and saved in a bounded SQLite record across
activity recreation and process exits. Only conversation from the current site is shared
under the saved sharing approvals; leaving the screen cancels automation and pending
uploads, while saved site approvals remain available for the next visit.
Say “clear conversation” outside a pending question to erase it explicitly.

Available actions are CLICK, TYPE, SUBMIT, SCROLL, NAVIGATE, BACK, WAIT, ASK and DONE.
ASK reads a question and listens for the user's reply. DONE speaks the result and ends
the task. Ordinary links and recognizable search inputs can be used automatically.
Unknown controls, ordinary form fields, non-search submissions, downloads and actions
involving payments, sending, account changes or deletion require confirmation. Model
risk flags and local rules both apply; these heuristics are not a complete risk detector.
An unapproved action expires after 30 seconds and cannot be approved after navigation.

After any operation the next request contains a fresh page snapshot. A dispatched click
or submission is not proof of task completion. The model is instructed to verify the
result before reporting success. The loop stops after 16 model turns, two consecutive
operation failures, or three repeats of the same operation. These limits also bound
accidental looping and API use. Success still depends on the model and website.

Tap **Speak** to interrupt a running task and provide a clarification, or **Stop** to
cancel it. Manual page interaction, a manual navigation, leaving the screen, settings,
and page-load failures cancel automation. A task's own navigation preserves its goal.
The task pauses while a page loads and stops if loading takes too long.

Local commands remain available outside an active question: “go to example dot com”,
“read this page”, “list links”, “list buttons”, “scroll down”, “scroll up”, “go back”,
“find search box”, “open app Calculator”, and “help”. App launching uses installed launcher
labels or exact package names. With TalkBack, swipe to Speak and double-tap to activate it.

## Settings and sharing

1. Open Settings and choose Gemini or OpenAI.
2. Choose a suggested model or type a model ID available to your account. It must support
   image input and structured JSON. Suggestions are examples, not a live account model list.
3. Enter your API key and save. Each provider retains its own key and model. Only the
   current provider's edited values are saved by Save.
4. Optionally change speech recognition from en-IN to hi-IN or another language tag.

Settings starts a spoken setup sequence for microphone access, Accessibility settings,
and the default Home app. Continue and Skip buttons remain available. Accessibility and
Home selection are optional; enabling the dormant accessibility service does not add
native app control. The app qualifies as a Home candidate through its HOME intent filter.

Settings and profile notes use EncryptedSharedPreferences backed by Android Keystore;
existing AES-GCM encrypted API keys are preserved during migration. Legacy settings are
cleared only after the encrypted copy commits successfully. The settings screen masks input
and blocks screenshots. Leave the key field blank to retain a saved key, enter a new
key to replace it, or use Remove saved key. Keys are never sent to page JavaScript, put
in URLs, or included in application errors. Backups are disabled. A compromised device
can still expose credentials. Provider use is billed to the user's API account.

Page sharing is configured in Settings. **Allow sending page details and conversation**
and **Include page screenshots** both default to Yes. An unknown site first needs spoken
consent naming the site and selected provider. Saying Yes saves that exact hostname for
that provider in encrypted settings across navigation, backgrounding, and app restarts.
Subdomains and other providers need separate approval. Settings lists approved hostnames
for the selected provider; add or remove entries, then Save settings to apply.

**Allow all sites without asking for this provider** defaults to Off. Turn it on and Save
to allow any site to share page details, conversation, and enabled screenshots/profile
notes with the selected provider. **Clear sharing approvals for this provider** empties
the site list and turns off this override; Save settings to apply. These choices are
independent for Gemini and OpenAI, and Save applies to the currently selected provider.

Sharing is checked again at the HTTP boundary, including fallback requests. Navigation,
cancellation, and leaving the browser still invalidate pending uploads and action approvals.
Turn page sharing off to block AI requests and retain local reading/navigation;
turn screenshots off to send only text and page metadata. These choices persist across
restarts and apply to both providers. Sensitive-page screenshot suppression still applies.
Consequential browser actions retain their separate spoken confirmation.

### Saved feedback preferences

Settings includes **Remember and use my feedback preferences** (default Yes) and an
editable list of saved preferences. These are app-private local settings, shared across
providers on this installation, and persist across restarts. Enabled preferences are
included in future consented requests; disabling the switch stops saving voice feedback and omits
the saved profile from requests without deleting the saved notes.

Examples of local voice commands:

- “Remember that I prefer prices before features.”
- “I prefer vegetarian food.”
- “From now on, explain unfamiliar terms.”
- “Keep answers short.” / “Give me more detail.” (replaces the previous detail preference)
- “Read my preferences.”
- “Forget my preferences.”

Only direct user feedback in these recognized forms is stored. The model does not infer
and save a profile from arbitrary page content or every conversation. Settings lets the
user edit or clear notes; press Save after editing. Feedback memory is limited to 20 notes
and 4000 characters. The current request takes precedence, and preferences never authorize
payments, submissions, deletion, or bypassing confirmation. Clearing conversation history
does not erase saved preferences; use Forget my preferences or Settings to remove those.

The provider receives the goal, enabled saved preferences, spoken context, action history, conversation page references, visible labels, field
metadata, page title/site name, viewport information, link destinations, and optional
screenshots. Extracted labels omit form values and recognized password/OTP/card nodes.
Screenshots are bounded to 720 pixels on either edge, encoded at JPEG quality 70 on a
worker thread, and omitted for recognized sensitive fields, editable content, or iframes. This detection is
incomplete. Passwords, OTP, file inputs and recognized card fields cannot be typed by
the executor. Speech uses on-device recognition when available (Android 12+) and otherwise
requests offline recognition; a recognizer may still require a network connection.
Installed offline TTS voices are preferred for the selected language.

Gemini defaults to gemini-2.5-flash-lite; existing model choices remain saved. A transient
network error, quota response, or server failure retries once without an image, using
Flash-Lite for Gemini and the selected model for OpenAI. Retries stay with the consented
provider and key. Authentication and invalid-request errors do not retry. If both requests
fail, local reading, scrolling, back navigation, and app launching remain available.

## Build and verification

Use JDK 17 and Android SDK 35. Open the project in Android Studio, or run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Run on Android 8.0+ with speech recognition and TTS installed. No backend URL or server
token is needed. backend/server.py remains an unused reference from the first prototype.

The debug APK builds against SDK 35. JVM checks
cover provider context redaction, consent, navigation, the generic agent state/policy,
step/failure limits, target validation, and typing/submission on two simulated site
fixtures. JavaScript checks can be run with:

```bash
node tests/page.test.js
node tests/agent-page.test.js
```

Instrumented tests cover saved site approvals and all-sites overrides, provider isolation,
revocation and the master sharing switch, encrypted settings migration, conversation persistence/clearing,
corruption handling, request consent/thread checks, voice lifecycle calls from a worker,
and real WebView redaction and clicks inside a shadow root created with closed mode.

These are simulated operations, not live model or real-browser task-completion tests.
The generic loop has not yet been validated end-to-end with either provider on a device.
Test a public
information/search task before trying personal forms. Check interruption during a slow
response, spoken clarifications, navigation, denied confirmation, failed typing, and
recovery from a changing page.

For the observed NVIDIA decoder emulator crash and its workaround, see
[the investigation](docs/emulator-crash.md) and scripts/emulator-debug.sh.

## Current scope

The executor controls this app's embedded browser. It does not control other Android
apps or provide general OS access. The declared AccessibilityService remains inactive.
No background microphone is started, so foreground microphone service declarations
are unnecessary. AgentSession contains task state independently of the browser/provider, allowing a
future native-app driver to use the same task model; that driver is not implemented.

The DOM bridge sees up to 150 visible labelled elements in the main document and open
shadow roots. The document-start shadow hook can affect website behavior. Cross-origin
frames, canvas-only interfaces, existing closed shadow roots, CAPTCHAs, arbitrary custom
editors/select widgets, file uploads, new windows and sites requiring trusted native
input are not fully supported. Forms with custom JavaScript can reject DOM value changes
or requestSubmit; the executor reports failure and the loop can re-observe or ask for help.
TalkBack interaction and speech timing still need broader device/user testing.

## References

- [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Gemini generateContent](https://ai.google.dev/api/generate-content)
- [AndroidX WebView](https://developer.android.com/reference/kotlin/androidx/webkit/WebViewCompat)
