package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

public final class VoiceEngineManager implements RecognitionListener {
    public interface Callback { void command(String text); void feedback(String text); }
    private final Callback callback;
    private final Context context;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean ready, destroyed, onDevice;
    private String queued;
    private Runnable afterSpeech;
    private long utterance;
    private boolean listening, answeringQuestion;
    private int automaticRetries;
    private String answerLocale;
    public VoiceEngineManager(Context context, Callback callback) {
        this.callback = callback;
        this.context = context.getApplicationContext();
        if (Looper.myLooper() == Looper.getMainLooper()) initialize(this.context); else main.post(() -> initialize(this.context));
    }
    private void initialize(Context context) {
        if (destroyed) return;
        try {
            onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
            if (Build.VERSION.SDK_INT >= 31 && onDevice) recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            else if (SpeechRecognizer.isRecognitionAvailable(context)) recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } catch (RuntimeException e) { callback.feedback("Speech recognition is unavailable on this device."); }
        if (recognizer != null) recognizer.setRecognitionListener(this);
        tts = new TextToSpeech(context, status -> main.post(() -> {
            if (destroyed) return;
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                setLanguage(answerLocale == null ? "en-IN" : answerLocale);
                if (queued != null) { String message = queued; queued = null; say(message); }
            } else callback.feedback("Speech output is unavailable. Check Android text-to-speech settings.");
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { main.post(() -> {
                if (!destroyed && id.equals(Long.toString(utterance)) && afterSpeech != null) {
                    Runnable next = afterSpeech; afterSpeech = null;
                    main.postDelayed(() -> { if (!destroyed && id.equals(Long.toString(utterance))) next.run(); }, 350);
                }
            }); }
            @Override public void onError(String id) { main.post(() -> { if (id.equals(Long.toString(utterance))) afterSpeech = null; }); }
        });
    }
    private void requestAudioFocus() {
        try {
            if (audioManager == null) audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    if (focusRequest == null) {
                        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                            .setOnAudioFocusChangeListener(focusChange -> {})
                            .build();
                    }
                    audioManager.requestAudioFocus(focusRequest);
                } else {
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
                }
            }
        } catch (RuntimeException ignored) {}
    }
    private void abandonAudioFocus() {
        try {
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else {
                    audioManager.abandonAudioFocus(null);
                }
            }
        } catch (RuntimeException ignored) {}
    }
    public void listen(String locale) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> listen(locale)); return; }
        if (destroyed) return;
        afterSpeech = null; queued = null; utterance++;
        if (recognizer == null) { callback.feedback("Speech recognition is unavailable on this device."); return; }
        tts.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
        if (onDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        setLanguage(locale);
        listening = false; recognizer.cancel();
        long attempt = utterance;
        callback.feedback("Starting microphone…");
        main.postDelayed(() -> {
            if (destroyed || attempt != utterance) return;
            try {
                requestAudioFocus();
                listening = true;
                recognizer.startListening(intent);
            } catch (RuntimeException e) {
                listening = false;
                abandonAudioFocus();
                speak("Could not start the microphone. Check microphone permission and speech recognition settings.");
            }
        }, 150);
    }
    public void speak(String text) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> speak(text)); return; }
        answeringQuestion = false; afterSpeech = null; say(text);
    }
    public void ask(String text, String locale) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> ask(text,locale)); return; }
        setLanguage(locale);
        answeringQuestion = true; automaticRetries = 1; answerLocale = locale;
        afterSpeech = () -> listen(locale);
        say(text);
    }
    public void guide(String text, Runnable next) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> guide(text,next)); return; }
        answeringQuestion = false; afterSpeech = next; say(text);
    }
    private void say(String text) {
        if (destroyed) return;
        utterance++; listening = false;
        abandonAudioFocus();
        if (recognizer != null) recognizer.cancel();
        callback.feedback(text);
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, Long.toString(utterance)); else queued = text;
    }
    public void setLanguage(String locale) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> setLanguage(locale)); return; }
        answerLocale = locale;
        if (ready && !destroyed) {
            Locale language = Locale.forLanguageTag(locale); tts.setLanguage(language);
            if (tts.getVoices() != null) for (android.speech.tts.Voice voice : tts.getVoices()) {
                if (!voice.isNetworkConnectionRequired() && voice.getLocale().equals(language)) { tts.setVoice(voice); break; }
            }
        }
    }
    public void stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::stop); return; }
        if (destroyed) return;
        answeringQuestion = false; listening = false; afterSpeech = null; utterance++; queued = null;
        abandonAudioFocus();
        if (recognizer != null) recognizer.cancel(); if (tts != null) tts.stop();
    }
    public void destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::destroy); return; }
        if (destroyed) return;
        stop(); destroyed = true;
        abandonAudioFocus();
        if (recognizer != null) recognizer.destroy(); if (tts != null) tts.shutdown();
    }
    @Override public void onResults(Bundle results) {
        abandonAudioFocus();
        if (destroyed || !listening) return;
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) { answeringQuestion = false; callback.command(matches.get(0)); }
        else retryQuestionOrSpeak("I did not hear a command. Tap Speak and try again.");
    }
    @Override public void onError(int error) {
        abandonAudioFocus();
        if (destroyed || !listening || error == SpeechRecognizer.ERROR_CLIENT) return;
        listening = false;
        Log.w("VoiceEngineManager","Speech recognition error " + error + ", on-device: " + onDevice);
        if (onDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
            onDevice = false;
            recognizer.destroy(); recognizer = null;
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context); recognizer.setRecognitionListener(this);
                    listen(answerLocale); return;
                }
            } catch (RuntimeException e) { Log.w("VoiceEngineManager","Could not start system speech recognition."); }
            speak("Offline speech is unavailable for this language, and system speech recognition could not start. Check Android speech recognition settings."); return;
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            retryQuestionOrSpeak(answeringQuestion ? "I did not hear clearly. Tap Speak, wait for the tone, then say your answer. The question is still pending until it expires." : "I did not hear clearly. Tap Speak, wait for the tone, then say your command."); return;
        }
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Microphone access is missing. Tap Speak to grant permission."; break;
            case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Speech recognition could not connect. Check your connection and tap Speak to try again."; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "The microphone is busy. Wait a moment, then tap Speak again."; break;
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED: case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                message = "Speech recognition is unavailable for " + answerLocale + ". Download its speech language pack in Android settings, or choose another speech language in the app's Settings."; break;
            case SpeechRecognizer.ERROR_AUDIO: message = "The microphone could not capture audio. Check your microphone and audio input settings, then tap Speak again."; break;
            default: message = "The speech recognition service could not complete the request. Check Android speech recognition settings, then tap Speak again.";
        }
        speak(message);
    }
    private void retryQuestionOrSpeak(String fallback) {
        if (answeringQuestion && automaticRetries > 0) {
            automaticRetries--;
            afterSpeech = () -> listen(answerLocale);
            say("I did not catch your answer. After the tone, say yes or no.");
        } else speak(fallback);
    }
    @Override public void onReadyForSpeech(Bundle b) {
        if (destroyed || !listening) return;
        callback.feedback("Listening. Say your command now.");
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
            main.postDelayed(tone::release, 200);
        } catch (RuntimeException ignored) { }
    }
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float value) {}
    @Override public void onBufferReceived(byte[] b) {}
    @Override public void onEndOfSpeech() { if (!destroyed && listening) callback.feedback("Recognizing your command…"); }
    @Override public void onPartialResults(Bundle b) {}
    @Override public void onEvent(int event, Bundle b) {}
}
