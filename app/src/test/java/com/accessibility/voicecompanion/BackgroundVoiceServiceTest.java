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
    }

    @After
    public void tearDown() {
        SmsOtpHelper.setAwaitingSmsConsent(false);
        BackgroundVoiceService.setKeyEventListener(null);
    }

    @Test
    public void testAwaitingSmsConsentFlagLifecycle() {
        assertFalse(SmsOtpHelper.isAwaitingSmsConsent());

        SmsOtpHelper.setAwaitingSmsConsent(true);
        assertTrue(SmsOtpHelper.isAwaitingSmsConsent());

        SmsOtpHelper.setAwaitingSmsConsent(false);
        assertFalse(SmsOtpHelper.isAwaitingSmsConsent());
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
}
