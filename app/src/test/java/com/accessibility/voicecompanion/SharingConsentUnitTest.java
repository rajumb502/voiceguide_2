package com.accessibility.voicecompanion;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class SharingConsentUnitTest {
    private SharingConsent consent;

    @Before
    public void setUp() {
        consent = new SharingConsent();
    }

    @Test
    public void unknownDomainBlocksUploadsUntilApproved() {
        assertFalse(consent.allows("bescom.karnataka.gov.in", "Gemini", 1));
        assertFalse(consent.isDomainAllowed("bescom.karnataka.gov.in"));

        consent.request("Pay bill", 1, 1000, "bescom.karnataka.gov.in", "Gemini");
        assertTrue(consent.pending());
        assertFalse("Domain is not allowed while pending approval", consent.allows("bescom.karnataka.gov.in", "Gemini", 1));

        SharingConsent.Reply reply = consent.evaluate("Yes", 1, 2000);
        assertEquals(SharingConsent.Status.ALLOWED, reply.status());
        assertEquals("Pay bill", reply.command());
        assertTrue(consent.allows("bescom.karnataka.gov.in", "Gemini", 1));
        assertTrue(consent.isDomainAllowed("bescom.karnataka.gov.in"));
    }

    @Test
    public void approvalIsDomainProviderAndNavigationSpecific() {
        consent.grant("bescom.karnataka.gov.in", "Gemini", 1);
        assertTrue(consent.allows("bescom.karnataka.gov.in", "Gemini", 1));

        // Different domain
        assertFalse(consent.allows("other.gov.in", "Gemini", 1));
        // Different provider
        assertFalse(consent.allows("bescom.karnataka.gov.in", "OpenAI", 1));
        // Different navigation
        assertFalse(consent.allows("bescom.karnataka.gov.in", "Gemini", 2));

        // Revocation
        consent.revoke();
        assertFalse(consent.allows("bescom.karnataka.gov.in", "Gemini", 1));
    }

    @Test
    public void spokenDeclineRejectsSharing() {
        String[] declinePhrases = {"no", "no thanks", "cancel", "stop", "don't share", "नहीं"};
        for (String phrase : declinePhrases) {
            consent.request("Pay bill", 1, 1000, "bescom.karnataka.gov.in", "Gemini");
            SharingConsent.Reply reply = consent.evaluate(phrase, 1, 1500);
            assertEquals(SharingConsent.Status.DECLINED, reply.status());
            assertFalse(consent.allows("bescom.karnataka.gov.in", "Gemini", 1));
            assertFalse(consent.pending());
        }
    }
}
