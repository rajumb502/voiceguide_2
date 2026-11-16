package com.accessibility.voicecompanion;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { settings = new AssistantSettings(this); conversation = new ConversationMemory(this); }
        catch (IllegalStateException e) { Toast.makeText(this,"Encrypted storage is unavailable. Saved data has been kept; please try again.",Toast.LENGTH_LONG).show(); finish(); return; }
        locale = settings.locale();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 24, 12, 12); setContentView(root);
        status = new TextView(this); status.setTextSize(20); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        LinearLayout voiceControls = new LinearLayout(this); root.addView(voiceControls);
        button(voiceControls, "Speak", v -> listen());
        address = new EditText(this); address.setSingleLine(); address.setHint("Website address, for example https://example.com"); address.setInputType(17); root.addView(address);
        LinearLayout controls = new LinearLayout(this); root.addView(controls);
        button(controls, "Open", v -> open(address.getText().toString()));
        button(controls, "Stop", v -> { cancel(); voice.speak("Stopped. Pending actions cancelled."); });
        button(controls, "Settings", v -> settings());
        web = new WebView(this); root.addView(web, new LinearLayout.LayoutParams(-1,0,1));
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setGeolocationEnabled(false);
        web.addJavascriptInterface(bridge, "AndroidVoiceBridge");
        web.setOnTouchListener((v,event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                if (voice != null && voice.isSpeaking()) {
                    voice.interruptAndListen(locale);
                    return true;
                }
                if (agent.running()) {
                    cancel();
                    voice.speak("Task paused because you touched the page. Tap Speak to give a new instruction.");
                }
            }
            return false;
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
        });
        try (InputStream input = getAssets().open("page.js")) { pageScript = new String(readLimited(input, 100000), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new IllegalStateException(e); }
        boolean early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (early) WebViewCompat.addDocumentStartJavaScript(web, pageScript, Collections.singleton("*"));
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !isWebUrl(request.getUrl().toString()); }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                sharing.revoke();
                navigation++; pageLoading = true; loadStarted = SystemClock.elapsedRealtime(); pageFailed = false;
                if (agent.running() && agentActing) { requestId++; busy = false; guard.clear(); }
                else cancel();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) { pageFailed = true; cancel(); if (active) { voice.playErrorTone(); voice.speak("This website could not load. Tap Speak and say go back or open another website."); } }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) { pageFailed = true; cancel(); if (active) { voice.playErrorTone(); voice.speak("The website returned an error. Tap Speak and say go back or try another website."); } }
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoading = false;
                if (!early) view.evaluateJavascript(pageScript, null);
                address.setText(url);
                remember("Page loaded: " + (view.getTitle() == null ? Uri.parse(url).getHost() : view.getTitle()));
                if (active && !pageFailed) {
                    if (agent.running()) scheduleObservation(900);
                    else speakAudioLandmarkBrief(view, url);
                }
            }
        });
        voice = new VoiceEngineManager(this, this);
        voice.setTonesEnabled(settings.audioTonesEnabled());
        voice.speak("Welcome to Voice Companion. Tap Speak and say go to followed by a website address, or say help. Choose your AI provider and API key in Settings for questions about a page.");
        if (getIntent().getData() != null) open(getIntent().getData().toString()); else open("https://example.com");
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
                        voice.speak(brief);
                        return;
                    }
                }
            } catch (Exception ignored) {}
            voice.speak("Loaded " + host + ". Tap Speak and tell me what you want to do, or say help.");
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (guard.hasPending()) {
                    ActionSafetyGuard.Pending pending = guard.onVolumeUp(SystemClock.elapsedRealtime(), navigation);
                    if (pending != null && pendingProposal != null) {
                        voice.playSuccessChime();
                        agent.answer("Approved via hardware confirmation.");
                        JSONObject proposal = pendingProposal, snapshot = pendingSnapshot;
                        pendingProposal = pendingSnapshot = null;
                        executeAgentAction(proposal, snapshot, pending.navigation(), true);
                    }
                    return true;
                } else if (voice != null && voice.isSpeaking()) {
                    voice.interruptAndListen(locale);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (guard.hasPending()) {
                    guard.onVolumeDown(SystemClock.elapsedRealtime());
                    cancel();
                    voice.playErrorTone();
                    voice.speak("Action cancelled.");
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    void simulateVoiceInput(String spokenText) {
        command(spokenText);
    }

    boolean simulateKeyEvent(KeyEvent event) {
        return dispatchKeyEvent(event);
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
        if (isWebUrl(url)) web.loadUrl(url); else voice.speak("Please enter an HTTP or HTTPS website address.");
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (intent.getData() != null) open(intent.getData().toString()); }
    private void listen() {
        if (agent.running() && !sharing.pending()) {
            agent.ask(); requestId++; busy = false; agentActing = false;
            if (continuation != null) main.removeCallbacks(continuation);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        else voice.listen(locale);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listen();
        else voice.speak("Microphone permission is needed for voice commands. Enable it in Android app settings, then tap Speak.");
    }
    @Override public void feedback(String text) { status.setText(text); }
    private void cancel() {
        sharing.revoke();
        if (voice != null) voice.stopThinkingPulse();
        requestId++; busy = false; agentActing = false; agent.stop(); guard.clear(); pendingProposal = pendingSnapshot = null;
        if (continuation != null) main.removeCallbacks(continuation);
    }
    @Override public void command(String text) {
        if (guard.hasPending()) {
            ActionSafetyGuard.SpokenResult spoken = guard.evaluateSpoken(text, SystemClock.elapsedRealtime(), navigation);
            if (spoken == ActionSafetyGuard.SpokenResult.ABORT) {
                cancel();
                voice.playErrorTone();
                voice.speak("Action cancelled.");
            } else if (spoken == ActionSafetyGuard.SpokenResult.EXPIRED) {
                cancel();
                voice.playErrorTone();
                voice.speak("Confirmation timed out for your safety. Please repeat your request.");
            } else {
                voice.speak("Spoken confirmation is not accepted for sensitive actions. Double-press Volume Up to confirm, or press Volume Down to cancel.");
            }
            return;
        }
        if (VoiceCommands.normalize(text).matches("stop|cancel|no|नहीं")) { cancel(); voice.speak("Stopped."); return; }
        if (sharing.pending()) {
            SharingConsent.Reply reply = sharing.evaluate(text,navigation,SystemClock.elapsedRealtime());
            if (reply.status() == SharingConsent.Status.ALLOWED) {
                try {
                    settings.approveSite(settings.provider(),Uri.parse(web.getUrl()).getHost());
                    requestAssistant(reply.command());
                } catch (IllegalStateException | IllegalArgumentException e) { cancel(); voice.speak("I could not save this site's sharing approval. Nothing was sent. Please try again or review Settings."); }
            }
            else if (reply.status() == SharingConsent.Status.UNCLEAR) {
                if (!localCommand(text)) voice.ask("Say yes to remember this site's sharing approval for " + settings.provider() + ", or no to stay local. You can remove approvals in Settings.",locale);
            } else { cancel(); voice.speak("Page sharing cancelled or expired. Local reading and navigation are available."); }
            return;
        }
        if (preferenceCommand(text)) return;
        if (localCommand(text)) return;
        if (agent.waiting()) { remember("User: " + text); agent.answer(text); requestAssistant(agent.goal()); return; }
        if (busy) { voice.speak("I am still checking. Say stop to cancel."); return; }
        if (!settings.sharingEnabled()) { voice.speak("AI page sharing is off. You can enable it in Settings, or use local page reading and navigation."); return; }
        agent.start(text);
        remember("User goal or clarification: " + text);
        requestAssistant(agent.goal());
    }
    private void requestAssistant(String text) {
        if (!active || !settings.sharingEnabled() || !agent.running()) return;
        if (pageLoading) { scheduleObservation(500); return; }
        String site = Uri.parse(web.getUrl() == null ? "" : web.getUrl()).getHost();
        if (site == null) { cancel(); voice.speak("Open a website before asking the assistant."); return; }
        if (!sharing.allows(site,settings.provider(),navigation) && settings.siteSharingAllowed(site,settings.provider())) sharing.grant(site,settings.provider(),navigation);
        if (!sharing.allows(site,settings.provider(),navigation)) {
            if (!sharing.pending()) {
                sharing.request(text,navigation,SystemClock.elapsedRealtime(),site,settings.provider());
                voice.ask("Allow sharing screen details on " + site + " with the AI assistant? Say Yes or No.",locale);
            }
            return;
        }
        if (!agent.nextStep()) { voice.speak("I reached the task's step limit. Tap Speak to give a new instruction."); return; }
        agentActing = false;
        final String provider = settings.provider(), model = settings.model(provider), apiKey;
        try { apiKey = settings.apiKey(provider); }
        catch (Exception e) { cancel(); voice.speak("Could not read your API key. Open Settings and enter it again."); return; }
        if (apiKey.isEmpty()) { cancel(); voice.speak("Set your " + provider + " API key in Settings first."); return; }
        busy = true; final long job = ++requestId, nav = navigation;
        voice.startThinkingPulse();
        if (agent.steps() == 1) voice.speak("I will work on that. You can tap Speak to interrupt me, or Stop to cancel.");
        web.evaluateJavascript("window.__vc ? window.__vc.snapshot() : null", raw -> {
            try {
                if (!valid(job, nav)) { voice.stopThinkingPulse(); return; }
                Object decoded = new JSONTokener(raw).nextValue();
                if (!(decoded instanceof String)) throw new JSONException("No page snapshot");
                JSONObject snapshot = new JSONObject((String) decoded);
                if (!sharing.allows(snapshot.getString("site"),provider,nav)) { voice.stopThinkingPulse(); cancel(); voice.speak("The page changed. Please ask again."); return; }
                JSONObject payload = new JSONObject().put("command", text).put("snapshot", snapshot).put("conversation_history", conversation.snapshot(snapshot.getString("site"))).put("current_page",ConversationMemory.page(web.getUrl(),web.getTitle(),navigation)).put("task_history",new JSONArray(agent.history()));
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
                            voice.stopThinkingPulse();
                            if (valid(job, nav)) { busy = false; apply(response, snapshot, nav); }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            voice.stopThinkingPulse();
                            voice.playErrorTone();
                            if (valid(job,nav)) { cancel(); voice.speak(e instanceof AssistantClient.RequestFailure ? e.getMessage() : "The assistant could not connect. You can still say scroll down, go back, read this page, or open app followed by an app name."); }
                        });
                    }
                });
            } catch (Exception e) {
                voice.stopThinkingPulse();
                voice.playErrorTone();
                cancel();
                voice.speak("Could not inspect this page. Try reloading it.");
            }
        });
    }
    private boolean preferenceCommand(String text) {
        String normalized = VoiceCommands.normalize(text);
        if (normalized.matches(".*(turn on|enable|switch on)\\s+(tone|tones|sound|audio tones).*")) {
            settings.saveAudioTones(true);
            voice.setTonesEnabled(true);
            voice.speak("Audio tones enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable|switch off|mute)\\s+(tone|tones|sound|audio tones).*")) {
            settings.saveAudioTones(false);
            voice.setTonesEnabled(false);
            voice.speak("Audio tones turned off.");
            return true;
        }
        if (normalized.matches(".*(turn on|enable|allow)\\s+(page sharing|sharing).*")) {
            settings.saveSharingEnabled(true);
            voice.speak("Page sharing enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable|stop)\\s+(page sharing|sharing).*")) {
            settings.saveSharingEnabled(false);
            voice.speak("Page sharing turned off.");
            return true;
        }
        if (normalized.matches(".*(turn on|enable|include)\\s+(screenshot|screenshots).*")) {
            settings.saveScreenshotsEnabled(true);
            voice.speak("Page screenshots enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable|stop)\\s+(screenshot|screenshots).*")) {
            settings.saveScreenshotsEnabled(false);
            voice.speak("Page screenshots turned off.");
            return true;
        }
        if (normalized.matches(".*(turn on|enable)\\s+(remember preferences|remembering preferences).*")) {
            settings.saveRememberPreferences(true);
            voice.speak("Remembering preferences enabled.");
            return true;
        }
        if (normalized.matches(".*(turn off|disable)\\s+(remember preferences|remembering preferences).*")) {
            settings.saveRememberPreferences(false);
            voice.speak("Remembering preferences turned off.");
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(hindi|hi|hi-in).*")) {
            settings.saveLocale("hi-IN");
            locale = "hi-IN";
            voice.setLanguage("hi-IN");
            voice.speak("Language changed to Hindi.");
            return true;
        }
        if (normalized.matches(".*(set language|change language|language)\\s+(to\\s+)?(english|en|en-in).*")) {
            settings.saveLocale("en-IN");
            locale = "en-IN";
            voice.setLanguage("en-IN");
            voice.speak("Language changed to Indian English.");
            return true;
        }
        if (normalized.matches(".*(set provider|change provider|use)\\s+(gemini|google).*")) {
            settings.saveProvider(AssistantSettings.GEMINI);
            voice.speak("AI provider set to Gemini.");
            return true;
        }
        if (normMatches(normalized, "(set provider|change provider|use)\\s+(openai|chatgpt)")) {
            settings.saveProvider(AssistantSettings.OPENAI);
            voice.speak("AI provider set to OpenAI.");
            return true;
        }
        if (normalized.matches("forget my preferences|clear my preferences")) {
            try { settings.saveUserPreferences(""); voice.speak("Saved preferences cleared."); }
            catch (Exception e) { voice.speak("I could not clear the saved preferences. Please try again in Settings."); }
            return true;
        }
        if (normalized.matches("what do you remember about me|read my preferences|my preferences")) {
            voice.speak(settings.userPreferences().isEmpty() ? "You have no saved preferences." : "Your saved preferences: " + settings.userPreferences()); return true;
        }
        UserPreferences.Update update = UserPreferences.feedback(text);
        if (update == null) return false;
        if (!settings.rememberPreferences()) { voice.speak("Remembering preferences is off. You can enable it in Settings."); return true; }
        try {
            settings.saveUserPreferences(UserPreferences.merge(settings.userPreferences(),update));
            voice.speak("I will remember: " + update.text() + ". You can review or change this in Settings.");
        } catch (Exception e) { voice.speak("I could not save that preference. Please try again in Settings."); }
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
                    if (match != null && !match.activityInfo.packageName.equals(candidate.activityInfo.packageName)) { voice.speak("More than one app has that name. Say open app followed by its package name."); return true; }
                    match = candidate;
                }
            }
            if (match == null) voice.speak("I could not find an installed app with that name.");
            else {
                try { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(match.activityInfo.packageName,match.activityInfo.name)); }
                catch (RuntimeException e) { voice.speak("That app could not be opened."); }
            }
            return true;
        }
        String url = VoiceCommands.navigationUrl(text);
        if (url != null) { cancel(); voice.speak("Opening " + Uri.parse(url).getHost()); open(url); return true; }
        switch (command) {
            case "clear conversation": case "forget conversation":
                cancel();
                try { conversation.clear(); voice.speak("Conversation cleared. I have forgotten previous page descriptions and requests."); }
                catch (IllegalStateException e) { voice.speak("Could not clear saved conversation. Please try again."); }
                return true;
            case "help": case "what can i say": case "what can you do":
                cancel(); voice.speak(VoiceCommands.HELP); return true;
            case "settings": case "open settings": settings(); return true;
            case "go back": case "back":
                cancel(); if (web.canGoBack()) web.goBack(); else voice.speak("There is no previous page."); return true;
            case "scroll down": case "scroll up":
                cancel();
                boolean moved = command.equals("scroll down") ? web.pageDown(false) : web.pageUp(false);
                voice.speak(moved ? "Scrolled. Tap Speak and say read this page or list links." : "You have reached the edge of this page."); return true;
            case "read this page": case "read page": case "read the page": case "where am i":
                readPage("page"); return true;
            case "find search box": case "find search":
                cancel(); final long searchJob = requestId, searchNav = navigation;
                web.evaluateJavascript("window.__vc ? window.__vc.findSearch() : null", raw -> {
                    if (!valid(searchJob,searchNav)) return;
                    try {
                        Object result = new JSONTokener(raw).nextValue();
                        voice.speak(result instanceof String ? "I found the search field: " + result + ". It is now in view. Tell me what you want to search for."
                            : "I could not identify a search field in the accessible page. It may be inside a menu or an inaccessible frame.");
                    } catch (Exception e) { voice.speak("I could not inspect the search fields. Please try again."); }
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
                spoken.append("Tap Speak to ask about an item, or say scroll down to explore more."); remember("Read aloud: " + spoken); voice.speak(spoken.toString());
            } catch (Exception e) { voice.speak("The page is not ready to read. Try again after it loads."); }
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
                if (SystemClock.elapsedRealtime()-loadStarted > 20000) { cancel(); voice.speak("The page is taking too long. Please try again when it has loaded."); }
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
                voice.playSuccessChime();
                remember("Assistant: " + speech);
                agent.stop();
                voice.speak(speech);
                return;
            }
            if (action.equals("ASK")) { agent.ask(); remember("Assistant question: " + speech); voice.ask(speech,locale); return; }
            if (action.equals("NAVIGATE") && !AgentPolicy.navigationAllowed(agent.userInstructions(),response.getString("url"),snapshot)) {
                remember("Assistant: I could not verify that destination from the current page. I will inspect the page again.");
                voice.speak("I could not verify that destination from the current page. I will inspect the page again.");
                recordOutcome(response,"Navigation refused: destination is neither user-authorized nor a current-page link. Inspect the existing page and its links. Do not ask which page for a follow-up about something already described.",false);
                scheduleObservation(600); return;
            }
            if (AgentPolicy.needsConfirmation(response,snapshot)) {
                pendingProposal = response; pendingSnapshot = snapshot;
                guard.queue(new ActionSafetyGuard.Pending(response.getString("target_id"),snapshot.getLong("revision"),nav,SystemClock.elapsedRealtime(),response.getString("action_summary")));
                main.postDelayed(() -> {
                    if (guard.hasPending() && pendingProposal == response) {
                        cancel();
                        voice.playErrorTone();
                        voice.speak("Confirmation timed out for your safety. Please repeat your request.");
                    }
                },ActionSafetyGuard.TIMEOUT_MS);
                String question = "High-stakes action: " + response.getString("action_summary") + ". Double-press Volume Up to confirm, or press Volume Down to cancel. Spoken confirmation is not accepted.";
                remember("Assistant confirmation: " + question);
                agent.ask(); voice.speak(question); return;
            }
            executeAgentAction(response,snapshot,nav,false);
        } catch (Exception e) { cancel(); voice.playErrorTone(); voice.speak("The assistant returned an invalid action. The task stopped."); }
    }
    private void executeAgentAction(JSONObject action, JSONObject snapshot, long nav, boolean approved) {
        if (!active || !agent.running() || navigation != nav) { cancel(); voice.speak("The page changed. Please repeat your request."); return; }
        try {
            String kind = action.getString("action");
            remember("Assistant progress: " + action.getString("spoken_feedback"));
            voice.speak(action.getString("spoken_feedback"));
            busy = true; agentActing = true;
            if (kind.equals("NAVIGATE")) { recordOutcome(action,"Navigation requested; inspect the loaded page.",true); if (!agent.running()) return; web.loadUrl(action.getString("url")); scheduleObservation(1500); return; }
            if (kind.equals("BACK")) {
                boolean possible = web.canGoBack(); recordOutcome(action,possible ? "Back requested; inspect the resulting page." : "No previous page.",possible);
                if (possible) web.goBack(); scheduleObservation(1200); return;
            }
            if (kind.equals("SCROLL")) {
                boolean moved = action.getString("direction").equals("down") ? web.pageDown(false) : web.pageUp(false);
                recordOutcome(action,moved ? "Viewport scrolled." : "Already at the page edge.",moved); scheduleObservation(700); return;
            }
            if (kind.equals("WAIT")) { recordOutcome(action,"Waited for the page; inspect again.",true); scheduleObservation(1500); return; }
            final long actionTicket = requestId;
            web.evaluateJavascript("window.__vc && window.__vc.perform(" + action.toString() + "," + snapshot.getLong("revision") + "," + approved + ")", result -> {
                if (!active || !agent.running() || requestId != actionTicket || navigation != nav) return;
                boolean success = "true".equals(result);
                if (success) voice.playSuccessChime();
                recordOutcome(action,success ? "Local operation dispatched. Verify the resulting page before reporting success." : "Target changed, was blocked, or operation failed. Inspect again and choose another approach.",success);
                scheduleObservation(1000);
            });
        } catch (Exception e) { cancel(); voice.playErrorTone(); voice.speak("The browser could not perform that operation. The task stopped."); }
    }
    private void recordOutcome(JSONObject action, String outcome, boolean success) {
        busy = false;
        boolean proceed = agent.record(action.optString("action") + " " + action.optString("target_id") + " " + action.optString("direction") + " " + action.optString("url"),action.optString("action_summary") + ". " + outcome,success);
        if (!proceed) { requestId++; agentActing = false; voice.playErrorTone(); voice.speak("I could not make reliable progress, so I stopped. Tap Speak to change the request or try another approach."); }
    }
    private void remember(String text) {
        String role = text.startsWith("User") ? "user" : text.startsWith("Page loaded:") ? "page" : "assistant";
        try { conversation.add(role,text,web == null ? null : web.getUrl(),web == null ? null : web.getTitle(),navigation); }
        catch (IllegalStateException e) { Toast.makeText(this,"Could not save conversation on this device.",Toast.LENGTH_LONG).show(); }
    }
    private void settings() {
        cancel(); voice.stop();
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
    }
    @Override protected void onPause() { active = false;
        cancel(); if (voice != null) voice.stop(); super.onPause(); }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (voice != null) voice.destroy();
        if (web != null) { ((android.view.ViewGroup)web.getParent()).removeView(web); web.destroy(); }
        worker.shutdown();
        if (conversation != null) conversation.close();
        super.onDestroy();
    }
}
