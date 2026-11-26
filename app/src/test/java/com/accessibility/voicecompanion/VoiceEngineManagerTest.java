package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSpeechRecognizer;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class VoiceEngineManagerTest {
    private VoiceEngineManager voice;
    private final List<String> feedback = new ArrayList<>(), commands = new ArrayList<>();
    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        ResolveInfo service = new ResolveInfo(); service.serviceInfo = new ServiceInfo();
        service.serviceInfo.packageName = "test.speech"; service.serviceInfo.name = "Recognition";
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(new Intent(RecognitionService.SERVICE_INTERFACE),service);
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true);
        voice = new VoiceEngineManager(context,new VoiceEngineManager.Callback() {
            @Override public void command(String text) { commands.add(text); }
            @Override public void feedback(String text) { feedback.add(text); }
        });
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).getOnInitListener().onInit(TextToSpeech.SUCCESS);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void tearDown() { voice.destroy(); }
    private void start() { voice.listen("en-IN"); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150)); }
    @Test public void unavailableOfflineLanguageFallsBackAndDeliversCommand() {
        for (int error : new int[]{SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE}) {
            if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) { voice.destroy(); setUp(); }
            start();
            SpeechRecognizer offline = ShadowSpeechRecognizer.getLatestSpeechRecognizer();
            assertTrue(shadowOf(offline).getLastRecognizerIntent().getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false));
            shadowOf(offline).triggerOnError(error);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
            SpeechRecognizer system = ShadowSpeechRecognizer.getLatestSpeechRecognizer();
            assertNotSame(offline,system); assertTrue(shadowOf(offline).isDestroyed());
            assertFalse(shadowOf(system).getLastRecognizerIntent().hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE));
            assertEquals("en-IN",shadowOf(system).getLastRecognizerIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
            shadowOf(system).triggerOnReadyForSpeech(new Bundle());
            assertEquals("Listening. Say your command now.",feedback.get(feedback.size()-1));
            Bundle result = new Bundle(); result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(List.of("help")));
            shadowOf(system).triggerOnResults(result); assertEquals("help",commands.get(commands.size()-1));
            assertTrue(feedback.stream().noneMatch(text -> text.contains("did not hear")));
        }
    }
    @Test public void unsupportedSystemLanguageReportsSetupProblemWithoutRetryLoop() {
        start(); shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
        SpeechRecognizer system = ShadowSpeechRecognizer.getLatestSpeechRecognizer();
        shadowOf(system).triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertSame(system,ShadowSpeechRecognizer.getLatestSpeechRecognizer());
        assertTrue(feedback.get(feedback.size()-1).contains("language pack"));
        assertFalse(feedback.get(feedback.size()-1).contains("did not hear"));
    }
    @Test public void stopCancelsScheduledSystemFallback() {
        start(); shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE);
        SpeechRecognizer system = ShadowSpeechRecognizer.getLatestSpeechRecognizer(); voice.stop();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNull(shadowOf(system).getLastRecognizerIntent()); assertTrue(commands.isEmpty());
    }
    @Test public void noMatchKeepsNormalRetryBehaviorAndDoesNotChangeRecognizer() {
        start(); SpeechRecognizer recognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer();
        shadowOf(recognizer).triggerOnError(SpeechRecognizer.ERROR_NO_MATCH);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertSame(recognizer,ShadowSpeechRecognizer.getLatestSpeechRecognizer());
        assertTrue(feedback.get(feedback.size()-1).contains("did not hear clearly"));
    }
    @Test public void speechRateDefaultsToOneAndClampsToRange() {
        assertEquals(1.0f, voice.getSpeechRate(), 0.001f);
        voice.setSpeechRate(2.0f);
        assertEquals(2.0f, voice.getSpeechRate(), 0.001f);
        voice.setSpeechRate(0.1f);  // below min → clamped to 0.5
        assertEquals(0.5f, voice.getSpeechRate(), 0.001f);
        voice.setSpeechRate(5.0f);  // above max → clamped to 3.0
        assertEquals(3.0f, voice.getSpeechRate(), 0.001f);
    }

    @Test public void tonesCanBeToggledOnAndOff() {
        assertTrue(voice.tonesEnabled());
        voice.setTonesEnabled(false);
        assertFalse(voice.tonesEnabled());
        voice.playSuccessChime();
        voice.playErrorTone();
        voice.startThinkingPulse();
        voice.setTonesEnabled(true);
        assertTrue(voice.tonesEnabled());
    }
}
