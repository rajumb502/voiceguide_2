package com.accessibility.voicecompanion;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Clean, accessible settings UI with Keystore-backed encryption. */
public class SettingsActivity extends Activity implements VoiceEngineManager.Callback {
    public static final String[] INDIAN_LANGUAGES = new String[]{
        "en-IN", "hi-IN", "kn-IN", "ta-IN", "mr-IN", "te-IN", "bn-IN"
    };

    private VoiceEngineManager voice;
    private int setupAttempted;
    private boolean setupRunning = true;
    private Runnable setupAction;
    private AssistantSettings settings;
    private String current;
    private AutoCompleteTextView model, language;
    private EditText key;
    private TextView keyStatus, feedback;
    private Switch sharing, screenshots, remember, audioTones, allSites;
    private EditText sites, notes, apps;
    private Spinner provider;
    private final HashMap<String,String> models = new HashMap<>(), keys = new HashMap<>();
    private final HashMap<String,String> siteDrafts = new HashMap<>();
    private final HashMap<String,String> appDrafts = new HashMap<>();
    private final HashMap<String,Boolean> allSiteDrafts = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        try { settings = new AssistantSettings(this); }
        catch (IllegalStateException e) { Toast.makeText(this,"Encrypted settings are unavailable. Saved data has been kept; please try again.",Toast.LENGTH_LONG).show(); finish(); return; }
        if (state != null) { setupAttempted = state.getInt("setup_attempted"); setupRunning = state.getBoolean("setup_running",true); }
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,48,24,24);
        scroll.addView(root); setContentView(scroll);
        label(root, "Assistant settings").setTextSize(26);
        Button setup = new Button(this); setup.setText("Continue voice guided setup"); root.addView(setup);
        setup.setOnClickListener(v -> { setupRunning = true; if (setupAction != null) runSetupAction(); else { setupAttempted = 0; advanceSetup(); } });
        Button skip = new Button(this); skip.setText("Skip permission setup"); root.addView(skip);
        skip.setOnClickListener(v -> { setupRunning = false; setupAction = null; voice.speak("Permission setup skipped. You can continue from this button later."); });
        label(root, "Choose a provider and a model that supports images and structured JSON. Requests use your API account and may incur charges.");
        label(root, "Provider"); provider = new Spinner(this);
        provider.setContentDescription("AI provider"); provider.setMinimumHeight(56);
        provider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{AssistantSettings.GEMINI, AssistantSettings.OPENAI})); root.addView(provider);
        TextView modelLabel = label(root, "Model ID (choose a suggestion or type your own)");
        model = new AutoCompleteTextView(this); model.setId(View.generateViewId()); modelLabel.setLabelFor(model.getId());
        model.setSingleLine(); model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); root.addView(model);
        model.setOnClickListener(v -> model.showDropDown());
        TextView keyLabel = label(root, "API key"); key = new EditText(this); key.setId(View.generateViewId()); keyLabel.setLabelFor(key.getId());
        key.setSingleLine(); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (Build.VERSION.SDK_INT >= 26) key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setSaveEnabled(false); root.addView(key);
        keyStatus = label(root, "");
        Button remove = new Button(this); remove.setText("Remove saved key for this provider"); root.addView(remove);
        remove.setOnClickListener(v -> { settings.removeKey(current); keys.remove(current); key.setText(""); updateKeyStatus(); feedback.setText("Saved key removed."); });
        TextView languageLabel = label(root, "Speech language (for example en-IN, hi-IN, kn-IN, ta-IN, mr-IN)");
        language = new AutoCompleteTextView(this); language.setId(View.generateViewId()); languageLabel.setLabelFor(language.getId());
        language.setSingleLine(); language.setText(settings.locale());
        language.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, INDIAN_LANGUAGES));
        language.setOnClickListener(v -> language.showDropDown());
        root.addView(language);

        audioTones = new Switch(this); audioTones.setText("Play audio earcon tones (beep, thinking pulse, chime)");
        audioTones.setChecked(settings.audioTonesEnabled()); root.addView(audioTones);

        sharing = new Switch(this); sharing.setText("Allow sending page details and conversation to the selected AI provider"); sharing.setChecked(settings.sharingEnabled()); root.addView(sharing);
        screenshots = new Switch(this); screenshots.setText("Include page screenshots"); screenshots.setChecked(settings.screenshotsEnabled()); root.addView(screenshots);
        label(root,"Both sharing options default to Yes. Screenshots may show private text. Turn page sharing off to use only local voice commands. These settings apply to Gemini and OpenAI.");
        allSites = new Switch(this); allSites.setText("Allow all sites without asking for this provider"); root.addView(allSites);
        label(root,"Off by default. When enabled, any site can share page details, conversation, and enabled screenshots and saved preferences with the selected provider. Turning page sharing off still blocks all uploads. Sensitive-field protection and action confirmations still apply.");
        TextView sitesLabel = label(root,"Approved sites for this provider (one hostname per line)");
        sites = new EditText(this); sites.setId(View.generateViewId()); sitesLabel.setLabelFor(sites.getId()); sites.setMinLines(2);
        sites.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        sites.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) sites.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        root.addView(sites);
        label(root,"A spoken Yes remembers that exact hostname for this provider across visits and app restarts. Subdomains need their own approval. Add or remove hostnames here, then Save settings. Enter example.com, without a URL, path, port, or wildcard.");
        Button clearSites = new Button(this); clearSites.setText("Clear sharing approvals for this provider"); root.addView(clearSites);
        clearSites.setOnClickListener(v -> { sites.setText(""); allSites.setChecked(false); voice.speak("Sharing approvals cleared for this provider. Save settings to apply."); });

        TextView appsLabel = label(root,"Approved Android apps for this provider (one package name per line)");
        apps = new EditText(this); apps.setId(View.generateViewId()); appsLabel.setLabelFor(apps.getId()); apps.setMinLines(2);
        apps.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        apps.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) apps.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        root.addView(apps);
        label(root,"Allowed native apps default to none. Banking and UPI apps (PhonePe, GPay, Paytm, SBI YONO, BHIM) cannot be approved. Example: com.whatsapp, com.android.contacts.");
        Button clearApps = new Button(this); clearApps.setText("Clear app approvals for this provider"); root.addView(clearApps);
        clearApps.setOnClickListener(v -> { apps.setText(""); voice.speak("App approvals cleared for this provider. Save settings to apply."); });

        remember = new Switch(this); remember.setText("Remember and use my feedback preferences"); remember.setChecked(settings.rememberPreferences()); root.addView(remember);
        TextView notesLabel = label(root,"Saved preferences (one per line)");
        notes = new EditText(this); notes.setId(View.generateViewId()); notesLabel.setLabelFor(notes.getId()); notes.setMinLines(3);
        notes.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) notes.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)}); notes.setText(settings.userPreferences()); root.addView(notes);
        label(root,"Say remember that followed by a preference, or keep answers short. Preferences are encrypted on this device and included with AI requests only when enabled and you consent for the current site. Page content is never automatically saved as a preference.");
        Button clearPreferences = new Button(this); clearPreferences.setText("Clear saved preferences"); clearPreferences.setOnClickListener(v -> notes.setText("")); root.addView(clearPreferences);
        label(root, "API keys are encrypted on this device. Page labels and screenshots go directly to the selected provider. Saved preferences are included when remembering preferences is enabled.");
        feedback = label(root, ""); feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Button save = new Button(this); save.setText("Save settings"); root.addView(save);
        save.setOnClickListener(v -> {
            try {
                String secret = key.getText().toString().trim();
                java.util.Set<String> approved = SharingConsent.sites(sites.getText().toString());
                java.util.Set<String> approvedPkgs = parsePackages(apps.getText().toString());
                settings.save(current, model.getText().toString().trim(), secret, language.getText().toString().trim());
                settings.saveGeneral(sharing.isChecked(),screenshots.isChecked(),remember.isChecked(),audioTones.isChecked(),notes.getText().toString());
                settings.saveSharingConsent(current,allSites.isChecked(),approved);
                settings.saveApprovedPackages(current, approvedPkgs);
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show(); finish();
            } catch (IllegalArgumentException e) { feedback.setText(e.getMessage()); }
            catch (Exception e) { feedback.setText("Could not save all settings. Please try again."); }
        });
        Button back = new Button(this); back.setText("Back without saving"); back.setOnClickListener(v -> finish()); root.addView(back);
        provider.setSelection(settings.provider().equals(AssistantSettings.OPENAI) ? 1 : 0);
        provider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (current != null) {
                    models.put(current, model.getText().toString()); keys.put(current, key.getText().toString());
                    siteDrafts.put(current,sites.getText().toString()); allSiteDrafts.put(current,allSites.isChecked());
                    appDrafts.put(current, apps.getText().toString());
                }
                current = position == 0 ? AssistantSettings.GEMINI : AssistantSettings.OPENAI;
                String[] suggestions = current.equals(AssistantSettings.OPENAI) ? new String[]{"gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini"} : new String[]{"gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro"};
                model.setAdapter(new ArrayAdapter<>(SettingsActivity.this, android.R.layout.simple_dropdown_item_1line, suggestions));
                model.setText(models.containsKey(current) ? models.get(current) : settings.model(current), false);
                key.setText(keys.containsKey(current) ? keys.get(current) : ""); updateKeyStatus();
                sites.setText(siteDrafts.containsKey(current) ? siteDrafts.get(current) : String.join("\n",settings.approvedSites(current)));
                apps.setText(appDrafts.containsKey(current) ? appDrafts.get(current) : String.join("\n",settings.approvedPackages(current)));
                allSites.setChecked(allSiteDrafts.containsKey(current) ? allSiteDrafts.get(current) : settings.allSitesAllowed(current));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        voice = new VoiceEngineManager(this,this);
        voice.setTonesEnabled(settings.audioTonesEnabled());
        voice.setLanguage(settings.locale());
        audioTones.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.saveAudioTones(isChecked);
            voice.setTonesEnabled(isChecked);
        });
        if (state == null) guideSetup();
    }

    private static Set<String> parsePackages(String text) {
        Set<String> set = new TreeSet<>();
        if (text == null || text.trim().isEmpty()) return set;
        for (String p : text.trim().split("\\s+")) {
            String pkg = p.trim().toLowerCase(Locale.ROOT);
            if (!pkg.isEmpty()) {
                if (SharingConsent.isBankingPackage(pkg)) {
                    throw new IllegalArgumentException("Cannot approve banking or payment app: " + pkg);
                }
                set.add(pkg);
            }
        }
        return set;
    }

    private void runSetupAction() {
        Runnable a = setupAction; setupAction = null;
        if (a != null) a.run();
    }

    private void guideSetup() {
        if (!setupRunning) return;
        voice.guide("Welcome to Voice Companion setup. Say start setup to configure required permissions, or skip setup to configure later.", () -> voice.listen(settings.locale()));
    }

    private void advanceSetup() {
        if (!setupRunning) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            setupAction = () -> requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
            voice.guide("Step 1. Microphone permission is needed so you can speak to the assistant. Say allow to open the permission dialog.", () -> voice.listen(settings.locale()));
            return;
        }
        if (!isAccessibilityServiceEnabled()) {
            if (Build.VERSION.SDK_INT >= 33 && setupAttempted > 0) {
                setupAction = () -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                };
                voice.guide("Accessibility service is blocked by Android restricted settings on sideloaded apps. Say allow to open App Info. Then tap the three dots in the top right corner and tap Allow restricted settings.", () -> voice.listen(settings.locale()));
                return;
            }
            setupAttempted++;
            setupAction = () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            voice.guide("Step 2. Accessibility Service is recommended for screen interaction. Say enable to open Android accessibility settings.", () -> voice.listen(settings.locale()));
            return;
        }
        if (!isDefaultHome()) {
            setupAction = () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            voice.guide("Step 3. You can set Voice Companion as your default home assistant. Say enable to open home app settings.", () -> voice.listen(settings.locale()));
            return;
        }
        setupRunning = false;
        voice.speak("All core permissions are configured. You can now adjust your AI provider or language settings, or say save settings to finish.");
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                return services != null && services.contains(getPackageName());
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isDefaultHome() {
        android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        java.util.List<android.content.IntentFilter> filters = new java.util.ArrayList<>();
        java.util.List<android.content.ComponentName> activities = new java.util.ArrayList<>();
        getPackageManager().getPreferredActivities(filters, activities, null);
        for (android.content.ComponentName name : activities) {
            if (getPackageName().equals(name.getPackageName())) return true;
        }
        return false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                voice.speak("Microphone permission granted.");
            } else {
                voice.speak("Microphone permission was not granted.");
            }
            advanceSetup();
        }
    }

    @Override public void command(String text) {
        String norm = VoiceCommands.normalize(text);
        if (norm.matches("skip|skip setup|stop")) {
            setupRunning = false; setupAction = null;
            voice.speak("Setup skipped.");
            return;
        }
        if (norm.matches("start setup|begin setup|setup")) {
            setupRunning = true;
            advanceSetup();
            return;
        }
        if (setupRunning && setupAction != null && norm.matches("allow|enable|open|yes|haan|proceed|continue")) {
            runSetupAction();
            return;
        }
        if (updateSettingByVoice(text)) return;
        voice.speak("Command not recognized. Say start setup, skip setup, or a setting command like turn off tones.");
    }

    boolean updateSettingByVoice(String text) {
        String norm = VoiceCommands.normalize(text);
        if (norm.matches(".*(turn on|enable|switch on)\\s+(tone|tones|sound|audio tones).*")) {
            audioTones.setChecked(true);
            settings.saveAudioTones(true);
            voice.setTonesEnabled(true);
            voice.speak("Audio tones enabled.");
            return true;
        }
        if (norm.matches(".*(turn off|disable|switch off|mute)\\s+(tone|tones|sound|audio tones).*")) {
            audioTones.setChecked(false);
            settings.saveAudioTones(false);
            voice.setTonesEnabled(false);
            voice.speak("Audio tones turned off.");
            return true;
        }
        if (norm.matches(".*(turn on|enable|allow)\\s+(page sharing|sharing).*")) {
            sharing.setChecked(true);
            settings.saveSharingEnabled(true);
            voice.speak("Page sharing enabled.");
            return true;
        }
        if (norm.matches(".*(turn off|disable|stop)\\s+(page sharing|sharing).*")) {
            sharing.setChecked(false);
            settings.saveSharingEnabled(false);
            voice.speak("Page sharing turned off.");
            return true;
        }
        if (norm.matches(".*(turn on|enable|include)\\s+(screenshot|screenshots).*")) {
            screenshots.setChecked(true);
            settings.saveScreenshotsEnabled(true);
            voice.speak("Page screenshots enabled.");
            return true;
        }
        if (norm.matches(".*(turn off|disable|stop)\\s+(screenshot|screenshots).*")) {
            screenshots.setChecked(false);
            settings.saveScreenshotsEnabled(false);
            voice.speak("Page screenshots turned off.");
            return true;
        }
        if (norm.matches(".*(turn on|enable)\\s+(remember preferences|remembering preferences).*")) {
            remember.setChecked(true);
            settings.saveRememberPreferences(true);
            voice.speak("Remembering preferences enabled.");
            return true;
        }
        if (norm.matches(".*(turn off|disable)\\s+(remember preferences|remembering preferences).*")) {
            remember.setChecked(false);
            settings.saveRememberPreferences(false);
            voice.speak("Remembering preferences turned off.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(hindi|hi|hi-in).*")) {
            language.setText("hi-IN");
            settings.saveLocale("hi-IN");
            voice.setLanguage("hi-IN");
            voice.speak("Language changed to Hindi.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(english|en|en-in).*")) {
            language.setText("en-IN");
            settings.saveLocale("en-IN");
            voice.setLanguage("en-IN");
            voice.speak("Language changed to Indian English.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(kannada|kn|kn-in).*")) {
            language.setText("kn-IN");
            settings.saveLocale("kn-IN");
            voice.setLanguage("kn-IN");
            voice.speak("Language changed to Kannada.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(tamil|ta|ta-in).*")) {
            language.setText("ta-IN");
            settings.saveLocale("ta-IN");
            voice.setLanguage("ta-IN");
            voice.speak("Language changed to Tamil.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(marathi|mr|mr-in).*")) {
            language.setText("mr-IN");
            settings.saveLocale("mr-IN");
            voice.setLanguage("mr-IN");
            voice.speak("Language changed to Marathi.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(telugu|te|te-in).*")) {
            language.setText("te-IN");
            settings.saveLocale("te-IN");
            voice.setLanguage("te-IN");
            voice.speak("Language changed to Telugu.");
            return true;
        }
        if (norm.matches(".*(set language|change language|language)\\s+(to\\s+)?(bengali|bangla|bn|bn-in).*")) {
            language.setText("bn-IN");
            settings.saveLocale("bn-IN");
            voice.setLanguage("bn-IN");
            voice.speak("Language changed to Bengali.");
            return true;
        }
        if (norm.matches(".*(set provider|change provider|use)\\s+(gemini|google).*")) {
            provider.setSelection(0);
            settings.saveProvider(AssistantSettings.GEMINI);
            voice.speak("AI provider set to Gemini.");
            return true;
        }
        if (norm.matches(".*(set provider|change provider|use)\\s+(openai|chatgpt).*")) {
            provider.setSelection(1);
            settings.saveProvider(AssistantSettings.OPENAI);
            voice.speak("AI provider set to OpenAI.");
            return true;
        }
        if (norm.matches(".*(allow|enable)\\s+whatsapp.*")) {
            settings.approvePackage(current, "com.whatsapp");
            apps.setText(String.join("\n", settings.approvedPackages(current)));
            voice.speak("WhatsApp approved for " + current + ".");
            return true;
        }
        if (norm.matches(".*(revoke|disable|remove)\\s+whatsapp.*")) {
            settings.revokePackage(current, "com.whatsapp");
            apps.setText(String.join("\n", settings.approvedPackages(current)));
            voice.speak("WhatsApp revoked for " + current + ".");
            return true;
        }
        if (norm.matches(".*(allow|enable)\\s+(phonepe|gpay|google pay|paytm|bhim|yono|sbi yono).*")) {
            voice.speak("Banking and payment apps cannot be shared with AI providers for your financial security.");
            return true;
        }
        if (norm.matches(".*(clear approvals|clear sites|clear approved sites).*")) {
            sites.setText("");
            allSites.setChecked(false);
            settings.saveSharingConsent(current, false, java.util.Collections.emptySet());
            voice.speak("Sharing approvals cleared.");
            return true;
        }
        if (norm.matches(".*(save settings|save|apply).*")) {
            try {
                String secret = key.getText().toString().trim();
                java.util.Set<String> approved = SharingConsent.sites(sites.getText().toString());
                java.util.Set<String> approvedPkgs = parsePackages(apps.getText().toString());
                settings.save(current, model.getText().toString().trim(), secret, language.getText().toString().trim());
                settings.saveGeneral(sharing.isChecked(),screenshots.isChecked(),remember.isChecked(),audioTones.isChecked(),notes.getText().toString());
                settings.saveSharingConsent(current,allSites.isChecked(),approved);
                settings.saveApprovedPackages(current, approvedPkgs);
                voice.speak("Settings saved.");
                finish();
                return true;
            } catch (Exception e) {
                voice.speak("Could not save settings. " + e.getMessage());
                return true;
            }
        }
        return false;
    }

    @Override public void feedback(String text) {
        feedback.setText(text);
    }

    private void updateKeyStatus() {
        try {
            boolean has = !settings.apiKey(current).isEmpty();
            keyStatus.setText(has ? "A key is saved for " + current + ". Type a new key to replace it." : "No key is saved for " + current + ".");
        } catch (Exception e) { keyStatus.setText("Could not read key status."); }
    }
    private TextView label(LinearLayout root, String text) {
        TextView view = new TextView(this); view.setText(text); view.setPadding(0,16,0,8); root.addView(view); return view;
    }
    @Override protected void onResume() {
        super.onResume();
        if (settings != null && voice != null) {
            voice.setLanguage(settings.locale());
            voice.setTonesEnabled(settings.audioTonesEnabled());
        }
        if (setupRunning && setupAttempted > 0) {
            advanceSetup();
        }
    }
    @Override protected void onPause() {
        if (voice != null) voice.stop();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (voice != null) voice.destroy();
        super.onDestroy();
    }
}
