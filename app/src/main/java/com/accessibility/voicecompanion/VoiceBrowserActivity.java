package com.accessibility.voicecompanion;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.*;
import androidx.webkit.WebViewCompat;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public final class VoiceBrowserActivity extends Activity implements VoiceEngineManager.Callback {
    public static class AndroidVoiceBridge {
        private volatile String hostname = "";
        @JavascriptInterface
        public void setHostname(String hostname) {
            this.hostname = hostname != null ? hostname : "";
        }
        public String getHostname() {
            return hostname;
        }
    }

    private WebView web;
    private TextView status;
    private EditText address;
    private VoiceEngineManager voice;
    private ConversationMemory conversation;
    private final SharingConsent sharing = new SharingConsent();
    private final AgentSession agent = new AgentSession();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AndroidVoiceBridge bridge = new AndroidVoiceBridge();
    private JSONObject pendingProposal, pendingSnapshot;
    private String pendingOtpCode;
    private MacroStore.Step pendingMacroStep;
    private boolean agentActing, pageLoading;
    private long loadStarted;
    private Runnable continuation;
    private final ActionSafetyGuard guard = new ActionSafetyGuard();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long navigation, requestId;
    private boolean busy, active, pageFailed;
    private String pageScript;
    private AssistantSettings settings;
    private String locale;
    private SmsOtpHelper smsOtpHelper;
    private MacroStore macroStore;
    private final List<MacroStore.Step> currentRunSteps = new ArrayList<>();
    private final List<MacroStore.Step> lastCompletedSteps = new ArrayList<>();
    private String lastCompletedGoal = "";
    private String lastInitialUrl = "";
    private boolean replayingMacro;
    private int macroReplayStepIndex;
    private MacroStore.Macro activeReplayMacro;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        try {
            settings = new AssistantSettings(this);
            conversation = new ConversationMemory(this);
            macroStore = new MacroStore(this);
        }
        catch (IllegalStateException e) { Toast.makeText(this,"Encrypted storage is unavailable. Saved data has been kept; please try again.",Toast.LENGTH_LONG).show(); finish(); return; }
        active = true;
        locale = settings.locale();
        smsOtpHelper = new SmsOtpHelper();
        InputStream input = null;
        try {
            input = getAssets().open("page.js");
        } catch (IOException ignored) {}
        if (input == null) {
            try {
                input = new FileInputStream("app/src/main/assets/page.js");
            } catch (IOException ignored) {}
        }
        if (input != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
                input.close();
                pageScript = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
        if (pageScript == null || pageScript.isEmpty()) {
            pageScript = "window.__vc = { snapshot: function() { return JSON.stringify({nodes:[]}); } };";
        }
        voice = new VoiceEngineManager(this, this);
        voice.setLanguage(locale);
        voice.setTonesEnabled(settings.audioTonesEnabled());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16,16,16,16);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        address = new EditText(this); address.setHint("Website URL or domain"); address.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        address.setSingleLine();
        if (Build.VERSION.SDK_INT >= 26) address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        controls.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        button(controls, "Go", v -> open(address.getText().toString()));
        root.addView(controls);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        button(row, "Speak", v -> listen());
        button(row, "Stop", v -> cancel());
        button(row, "Settings", v -> settings());
        root.addView(row);
        status = new TextView(this); status.setPadding(0,16,0,8); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.addJavascriptInterface(bridge, "AndroidVoiceBridge");
        try {
            WebViewCompat.addDocumentStartJavaScript(web, pageScript, Collections.singleton("*"));
        } catch (Exception ignored) {}
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                navigation++; requestId++; pageLoading = true; pageFailed = false; loadStarted = SystemClock.elapsedRealtime();
                feedback("Loading " + Uri.parse(url).getHost() + "…");
                status.setText("Loading " + Uri.parse(url).getHost() + "…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoading = false;
                if (!pageFailed) {
                    address.setText(url);
                    feedback("Loaded " + Uri.parse(url).getHost() + ".");
                    status.setText("Loaded " + Uri.parse(url).getHost() + ".");
                    remember("Page loaded: " + url + " - " + (view.getTitle() == null ? "" : view.getTitle()));
                    speakAudioLandmarkBrief(view, url);
                    if (replayingMacro && activeReplayMacro != null) {
                        main.postDelayed(VoiceBrowserActivity.this::executeNextMacroStep, 1000);
                    }
                }
            }
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                pageFailed = true; pageLoading = false;
                feedback("Could not load " + Uri.parse(failingUrl).getHost() + ": " + description);
                status.setText("Could not load " + Uri.parse(failingUrl).getHost() + ": " + description);
                if (voice != null) {
                    voice.playErrorTone();
                    voice.speak("Could not load " + Uri.parse(failingUrl).getHost() + ". Check the address or connection.");
                }
            }
        });
        web.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (voice != null && voice.isSpeaking()) {
                    voice.interruptAndListen(locale);
                    return true;
                }
            }
            return false;
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        open("https://en.m.wikipedia.org");
    }

    private void speakAudioLandmarkBrief(WebView view, String url) {
        final long briefNav = navigation;
        web.evaluateJavascript("window.__vc ? window.__vc.snapshot() : null", raw -> {
            if (!active || briefNav != navigation || pageFailed) return;
            String host = Uri.parse(url).getHost();
            String pageTitle = view.getTitle() == null || view.getTitle().trim().isEmpty() ? host : view.getTitle().trim();
            try {
                if (raw != null) {
                    Object decoded = new JSONTokener(raw).nextValue();
                    if (decoded instanceof String) {
                        JSONObject snap = new JSONObject((String) decoded);
                        JSONArray nodes = snap.optJSONArray("nodes");
                        int inputs = 0, buttons = 0;
                        if (nodes != null) {
                            for (int i = 0; i < nodes.length(); i++) {
                                JSONObject n = nodes.getJSONObject(i);
                                if (n.optBoolean("editable")) inputs++;
                                else if (n.optBoolean("clickable")) buttons++;
                            }
                        }
                        String brief = pageTitle + " loaded. Found " + inputs + " input fields and " + buttons + " interactive controls. Tap Speak and tell me what you want to do.";
                        if (voice != null) voice.speak(brief);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            if (voice != null) voice.speak("Loaded " + host + ". Tap Speak and tell me what you want to do, or say help.");
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (guard.hasPending()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            ActionSafetyGuard.Pending pending = guard.onVolumeUp(SystemClock.elapsedRealtime(), navigation);
                            if (pending != null) {
                                if (pendingProposal != null) {
                                    if (voice != null) voice.playSuccessChime();
                                    agent.answer("Approved via hardware confirmation.");
                                    JSONObject proposal = pendingProposal, snapshot = pendingSnapshot;
                                    pendingProposal = pendingSnapshot = null;
                                    executeAgentAction(proposal, snapshot, pending.navigation(), true);
                                } else if (pendingOtpCode != null) {
                                    if (voice != null) voice.playSuccessChime();
                                    String otp = pendingOtpCode;
                                    pendingOtpCode = null;
                                    agent.answer("Approved OTP entry via hardware confirmation.");
                                    executeOtpFill(otp);
                                } else if (pendingMacroStep != null) {
                                    if (voice != null) voice.playSuccessChime();
                                    MacroStore.Step stepToExecute = pendingMacroStep;
                                    pendingMacroStep = null;
                                    executeApprovedMacroStep(stepToExecute);
                                }
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            guard.onVolumeDown(SystemClock.elapsedRealtime());
                            cancel();
                            if (voice != null) {
                                voice.playErrorTone();
                                voice.speak("Action cancelled.");
                            }
                        }
                    }
                }
                return true; // Consume both ACTION_DOWN and ACTION_UP while pending confirmation to suppress system volume slider
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && voice != null && voice.isSpeaking()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    voice.interruptAndListen(locale);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    void handleOtpReceived(String otpCode, String rawMessage) {
        if (!active) return;
        pendingOtpCode = otpCode;
        String spaced = SmsOtpHelper.formatDigitsForSpeech(otpCode);
        String summary = "Enter OTP " + spaced;
        guard.queue(new ActionSafetyGuard.Pending("otp_action", 0L, navigation, SystemClock.elapsedRealtime(), summary));
        main.postDelayed(() -> {
            if (guard.hasPending() && pendingOtpCode != null && pendingOtpCode.equals(otpCode)) {
                cancel();
                if (voice != null) {
                    voice.playErrorTone();
                    voice.speak("OTP confirmation timed out for your safety.");
                }
            }
        }, ActionSafetyGuard.TIMEOUT_MS);
        String question = "OTP received: " + spaced + ". High-stakes action: Enter OTP. Double-press Volume Up to confirm, or press Volume Down to cancel. Spoken confirmation is not accepted.";
        remember("Assistant confirmation: " + question);
        agent.ask();
        if (voice != null) voice.speak(question);
    }

    private void executeOtpFill(String otp) {
        if (web == null) return;
        if (voice != null) voice.speak("Entering OTP.");
        web.evaluateJavascript("window.__vc ? window.__vc.enterOtp(" + JSONObject.quote(otp) + ") : false", result -> {
            boolean entered = "true".equals(result);
            if (entered) {
                if (voice != null) {
                    voice.playSuccessChime();
                    voice.speak("OTP entered successfully. Double-press Volume Up if a submit button requires confirmation.");
                }
            } else {
                if (voice != null) {
                    voice.playErrorTone();
                    voice.speak("Could not find an OTP field on this page to fill the code.");
                }
            }
        });
    }

    boolean dispatchCoordinateTap(float cssX, float cssY) {
        return dispatchCoordinateTap(cssX, cssY, -1f, -1f);
    }

    boolean dispatchCoordinateTap(float cssX, float cssY, float cssViewportWidth, float cssViewportHeight) {
        if (web == null) return false;
        try {
            float density = getResources().getDisplayMetrics().density;
            float scaleX = density;
            float scaleY = density;

            int viewWidth = web.getWidth();
            int viewHeight = web.getHeight();

            // When viewport CSS dimensions from snapshot (innerWidth/innerHeight) are available,
            // calculate the exact viewport-to-view scale ratio. This dynamically handles:
            // 1. Soft keyboard opening (which shrinks viewHeight and innerHeight in adjustResize mode)
            // 2. Pinch zoom or layout viewport scaling
            // 3. Discrepancies between DisplayMetrics density and window.devicePixelRatio
            if (viewWidth > 0 && cssViewportWidth > 0) {
                scaleX = (float) viewWidth / cssViewportWidth;
            }
            if (viewHeight > 0 && cssViewportHeight > 0) {
                scaleY = (float) viewHeight / cssViewportHeight;
            }

            float screenX = cssX * scaleX;
            float screenY = cssY * scaleY;

            // Clamp within visible WebView bounds
            if (viewWidth > 0) screenX = Math.max(0f, Math.min(screenX, (float) viewWidth));
            if (viewHeight > 0) screenY = Math.max(0f, Math.min(screenY, (float) viewHeight));

            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, screenX, screenY, 0);
            MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, screenX, screenY, 0);
            boolean downHandled = web.dispatchTouchEvent(down);
            boolean upHandled = web.dispatchTouchEvent(up);
            down.recycle();
            up.recycle();
            return downHandled || upHandled;
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONObject findNodeInSnapshot(JSONObject snapshot, String targetId) {
        if (snapshot == null || targetId == null || targetId.isEmpty()) return null;
        JSONArray nodes = snapshot.optJSONArray("nodes");
        if (nodes == null) return null;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.optJSONObject(i);
            if (n != null && targetId.equals(n.optString("id"))) return n;
        }
        return null;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (smsOtpHelper != null && smsOtpHelper.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
    }

    void simulateVoiceInput(String spokenText) {
        command(spokenText);
    }

    boolean simulateKeyEvent(KeyEvent event) {
        return dispatchKeyEvent(event);
    }

    MacroStore getMacroStore() {
        return macroStore;
    }

    boolean isReplayingMacro() {
        return replayingMacro;
    }

    MacroStore.Step getPendingMacroStep() {
        return pendingMacroStep;
    }

    List<MacroStore.Step> getLastCompletedSteps() {
        return new ArrayList<>(lastCompletedSteps);
    }

    private void button(LinearLayout parent, String text, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(text); button.setMinHeight(64); button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(0,-2,1));
    }
    private static boolean isWebUrl(String url) {
        Uri uri = Uri.parse(url); return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null;
    }
    private void open(String url) {
        if (!url.contains(":")) url = "https://" + url;
        if (isWebUrl(url)) web.loadUrl(url); else if (voice != null) voice.speak("Please enter an HTTP or HTTPS website address.");
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (intent.getData() != null) open(intent.getData().toString()); }
    private void listen() {
        if (agent.running() && !sharing.pending()) {
            agent.ask(); requestId++; busy = false; agentActing = false;
            if (continuation != null) main.removeCallbacks(continuation);
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
        else if (voice != null) voice.listen(locale);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listen();
        else if (voice != null) voice.speak("Microphone permission is needed for voice commands. Enable it in Android app settings, then tap Speak.");
    }
    @Override public void feedback(String text) { status.setText(text); }
    private void cancel() {
        replayingMacro = false;
        activeReplayMacro = null;
        pendingMacroStep = null;
        sharing.revoke();
        if (voice != null) voice.stopThinkingPulse();
        requestId++; busy = false; agentActing = false; agent.stop(); guard.clear(); pendingProposal = pendingSnapshot = null;
        pendingOtpCode = null;
        if (continuation != null) main.removeCallbacks(continuation);
    }
    @Override public void command(String text) {
        if (guard.hasPending()) {
            ActionSafetyGuard.SpokenResult spoken = guard.evaluateSpoken(text, SystemClock.elapsedRealtime(), navigation);
            if (spoken == ActionSafetyGuard.SpokenResult.ABORT) {
                cancel();
                if (voice != null) {
                    voice.playErrorTone();
                    voice.speak("Action cancelled.");
                }
            } else if (spoken == ActionSafetyGuard.SpokenResult.EXPIRED) {
                cancel();
                if (voice != null) {
                    voice.playErrorTone();
                    voice.speak("Confirmation timed out for your safety. Please repeat your request.");
                }
            } else {
                if (voice != null) voice.speak("Spoken confirmation is not accepted for sensitive actions. Double-press Volume Up to confirm, or press Volume Down to cancel.");
            }
            return;
        }
        if (VoiceCommands.normalize(text).matches("stop|cancel|no|नहीं")) { cancel(); if (voice != null) voice.speak("Stopped."); return; }
        if (sharing.pending()) {
            SharingConsent.Reply reply = sharing.evaluate(text,navigation,SystemClock.elapsedRealtime());
            if (reply.status() == SharingConsent.Status.ALLOWED) {
                try {
                    settings.approveSite(settings.provider(),Uri.parse(web.getUrl()).getHost());
                    requestAssistant(reply.command());
                } catch (IllegalStateException | IllegalArgumentException e) { cancel(); if (voice != null) voice.speak("I could not save this site's sharing approval. Nothing was sent. Please try again or review Settings."); }
            }
            else if (reply.status() == SharingConsent.Status.UNCLEAR) {
                if (!localCommand(text) && voice != null) voice.ask("Say yes to remember this site's sharing approval for " + settings.provider() + ", or no to stay local. You can remove approvals in Settings.",locale);
            } else { cancel(); if (voice != null) voice.speak("Page sharing cancelled or expired. Local reading and navigation are available."); }
            return;
        }
        if (preferenceCommand(text)) return;
        if (macroCommand(text)) return;
        if (localCommand(text)) return;
        if (agent.waiting()) { remember("User: " + text); agent.answer(text); requestAssistant(agent.goal()); return; }
        if (busy) { if (voice != null) voice.speak("I am still checking. Say stop to cancel."); return; }
        if (!settings.sharingEnabled()) { if (voice != null) voice.speak("AI page sharing is off. You can enable it in Settings, or use local page reading and navigation."); return; }
        currentRunSteps.clear();
        lastInitialUrl = web != null && web.getUrl() != null ? web.getUrl() : "";
        agent.start(text);
        remember("User goal or clarification: " + text);
        requestAssistant(agent.goal());
    }

    private boolean macroCommand(String text) {
        if (macroStore == null) return false;
        String normalized = VoiceCommands.normalize(text);
        if (normalized.matches("list macros|show macros|what macros do i have|what are my macros|read macros")) {
            cancel();
            List<String> names = macroStore.listMacroNames();
            if (names.isEmpty()) {
                if (voice != null) voice.speak("You have no saved macros.");
            } else {
                StringBuilder sb = new StringBuilder("Saved macros: ");
                for (int i = 0; i < names.size(); i++) {
                    sb.append(names.get(i));
                    if (i < names.size() - 1) sb.append(", ");
                }
                sb.append(". Say run macro followed by the name to replay.");
                if (voice != null) voice.speak(sb.toString());
            }
            return true;
        }
        if (normalized.matches(".*(delete macro|remove macro)\\s+.+")) {
            cancel();
            String name = normalized.replaceFirst(".*(delete macro|remove macro)\\s+", "").trim();
            if (macroStore.deleteMacro(name)) {
                if (voice != null) {
                    voice.playSuccessChime();
                    voice.speak("Macro " + name + " deleted.");
                }
            } else {
                if (voice != null) {
                    voice.playErrorTone();
                    voice.speak("Could not find a macro named " + name + " to delete.");
                }
            }
            return true;
        }
        if (normalized.matches(".*(save macro as|save this as macro|save this macro as|save as macro|save macro)\\s*.*")) {
            cancel();
            String name = normalized.replaceFirst(".*(save macro as|save this as macro|save this macro as|save as macro|save macro)\\s*", "").trim();
            if (name.isEmpty()) {
                name = lastCompletedGoal.isEmpty() ? "saved workflow" : lastCompletedGoal;
            }
            List<MacroStore.Step> stepsToSave = !lastCompletedSteps.isEmpty() ? lastCompletedSteps : currentRunSteps;
            if (!stepsToSave.isEmpty()) {
                MacroStore.Macro m = new MacroStore.Macro(name, lastInitialUrl, stepsToSave);
                macroStore.saveMacro(m);
                if (voice != null) {
                    voice.playSuccessChime();
                    voice.speak("Saved macro " + name + " with " + stepsToSave.size() + " steps. You can say run macro " + name + " anytime.");
                }
            } else {
                if (voice != null) voice.speak("There is no completed multi-step workflow to save as a macro.");
            }
            return true;
        }
        MacroStore.Macro match = macroStore.findMatchingMacro(text);
        if (match != null) {
            cancel();
            replayMacro(match);
            return true;
        }
        if (normalized.matches(".*(run macro|replay macro|start macro|execute macro)\\s+.+")) {
            cancel();
            String query = normalized.replaceFirst(".*(run macro|replay macro|start macro|execute macro)\\s+", "").trim();
            if (voice != null) {
                voice.playErrorTone();
                voice.speak("Could not find a macro named " + query + ". Say list macros to hear saved macros.");
            }
            return true;
        }
        return false;
    }

    void replayMacro(MacroStore.Macro macro) {
        if (macro == null || macro.steps.isEmpty()) return;
        replayingMacro = true;
        activeReplayMacro = macro;
        macroReplayStepIndex = 0;
        if (voice != null) {
            voice.playSuccessChime();
            voice.speak("Replaying macro " + macro.name + ".");
        }
        if (!macro.initialUrl.isEmpty() && (web.getUrl() == null || !web.getUrl().startsWith(macro.initialUrl))) {
            open(macro.initialUrl);
            main.postDelayed(this::executeNextMacroStep, 1500);
        } else {
            executeNextMacroStep();
        }
    }

    void executeNextMacroStep() {
        if (!replayingMacro || activeReplayMacro == null || !active) return;
        if (macroReplayStepIndex >= activeReplayMacro.steps.size()) {
            MacroStore.Macro done = activeReplayMacro;
            replayingMacro = false;
            activeReplayMacro = null;
            if (voice != null) {
                voice.playSuccessChime();
                voice.speak("Macro " + done.name + " completed successfully.");
            }
            return;
        }
        MacroStore.Step step = activeReplayMacro.steps.get(macroReplayStepIndex);
        if ("NAVIGATE".equalsIgnoreCase(step.action)) {
            macroReplayStepIndex++;
            open(step.url);
            main.postDelayed(this::executeNextMacroStep, 1500);
            return;
        }
        if ("SCROLL".equalsIgnoreCase(step.action)) {
            macroReplayStepIndex++;
            if ("down".equalsIgnoreCase(step.direction)) web.pageDown(false);
            else web.pageUp(false);
            main.postDelayed(this::executeNextMacroStep, 800);
            return;
        }
        String stepDescriptor = (step.action + " " + step.label + " " + step.selector + " " + step.name + " " + step.placeholder).trim();
        boolean isHighStakes = "SUBMIT".equalsIgnoreCase(step.action) || guard.requiresConfirmation(false, stepDescriptor);

        if (isHighStakes) {
            pendingMacroStep = step;
            String summary = "Macro " + activeReplayMacro.name + ": " + (step.label.isEmpty() ? step.action : step.label);
            guard.queue(new ActionSafetyGuard.Pending("macro_step_" + macroReplayStepIndex, 0L, navigation, SystemClock.elapsedRealtime(), summary));
            main.postDelayed(() -> {
                if (guard.hasPending() && pendingMacroStep == step) {
                    cancel();
                    if (voice != null) {
                        voice.playErrorTone();
                        voice.speak("Macro confirmation timed out for your safety.");
                    }
                }
            }, ActionSafetyGuard.TIMEOUT_MS);
            String question = "High-stakes action in macro: " + (step.label.isEmpty() ? step.action : step.label) +
                ". Double-press Volume Up to confirm, or press Volume Down to cancel. Spoken confirmation is not accepted.";
            remember("Assistant confirmation: " + question);
            agent.ask();
            if (voice != null) voice.speak(question);
            return;
        }

        try {
            JSONObject stepJson = step.toJson();
            web.evaluateJavascript("window.__vc ? window.__vc.executeMacroStep(" + stepJson.toString() + ", false) : false", res -> {
                boolean success = "true".equals(res);
                if (success) {
                    macroReplayStepIndex++;
                    main.postDelayed(this::executeNextMacroStep, 1000);
                } else {
                    replayingMacro = false;
                    MacroStore.Macro broken = activeReplayMacro;
                    activeReplayMacro = null;
                    if (voice != null) {
                        voice.playErrorTone();
                        voice.speak("Page layout changed at step " + (macroReplayStepIndex + 1) + ". Asking AI assistant to complete the workflow.");
                    }
                    agent.start("Continue workflow: " + broken.name);
                    requestAssistant(agent.goal());
                }
            });
        } catch (Exception e) {
            replayingMacro = false;
            MacroStore.Macro broken = activeReplayMacro;
            activeReplayMacro = null;
            if (voice != null) {
                voice.playErrorTone();
                voice.speak("Could not execute macro step. Asking AI assistant to help.");
            }
            if (broken != null) {
                agent.start("Continue workflow: " + broken.name);
                requestAssistant(agent.goal());
            }
        }
    }

    private void executeApprovedMacroStep(MacroStore.Step step) {
        if (!active || !replayingMacro || activeReplayMacro == null) return;
        try {
            JSONObject stepJson = step.toJson();
            web.evaluateJavascript("window.__vc ? window.__vc.executeMacroStep(" + stepJson.toString() + ", true) : false", res -> {
                boolean success = "true".equals(res);
                if (success) {
                    macroReplayStepIndex++;
                    main.postDelayed(this::executeNextMacroStep, 1000);
                } else {
                    replayingMacro = false;
                    MacroStore.Macro broken = activeReplayMacro;
                    activeReplayMacro = null;
                    if (voice != null) {
                        voice.playErrorTone();
                        voice.speak("Could not execute sensitive step in macro. Asking AI assistant to complete the workflow.");
                    }
                    agent.start("Continue workflow: " + broken.name);
                    requestAssistant(agent.goal());
                }
            });
        } catch (Exception e) {
            replayingMacro = false;
            MacroStore.Macro broken = activeReplayMacro;
            activeReplayMacro = null;
            if (broken != null) {
                agent.start("Continue workflow: " + broken.name);
                requestAssistant(agent.goal());
            }
        }
    }

    private void requestAssistant(String text) {
        if (!active || !settings.sharingEnabled() || !agent.running()) return;
        if (pageLoading) { scheduleObservation(500); return; }
        String site = Uri.parse(web.getUrl() == null ? "" : web.getUrl()).getHost();
        if (site == null) { cancel(); if (voice != null) voice.speak("Open a website before asking the assistant."); return; }
        if (!sharing.allows(site,settings.provider(),navigation) && settings.siteSharingAllowed(site,settings.provider())) sharing.grant(site,settings.provider(),navigation);
        if (!sharing.allows(site,settings.provider(),navigation)) {
            if (!sharing.pending()) {
                sharing.request(text,navigation,SystemClock.elapsedRealtime(),site,settings.provider());
                if (voice != null) voice.ask("Allow sharing screen details on " + site + " with the AI assistant? Say Yes or No.",locale);
            }
            return;
        }
        if (!agent.nextStep()) { if (voice != null) voice.speak("I reached the task's step limit. Tap Speak to give a new instruction."); return; }
        agentActing = false;
        final String provider = settings.provider(), model = settings.model(provider), apiKey;
        try { apiKey = settings.apiKey(provider); }
        catch (Exception e) { cancel(); if (voice != null) voice.speak("Could not read your API key. Open Settings and enter it again."); return; }
        if (apiKey.isEmpty()) { cancel(); if (voice != null) voice.speak("Set your " + provider + " API key in Settings first."); return; }
        busy = true; final long job = ++requestId, nav = navigation;
        if (voice != null) voice.startThinkingPulse();
        if (agent.steps() == 1 && voice != null) voice.speak("I will work on that. You can tap Speak to interrupt me, or Stop to cancel.");
        web.evaluateJavascript("window.__vc ? window.__vc.snapshot() : null", raw -> {
            try {
                if (!valid(job, nav)) { if (voice != null) voice.stopThinkingPulse(); return; }
                Object decoded = new JSONTokener(raw).nextValue();
                if (!(decoded instanceof String)) throw new JSONException("No page snapshot");
                JSONObject snapshot = new JSONObject((String) decoded);
                if (!sharing.allows(snapshot.getString("site"),provider,nav)) { if (voice != null) voice.stopThinkingPulse(); cancel(); if (voice != null) voice.speak("The page changed. Please ask again."); return; }
                JSONObject payload = new JSONObject().put("command", text).put("snapshot", snapshot).put("conversation_history", conversation != null ? conversation.snapshot(snapshot.getString("site")) : new JSONArray()).put("current_page",ConversationMemory.page(web.getUrl(),web.getTitle(),navigation)).put("task_history",new JSONArray(agent.history())).put("locale", locale);
                if (settings.rememberPreferences()) payload.put("user_preferences","Preferred language: " + settings.locale() + "\n" + settings.userPreferences());
                Bitmap captured = null;
                if (settings.screenshotsEnabled() && !snapshot.getBoolean("sensitive")) {
                    int width = web.getWidth(), height = web.getHeight();
                    if (width <= 0 || height <= 0) throw new IllegalStateException("Page has no viewport");
                    float scale = Math.min(1f, 720f / Math.max(width, height));
                    Bitmap bitmap = Bitmap.createBitmap(Math.max(1,(int)(width*scale)), Math.max(1,(int)(height*scale)), Bitmap.Config.ARGB_8888);
                    try { Canvas canvas = new Canvas(bitmap); canvas.scale(scale, scale); web.draw(canvas); captured = bitmap; }
                    catch (RuntimeException e) { bitmap.recycle(); throw e; }
                }
                final Bitmap bitmap = captured;
                worker.submit(() -> {
                    try {
                        if (bitmap != null) {
                            try {
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                if (!bitmap.compress(Bitmap.CompressFormat.JPEG,70,bytes)) throw new IOException("Could not encode screenshot");
                                payload.put("image", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                            } finally { bitmap.recycle(); }
                        }
                        JSONObject response = AssistantClient.request(provider, model, apiKey, payload,sharing,nav,settings);
                        runOnUiThread(() -> {
                            if (voice != null) voice.stopThinkingPulse();
                            if (valid(job, nav)) { busy = false; apply(response, snapshot, nav); }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (voice != null) {
                                voice.stopThinkingPulse();
                                voice.playErrorTone();
                            }
                            if (valid(job,nav)) { cancel(); if (voice != null) voice.speak(e instanceof AssistantClient.RequestFailure ? e.getMessage() : "The assistant could not connect. You can still say scroll down, go back, read this page, or open app followed by an app name."); }
                        });
                    }
                });
            } catch (Exception e) {
                if (voice != null) {
                    voice.stopThinkingPulse();
                    voice.playErrorTone();
                }
                cancel();
                if (voice != null) voice.speak("Could not inspect this page. Try reloading it.");
            }
        });
    }
    private boolean preferenceCommand(String text) {
        String normalized = VoiceCommands.normalize(text);
        if (normalized.matches(".*(turn on|enable|switch on)\\s+(tone|tones|sound|audio tones).*")) {
            settings.saveAudioTones(true);
            if (voice != null) {
                voice.setTonesEnabled(true);
                voice.speak("Audio tones enabled.");
            }
            return true;
        }
        if (normalized.matches(".*(turn off|disable|switch off|mute)\\s+(tone|tones|sound|audio tones).*")) {
            settings.saveAudioTones(false);
            if (voice != null) {
                voice.setTonesEnabled(false);
                voice.speak("Audio tones turned off.");
            }
            return true;
        }
        if (normalized.matches(".*(turn on|enable|allow)\\s+(page sharing|sharing).*")) {
            settings.saveSharingEnabled(true);
            if (voice != null) voice.speak("Page sharing enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable|stop)\\s+(page sharing|sharing).*")) {
            settings.saveSharingEnabled(false);
            if (voice != null) voice.speak("Page sharing turned off.");
            return true;
        }
        if (normalized.matches(".*(turn on|enable|include)\\s+(screenshot|screenshots).*")) {
            settings.saveScreenshotsEnabled(true);
            if (voice != null) voice.speak("Page screenshots enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable|stop)\\s+(screenshot|screenshots).*")) {
            settings.saveScreenshotsEnabled(false);
            if (voice != null) voice.speak("Page screenshots turned off.");
            return true;
        }
        if (normalized.matches(".*(turn on|enable)\\s+(remember preferences|remembering preferences).*")) {
            settings.saveRememberPreferences(true);
            if (voice != null) voice.speak("Remembering preferences enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable)\\s+(remember preferences|remembering preferences).*")) {
            settings.saveRememberPreferences(false);
            if (voice != null) voice.speak("Remembering preferences turned off.");
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(hindi|hi|hi-in).*")) {
            settings.saveLocale("hi-IN");
            locale = "hi-IN";
            if (voice != null) {
                voice.setLanguage("hi-IN");
                voice.speak("Language changed to Hindi.");
            }
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(english|en|en-in).*")) {
            settings.saveLocale("en-IN");
            locale = "en-IN";
            if (voice != null) {
                voice.setLanguage("en-IN");
                voice.speak("Language changed to Indian English.");
            }
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(kannada|kn|kn-in).*")) {
            settings.saveLocale("kn-IN");
            locale = "kn-IN";
            if (voice != null) {
                voice.setLanguage("kn-IN");
                voice.speak("Language changed to Kannada.");
            }
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(tamil|ta|ta-in).*")) {
            settings.saveLocale("ta-IN");
            locale = "ta-IN";
            if (voice != null) {
                voice.setLanguage("ta-IN");
                voice.speak("Language changed to Tamil.");
            }
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(marathi|mr|mr-in).*")) {
            settings.saveLocale("mr-IN");
            locale = "mr-IN";
            if (voice != null) {
                voice.setLanguage("mr-IN");
                voice.speak("Language changed to Marathi.");
            }
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(telugu|te|te-in).*")) {
            settings.saveLocale("te-IN");
            locale = "te-IN";
            if (voice != null) {
                voice.setLanguage("te-IN");
                voice.speak("Language changed to Telugu.");
            }
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(bengali|bn|bn-in).*")) {
            settings.saveLocale("bn-IN");
            locale = "bn-IN";
            if (voice != null) {
                voice.setLanguage("bn-IN");
                voice.speak("Language changed to Bengali.");
            }
            return true;
        }
        if (normalized.matches("use gemini|switch to gemini|set provider to gemini")) {
            settings.saveProvider(AssistantSettings.GEMINI);
            if (voice != null) voice.speak("AI provider set to Gemini.");
            return true;
        }
        if (normalized.matches("use openai|switch to openai|set provider to openai")) {
            settings.saveProvider(AssistantSettings.OPENAI);
            if (voice != null) voice.speak("AI provider set to OpenAI.");
            return true;
        }
        if (normalized.matches("forget my preferences|clear my preferences")) {
            try { settings.saveUserPreferences(""); if (voice != null) voice.speak("Saved preferences cleared."); }
            catch (Exception e) { if (voice != null) voice.speak("I could not clear the saved preferences. Please try again in Settings."); }
            return true;
        }
        if (normalized.matches("what do you remember about me|read my preferences|my preferences")) {
            if (voice != null) voice.speak(settings.userPreferences().isEmpty() ? "You have no saved preferences." : "Your saved preferences: " + settings.userPreferences()); return true;
        }
        UserPreferences.Update update = UserPreferences.feedback(text);
        if (update == null) return false;
        if (!settings.rememberPreferences()) { if (voice != null) voice.speak("Remembering preferences is off. You can enable it in Settings."); return true; }
        try {
            settings.saveUserPreferences(UserPreferences.merge(settings.userPreferences(),update));
            if (voice != null) voice.speak("I will remember: " + update.text() + ". You can review or change this in Settings.");
        } catch (Exception e) { if (voice != null) voice.speak("I could not save that preference. Please try again in Settings."); }
        return true;
    }

    private static boolean normMatches(String text, String regex) {
        return java.util.regex.Pattern.compile(".*" + regex + ".*").matcher(text).matches();
    }
    private boolean localCommand(String text) {
        String command = VoiceCommands.normalize(text);
        String app = VoiceCommands.appName(text);
        if (app != null) {
            cancel();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            android.content.pm.ResolveInfo match = null;
            for (android.content.pm.ResolveInfo candidate : getPackageManager().queryIntentActivities(launcher,0)) {
                if (app.equals(VoiceCommands.normalize(candidate.loadLabel(getPackageManager()).toString())) || app.equals(candidate.activityInfo.packageName)) {
                    if (match != null && !match.activityInfo.packageName.equals(candidate.activityInfo.packageName)) { if (voice != null) voice.speak("More than one app has that name. Say open app followed by its package name."); return true; }
                    match = candidate;
                }
            }
            if (match == null) { if (voice != null) voice.speak("I could not find an installed app with that name."); }
            else {
                try { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(match.activityInfo.packageName,match.activityInfo.name)); }
                catch (RuntimeException e) { if (voice != null) voice.speak("That app could not be opened."); }
            }
            return true;
        }
        String url = VoiceCommands.navigationUrl(text);
        if (url != null) { cancel(); if (voice != null) voice.speak("Opening " + Uri.parse(url).getHost()); open(url); return true; }
        switch (command) {
            case "clear conversation": case "forget conversation":
                cancel();
                try {
                    if (conversation != null) conversation.clear();
                    if (voice != null) voice.speak("Conversation cleared. I have forgotten previous page descriptions and requests.");
                }
                catch (IllegalStateException e) { if (voice != null) voice.speak("Could not clear saved conversation. Please try again."); }
                return true;
            case "help": case "what can i say": case "what can you do":
                cancel(); if (voice != null) voice.speak(VoiceCommands.HELP); return true;
            case "settings": case "open settings": settings(); return true;
            case "go back": case "back":
                cancel(); if (web.canGoBack()) web.goBack(); else if (voice != null) voice.speak("There is no previous page."); return true;
            case "scroll down": case "scroll up":
                cancel();
                boolean moved = command.equals("scroll down") ? web.pageDown(false) : web.pageUp(false);
                if (voice != null) voice.speak(moved ? "Scrolled. Tap Speak and say read this page or list links." : "You have reached the edge of this page."); return true;
            case "read this page": case "read page": case "read the page": case "where am i":
                readPage("page"); return true;
            case "find search box": case "find search":
                cancel(); final long searchJob = requestId, searchNav = navigation;
                web.evaluateJavascript("window.__vc ? window.__vc.findSearch() : null", raw -> {
                    if (!valid(searchJob,searchNav)) return;
                    try {
                        Object result = new JSONTokener(raw).nextValue();
                        if (voice != null) voice.speak(result instanceof String ? "I found the search field: " + result + ". It is now in view. Tell me what you want to search for."
                            : "I could not identify a search field in the accessible page. It may be inside a menu or an inaccessible frame.");
                    } catch (Exception e) { if (voice != null) voice.speak("I could not inspect the search fields. Please try again."); }
                }); return true;
            case "list links": case "read links": readPage("links"); return true;
            case "list buttons": case "read buttons": readPage("buttons"); return true;
            default: return false;
        }
    }
    private void readPage(String mode) {
        cancel(); final long job = requestId, nav = navigation;
        web.evaluateJavascript("window.__vc ? window.__vc.snapshot() : null", raw -> {
            if (!valid(job,nav)) return;
            try {
                Object decoded = new JSONTokener(raw).nextValue();
                if (!(decoded instanceof String)) throw new JSONException("Missing page");
                JSONArray nodes = new JSONObject((String)decoded).getJSONArray("nodes");
                StringBuilder spoken = new StringBuilder(mode.equals("page") ? "Visible page content. " : "Visible " + mode + ". ");
                java.util.Set<String> seen = new java.util.HashSet<>(); int count = 0;
                for (int i=0; i<nodes.length() && count<10 && spoken.length()<1800; i++) {
                    JSONObject node = nodes.getJSONObject(i); String tag = node.getString("tag"), role = node.optString("role");
                    boolean link = tag.equals("A") || role.equals("link"), button = tag.equals("BUTTON") || role.equals("button");
                    if (mode.equals("links") && !link || mode.equals("buttons") && !button) continue;
                    String label = node.getString("label").trim(); if (label.isEmpty() || !seen.add(label)) continue;
                    spoken.append(link ? "Link: " : button ? "Button: " : "").append(label).append(". "); count++;
                }
                if (count == 0) spoken.append("No labelled content found in this view. ");
                spoken.append("Tap Speak to ask about an item, or say scroll down to explore more."); remember("Read aloud: " + spoken); if (voice != null) voice.speak(spoken.toString());
            } catch (Exception e) { if (voice != null) voice.speak("The page is not ready to read. Try again after it loads."); }
        });
    }
    private boolean valid(long job, long nav) { return active && requestId == job && navigation == nav; }
    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new IOException("Response too large");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
    private void scheduleObservation(long delay) {
        if (continuation != null) main.removeCallbacks(continuation);
        final long ticket = requestId;
        continuation = () -> {
            if (!active || ticket != requestId || !agent.running() || !settings.sharingEnabled() || busy) return;
            if (pageLoading) {
                if (SystemClock.elapsedRealtime()-loadStarted > 20000) { cancel(); if (voice != null) voice.speak("The page is taking too long. Please try again when it has loaded."); }
                else scheduleObservation(500);
                return;
            }
            requestAssistant(agent.goal());
        };
        main.postDelayed(continuation,delay);
    }
    private void apply(JSONObject response, JSONObject snapshot, long nav) {
        if (!agent.running()) return;
        try {
            String action = response.getString("action"), speech = response.getString("spoken_feedback");
            if (action.equals("SPEAK") || action.equals("DONE")) {
                if (voice != null) voice.playSuccessChime();
                remember("Assistant: " + speech);
                agent.stop();
                if (!currentRunSteps.isEmpty()) {
                    lastCompletedSteps.clear();
                    lastCompletedSteps.addAll(currentRunSteps);
                    lastCompletedGoal = agent.goal();
                    speech = speech + " Say 'save macro' followed by a name if you want to save this workflow for instant replay next time.";
                }
                if (voice != null) voice.speak(speech);
                return;
            }
            if (action.equals("ASK")) { agent.ask(); remember("Assistant question: " + speech); if (voice != null) voice.ask(speech,locale); return; }
            if (action.equals("NAVIGATE") && !AgentPolicy.navigationAllowed(agent.userInstructions(),response.getString("url"),snapshot)) {
                remember("Assistant: I could not verify that destination from the current page. I will inspect the page again.");
                if (voice != null) voice.speak("I could not verify that destination from the current page. I will inspect the page again.");
                recordOutcome(response,"Navigation refused: destination is neither user-authorized nor a current-page link. Inspect the existing page and its links. Do not ask which page for a follow-up about something already described.",false);
                scheduleObservation(600); return;
            }
            if (AgentPolicy.needsConfirmation(response,snapshot)) {
                pendingProposal = response; pendingSnapshot = snapshot;
                guard.queue(new ActionSafetyGuard.Pending(response.getString("target_id"),snapshot.getLong("revision"),nav,SystemClock.elapsedRealtime(),response.getString("action_summary")));
                main.postDelayed(() -> {
                    if (guard.hasPending() && pendingProposal == response) {
                        cancel();
                        if (voice != null) {
                            voice.playErrorTone();
                            voice.speak("Confirmation timed out for your safety. Please repeat your request.");
                        }
                    }
                },ActionSafetyGuard.TIMEOUT_MS);
                String question = "High-stakes action: " + response.getString("action_summary") + ". Double-press Volume Up to confirm, or press Volume Down to cancel. Spoken confirmation is not accepted.";
                remember("Assistant confirmation: " + question);
                agent.ask(); if (voice != null) voice.speak(question); return;
            }
            executeAgentAction(response,snapshot,nav,false);
        } catch (Exception e) { cancel(); if (voice != null) { voice.playErrorTone(); voice.speak("The assistant returned an invalid action. The task stopped."); } }
    }
    private void executeAgentAction(JSONObject action, JSONObject snapshot, long nav, boolean approved) {
        if (!active || !agent.running() || navigation != nav) { cancel(); if (voice != null) voice.speak("The page changed. Please repeat your request."); return; }
        try {
            String kind = action.getString("action");
            remember("Assistant progress: " + action.getString("spoken_feedback"));
            if (voice != null) voice.speak(action.getString("spoken_feedback"));
            busy = true; agentActing = true;
            if (kind.equals("NAVIGATE")) {
                currentRunSteps.add(new MacroStore.Step("NAVIGATE", "", "", "", "", "", "", "", action.getString("url"), ""));
                recordOutcome(action,"Navigation requested; inspect the loaded page.",true);
                if (!agent.running()) return;
                web.loadUrl(action.getString("url"));
                scheduleObservation(1500);
                return;
            }
            if (kind.equals("BACK")) {
                boolean possible = web.canGoBack(); recordOutcome(action,possible ? "Back requested; inspect the resulting page." : "No previous page.",possible);
                if (possible) web.goBack(); scheduleObservation(1200); return;
            }
            if (kind.equals("SCROLL")) {
                currentRunSteps.add(new MacroStore.Step("SCROLL", "", "", "", "", "", "", "", "", action.getString("direction")));
                boolean moved = action.getString("direction").equals("down") ? web.pageDown(false) : web.pageUp(false);
                recordOutcome(action,moved ? "Viewport scrolled." : "Already at the page edge.",moved); scheduleObservation(700); return;
            }
            if (kind.equals("WAIT")) { recordOutcome(action,"Waited for the page; inspect again.",true); scheduleObservation(1500); return; }
            final long actionTicket = requestId;
            final MacroStore.Step domStep;
            JSONObject targetNode = findNodeInSnapshot(snapshot, action.optString("target_id"));
            if (targetNode != null) {
                domStep = new MacroStore.Step(
                    kind,
                    targetNode.optString("selector", ""),
                    targetNode.optString("label", ""),
                    targetNode.optString("tag", ""),
                    targetNode.optString("role", ""),
                    targetNode.optString("name", ""),
                    targetNode.optString("placeholder", ""),
                    action.optString("text", ""),
                    "", ""
                );
            } else {
                domStep = new MacroStore.Step(kind, "", "", "", "", "", "", action.optString("text", ""), "", "");
            }
            web.evaluateJavascript("window.__vc && window.__vc.perform(" + action.toString() + "," + snapshot.getLong("revision") + "," + approved + ")", result -> {
                if (!active || !agent.running() || requestId != actionTicket || navigation != nav) return;
                boolean success = "true".equals(result);
                if (!success && kind.equals("CLICK")) {
                    double cx = -1, cy = -1;
                    if (action.has("x") && action.has("y")) {
                        cx = action.optDouble("x", -1);
                        cy = action.optDouble("y", -1);
                    } else if (targetNode != null && targetNode.has("x") && targetNode.has("y")) {
                        cx = targetNode.optDouble("x", 0) + targetNode.optDouble("width", 0) / 2.0;
                        cy = targetNode.optDouble("y", 0) + targetNode.optDouble("height", 0) / 2.0;
                    }
                    if (cx >= 0 && cy >= 0) {
                        float vWidth = (float) snapshot.optDouble("width", -1);
                        float vHeight = (float) snapshot.optDouble("height", -1);
                        success = dispatchCoordinateTap((float) cx, (float) cy, vWidth, vHeight);
                    }
                }
                if (success) {
                    currentRunSteps.add(domStep);
                    if (voice != null) voice.playSuccessChime();
                }
                recordOutcome(action,success ? "Local operation dispatched. Verify the resulting page before reporting success." : "Target changed, was blocked, or operation failed. Inspect again and choose another approach.",success);
                scheduleObservation(1000);
            });
        } catch (Exception e) { cancel(); if (voice != null) { voice.playErrorTone(); voice.speak("The browser could not perform that operation. The task stopped."); } }
    }
    private void recordOutcome(JSONObject action, String outcome, boolean success) {
        busy = false;
        boolean proceed = agent.record(action.optString("action") + " " + action.optString("target_id") + " " + action.optString("direction") + " " + action.optString("url"),action.optString("action_summary") + ". " + outcome,success);
        if (!proceed) { requestId++; agentActing = false; if (voice != null) { voice.playErrorTone(); voice.speak("I could not make reliable progress, so I stopped. Tap Speak to change the request or try another approach."); } }
    }
    private void remember(String text) {
        if (conversation == null) return;
        String role = text.startsWith("User") ? "user" : text.startsWith("Page loaded:") ? "page" : "assistant";
        try { conversation.add(role,text,web == null ? null : web.getUrl(),web == null ? null : web.getTitle(),navigation); }
        catch (IllegalStateException e) { Toast.makeText(this,"Could not save conversation on this device.",Toast.LENGTH_LONG).show(); }
    }
    private void settings() {
        cancel(); if (voice != null) voice.stop();
        startActivity(new Intent(this, SettingsActivity.class));
    }
    @Override protected void onResume() {
        super.onResume();
        active = true;
        if (settings != null) {
            locale = settings.locale();
            if (voice != null) {
                voice.setLanguage(locale);
                voice.setTonesEnabled(settings.audioTonesEnabled());
            }
        }
        if (smsOtpHelper != null) {
            smsOtpHelper.startListening(this, new SmsOtpHelper.OtpListener() {
                @Override public void onOtpReceived(String otpCode, String rawMessage) {
                    handleOtpReceived(otpCode, rawMessage);
                }
                @Override public void onOtpTimeoutOrError(String error) {}
            });
        }
    }
    @Override protected void onPause() {
        active = false;
        replayingMacro = false;
        activeReplayMacro = null;
        pendingMacroStep = null;
        if (smsOtpHelper != null) smsOtpHelper.stopListening();
        cancel();
        if (voice != null) voice.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (smsOtpHelper != null) smsOtpHelper.stopListening();
        if (voice != null) voice.destroy();
        if (web != null) { ((android.view.ViewGroup)web.getParent()).removeView(web); web.destroy(); }
        worker.shutdown();
        if (conversation != null) conversation.close();
        super.onDestroy();
    }
}
