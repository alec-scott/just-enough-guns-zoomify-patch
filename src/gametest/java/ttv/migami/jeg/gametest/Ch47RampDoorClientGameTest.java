package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;

/**
 * Guards three bugs the 2026-11-20 play-through of the Chinook's ramp and crew door found (design/chinook/PLAN.md, P4):
 * <ul>
 *     <li>A click on the ramp panel, held for a few ticks as a person clicks, moves the ramp one step, not two (the
 *     cabin's use handler skipped vanilla's right-click delay, so the held key fired it again the same tick).</li>
 *     <li>Walking up the lowered ramp from the ground ends on the cabin floor: the person stepping on at the lip sank
 *     into the ground under the belly.</li>
 *     <li>A click through the open crew door from inside shuts it, and doesn't sit the person in the pilot's seat.</li>
 * </ul>
 * And two of 2026-11-21: the ramp warns as it moves (the red jump light flashing, a beep with each flash, green once it
 * stands open), and a person jumping in at the open crew door from the ground lands on the cabin floor (the jump's top
 * carried them out of the door's aboard zone, and they fell back outside).
 */
public class Ch47RampDoorClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final double X = 0.5D, Z = 10.5D, GROUND = -60.0D;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("summon jeg:ch47 " + X + " " + GROUND + " " + Z);
            context.waitTicks(40);
            StringBuilder failures = new StringBuilder();

            // ---- The ramp panel: one click, one step (closed -> level), then down.
            // Aimed at the panel on the right wall of the grown cabin (1.5 m out from the centre line).
            this.place(context, server, X, GROUND + 1.0D, Z - 2.7D, 129.0F, 18.0F);
            int id = this.chinook(context).getId();
            this.click(context);
            // The ramp warning while it travels: the red jump light flashing and a beep with each flash; then the green
            // light steady and no more beeps once it stands open.
            int redOn = 0, redSwitches = 0, greenWhileMoving = 0;
            boolean redLast = false;
            for (int i = 0; i < 110; i++) {
                context.waitTick();
                boolean[] lights = context.computeOnClient(mc -> {
                    ChinookEntity chinook = (ChinookEntity) mc.level.getEntity(id);
                    return new boolean[]{chinook.jumpLightRed(), chinook.jumpLightGreen(), chinook.rampMoving()};
                });
                if (lights[0]) redOn++;
                if (lights[0] != redLast) redSwitches++;
                if (lights[1] && lights[2]) greenWhileMoving++;
                redLast = lights[0];
            }
            int beepsMoving = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.Ch47CabinAudio.rampBeeps(id));
            boolean[] settled = context.computeOnClient(mc -> {
                ChinookEntity chinook = (ChinookEntity) mc.level.getEntity(id);
                return new boolean[]{chinook.jumpLightRed(), chinook.jumpLightGreen()};
            });
            context.waitTicks(40);
            int beepsAfter = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.Ch47CabinAudio.rampBeeps(id)) - beepsMoving;
            LOG.info("[ch47-ramp-door] ramp warning: red on {} ticks, {} switches, {} beeps; settled red {} green {}; beeps after {}",
                    redOn, redSwitches, beepsMoving, settled[0], settled[1], beepsAfter);
            if (redSwitches < 6) failures.append("the red jump light should flash while the ramp moves, it switched ").append(redSwitches).append(" times\n");
            if (beepsMoving < 3) failures.append("the ramp should beep with each flash while it moves, it beeped ").append(beepsMoving).append(" times\n");
            if (greenWhileMoving > 0) failures.append("the green jump light must stay dark while the ramp moves\n");
            if (settled[0] || !settled[1]) failures.append("with the ramp standing open, red should be dark and green lit\n");
            if (beepsAfter > 0) failures.append("the ramp kept beeping after it stopped\n");
            float afterOne = this.chinook(context).rampAngle(1.0F);
            LOG.info("[ch47-ramp-door] ramp after one panel click: {} deg", afterOne);
            if (Math.abs(afterOne - ChinookEntity.RAMP_LEVEL) > 0.01F) {
                failures.append("one panel click should leave the ramp level, it is at ").append(afterOne).append(" deg\n");
            }
            this.click(context);
            context.waitTicks(75);
            float afterTwo = this.chinook(context).rampAngle(1.0F);
            LOG.info("[ch47-ramp-door] ramp after a second click: {} deg", afterTwo);
            if (Math.abs(afterTwo - ChinookEntity.RAMP_DOWN) > 0.01F) {
                failures.append("a second click should lower the ramp, it is at ").append(afterTwo).append(" deg\n");
            }

            // ---- Walk up the lowered ramp from the ground behind it.
            this.place(context, server, X, GROUND, Z - 9.5D, 0.0F, 15.0F);
            double lowest = Double.MAX_VALUE;
            context.getInput().holdKey(options -> options.keyUp);
            for (int i = 0; i < 80; i++) {
                context.waitTick();
                lowest = Math.min(lowest, context.computeOnClient(mc -> mc.player.getY()));
            }
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);
            Vec3 end = context.computeOnClient(mc -> mc.player.position());
            LOG.info("[ch47-ramp-door] walked up the ramp: lowest y {}, ended at {}", lowest, end);
            if (lowest < GROUND - 0.05D) {
                failures.append("walking up the ramp sank to y ").append(lowest).append(", below the ground at ").append(GROUND).append('\n');
            }
            if (Math.abs(end.y - (GROUND + ChinookEntity.FLOOR_Y)) > 0.05D || end.z < Z + ChinookEntity.HINGE_Z) {
                failures.append("walking up the ramp should end on the cabin floor inside, ended at ").append(end).append('\n');
            }

            // ---- The crew door from inside: open it, then shut it with a click through the opening. Aimed along the
            // doorway's aft edge: the right door gun and its seat stand stowed in its middle, and pointing at either
            // takes the gun (2026-11-25).
            this.place(context, server, X - 0.3D, GROUND + 1.0D, Z + 4.22D, 90.0F, 28.0F);
            this.click(context);
            context.waitTicks(30);
            boolean opened = this.chinook(context).crewDoorOpen();
            this.click(context);
            context.waitTicks(30);
            boolean shut = !this.chinook(context).crewDoorOpen();
            boolean seated = context.computeOnClient(mc -> mc.player.isPassenger());
            LOG.info("[ch47-ramp-door] crew door from inside: opened {}, shut again {}, seated {}", opened, shut, seated);
            if (!opened) failures.append("a click on the shut crew door from inside should open it\n");
            if (!shut) failures.append("a click through the open crew door from inside should shut it\n");
            if (seated) failures.append("a click through the open crew door must not seat the player\n");

            // ---- Jumping in at the open crew door from the ground beside it (the user, 2026-11-21: "ensure you can jump
            // up to the sliding door entrance"). Opened from inside, then the player outside the door, facing in.
            this.click(context);
            context.waitTicks(45);
            double doorZ = Z + (ChinookEntity.DOOR_Z0 + ChinookEntity.DOOR_Z1) / 2.0D;
            this.place(context, server, X - 3.2D, GROUND, doorZ, -90.0F, 0.0F);
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(16);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(4);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(25);
            Vec3 in = context.computeOnClient(mc -> mc.player.position());
            LOG.info("[ch47-ramp-door] jumped in at the crew door (door open {}): ended at {}", this.chinook(context).crewDoorOpen(), in);
            if (Math.abs(in.y - (GROUND + ChinookEntity.FLOOR_Y)) > 0.05D || in.x < X - ChinookEntity.HALF_IN) {
                failures.append("jumping in at the open crew door should end on the cabin floor, ended at ").append(in).append('\n');
            }

            if (!failures.isEmpty()) {
                throw new AssertionError(failures.toString());
            }
        }
    }

    /** Teleport the player there, facing that way on both sides, and let the cabin take them aboard. */
    private void place(ClientGameTestContext context, TestServerContext server, double x, double y, double z, float yaw, float pitch) {
        server.runCommand("tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        context.waitTicks(5);
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
        context.waitTicks(5);
    }

    /** A right-click as a person makes one: the key down for three ticks. */
    private void click(ClientGameTestContext context) {
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(3);
        context.getInput().releaseKey(options -> options.keyUse);
    }

    private ChinookEntity chinook(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ChinookEntity chinook) {
                    return chinook;
                }
            }
            throw new AssertionError("no Chinook");
        });
    }
}
