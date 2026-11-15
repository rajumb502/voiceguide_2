package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

public final class VoiceEngineManager implements RecognitionListener {
    public interface Callback { void command(String text); void feedback(String text); }
    private final Callback callback;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean ready, destroyed;
    private String queued;
    private Runnable afterSpeech;
    private long utterance;
    private boolean listening, answeringQuestion;
    private int automaticRetries;
    private String answerLocale;
    public VoiceEngineManager(Context context, Callback callback) {
        this.callback = callback;
        Context app = context.getApplicationContext();
        if (Looper.myLooper() == Looper.getMainLooper()) initialize(app); else main.post(() -> initialize(app));
    }
    private void initialize(Context context) {
        if (destroyed) return;
        try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
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
    public void listen(String locale) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> listen(locale)); return; }
        if (destroyed) return;
        afterSpeech = null; queued = null; utterance++;
        if (recognizer == null) { callback.feedback("Speech recognition is unavailable on this device."); return; }
        tts.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        setLanguage(locale);
        listening = false; recognizer.cancel();
        long attempt = utterance;
        callback.feedback("Starting microphone…");
        main.postDelayed(() -> {
            if (destroyed || attempt != utterance) return;
            try { listening = true; recognizer.startListening(intent); }
            catch (RuntimeException e) { listening = false; speak("Could not start the microphone. Check microphone permission and speech recognition settings."); }
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
        if (recognizer != null) recognizer.cancel(); if (tts != null) tts.stop();
    }
    public void destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::destroy); return; }
        if (destroyed) return;
        stop(); destroyed = true;
        if (recognizer != null) recognizer.destroy(); if (tts != null) tts.shutdown();
    }
    @Override public void onResults(Bundle results) {
        if (destroyed || !listening) return;
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) { answeringQuestion = false; callback.command(matches.get(0)); }
        else retryQuestionOrSpeak("I did not hear a command. Tap Speak and try again.");
    }
    @Override public void onError(int error) {
        if (destroyed || !listening || error == SpeechRecognizer.ERROR_CLIENT) return;
        listening = false;
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            retryQuestionOrSpeak(answeringQuestion ? "I did not hear clearly. Tap Speak, wait for the tone, then say your answer. The question is still pending until it expires." : "I did not hear clearly. Tap Speak, wait for the tone, then say your command."); return;
        }
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Microphone access is missing. Tap Speak to grant permission."; break;
            case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Speech recognition could not connect. Check your connection and tap Speak to try again."; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "The microphone is busy. Wait a moment, then tap Speak again."; break;
            default: message = "I did not hear clearly. Tap Speak, wait for the tone, then say your command.";
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
