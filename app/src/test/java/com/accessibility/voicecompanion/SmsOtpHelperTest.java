package com.accessibility.voicecompanion;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SmsOtpHelperTest {

    @Test
    public void extractsKeywordPrecededOtp() {
        String msg1 = "Your OTP for BESCOM bill payment is 482910. Do not share.";
        assertEquals("482910", SmsOtpHelper.extractOtp(msg1));

        String msg2 = "Use verification code 9381 to log into your account.";
        assertEquals("9381", SmsOtpHelper.extractOtp(msg2));

        String msg3 = "Your one-time-password is 829104.";
        assertEquals("829104", SmsOtpHelper.extractOtp(msg3));

        String msg4 = "SBI: Your passcode is 102938 for online transaction.";
        assertEquals("102938", SmsOtpHelper.extractOtp(msg4));
    }

    @Test
    public void extractsKeywordFollowedOtp() {
        String msg = "482910 is your OTP for transaction at Amazon India.";
        assertEquals("482910", SmsOtpHelper.extractOtp(msg));

        String msg2 = "83921 is the verification code for Aadhaar e-KYC.";
        assertEquals("83921", SmsOtpHelper.extractOtp(msg2));
    }

    @Test
    public void prioritizesOtpOverOtherNumbersLikeOrderIds() {
        String msg = "Your order #9876543 has arrived. Use OTP 4567 to accept delivery.";
        assertEquals("4567", SmsOtpHelper.extractOtp(msg));
    }

    @Test
    public void extractsStandaloneDigitsFallback() {
        String msg = "Please enter 678912 to proceed.";
        assertEquals("678912", SmsOtpHelper.extractOtp(msg));
    }

    @Test
    public void returnsNullForInvalidOrMissingOtp() {
        assertNull(SmsOtpHelper.extractOtp(null));
        assertNull(SmsOtpHelper.extractOtp(""));
        assertNull(SmsOtpHelper.extractOtp("   "));
        assertNull(SmsOtpHelper.extractOtp("No numbers in this text"));
        // 3 digits is too short
        assertNull(SmsOtpHelper.extractOtp("Your code is 123."));
        // 9+ digits is too long for an OTP
        assertNull(SmsOtpHelper.extractOtp("Call us at 9876543210."));
    }

    @Test
    public void formatsDigitsForSpeechWithSpaces() {
        assertEquals("4 8 2 9 1 0", SmsOtpHelper.formatDigitsForSpeech("482910"));
        assertEquals("1 2 3 4", SmsOtpHelper.formatDigitsForSpeech("1234"));
        assertEquals("", SmsOtpHelper.formatDigitsForSpeech(""));
        assertEquals("", SmsOtpHelper.formatDigitsForSpeech(null));
    }
}
