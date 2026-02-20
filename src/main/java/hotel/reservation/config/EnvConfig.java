package hotel.reservation.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Centralized environment configuration loaded from .env file.
 * Provides typed accessors with sensible defaults for all tunable parameters.
 * Used by SCOP classes (Playground, Role, Agent) that are not Spring-managed beans.
 */
public final class EnvConfig {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private EnvConfig() {}

    // ── CNP Protocol ────────────────────────────────────────

    /** Proposal collection deadline in milliseconds. */
    public static long cnpProposalDeadlineMs() {
        return getLong("CNP_PROPOSAL_DEADLINE_MS", 30_000);
    }

    /** Maximum number of shortlisted candidates for sequential negotiation. */
    public static int cnpMaxCandidates() {
        return getInt("CNP_MAX_CANDIDATES", 3);
    }

    /** Maximum negotiation rounds before forcing a decision. */
    public static int cnpMaxNegotiationRounds() {
        return getInt("CNP_MAX_NEGOTIATION_ROUNDS", 5);
    }

    // ── Playground Timing ───────────────────────────────────

    /** Internal data-fetch API port (same Spring Boot server). */
    public static int apiPort() {
        return getInt("API_PORT", 3001);
    }

    /** Playground timeout tick count. */
    public static int playgroundTimeoutTick() {
        return getInt("PLAYGROUND_TIMEOUT_TICK", 100_000);
    }

    /** Playground step delay in milliseconds. */
    public static int playgroundStepDelay() {
        return getInt("PLAYGROUND_STEP_DELAY", 1500);
    }

    // ── Helpers ─────────────────────────────────────────────

    private static String get(String key, String defaultValue) {
        String value = dotenv.get(key);
        return value != null ? value : defaultValue;
    }

    private static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
