package com.accessibility.voicecompanion;
import org.json.*;

/** Simulated planner responses test the generic protocol without contacting a provider. */
public class AgentFlowTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    private static JSONObject action(String kind, String target, String summary) throws Exception {
        return new JSONObject().put("action",kind).put("target_id",target).put("action_summary",summary).put("spoken_feedback",summary)
            .put("requires_confirmation",false).put("text","").put("url","").put("direction","");
    }
    public static void main(String[] args) throws Exception {
        for (String site : new String[]{"library.example","news.example"}) {
            AgentSession agent = new AgentSession(); agent.start("Find an article about birds on " + site);
            JSONObject search = new JSONObject("{\"id\":\"4\",\"label\":\"Search\",\"tag\":\"INPUT\",\"type\":\"search\",\"disabled\":false,\"editable\":true,\"search\":true,\"search_form\":true}");
            JSONObject link = new JSONObject("{\"id\":\"5\",\"label\":\"Bird migration\",\"tag\":\"A\",\"disabled\":false,\"editable\":false,\"href\":\"https://" + site + "/birds\"}");
            JSONObject snapshot = new JSONObject().put("nodes",new JSONArray().put(search).put(link));
            check(agent.nextStep());
            JSONObject type = AssistantClient.validate(action("TYPE","4","Search for birds").put("text","birds"),snapshot);
            check(!AgentPolicy.needsConfirmation(type,snapshot)); check(agent.record("TYPE 4","Field filled",true));
            check(agent.nextStep());
            JSONObject submit = AssistantClient.validate(action("SUBMIT","4","Search this site"),snapshot);
            check(!AgentPolicy.needsConfirmation(submit,snapshot)); check(agent.record("SUBMIT 4","Search navigation requested",true));
            check(agent.nextStep());
            JSONObject click = AssistantClient.validate(action("CLICK","5","Open the article"),snapshot);
            check(!AgentPolicy.needsConfirmation(click,snapshot)); check(agent.record("CLICK 5","Article opened; fresh page observed",true));
            check(agent.nextStep()); agent.ask(); check(agent.waiting()); agent.answer("Read the introduction"); check(agent.running());
            check(agent.goal().contains(site));
            agent.ask(); agent.answer("Use other.example"); check(AgentPolicy.navigationAllowed(agent.userInstructions(),"https://other.example/",snapshot)); check(agent.history().toString().contains("Read the introduction"));
            agent.stop(); check(!agent.nextStep());
            check(AgentPolicy.needsConfirmation(action("CLICK","5","Purchase this subscription"),snapshot));
            search.put("search_form",false); check(AgentPolicy.needsConfirmation(submit,snapshot));
            search.put("sensitive",true);
            try { AssistantClient.validate(type,snapshot); throw new AssertionError("Sensitive typing accepted"); } catch (JSONException expected) { }
            check(AgentPolicy.navigationAllowed(agent.goal(),"https://"+site+"/home",snapshot));
            check(!AgentPolicy.navigationAllowed(agent.goal(),"https://unrelated.example/",snapshot));
            try { AssistantClient.validate(action("SCROLL","","Scroll").put("direction","sideways"),snapshot); throw new AssertionError(); } catch (JSONException expected) { }
        }
        AgentSession failures = new AgentSession(); failures.start("Read a page");
        check(failures.record("CLICK 1","blocked",false)); check(!failures.record("CLICK 1","blocked",false)); check(!failures.running());
        AgentSession limit = new AgentSession(); limit.start("Browse");
        for (int i=0;i<AgentSession.MAX_STEPS;i++) check(limit.nextStep());
        check(!limit.nextStep()); check(!limit.running());
        AgentSession repeats = new AgentSession(); repeats.start("Browse");
        check(repeats.record("WAIT","wait",true)); check(repeats.record("WAIT","wait",true)); check(!repeats.record("WAIT","wait",true));
        System.out.println("Generic agent flow, policy and task limit tests passed for two site fixtures");
    }
}
