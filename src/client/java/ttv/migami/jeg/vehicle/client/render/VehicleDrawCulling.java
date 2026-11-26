package ttv.migami.jeg.vehicle.client.render;

/**
 * Work the vehicle renderer leaves out because it can't change what is seen (2026-11-26: the ArmourTest fleet in view
 * took 13 ms a frame under Photon). Always on in play; the switches are for A/B comparisons: the dev client's
 * {@code /jegcull on|off} and {@code VehicleDrawCullingClientGameTest}, which checks the picture is the same either way.
 */
public final class VehicleDrawCulling {
    /** The glass and glow layers draw only the quads with glass or glow on them ({@link QuadMaskConsumer}). */
    public static volatile boolean quadMask = true;
    /** A vehicle whose vertices all had one light is drawn with that light for a few ticks ({@link VehicleVertexLight}). */
    public static volatile boolean evenLight = true;
    /** No vertex-by-vertex light in Iris's shadow pass, which keeps none. */
    public static volatile boolean shadowPassLight = true;
    /**
     * The lamp-spill pass sums only the lights that reach each face, and works out each point of a face lit cell by cell
     * once ({@link VehicleLightsLayer}): the same vertices for less work.
     */
    public static volatile boolean spillReuse = true;
    /** Tests: each spill face drawn both ways and compared vertex by vertex (slow; never on in play). */
    public static volatile boolean spillCheck;

    /** Quads the glass and glow layers left out, and model draws given one light (render thread; for tests and A/B). */
    public static long quadsMasked;
    /** Quads the glass and glow masks looked at (kept or not). */
    public static long quadsSeen;
    public static long evenDraws;
    /** Spill faces compared under {@link #spillCheck}, and those the two ways drew differently (must stay 0). */
    public static long spillFacesChecked;
    public static long spillFacesDiffering;

    private VehicleDrawCulling() {}

    /** All on or all off. */
    public static void set(boolean on) {
        quadMask = on;
        evenLight = on;
        shadowPassLight = on;
        spillReuse = on;
    }
}
