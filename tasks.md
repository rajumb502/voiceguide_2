   - **Status:** **Completed.** Bounded `awaitingSmsConsent` window to 60,000ms (`CONSENT_TIMEOUT_MS = 60 * 1000L`). Auto-resets upon click in `handleSmsConsentDialog()`, on `stopListening()`, or upon 60s timeout expiration. Verified in `BackgroundVoiceServiceTest.java`.

- [x] **13. Guard Against Cross-Session Context Bleed & Hallucinated Balances (`ConversationMemory.java` & `AssistantClient.java`)**
  - **Why:** Prevent financial figures, biller IDs, or personal facts from one site/session from leaking into unrelated tasks or being hallucinated into responses.
  - **Action:**
    - Add a unit test in `ConversationMemoryTest` verifying zero cross-hostname context injection (e.g., a BESCOM bill amount saved on `bescom.co.in` is never injected into prompts on `irctc.co.in` or `wikipedia.org`).
    - Reinforce the `AssistantClient` system prompt rule: *"Never state account balances, bill amounts, or booking confirmations unless explicitly present in the current page's live DOM snapshot."*
  - **Status:** **Completed.** Reinforced `AssistantClient.INSTRUCTIONS` to strictly prohibit stating balances, bill amounts, or booking confirmations unless present in live DOM snapshot. Verified zero cross-site memory leakage in `ConversationMemoryTest.java` and `HostChecksTest`.

- [ ] **14. Prepare Google Play Accessibility Declaration Artifacts (`docs/play_store_accessibility.md`)**