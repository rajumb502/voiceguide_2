package com.accessibility.voicecompanion;
import org.json.*;
public class UserPreferencesTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        String notes = UserPreferences.merge("",UserPreferences.feedback("Remember that I prefer prices before features"));
        check(notes.equals("I prefer prices before features"));
        notes = UserPreferences.merge(notes,UserPreferences.feedback("Keep answers short"));
        check(notes.contains("Response detail: brief"));
        notes = UserPreferences.merge(notes,UserPreferences.feedback("Give me more detail"));
        check(notes.contains("Response detail: detailed") && !notes.contains("Response detail: brief"));
        check(UserPreferences.feedback("open this item") == null);
        check(UserPreferences.feedback("the page says remember to pay now") == null);
        check(UserPreferences.feedback("I prefer vegetarian food") != null);
        check(UserPreferences.feedback("From now on, explain unfamiliar terms") != null);
        String duplicated = UserPreferences.merge(notes,UserPreferences.feedback("Remember that I prefer prices before features"));
        check(duplicated.indexOf("I prefer prices") == duplicated.lastIndexOf("I prefer prices"));
        for (int i=0;i<50;i++) notes = UserPreferences.merge(notes,new UserPreferences.Update("Preference "+i+" "+"x".repeat(600),""));
        check(notes.length()<=4000 && notes.split("\n").length<=20);

        UserPreferences.Fact consumer = UserPreferences.extractFact("remember my consumer ID is 12345");
        check(consumer != null && "consumer ID".equals(consumer.key()) && "12345".equals(consumer.value()));
        UserPreferences.Fact voter = UserPreferences.extractFact("remember my voter ID is ABC1234567");
        check(voter != null && "voter ID".equals(voter.key()) && "ABC1234567".equals(voter.value()));
        UserPreferences.Fact email = UserPreferences.extractFact("Please remember that my email is user@example.com.");
        check(email != null && "email".equals(email.key()) && "user@example.com".equals(email.value()));
        check(UserPreferences.extractFact("remember my password is secret") == null);
        check(UserPreferences.extractFact("remember my otp is 1234") == null);
        check(UserPreferences.extractFact("the page says remember my consumer ID is 999") == null);
        check(UserPreferences.extractFact("Remember that I prefer prices before features") == null);

        JSONObject snapshot = new JSONObject().put("width",400).put("height",800).put("nodes",new JSONArray()).put("sensitive",false);
        JSONObject payload = new JSONObject().put("command","Describe this page").put("snapshot",snapshot).put("user_preferences","Response detail: brief\nconsumer ID: 12345");
        for (String provider : new String[]{AssistantSettings.OPENAI,AssistantSettings.GEMINI}) {
            String sent = AssistantClient.body(provider,"model",payload).toString();
            check(sent.contains("user_preferences") && sent.contains("Response detail: brief") && sent.contains("consumer ID: 12345") && sent.contains("filling matching form fields"));
        }
        payload.remove("user_preferences");
        for (String provider : new String[]{AssistantSettings.OPENAI,AssistantSettings.GEMINI}) {
            JSONObject body=AssistantClient.body(provider,"model",payload);
            String text=provider.equals(AssistantSettings.GEMINI) ? body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text") : body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text");
            check(!new JSONObject(text).has("user_preferences"));
        }
        System.out.println("Preference feedback, fact extraction, replacement, limits and provider delivery tests passed");
    }
}
