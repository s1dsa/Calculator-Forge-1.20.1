package sonar.calculator.mod.machine;

/**
 * The original Conductor Mast wait/status branch (upstream {@code GuiConductorMast}).
 *
 * <p>The mast publishes an integer delay ({@code lightningDelay}, upstream {@code lightningSpeed})
 * and a counting timer ({@code lightningTimer}, upstream {@code lightingTicks}). While the delay is
 * at least 600 ticks and the timer is below 500 ticks the original GUI shows a humorous wait
 * message selected by the {@code lightningRandom} roll; at every other time it shows the remaining
 * whole seconds. Keeping the decision here makes it testable without a client and guarantees the
 * screen and the GameTests cannot drift apart.</p>
 */
public final class ConductorStatus {
    public static final int MESSAGE_MIN_DELAY = 600;
    public static final int MESSAGE_MAX_TIMER = 500;
    public static final int MESSAGE_COUNT = 9;

    private ConductorStatus() {}

    /**
     * Translation key of the wait message, or {@code null} when the remaining-seconds branch applies.
     * Message index 0 uses the legacy key {@code conductor.msg}; indexes 1..8 use
     * {@code conductor.msg1}..{@code conductor.msg8}. An out-of-range index also falls back to the
     * seconds branch, mirroring the upstream switch which has no default label.
     */
    public static String messageKey(int delay, int timer, int random) {
        if (delay < MESSAGE_MIN_DELAY || timer >= MESSAGE_MAX_TIMER) return null;
        if (random < 0 || random >= MESSAGE_COUNT) return null;
        return random == 0 ? "conductor.msg" : "conductor.msg" + random;
    }

    /** Whole seconds still to wait, exactly as upstream divides both tick counters by 20. */
    public static int remainingSeconds(int delay, int timer) {
        return delay / 20 - timer / 20;
    }
}
