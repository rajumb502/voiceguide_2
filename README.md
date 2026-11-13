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

Available actions are CLICK, TYPE, SUBMIT, SCROLL, NAVIGATE, BACK, WAIT, ASK and DONE.
ASK reads a question and listens for the user's reply. DONE speaks the result and ends
the task. Ordinary links and recognizable search inputs can be used automatically.
Unknown controls, ordinary form fields, non-search submissions, downloads and actions
involving payments, sending, account changes or deletion require confirmation. Model
risk flags and local rules both apply; these heuristics are not a complete risk detector.

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
“find search box”, and “help”. With TalkBack, swipe to Speak and double-tap to activate it.

## Settings and sharing

1. Open Settings and choose Gemini or OpenAI.
2. Choose a suggested model or type a model ID available to your account. It must support
   image input and structured JSON. Suggestions are examples, not a live account model list.
3. Enter your API key and save. Each provider retains its own key and model. Only the
   current provider's edited values are saved by Save.
4. Optionally change speech recognition from en-IN to hi-IN or another language tag.

Keys are encrypted using AES-GCM and Android Keystore. The settings screen masks input
and blocks screenshots. Leave the key field blank to retain a saved key, enter a new
key to replace it, or use Remove saved key. Keys are never sent to page JavaScript, put
in URLs, or included in application errors. Backups are disabled. A compromised device
can still expose credentials. Provider use is billed to the user's API account.

Before the first AI request in a browser session, the app reads a sharing disclosure
and listens for yes/no. Explicit replies such as “yes, submit” grant sharing consent
only. Unclear replies repeat the question; explicit no/cancel declines. Nothing is sent
before yes. Consent lasts until the user leaves the screen. Missed answers get one
automatic listening retry, and Speak supports a manual retry. Sharing questions expire
after 60 seconds; consequential-action confirmations expire after 30 seconds.

The provider receives the goal, spoken context, action history, visible labels, field
metadata, page title/site name, viewport information, link destinations, and optional
screenshots. Extracted labels omit form values. Screenshots can contain private text;
they are omitted when recognized sensitive input fields are present. This detection is
incomplete. Passwords, OTP, file inputs and recognized card fields cannot be typed by
the executor. Native speech recognition may also contact its provider.

## Build and verification

Use JDK 17 and Android SDK 35. Open the project in Android Studio, or run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Run on Android 8.0+ with speech recognition and TTS installed. No backend URL or server
token is needed. backend/server.py remains an unused reference from the first prototype.

Java sources have compiled against the locally installed SDK 35. Offline host checks
cover provider context redaction, consent, navigation, the generic agent state/policy,
step/failure limits, target validation, and typing/submission on two simulated site
fixtures. JavaScript checks can be run with:

```bash
node tests/page.test.js
node tests/agent-page.test.js
```

These are simulated operations, not live model or real-browser task-completion tests.
The generic loop has not yet been validated end-to-end with either provider on a device,
and a complete APK build has not been verified in this agent session. Test a public
information/search task before trying personal forms. Check interruption during a slow
response, spoken clarifications, navigation, denied confirmation, failed typing, and
recovery from a changing page.

For the observed NVIDIA decoder emulator crash and its workaround, see
[the investigation](docs/emulator-crash.md) and scripts/emulator-debug.sh.

## Current scope

The executor controls this app's embedded browser. It does not control other Android
apps or provide general OS access. The declared AccessibilityService remains inactive.
AgentSession contains task state independently of the browser/provider, allowing a
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
