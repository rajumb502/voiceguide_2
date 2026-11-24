package com.accessibility.voicecompanion;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NativeAppDriverTest {

    private AccessibilityService mockService;
    private AccessibilityNodeInfo mockRoot;

    @Before
    public void setUp() {
        mockService = mock(AccessibilityService.class);
        mockRoot = mock(AccessibilityNodeInfo.class);
        when(mockService.getRootInActiveWindow()).thenReturn(mockRoot);
    }

    @Test
    public void testSensitiveNodeDetectionAndRedaction() throws Exception {
        // Test password node
        AccessibilityNodeInfo passwordNode = mock(AccessibilityNodeInfo.class);
        when(passwordNode.isPassword()).thenReturn(true);
        assertTrue(NativeAppDriver.isSensitiveNode(passwordNode));

        // Test OTP keyword in text
        AccessibilityNodeInfo otpNode = mock(AccessibilityNodeInfo.class);
        when(otpNode.getText()).thenReturn("Your OTP is 482910");
        assertTrue(NativeAppDriver.isSensitiveNode(otpNode));

        // Test MPIN in viewId
        AccessibilityNodeInfo mpinNode = mock(AccessibilityNodeInfo.class);
        when(mpinNode.getViewIdResourceName()).thenReturn("com.example.app:id/enter_mpin_input");
        assertTrue(NativeAppDriver.isSensitiveNode(mpinNode));

        // Test Aadhaar / PAN in contentDescription
        AccessibilityNodeInfo panNode = mock(AccessibilityNodeInfo.class);
        when(panNode.getContentDescription()).thenReturn("Enter PAN card number");
        assertTrue(NativeAppDriver.isSensitiveNode(panNode));

        // Normal chat message node
        AccessibilityNodeInfo chatNode = mock(AccessibilityNodeInfo.class);
        when(chatNode.getText()).thenReturn("Hello, how are you?");
        when(chatNode.isPassword()).thenReturn(false);
        assertFalse(NativeAppDriver.isSensitiveNode(chatNode));
    }

    @Test
    public void testCaptureSnapshotSerializesHierarchyAndRedacts() throws Exception {
        when(mockRoot.isVisibleToUser()).thenReturn(true);
        when(mockRoot.getClassName()).thenReturn("android.widget.FrameLayout");
        when(mockRoot.getPackageName()).thenReturn("com.whatsapp");
        when(mockRoot.getChildCount()).thenReturn(2);

        AccessibilityNodeInfo childMsg = mock(AccessibilityNodeInfo.class);
        when(childMsg.isVisibleToUser()).thenReturn(true);
        when(childMsg.getClassName()).thenReturn("android.widget.TextView");
        when(childMsg.getText()).thenReturn("Meeting at 5 PM");
        when(childMsg.getChildCount()).thenReturn(0);

        AccessibilityNodeInfo childSecret = mock(AccessibilityNodeInfo.class);
        when(childSecret.isVisibleToUser()).thenReturn(true);
        when(childSecret.getClassName()).thenReturn("android.widget.EditText");
        when(childSecret.getText()).thenReturn("Secret PIN 1234");
        when(childSecret.getChildCount()).thenReturn(0);

        when(mockRoot.getChild(0)).thenReturn(childMsg);
        when(mockRoot.getChild(1)).thenReturn(childSecret);

        JSONObject snapshot = NativeAppDriver.captureSnapshot(mockService);
        assertNotNull(snapshot);
        JSONArray nodes = snapshot.getJSONArray("nodes");
        assertEquals(3, nodes.length());

        // Node 1: root
        JSONObject rootObj = nodes.getJSONObject(0);
        assertEquals("android.widget.FrameLayout", rootObj.getString("className"));

        // Node 2: normal message
        JSONObject msgObj = nodes.getJSONObject(1);
        assertEquals("Meeting at 5 PM", msgObj.getString("text"));
        assertFalse(msgObj.getBoolean("sensitive"));

        // Node 3: sensitive secret
        JSONObject secretObj = nodes.getJSONObject(2);
        assertTrue(secretObj.getBoolean("sensitive"));
        assertEquals("[REDACTED]", secretObj.getString("text"));
    }

    @Test
    public void testClickAndTextDispatch() {
        AccessibilityNodeInfo buttonNode = mock(AccessibilityNodeInfo.class);
        when(buttonNode.isClickable()).thenReturn(true);
        when(buttonNode.isEnabled()).thenReturn(true);
        when(buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true);

        when(mockRoot.findAccessibilityNodeInfosByText("Send")).thenReturn(Collections.singletonList(buttonNode));

        NativeAppDriver.ActionResult result = NativeAppDriver.click(mockService, "Send");
        assertTrue(result.success());
        verify(buttonNode).performAction(AccessibilityNodeInfo.ACTION_CLICK);

        // Test Set Text
        AccessibilityNodeInfo editNode = mock(AccessibilityNodeInfo.class);
        when(editNode.isEditable()).thenReturn(true);
        when(editNode.performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any(Bundle.class))).thenReturn(true);
        when(mockRoot.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")).thenReturn(Collections.singletonList(editNode));

        NativeAppDriver.ActionResult textResult = NativeAppDriver.setText(mockService, "com.whatsapp:id/entry", "Hello");
        assertTrue(textResult.success());
        verify(editNode).performAction(eq(AccessibilityNodeInfo.ACTION_SET_TEXT), any(Bundle.class));
    }

    @Test
    public void testHighStakesActionGatingWithActionSafetyGuard() {
        ActionSafetyGuard guard = new ActionSafetyGuard();
        // High-stakes native action like "Send", "Pay", "Delete" requires double volume up confirmation
        assertTrue(guard.requiresConfirmation(false, "Click Send"));
        assertTrue(guard.requiresConfirmation(false, "Pay 500"));
        assertTrue(guard.requiresConfirmation(false, "Delete chat"));
        assertFalse(guard.requiresConfirmation(false, "Scroll down"));

        // Queue high stakes action
        guard.queue(new ActionSafetyGuard.Pending("native_send_btn", 0L, 1L, 1000L, "Send message"));
        assertTrue(guard.hasPending());

        // First Volume Up does not confirm
        assertNull(guard.onVolumeUp(1100L, 1L));
        assertTrue(guard.hasPending());

        // Second Volume Up within 1,200ms confirms
        ActionSafetyGuard.Pending confirmed = guard.onVolumeUp(1300L, 1L);
        assertNotNull(confirmed);
        assertEquals("native_send_btn", confirmed.targetId());
    }

    @Test
    public void testBackAndScrollGlobalActions() {
        when(mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)).thenReturn(true);
        NativeAppDriver.ActionResult backResult = NativeAppDriver.back(mockService);
        assertTrue(backResult.success());
        verify(mockService).performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);

        AccessibilityNodeInfo scrollContainer = mock(AccessibilityNodeInfo.class);
        when(scrollContainer.isScrollable()).thenReturn(true);
        when(scrollContainer.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)).thenReturn(true);
        when(mockRoot.getChildCount()).thenReturn(1);
        when(mockRoot.getChild(0)).thenReturn(scrollContainer);

        NativeAppDriver.ActionResult scrollResult = NativeAppDriver.scroll(mockService, true);
        assertTrue(scrollResult.success());
        verify(scrollContainer).performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }
}
