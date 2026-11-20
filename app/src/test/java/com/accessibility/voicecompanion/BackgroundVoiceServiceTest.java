package com.accessibility.voicecompanion;

import android.view.accessibility.AccessibilityEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BackgroundVoiceServiceTest {

    private BackgroundVoiceService service;

    @Before
    public void setUp() {
        service = new BackgroundVoiceService();
        SmsOtpHelper.setAwaitingSmsConsent(false);
    }

    @After
    public void tearDown() {
        SmsOtpHelper.setAwaitingSmsConsent(false);
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
}
