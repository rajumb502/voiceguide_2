package com.accessibility.voicecompanion;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.HashMap;

public final class SettingsActivity extends Activity implements VoiceEngineManager.Callback {
    private VoiceEngineManager voice;
    private int setupAttempted;
    private boolean setupRunning = true;
    private Runnable setupAction;
    private AssistantSettings settings;
    private String current;
    private AutoCompleteTextView model;
    private EditText key, language;
    private TextView keyStatus, feedback;
    private final HashMap<String,String> models = new HashMap<>(), keys = new HashMap<>();
    private final HashMap<String,String> siteDrafts = new HashMap<>();
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
        label(root, "Provider"); Spinner provider = new Spinner(this);
        provider.setContentDescription("AI provider"); provider.setMinimumHeight(56);
        provider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{AssistantSettings.GEMINI, AssistantSettings.OPENAI})); root.addView(provider);
        TextView modelLabel = label(root, "Model ID (choose a suggestion or type your own)");
        model = new AutoCompleteTextView(this); model.setId(View.generateViewId()); modelLabel.setLabelFor(model.getId());
        model.setSingleLine(); model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); root.addView(model);
        model.setOnClickListener(v -> model.showDropDown());
        TextView keyLabel = label(root, "API key"); key = new EditText(this); key.setId(View.generateViewId()); keyLabel.setLabelFor(key.getId());
        key.setSingleLine(); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); key.setSaveEnabled(false); root.addView(key);
        keyStatus = label(root, "");
        Button remove = new Button(this); remove.setText("Remove saved key for this provider"); root.addView(remove);
        remove.setOnClickListener(v -> { settings.removeKey(current); keys.remove(current); key.setText(""); updateKeyStatus(); feedback.setText("Saved key removed."); });
        TextView languageLabel = label(root, "Speech language (for example en-IN or hi-IN)");
        language = new EditText(this); language.setId(View.generateViewId()); languageLabel.setLabelFor(language.getId()); language.setSingleLine(); language.setText(settings.locale()); root.addView(language);
        Switch sharing = new Switch(this); sharing.setText("Allow sending page details and conversation to the selected AI provider"); sharing.setChecked(settings.sharingEnabled()); root.addView(sharing);
        Switch screenshots = new Switch(this); screenshots.setText("Include page screenshots"); screenshots.setChecked(settings.screenshotsEnabled()); root.addView(screenshots);
        label(root,"Both sharing options default to Yes. Screenshots may show private text. Turn page sharing off to use only local voice commands. These settings apply to Gemini and OpenAI.");
        Switch allSites = new Switch(this); allSites.setText("Allow all sites without asking for this provider"); root.addView(allSites);
        label(root,"Off by default. When enabled, any site can share page details, conversation, and enabled screenshots and saved preferences with the selected provider. Turning page sharing off still blocks all uploads. Sensitive-field protection and action confirmations still apply.");
        TextView sitesLabel = label(root,"Approved sites for this provider (one hostname per line)");
        EditText sites = new EditText(this); sites.setId(View.generateViewId()); sitesLabel.setLabelFor(sites.getId()); sites.setMinLines(2);
        sites.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        sites.setSaveEnabled(false); sites.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); root.addView(sites);
        label(root,"A spoken Yes remembers that exact hostname for this provider across visits and app restarts. Subdomains need their own approval. Add or remove hostnames here, then Save settings. Enter example.com, without a URL, path, port, or wildcard.");
        Button clearSites = new Button(this); clearSites.setText("Clear sharing approvals for this provider"); root.addView(clearSites);
        clearSites.setOnClickListener(v -> { sites.setText(""); allSites.setChecked(false); voice.speak("Sharing approvals cleared for this provider. Save settings to apply."); });
        Switch remember = new Switch(this); remember.setText("Remember and use my feedback preferences"); remember.setChecked(settings.rememberPreferences()); root.addView(remember);
        TextView notesLabel = label(root,"Saved preferences (one per line)");
        EditText notes = new EditText(this); notes.setId(View.generateViewId()); notesLabel.setLabelFor(notes.getId()); notes.setMinLines(3);
        notes.setSaveEnabled(false); notes.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
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
                settings.save(current, model.getText().toString().trim(), secret, language.getText().toString().trim());
                settings.saveGeneral(sharing.isChecked(),screenshots.isChecked(),remember.isChecked(),notes.getText().toString());
                settings.saveSharingConsent(current,allSites.isChecked(),approved);
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
                }
                current = position == 0 ? AssistantSettings.GEMINI : AssistantSettings.OPENAI;
                String[] suggestions = current.equals(AssistantSettings.OPENAI) ? new String[]{"gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini"} : new String[]{"gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro"};
                model.setAdapter(new ArrayAdapter<>(SettingsActivity.this, android.R.layout.simple_dropdown_item_1line, suggestions));
                model.setText(models.containsKey(current) ? models.get(current) : settings.model(current), false);
                key.setText(keys.containsKey(current) ? keys.get(current) : ""); updateKeyStatus();
                sites.setText(siteDrafts.containsKey(current) ? siteDrafts.get(current) : String.join("\n",settings.approvedSites(current)));
                allSites.setChecked(allSiteDrafts.containsKey(current) ? allSiteDrafts.get(current) : settings.allSitesAllowed(current));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        voice = new VoiceEngineManager(this,this); voice.setLanguage(settings.locale());
    }
    private void advanceSetup() {
        if (!setupRunning || voice == null) return;
        setupAction = null;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && (setupAttempted & 1) == 0) {
            guideSetup(1,"First, allow microphone access for voice commands. Android will ask for permission. Choose while using the app.",
                () -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7)); return;
        }
        AccessibilityManager manager = (AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        boolean enabled = false;
        ComponentName service = new ComponentName(this,BackgroundVoiceService.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (service.equals(ComponentName.unflattenFromString(info.getId()))) enabled = true;
        }
        if (!enabled && (setupAttempted & 2) == 0) {
            guideSetup(2,"Next is optional accessibility setup. Browser voice commands work without it, and native app control is not available yet. In Accessibility settings, choose downloaded apps, then Voice Companion. Return here when finished.",
                () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); return;
        }
        android.content.pm.ResolveInfo home = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
        if ((home == null || !getPackageName().equals(home.activityInfo.packageName)) && (setupAttempted & 4) == 0) {
            guideSetup(4,"Finally, optional home app setup. Choose Voice Companion to open this voice browser when you press Home. You can keep your current launcher. Return here when finished.",
                () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS))); return;
        }
        setupRunning = false;
        voice.speak("Setup steps finished. You can review your choices with Continue voice guided setup. Provider and privacy settings are below.");
    }
    private void guideSetup(int step, String instruction, Runnable action) {
        setupAction = () -> { setupAttempted |= step; action.run(); };
        voice.guide(instruction + " You can also tap Continue voice guided setup.",this::runSetupAction);
    }
    private void runSetupAction() {
        Runnable action = setupAction; setupAction = null;
        if (action == null) return;
        try { action.run(); }
        catch (RuntimeException e) { voice.speak("This settings screen is unavailable. Tap Continue voice guided setup for the next step."); }
    }
    @Override public void command(String text) { if (SharingConsent.isYes(text)) runSetupAction(); else { setupRunning = false; setupAction = null; voice.speak("Setup paused."); } }
    @Override public void feedback(String text) { feedback.setText(text); }
    @Override protected void onResume() { super.onResume(); advanceSetup(); }
    @Override protected void onPause() { if (voice != null) voice.stop(); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code == 7) advanceSetup();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("setup_attempted",setupAttempted); state.putBoolean("setup_running",setupRunning); }
    @Override protected void onDestroy() { if (voice != null) voice.destroy(); super.onDestroy(); }
    private TextView label(LinearLayout root, String text) { TextView view = new TextView(this); view.setText(text); view.setTextSize(18); view.setPadding(0,16,0,8); root.addView(view); return view; }
    private void updateKeyStatus() {
        try { keyStatus.setText(settings.apiKey(current).isEmpty() ? "No saved key. Enter your key above." : "A key is saved. Leave the field blank to keep it, or enter a replacement."); }
        catch (Exception e) { keyStatus.setText("Saved key cannot be read. Remove it and enter a replacement."); }
    }
}
