package com.accessibility.voicecompanion;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.HashMap;

public final class SettingsActivity extends Activity {
    private AssistantSettings settings;
    private String current;
    private AutoCompleteTextView model;
    private EditText key, language;
    private TextView keyStatus, feedback;
    private final HashMap<String,String> models = new HashMap<>(), keys = new HashMap<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        settings = new AssistantSettings(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,48,24,24);
        scroll.addView(root); setContentView(scroll);
        label(root, "Assistant settings").setTextSize(26);
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
        Switch remember = new Switch(this); remember.setText("Remember and use my feedback preferences"); remember.setChecked(settings.rememberPreferences()); root.addView(remember);
        TextView notesLabel = label(root,"Saved preferences (one per line)");
        EditText notes = new EditText(this); notes.setId(View.generateViewId()); notesLabel.setLabelFor(notes.getId()); notes.setMinLines(3);
        notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)}); notes.setText(settings.userPreferences()); root.addView(notes);
        label(root,"Say remember that followed by a preference, or keep answers short. Preferences stay on this device across app restarts and are included with AI requests when enabled. Page content is never automatically saved as a preference.");
        Button clearPreferences = new Button(this); clearPreferences.setText("Clear saved preferences"); clearPreferences.setOnClickListener(v -> notes.setText("")); root.addView(clearPreferences);
        label(root, "API keys are encrypted on this device. Page labels and screenshots go directly to the selected provider. Saved preferences are included when remembering preferences is enabled.");
        feedback = label(root, ""); feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Button save = new Button(this); save.setText("Save settings"); root.addView(save);
        save.setOnClickListener(v -> {
            try {
                String secret = key.getText().toString().trim();
                settings.save(current, model.getText().toString().trim(), secret, language.getText().toString().trim());
                settings.saveGeneral(sharing.isChecked(),screenshots.isChecked(),remember.isChecked(),notes.getText().toString());
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show(); finish();
            } catch (IllegalArgumentException e) { feedback.setText(e.getMessage()); }
            catch (Exception e) { feedback.setText("Could not save all settings. Please try again."); }
        });
        Button back = new Button(this); back.setText("Back without saving"); back.setOnClickListener(v -> finish()); root.addView(back);
        provider.setSelection(settings.provider().equals(AssistantSettings.OPENAI) ? 1 : 0);
        provider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (current != null) { models.put(current, model.getText().toString()); keys.put(current, key.getText().toString()); }
                current = position == 0 ? AssistantSettings.GEMINI : AssistantSettings.OPENAI;
                String[] suggestions = current.equals(AssistantSettings.OPENAI) ? new String[]{"gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini"} : new String[]{"gemini-2.5-flash", "gemini-2.5-pro"};
                model.setAdapter(new ArrayAdapter<>(SettingsActivity.this, android.R.layout.simple_dropdown_item_1line, suggestions));
                model.setText(models.containsKey(current) ? models.get(current) : settings.model(current), false);
                key.setText(keys.containsKey(current) ? keys.get(current) : ""); updateKeyStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }
    private TextView label(LinearLayout root, String text) { TextView view = new TextView(this); view.setText(text); view.setTextSize(18); view.setPadding(0,16,0,8); root.addView(view); return view; }
    private void updateKeyStatus() {
        try { keyStatus.setText(settings.apiKey(current).isEmpty() ? "No saved key. Enter your key above." : "A key is saved. Leave the field blank to keep it, or enter a replacement."); }
        catch (Exception e) { keyStatus.setText("Saved key cannot be read. Remove it and enter a replacement."); }
    }
}
