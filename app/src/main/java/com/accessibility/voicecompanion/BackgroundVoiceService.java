package com.accessibility.voicecompanion;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Reserved for a separately tested native-app interaction milestone. No background collection. */
public final class BackgroundVoiceService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
