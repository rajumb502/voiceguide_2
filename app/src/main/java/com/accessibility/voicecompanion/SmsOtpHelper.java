package com.accessibility.voicecompanion;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hands-Free SMS OTP Auto-Read using Android SMS User Consent API.
 * Requires zero READ_SMS permission.
 */
public class SmsOtpHelper {
    public static final int SMS_CONSENT_REQUEST_CODE = 2001;

    public interface OtpListener {
        void onOtpReceived(String otpCode, String rawMessage);
        void onOtpTimeoutOrError(String error);
    }

    private WeakReference<Activity> activityRef;
    private WeakReference<OtpListener> listenerRef;
    private BroadcastReceiver receiver;
    private boolean isRegistered = false;

    public SmsOtpHelper() {}

    public synchronized void startListening(Activity activity, OtpListener listener) {
        stopListening();
        this.activityRef = new WeakReference<>(activity);
        this.listenerRef = new WeakReference<>(listener);

        try {
            SmsRetriever.getClient(activity).startSmsUserConsent(null);
        } catch (Exception ignored) {
            // Can fail if Google Play Services is missing or in mock tests
        }

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) return;
                Bundle extras = intent.getExtras();
                if (extras == null) return;
                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status == null) return;

                int statusCode = status.getStatusCode();
                if (statusCode == CommonStatusCodes.SUCCESS) {
                    Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                    Activity act = activityRef != null ? activityRef.get() : null;
                    if (consentIntent != null && act != null && !act.isFinishing()) {
                        try {
                            act.startActivityForResult(consentIntent, SMS_CONSENT_REQUEST_CODE);
                        } catch (Exception e) {
                            notifyError("Could not display SMS consent dialog: " + e.getMessage());
                        }
                    }
                } else if (statusCode == CommonStatusCodes.TIMEOUT) {
                    notifyError("SMS consent timed out.");
                }
            }
        };

        IntentFilter filter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(receiver, filter);
            }
            isRegistered = true;
        } catch (Exception e) {
            isRegistered = false;
        }
    }

    public synchronized void stopListening() {
        if (isRegistered && receiver != null) {
            Activity act = activityRef != null ? activityRef.get() : null;
            if (act != null) {
                try {
                    act.unregisterReceiver(receiver);
                } catch (IllegalArgumentException ignored) {}
            }
            isRegistered = false;
        }
        receiver = null;
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != SMS_CONSENT_REQUEST_CODE) return false;
        if (resultCode == Activity.RESULT_OK && data != null) {
            String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
            String otp = extractOtp(message);
            if (otp != null) {
                OtpListener l = listenerRef != null ? listenerRef.get() : null;
                if (l != null) l.onOtpReceived(otp, message);
                return true;
            } else {
                notifyError("Received SMS but could not parse a valid OTP code.");
            }
        } else {
            notifyError("SMS consent cancelled or dismissed.");
        }
        return false;
    }

    private void notifyError(String error) {
        OtpListener l = listenerRef != null ? listenerRef.get() : null;
        if (l != null) l.onOtpTimeoutOrError(error);
    }

    /**
     * Extracts a 4 to 8 digit OTP from an SMS message.
     * Prioritizes numbers associated with keywords (OTP, code, verification, passcode).
     */
    public static String extractOtp(String message) {
        if (message == null || message.trim().isEmpty()) return null;

        // 1. Keyword followed by 4-8 digits (e.g. "OTP is 482910", "code: 1234")
        Matcher m1 = Pattern.compile("(?i)(?:\\botp\\b|\\bcode\\b|one[- ]time[- ]password|verification|passcode)[^0-9]{0,20}?([0-9]{4,8})\\b").matcher(message);
        if (m1.find()) {
            return m1.group(1);
        }

        // 2. 4-8 digits followed by keyword (e.g. "482910 is your OTP")
        Matcher m2 = Pattern.compile("(?i)\\b([0-9]{4,8})\\b[^a-zA-Z0-9]{0,20}?(?:is your|is the)\\s+(?:otp|code|verification|one[- ]time)").matcher(message);
        if (m2.find()) {
            return m2.group(1);
        }

        // 3. Standalone 4-8 digit number
        Matcher m3 = Pattern.compile("\\b([0-9]{4,8})\\b").matcher(message);
        if (m3.find()) {
            return m3.group(1);
        }

        return null;
    }

    /**
     * Spells out digits with spaces so screen readers / TTS speak digits individually.
     * e.g., "482910" -> "4 8 2 9 1 0"
     */
    public static String formatDigitsForSpeech(String otp) {
        if (otp == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < otp.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(otp.charAt(i));
        }
        return sb.toString();
    }
}
