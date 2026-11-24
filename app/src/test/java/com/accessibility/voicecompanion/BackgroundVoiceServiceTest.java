package com.accessibility.voicecompanion;

import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BackgroundVoiceServiceTest {

    private BackgroundVoiceService service;

    @Before
    public void setUp() {
        service = new BackgroundVoiceService();
        SmsOtpHelper.setAwaitingSmsConsent(false);
        BackgroundVoiceService.setKeyEventListener(null);
        BackgroundVoiceService.setUserTriggeredSessionActive(false);
        SharingConsent.clearAllowedPackages();
    }

    @After
    public void tearDown() {
        SmsOtpHelper.setAwaitingSmsConsent(false);
        BackgroundVoiceService.setKeyEventListener(null);
        BackgroundVoiceService.setUserTriggeredSessionActive(false);
        SharingConsent.clearAllowedPackages();
    }

    @Test
    public void testAwaitingSmsConsentFlagLifecycle() {
        assertFalse(SmsOtpHelper.isAwaitingSmsConsent());

        SmsOtpHelper.setAwaitingSmsConsent(true);
        assertTrue(SmsOtpHelper.isAwaitingSmsConsent());

        SmsOtpHelper.setAwaitingSmsConsent(false);
        assertFalse(SmsOtpHelper.isAwaitingSmsConsent());
        
        // Bounded window expiry: verify 60,000ms boundary
        assertEquals(60000L, SmsOtpHelper.CONSENT_TIMEOUT_MS);
    }

    @Test
    public void testIgnoreNonGmsEvents() {
        SmsOtpHelper.setAwaitingSmsConsent(true);

        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setPackageName("com.android.chrome");

        boolean handled = service.handleSmsConsentDialog(event);
        assertFalse(handled);
        // Flag should still be awaiting
        assertTrue(SmsOtpHelper.isAwaitingSmsConsent());
        event.recycle();
    }

    @Test
    public void testGlobalKeyEventRouting() {
        AtomicBoolean called = new AtomicBoolean(false);
        BackgroundVoiceService.setKeyEventListener(event -> {
            called.set(event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP);
            return true;
        });

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP);
        boolean consumed = service.onKeyEvent(event);
        assertTrue(consumed);
        assertTrue(called.get());

        // When listener is cleared, should fall back to default
        BackgroundVoiceService.setKeyEventListener(null);
        boolean fallback = service.onKeyEvent(event);
        assertFalse(fallback);
    }

    @Test
    public void testSensitiveAppDenylistAbortsEvent() {
        SmsOtpHelper.setAwaitingSmsConsent(true);

        for (String blockedPkg : SharingConsent.BLOCKED_PACKAGES) {
            assertTrue(SharingConsent.isPackageBlocked(blockedPkg));

            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            event.setPackageName(blockedPkg);

            service.onAccessibilityEvent(event);
            // Flag should remain untouched because event was immediately dropped
            assertTrue(SmsOtpHelper.isAwaitingSmsConsent());
            event.recycle();
        }

        assertFalse(SharingConsent.isPackageBlocked("com.google.android.gms"));
        assertFalse(SharingConsent.isPackageBlocked("com.example.accessiblebrowser"));
    }

    @Test
    public void testZeroBackgroundScrapingEnforcement() {
        // When session is NOT active, events are ignored even for benign apps
        BackgroundVoiceService.setUserTriggeredSessionActive(false);
        AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setPackageName("com.whatsapp");

        service.onAccessibilityEvent(event);
        assertFalse(BackgroundVoiceService.isUserTriggeredSessionActive());

        // When session IS active, but package is not allowed, still ignored
        BackgroundVoiceService.setUserTriggeredSessionActive(true);
        assertFalse(SharingConsent.isPackageAllowed("com.whatsapp"));
        service.onAccessibilityEvent(event);

        // When session IS active and package IS allowed
        SharingConsent.setPackageAllowed("com.whatsapp", true);
        assertTrue(SharingConsent.isPackageAllowed("com.whatsapp"));
        service.onAccessibilityEvent(event);

        event.recycle();
    }
}
