package com.accessibility.voicecompanion;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StorageTest {
    private Context context;
    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return super.getSharedPreferences("refactor_test_" + name,mode); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) { return target.openOrCreateDatabase("refactor_test_" + name,mode,factory); }
            @Override public File getDatabasePath(String name) { return target.getDatabasePath("refactor_test_" + name); }
        };
        clean();
    }
    @After public void clean() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        target.deleteSharedPreferences("refactor_test_assistant");
        target.deleteSharedPreferences("refactor_test_assistant_encrypted");
        target.deleteDatabase("refactor_test_conversation.db");
    }
    @Test public void migrationPreservesKeysAndEncryptsProfile() throws Exception {
        SharedPreferences legacy = context.getSharedPreferences("assistant",Context.MODE_PRIVATE);
        assertTrue(legacy.edit().putString("Gemini.key",AssistantSettings.encrypt("Gemini","test-api-key"))
            .putString("user_preferences","Biller: test-profile-123456").putString("locale","hi-IN").putBoolean("share_page",false).commit());
        AssistantSettings settings = new AssistantSettings(context);
        assertEquals("test-api-key",settings.apiKey("Gemini"));
        assertEquals("Biller: test-profile-123456",settings.userPreferences());
        assertEquals("hi-IN",settings.locale()); assertFalse(settings.sharingEnabled());
        assertTrue(legacy.getAll().isEmpty());
        settings = new AssistantSettings(context);
        assertEquals("test-api-key",settings.apiKey("Gemini"));
        String xml = new String(Files.readAllBytes(new File(context.getApplicationInfo().dataDir,"shared_prefs/refactor_test_assistant_encrypted.xml").toPath()),StandardCharsets.UTF_8);
        assertFalse(xml.contains("test-profile-123456")); assertFalse(xml.contains("test-api-key"));
    }
    @Test public void conversationSurvivesReopenAndClearsOnDisk() throws Exception {
        try (ConversationMemory memory = new ConversationMemory(context)) {
            memory.add("user","private-conversation-123456","https://example.com/?secret=token","Private title",1);
        }
        try (ConversationMemory restored = new ConversationMemory(context)) {
            assertEquals("private-conversation-123456",restored.snapshot("example.com").getJSONObject(0).getString("text"));
            assertFalse(restored.snapshot().toString().contains("token"));
            assertEquals(0,restored.snapshot("other.example").length());
            String raw = new String(Files.readAllBytes(context.getDatabasePath("conversation.db").toPath()),StandardCharsets.ISO_8859_1);
            assertFalse(raw.contains("private-conversation-123456")); assertFalse(raw.contains("Private title"));
            restored.clear();
        }
        try (ConversationMemory cleared = new ConversationMemory(context)) { assertEquals(0,cleared.snapshot().length()); }
    }
    @Test public void corruptConversationFailsWithoutOverwritingSavedData() throws Exception {
        try (ConversationMemory memory = new ConversationMemory(context)) { memory.add("user","Saved","https://example.com","Title",1); }
        try (SQLiteDatabase database = context.openOrCreateDatabase("conversation.db",Context.MODE_PRIVATE,null)) {
            database.execSQL("UPDATE memory SET content = 'corrupted' WHERE id = 1");
        }
        try (ConversationMemory ignored = new ConversationMemory(context)) { fail("Corrupt storage must not be silently reset"); }
        catch (IllegalStateException expected) { }
        try (SQLiteDatabase database = context.openOrCreateDatabase("conversation.db",Context.MODE_PRIVATE,null);
             android.database.Cursor row = database.rawQuery("SELECT content FROM memory",null)) {
            assertTrue(row.moveToFirst()); assertEquals("corrupted",row.getString(0));
        }
    }
    @Test public void requestsRequireConsentAndWorkerThread() throws Exception {
        org.json.JSONObject payload = new org.json.JSONObject().put("snapshot",new org.json.JSONObject().put("site","example.com"));
        AssistantSettings settings = new AssistantSettings(context);
        try { AssistantClient.request("Gemini","model","key",payload,new SharingConsent(),1,settings); fail("Consent is required before HTTP"); }
        catch (AssistantClient.RequestFailure expected) { assertTrue(expected.getMessage().contains("not approved")); }
        SharingConsent visit = new SharingConsent(); visit.grant("example.com","Gemini",1);
        try { AssistantClient.request("Gemini","model","key",payload,visit,1,settings); fail("Unsaved approval must not upload"); }
        catch (AssistantClient.RequestFailure expected) { }
        settings.approveSite("Gemini","example.com"); visit.revoke();
        try { AssistantClient.request("Gemini","model","key",payload,visit,1,settings); fail("Leaving the foreground must still cancel in-flight uploads"); }
        catch (AssistantClient.RequestFailure expected) { }
        visit.grant("example.com","Gemini",1);
        settings.saveSharingConsent("Gemini",false,java.util.Collections.emptySet());
        try { AssistantClient.request("Gemini","model","key",payload,visit,1,settings); fail("Revoked saved approval must not upload"); }
        catch (AssistantClient.RequestFailure expected) { }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { AssistantClient.request("Gemini","model","key",payload,new SharingConsent(),1,settings); fail("UI requests must be rejected"); }
            catch (IllegalStateException expected) { }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
    @Test public void savedConsentSurvivesReopenAndIsExactAndProviderSpecific() throws Exception {
        AssistantSettings settings = new AssistantSettings(context);
        assertFalse(settings.allSitesAllowed("Gemini")); assertFalse(settings.siteSharingAllowed("example.com","Gemini"));
        settings.approveSite("Gemini","EXAMPLE.COM.");
        AssistantSettings restored = new AssistantSettings(context);
        assertTrue(restored.siteSharingAllowed("example.com","Gemini"));
        assertFalse(restored.siteSharingAllowed("sub.example.com","Gemini"));
        assertFalse(restored.siteSharingAllowed("example.com.evil.test","Gemini"));
        assertFalse(restored.siteSharingAllowed("example.com","OpenAI"));
        restored.approvedSites("Gemini").clear(); assertTrue(settings.siteSharingAllowed("example.com","Gemini"));
        restored.saveSharingConsent("Gemini",false,java.util.Collections.emptySet());
        assertFalse(settings.siteSharingAllowed("example.com","Gemini"));
    }
    @Test public void allSitesOverrideIsPersistentRevocableAndRespectsMasterSwitch() {
        AssistantSettings settings = new AssistantSettings(context);
        settings.saveSharingConsent("Gemini",true,SharingConsent.sites("example.com"));
        AssistantSettings restored = new AssistantSettings(context);
        assertTrue(restored.allSitesAllowed("Gemini")); assertTrue(restored.siteSharingAllowed("other.example","Gemini"));
        assertFalse(restored.siteSharingAllowed("other.example","OpenAI"));
        assertFalse(restored.siteSharingAllowed("https://invalid.example/path","Gemini"));
        restored.saveGeneral(false,true,true,"");
        assertFalse(restored.siteSharingAllowed("example.com","Gemini")); assertFalse(restored.siteSharingAllowed("other.example","Gemini"));
        restored.saveGeneral(true,true,true,"");
        restored.saveSharingConsent("Gemini",false,restored.approvedSites("Gemini"));
        assertTrue(settings.siteSharingAllowed("example.com","Gemini")); assertFalse(settings.siteSharingAllowed("other.example","Gemini"));
        try { settings.saveSharingConsent("Gemini",true,new java.util.HashSet<>(java.util.Arrays.asList("valid.example","https://invalid.example"))); fail("Invalid hostnames must reject the whole edit"); }
        catch (IllegalArgumentException expected) { }
        assertFalse(settings.allSitesAllowed("Gemini")); assertEquals(1,settings.approvedSites("Gemini").size());
    }
}
