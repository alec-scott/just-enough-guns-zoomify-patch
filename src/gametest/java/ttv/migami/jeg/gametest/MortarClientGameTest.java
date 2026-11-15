package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.client.MortarAim;
import ttv.migami.jeg.vehicle.client.audio.MortarAudio;
import ttv.migami.jeg.vehicle.entity.MortarEntity;
import ttv.migami.jeg.vehicle.projectile.MortarRoundEntity;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * The 81 mm mortar end to end, with real keys and clicks where a player would use them (design/mortar/BRIEF.md):
 * deploy from the item, set-up time, W/S elevation within 45-85 degrees, A/D traverse, the load-and-drop drill, a
 * full-range shot at 45 degrees through unloaded terrain and a short one at 85, both landing where the fire-control
 * HUD predicts; SBW's blast (100 damage to nothing at 8 blocks, no vanilla entity damage on top) breaking blocks like
 * a rocket; the report heard; loading from outside and packing up.
 */
public class MortarClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double GROUND = -60.0D;
    private int mortarId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        // The server's view distance (12 chunks on yftm), set before joining: C2ME agrees the client's view distance
        // with the server at join, and a later change only reached the server's own setting (chunks stayed at 5).
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            // A fixed settle, not waitForChunksRender: at 12 chunks with Voxy every chunk never finishes rendering.
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("tp @a 0.5 -60 0.5 0 55");
            server.runCommand("give @a jeg:mortar 1");
            server.runCommand("give @a jeg:mortar_round 16");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.getInventory().setSelectedSlot(0);
            });
            context.waitTicks(40);

            // 1. Deploy with a real right-click on the ground.
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(5);
            this.mortarId = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof MortarEntity mortar) {
                        return mortar.getId();
                    }
                }
                throw new AssertionError("right-clicking the ground with the mortar did not set one up");
            });
            boolean settingUp = server.computeOnServer(s -> mortar(s).settingUp());
            int mortarsLeft = server.computeOnServer(s -> count(s, ModItems.MORTAR.get()));
            check(settingUp, "a freshly deployed mortar must take time to set up");
            check(mortarsLeft == 0, "deploying must use up the mortar item, found " + mortarsLeft);
            context.waitTicks(MortarEntity.SETUP_TICKS + 2);
            check(!server.computeOnServer(s -> mortar(s).settingUp()), "set-up must finish after " + MortarEntity.SETUP_TICKS + " ticks");

            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                player.getInventory().setSelectedSlot(2);
                check(player.startRiding(mortar(s), true, true), "the gunner must be able to man the mortar");
            });
            context.waitTicks(10);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("mortar_manned");

            // 2. Elevation on W/S, held to its limits; traverse on A/D.
            float start = server.computeOnServer(s -> mortar(s).elevation());
            this.hold(context, options -> options.keyUp, 20);
            float raised = server.computeOnServer(s -> mortar(s).elevation());
            LOGGER.info("[mortar] elevation {} -> {} after holding W for 20 ticks", start, raised);
            check(Math.abs(raised - Math.min(MortarRig.ELEVATION_MAX, start + 20 * MortarEntity.ELEVATION_RATE)) < 0.8F,
                    "W must raise the tube at " + MortarEntity.ELEVATION_RATE + " deg/tick: " + start + " -> " + raised);
            this.hold(context, options -> options.keyUp, 220);
            check(server.computeOnServer(s -> mortar(s).elevation()) == MortarRig.ELEVATION_MAX, "elevation must stop at 85 degrees");
            context.takeScreenshot("mortar_elevation_85");
            this.hold(context, options -> options.keyDown, 220);
            check(server.computeOnServer(s -> mortar(s).elevation()) == MortarRig.ELEVATION_MIN, "elevation must stop at 45 degrees");
            context.takeScreenshot("mortar_elevation_45");
            // The gunner's client turns the mortar (it is authoritative for the vehicle's yaw) and the server follows from
            // its vehicle-move packets, so the rate is read on the client and the server is checked to catch up.
            float yaw0 = context.computeOnClient(mc -> mc.level.getEntity(this.mortarId).getYRot());
            this.hold(context, options -> options.keyLeft, 20);
            float yaw1 = context.computeOnClient(mc -> mc.level.getEntity(this.mortarId).getYRot());
            float turned = net.minecraft.util.Mth.wrapDegrees(yaw1 - yaw0);
            context.waitTicks(10);
            float serverYaw = server.computeOnServer(s -> mortar(s).getYRot());
            LOGGER.info("[mortar] traverse {} -> {} ({} deg) after holding A for 20 ticks; server {}", yaw0, yaw1, turned, serverYaw);
            check(Math.abs(Math.abs(turned) - 20 * MortarEntity.TRAVERSE_RATE) < 1.5F, "A must traverse at " + MortarEntity.TRAVERSE_RATE + " deg/tick, turned " + turned);
            check(Math.abs(net.minecraft.util.Mth.wrapDegrees(serverYaw - yaw1)) < 0.5F, "the server must take the gunner's bearing: client " + yaw1 + ", server " + serverYaw);
            this.hold(context, options -> options.keyRight, 20);

            // 3. Full range at 45 degrees: the target area is far outside the loaded world.
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(5);
            var hud45 = context.computeOnClient(mc -> MortarAim.predict((MortarEntity) mc.level.getEntity(this.mortarId), 1.0F));
            LOGGER.info("[mortar] HUD at 45: range {} ticks {} assumedLevel {}", hud45.range(), hud45.ticks(), hud45.assumedLevel());
            context.takeScreenshot("mortar_hud_45");
            Shot far = this.fire(context, server);
            double flatRange = far.predicted.horizontalDistance(far.muzzle);
            double hudError = Math.abs(hud45.range() - far.range);
            LOGGER.info(String.format(Locale.ROOT, "[mortar] 45 deg: landed %.1f blocks out after %d ticks (predicted %.1f, HUD %.1f); drill %d ticks",
                    far.range, far.ticks, flatRange, hud45.range(), far.drillTicks));
            check(far.range > 700.0D, "a 45 degree round must fly about 760 blocks, flew " + far.range);
            check(Math.abs(far.range - flatRange) < 0.04D * flatRange + 4.0D, "the round must land where MortarRig predicts: " + far.range + " vs " + flatRange);
            check(hudError < 0.04D * far.range + 4.0D, "the HUD must predict the range within the spread: " + hud45.range() + " vs " + far.range);
            check(far.drillTicks >= MortarEntity.DRILL_TICKS - 2 && far.drillTicks <= MortarEntity.DRILL_TICKS + 4,
                    "the round must leave about a second after loading, took " + far.drillTicks + " ticks");
            check(far.roundsUsed == 1, "loading must use one round from the inventory, used " + far.roundsUsed);
            check(MortarAudio.played() > 0 && MortarAudio.lastNear() > 0.3F, "the gunner must hear the report, played "
                    + MortarAudio.played() + " near " + MortarAudio.lastNear());

            // 4. Short range at 85 degrees, landing on loaded terrain where the HUD's marker is. The client sees as far as
            // the server sends (12 chunks on yftm, set at the top), so the 130-block impact is in its loaded world.
            this.hold(context, options -> options.keyUp, 180);
            context.waitTicks(MortarEntity.RELOAD_TICKS);
            // Wait for the chunk where the round will land, not for every chunk (with Voxy that never settles).
            context.waitFor(mc -> {
                var mortar = (MortarEntity) mc.level.getEntity(this.mortarId);
                Vec3 muzzle = MortarRig.muzzleWorld(mortar.position(), mortar.getYRot(), mortar.elevation());
                Vec3 impact = MortarRig.predictToHeight(muzzle, MortarRig.launchVelocity(mortar.getYRot(), mortar.elevation()), GROUND).position();
                return !(mc.level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(impact.x),
                        net.minecraft.core.SectionPos.blockToSectionCoord(impact.z)) instanceof net.minecraft.world.level.chunk.EmptyLevelChunk);
            }, 1200);
            var hud85 = context.computeOnClient(mc -> MortarAim.predict((MortarEntity) mc.level.getEntity(this.mortarId), 1.0F));
            context.takeScreenshot("mortar_hud_85_marker");
            Shot near = this.fire(context, server);
            double markerError = near.impact.distanceTo(hud85.impact());
            LOGGER.info(String.format(Locale.ROOT, "[mortar] 85 deg: landed %.1f blocks out after %d ticks; HUD marker %.1f blocks off (assumedLevel %s)",
                    near.range, near.ticks, markerError, hud85.assumedLevel()));
            check(!hud85.assumedLevel(), "at 85 degrees the target area is loaded and the HUD must say where on the terrain");
            check(near.range > 100.0D && near.range < 170.0D, "an 85 degree round must land 130-ish blocks out, landed " + near.range);
            // SBW's spread (0.5) is mostly an elevation error at 85 degrees, where range changes fastest with elevation
            // (about 1500 blocks per radian): up to about 13 blocks at this range.
            check(markerError < 15.0D, "the round must land near the HUD's marker (within the spread), " + markerError + " blocks off");

            // 5. The blast: SBW's damage, no vanilla entity damage on top, blocks broken like a rocket's.
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
            context.waitTicks(5);
            this.blast(context, server);

            // 6. A second player's job: loading from outside with a round in hand; then packing up returns both.
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                var mortar = mortar(s);
                player.teleportTo(mortar.getX() + 1.5D, mortar.getY(), mortar.getZ());
            });
            context.waitTicks(MortarEntity.RELOAD_TICKS);
            int before = server.computeOnServer(s -> count(s, ModItems.MORTAR_ROUND.get()));
            boolean loaded = server.computeOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                ItemStack round = new ItemStack(ModItems.MORTAR_ROUND.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, round);
                mortar(s).interact(player, InteractionHand.MAIN_HAND, mortar(s).position());
                return mortar(s).roundInTube() && round.isEmpty();
            });
            check(loaded, "a player on foot must be able to load the round in their hand");
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setShiftKeyDown(true);
                mortar(s).interact(player, InteractionHand.MAIN_HAND, mortar(s).position());
                player.setShiftKeyDown(false);
            });
            context.waitTicks(2);
            boolean gone = server.computeOnServer(s -> s.overworld().getEntity(this.mortarId) == null);
            int mortars = server.computeOnServer(s -> count(s, ModItems.MORTAR.get()));
            int after = server.computeOnServer(s -> count(s, ModItems.MORTAR_ROUND.get()));
            LOGGER.info("[mortar] pack-up: removed {} mortar items {} rounds {} -> {}", gone, mortars, before, after);
            check(gone && mortars == 1, "packing up must remove the mortar and return the item");
            check(after == before + 1, "packing up must return the round left in the tube: " + before + " -> " + after);
            LOGGER.info("[mortar] all checks passed");
        }
    }

    private void hold(ClientGameTestContext context, java.util.function.Function<net.minecraft.client.Options, net.minecraft.client.KeyMapping> key, int ticks) {
        context.getInput().holdKey(key);
        context.waitTicks(ticks);
        context.getInput().releaseKey(key);
        context.waitTicks(2);
    }

    private Shot fire(ClientGameTestContext context, TestServerContext server) {
        int roundsBefore = server.computeOnServer(s -> count(s, ModItems.MORTAR_ROUND.get()));
        int firedBefore = server.computeOnServer(s -> mortar(s).roundsFired());
        context.getInput().pressKey(KeyBindings.RELOAD);
        int drill = 0;
        while (server.computeOnServer(s -> mortar(s).roundsFired()) == firedBefore) {
            context.waitTick();
            if (++drill > 60) {
                throw new AssertionError("no round left the tube within 3 s of pressing R");
            }
        }
        Vec3[] spawn = server.computeOnServer(s -> {
            for (var entity : s.overworld().getAllEntities()) {
                if (entity instanceof MortarRoundEntity round) {
                    return new Vec3[] {round.position(), round.getDeltaMovement()};
                }
            }
            throw new AssertionError("the fired round is not in the world");
        });
        // Undo this tick's movement to recover the muzzle and launch velocity the mortar gave it.
        Vec3 velocity = spawn[1].add(0.0D, MortarRig.GRAVITY, 0.0D);
        Vec3 muzzle = spawn[0].subtract(spawn[1]);
        MortarRig.Impact predicted = MortarRig.predictToHeight(muzzle, velocity, GROUND);
        Vec3 last = spawn[0];
        int ticks = 0;
        while (true) {
            Vec3 now = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof MortarRoundEntity round) {
                        return round.position();
                    }
                }
                return null;
            });
            if (now == null) {
                break;
            }
            last = now;
            context.waitTick();
            if (++ticks > MortarRig.MAX_FLIGHT_TICKS) {
                throw new AssertionError("the round never came down");
            }
        }
        context.waitTicks(10);
        int roundsAfter = server.computeOnServer(s -> count(s, ModItems.MORTAR_ROUND.get()));
        double dx = last.x - muzzle.x, dz = last.z - muzzle.z;
        return new Shot(muzzle, last, predicted, Math.sqrt(dx * dx + dz * dz), ticks, drill, roundsBefore - roundsAfter);
    }

    private void blast(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("setblock 40 -60 2 minecraft:stone");
        server.runCommand("setblock 40 -60 -2 minecraft:obsidian");
        server.runCommand("summon minecraft:husk 42.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Health:1000f,Tags:[\"mortar_near\"],attributes:[{id:\"minecraft:max_health\",base:1000d}]}");
        server.runCommand("summon minecraft:husk 46.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Health:1000f,Tags:[\"mortar_edge\"],attributes:[{id:\"minecraft:max_health\",base:1000d}]}");
        server.runCommand("summon minecraft:husk 51.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Health:1000f,Tags:[\"mortar_out\"],attributes:[{id:\"minecraft:max_health\",base:1000d}]}");
        context.waitTicks(5);
        float[] before = {health(server, "mortar_near"), health(server, "mortar_edge"), health(server, "mortar_out")};
        server.runOnServer(s -> {
            var level = s.overworld();
            var player = s.getPlayerList().getPlayers().get(0);
            level.addFreshEntity(new MortarRoundEntity(level, player, mortar(s), new Vec3(40.5D, -52.0D, 0.5D), new Vec3(0.0D, -1.5D, 0.0D)));
        });
        context.waitTicks(20);
        float near = before[0] - health(server, "mortar_near");
        float edge = before[1] - health(server, "mortar_edge");
        float out = before[2] - health(server, "mortar_out");
        boolean stoneGone = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(40, -60, 2)).isAir());
        boolean obsidianStands = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(40, -60, -2)).is(Blocks.OBSIDIAN));
        LOGGER.info("[mortar] blast: near {} edge {} outside {} stoneGone {} obsidianStands {}", near, edge, out, stoneGone, obsidianStands);
        context.takeScreenshot("mortar_blast_crater");
        check(near > 60.0F && near <= MortarRoundEntity.BLAST_DAMAGE + 0.5F, "2 blocks from the burst a target must take most of SBW's 100, took " + near);
        check(edge > 5.0F && edge < 50.0F, "6 blocks out the blast must have eased off but still hurt, took " + edge);
        check(out == 0.0F, "outside SBW's 8-block radius nothing must be hurt (no vanilla explosion damage on top), took " + out);
        check(stoneGone, "stone next to the burst must break, as it does for a rocket");
        check(obsidianStands, "obsidian is armour and must survive");
    }

    private MortarEntity mortar(MinecraftServer server) {
        if (server.overworld().getEntity(this.mortarId) instanceof MortarEntity mortar) {
            return mortar;
        }
        throw new AssertionError("the mortar is gone");
    }

    private static int count(MinecraftServer server, net.minecraft.world.item.Item item) {
        var inventory = server.getPlayerList().getPlayers().get(0).getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                total += inventory.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static float health(TestServerContext server, String tag) {
        return server.computeOnServer(s -> {
            for (var entity : s.overworld().getAllEntities()) {
                if (entity instanceof LivingEntity living && entity.entityTags().contains(tag)) {
                    return living.getHealth();
                }
            }
            throw new AssertionError("blast probe missing: " + tag);
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Shot(Vec3 muzzle, Vec3 impact, MortarRig.Impact predicted, double range, int ticks, int drillTicks, int roundsUsed) {}
}
