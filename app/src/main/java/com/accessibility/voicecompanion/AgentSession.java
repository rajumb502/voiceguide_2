package com.accessibility.voicecompanion;

import java.util.ArrayList;
import java.util.List;

/** Task state independent of a particular website, provider, or Android input driver. */
public final class AgentSession {
    public static final int MAX_STEPS = 16;
    private String goal = "", userInstructions = "";
    private boolean running, waiting;
    private int steps, failures, repeats;
    private String lastAction = "";
    private final List<String> history = new ArrayList<>();
    public void start(String goal) { this.goal = goal; userInstructions = goal; running = true; waiting = false; steps = failures = repeats = 0; lastAction = ""; history.clear(); }
    public boolean running() { return running; }
    public boolean waiting() { return waiting; }
    public String goal() { return goal; }
    public String userInstructions() { return userInstructions; }
    public int steps() { return steps; }
    public List<String> history() { return new ArrayList<>(history); }
    public boolean nextStep() { if (!running || ++steps > MAX_STEPS) { stop(); return false; } return true; }
    public void ask() { running = false; waiting = true; }
    public void answer(String reply) { if (waiting) { userInstructions = (userInstructions + "\n" + reply);
            if (userInstructions.length() > 8000) userInstructions = goal + "\n" + reply;
            add("User clarification: " + reply); waiting = false; running = true; } }
    public boolean record(String action, String outcome, boolean success) {
        add(action + ": " + outcome);
        failures = success ? 0 : failures + 1;
        repeats = action.equals(lastAction) ? repeats + 1 : 1; lastAction = action;
        if (failures >= 2 || repeats >= 3) { stop(); return false; }
        return true;
    }
    private void add(String entry) { if (history.size() >= 16) history.remove(0); history.add(entry.substring(0,Math.min(2000,entry.length()))); }
    public void stop() { running = false; waiting = false; }
    public void reset() { stop(); goal = ""; userInstructions = ""; steps = failures = repeats = 0; lastAction = ""; history.clear(); }
}
