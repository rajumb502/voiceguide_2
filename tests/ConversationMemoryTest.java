package com.accessibility.voicecompanion;
import org.json.*;

public class ConversationMemoryTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static JSONObject input(String followup, ConversationMemory memory) throws Exception {
        JSONObject snapshot = new JSONObject().put("width",400).put("height",800).put("sensitive",false).put("site","amazon.in").put("title","Amazon home")
            .put("nodes",new JSONArray());
        return new JSONObject().put("command",followup).put("snapshot",snapshot).put("conversation_history",memory.snapshot())
            .put("current_page",ConversationMemory.page("https://amazon.in/home?secret=private-query","Amazon home",7));
    }
    private static JSONObject content(String provider, JSONObject body) throws Exception {
        String text = provider.equals(AssistantSettings.GEMINI)
            ? body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
            : body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");
        return new JSONObject(text);
    }
    public static void main(String[] args) throws Exception {
        ConversationMemory memory = new ConversationMemory(); AgentSession session = new AgentSession();
        session.start("Describe this page");
        memory.add("user","Describe this page","https://amazon.in/home?secret=private-query#private-fragment","Amazon home",7);
        memory.add("assistant","There is a sponsored ad for Acme headphones.","https://amazon.in/home?secret=private-query","Amazon home",7);
        session.stop(); session.start("Tell me more about that ad");
        memory.add("user",session.goal(), "https://amazon.in/home","Amazon home",7);
        for (String provider : new String[]{AssistantSettings.GEMINI,AssistantSettings.OPENAI}) {
            JSONObject sent = content(provider,AssistantClient.body(provider,"model",input(session.goal(),memory)));
            JSONArray turns = sent.getJSONArray("conversation_history");
            check(turns.length()==3);
            check(turns.getJSONObject(1).getString("role").equals("assistant"));
            check(turns.getJSONObject(1).getString("text").contains("Acme headphones"));
            check(sent.getJSONObject("current_page").getString("site").equals("amazon.in"));
            check(sent.getJSONObject("current_page").getLong("page_id")==7);
            check(!sent.toString().contains("private-query")); check(!sent.toString().contains("private-fragment"));
        }
        JSONArray copy=memory.snapshot(); copy.remove(0); check(memory.snapshot().length()==3);
        memory.add("page","Different page now","https://museum.example/hours","Museum hours",8);
        check(memory.snapshot().getJSONObject(1).getJSONObject("page").getString("site").equals("amazon.in"));
        check(memory.snapshot().getJSONObject(3).getJSONObject("page").getString("site").equals("museum.example"));
        check(memory.snapshot("amazon.in").length()==3);
        check(memory.snapshot("museum.example").length()==1);
        check(memory.snapshot("unknown.example").length()==0);

        // Verify cross-hostname context isolation (Post-Muse Hardening Task 13)
        ConversationMemory siteIsolation = new ConversationMemory();
        siteIsolation.add("assistant", "BESCOM Bill: ₹1,450 Account 54321", "https://bescom.co.in/portal", "BESCOM Portal", 101);
        check(siteIsolation.snapshot("bescom.co.in").length() == 1);
        check(siteIsolation.snapshot("bescom.co.in").getJSONObject(0).getString("text").contains("₹1,450"));
        check(siteIsolation.snapshot("irctc.co.in").length() == 0);
        check(siteIsolation.snapshot("wikipedia.org").length() == 0);

        for (int i=0;i<40;i++) memory.add("assistant","Long text "+"x".repeat(3000),"https://example.com/","Example",9);
        check(memory.snapshot().length()<=24); check(memory.snapshot().toString().length()<=16000);
        memory.clear(); check(memory.snapshot().length()==0);
        System.out.println("Conversation follow-up, page grounding, redaction and memory limit tests passed for both providers");
    }
}
