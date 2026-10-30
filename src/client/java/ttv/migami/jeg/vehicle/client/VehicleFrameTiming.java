package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Smooths the partial tick while riding a vehicle, where small timing errors are very visible at speed.
 * <ul>
 *     <li>Vanilla measures frame time in whole milliseconds. With ~6 ms frames that rounding alone makes each frame
 *     move up to ~16% too far or too short, so a nanosecond clock tracks the partial tick instead, held to vanilla's
 *     tick boundaries so it never drifts.</li>
 *     <li>Vanilla samples the partial tick at the start of a frame but draws it after that frame's game ticks and
 *     input handling, a varying delay later. The partial tick is advanced to the moment rendering starts.</li>
 * </ul>
 * The value is fixed for the rest of the frame so everything drawn stays in step.
 */
public final class VehicleFrameTiming {
    private static final double DRIFT_CORRECTION = 0.05D;
    private static final double RESYNC_THRESHOLD = 0.15D;

    private static long frameStartNanos;
    private static long renderStartNanos;
    private static long lastAdvanceNanos;
    private static double smoothResidual = Double.NaN;
    private static boolean active;

    private VehicleFrameTiming() {}

    /** Called after vanilla advances game time, with the ticks it will run and its millisecond-based residual. */
    public static void onAdvanceGameTime(int ticks, float vanillaResidual, float msPerTick) {
        long now = System.nanoTime();
        if (Double.isNaN(smoothResidual) || msPerTick <= 0.0F || lastAdvanceNanos == 0L) {
            smoothResidual = vanillaResidual;
        } else {
            smoothResidual += (now - lastAdvanceNanos) / (msPerTick * 1_000_000.0D) - ticks;
            double error = vanillaResidual - smoothResidual;
            if (Math.abs(error) > RESYNC_THRESHOLD) {
                smoothResidual = vanillaResidual;
            } else {
                smoothResidual += error * DRIFT_CORRECTION;
            }
        }
        lastAdvanceNanos = now;
    }

    public static void markFrameStart(Minecraft minecraft) {
        frameStartNanos = System.nanoTime();
        renderStartNanos = frameStartNanos;
        active = minecraft.player != null && minecraft.player.getVehicle() instanceof VehicleEntity;
    }

    public static void markRenderStart() {
        renderStartNanos = System.nanoTime();
    }

    public static float adjustPartialTick(float partialTick, float msPerTick) {
        if (!active || msPerTick <= 0.0F || partialTick >= 1.0F || Double.isNaN(smoothResidual)) {
            return partialTick;
        }
        double elapsedTicks = Math.max(0L, renderStartNanos - frameStartNanos) / (msPerTick * 1_000_000.0D);
        return (float) Math.min(1.0D, Math.max(0.0D, smoothResidual + elapsedTicks));
    }
}
