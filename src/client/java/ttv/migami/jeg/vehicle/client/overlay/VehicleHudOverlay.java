package ttv.migami.jeg.vehicle.client.overlay;

import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.VehicleClientState;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;
import ttv.migami.jeg.vehicle.util.BulldozerRig;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/**
 * The vehicle HUD. Sights, reticles and markers are drawn at the GUI's own scale where they must line up with the
 * world; everything that informs (the operator panels, the vehicle card, the crew list, the weapon list, the
 * instruments and warnings) is an information layer drawn with {@link HudKit} at 80% of it: dark rounded panels, one
 * clean sans font, smooth icons, state colours only where something needs attention, and keycap hints that fade after
 * boarding. The layer keeps to the edges: operator panel top left, instruments top right, crew bottom left, weapons
 * bottom right, and the vehicle card bottom middle (or top middle, or bottom right; see {@link #vehicleInfoAtTop}).
 */
public final class VehicleHudOverlay {
    private static final Identifier ARMOR_ICON = Reference.id("textures/overlay/vehicle/base/armor.png");
    private static final Identifier COMPASS = Reference.id("textures/overlay/vehicle/base/compass.png");
    private static final Identifier VALUE_BAR = Reference.id("textures/overlay/vehicle/base/value_bar.png");
    private static final Identifier VALUE_FRAME = Reference.id("textures/overlay/vehicle/base/value_frame.png");
    private static final Identifier ROLL_INDICATOR = Reference.id("textures/overlay/vehicle/helicopter/roll_ind.png");
    private static final Identifier LAND_FRAME = Reference.id("textures/overlay/vehicle/land/tv_frame.png");
    private static final Identifier CROSSHAIR_GUN = Reference.id("textures/overlay/vehicle/crosshair/common_gun.png");
    private static final Identifier CROSSHAIR_CANNON = Reference.id("textures/overlay/vehicle/crosshair/common_cannon.png");
    private static final Identifier CROSSHAIR_CANNON_ZOOMING = Reference.id("textures/overlay/vehicle/crosshair/common_cannon_zooming.png");
    private static final Identifier CROSSHAIR_CN_HPJ_ZOOMING = Reference.id("textures/overlay/vehicle/crosshair/cn_hpj_zooming.png");
    private static final Identifier CROSSHAIR_LASER_CANNON = Reference.id("textures/overlay/vehicle/crosshair/laser_cannon.png");
    private static final Identifier CROSSHAIR_MISSILE = Reference.id("textures/overlay/vehicle/crosshair/common_missile.png");
    private static final Identifier CROSSHAIR_SEEK_MISSILE = Reference.id("textures/overlay/vehicle/crosshair/common_seek_missile.png");
    private static final Identifier CROSSHAIR_THIRD_CAMERA = Reference.id("textures/overlay/vehicle/crosshair/third_camera.png");
    private static final Identifier CROSSHAIR_US_APC = Reference.id("textures/overlay/vehicle/crosshair/us_apc.png");
    private static final Identifier WEAPON_ICON_CANNON_20MM = Reference.id("textures/overlay/vehicle/weapon/icons/cannon_20mm.png");
    private static final Identifier WEAPON_ICON_COAX_762 = Reference.id("textures/overlay/vehicle/weapon/icons/gun_7_62mm.png");
    /** Weapon icon art (white silhouettes, 300x64), drawn at 56x12 in the weapon list. */
    private static final int WEAPON_ICON_TEXTURE_WIDTH = 300;
    private static final int WEAPON_ICON_TEXTURE_HEIGHT = 64;
    private static final int WEAPON_ICON_W = 56, WEAPON_ICON_H = 12;
    private static final float HELICOPTER_CRITICAL_DAMAGE_WARNING_HEALTH = 20.0F;

    private VehicleHudOverlay() {}

    public static void renderVehicleHud(GuiGraphicsExtractor guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // F1 hides it with the rest of the HUD.
        if (player == null || minecraft.options.hideGui || !(player.getVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }
        render(guiGraphics, minecraft, vehicle);
    }

    public static boolean isVehicleHudActive() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof VehicleEntity;
    }

    /** Top of the vehicle card in GUI units when it was last drawn at the bottom middle, else -1. */
    private static int cardTopGui = -1;

    /**
     * How far to lift the action bar so it sits above the vehicle card at the bottom middle (the action bar's text
     * spans 72 to 64 above the bottom); 0 when the card is elsewhere or not shown.
     */
    public static int overlayMessageLift() {
        Minecraft minecraft = Minecraft.getInstance();
        if (cardTopGui < 0 || !(minecraft.player != null && minecraft.player.getVehicle() instanceof VehicleEntity)) {
            return 0;
        }
        return Math.max(0, minecraft.getWindow().getGuiScaledHeight() - 61 - cardTopGui);
    }

    public static boolean shouldReplaceHotbar() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && player.getVehicle() instanceof VehicleEntity vehicle
                && shouldReplaceHotbar(player, vehicle);
    }

    private static boolean shouldReplaceHotbar(LocalPlayer player, VehicleEntity vehicle) {
        return player == vehicle.getControllingPassenger()
                || vehicle.shouldBanPassengerHand(player)
                || vehicle.canPassengerUseSelectedVehicleWeapon(player);
    }

    /** The information layer's frame: its graphics, the screen size in HUD units and the scale to GUI units. */
    private record Layer(GuiGraphicsExtractor g, Minecraft mc, Font font, VehicleEntity vehicle, int width, int height, float scale) {
        int toGui(int y) {
            return Math.round(y * this.scale);
        }
    }

    private static void render(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, VehicleEntity vehicle) {
        // Sights and markers first, at the GUI's own scale: they must sit where things are on screen.
        var turretInfo = vehicle.vehicleData().defaults().turret();
        if (turretInfo.slewLimited() && vehicle.turretSeat() == vehicle.seatOf(minecraft.player)) {
            renderBoreMarker(guiGraphics, minecraft, vehicle);
        }
        boolean focusedSight = isFocusedVehicleSight(vehicle);
        if (focusedSight) {
            renderFocusedSight(guiGraphics, minecraft);
        }
        boolean hmd = helicopterHmd(minecraft, vehicle, focusedSight);
        if (vehicle.hasVehicleWeapons() && vehicle.canPassengerUseSelectedVehicleWeapon(minecraft.player)) {
            boolean jetPilot = vehicle.isJet() && vehicle.getControllingPassenger() == minecraft.player;
            // A jet's guns are fixed: its marker sits where the gun points, just below the nose, and banks with the jet. Zoomed (the targeting
            // pod) only the pod's reticle shows: where the missiles go.
            boolean pod = vehicle.isJetPodView(minecraft.player);
            if (jetPilot && pod) {
                renderPodCamera(guiGraphics, minecraft, vehicle);
            } else if (!jetPilot && !hmd) {
                // In a helicopter's first person the helmet display is the HUD: no reticle over the view (the user's
                // review, 2026-11-19, "for immersion"); zoomed, the sight is drawn as before.
                renderReticle(guiGraphics, vehicle);
            }
            // From the cockpit the view over the nose is clear, as the helicopters' is (the user's review, 2026-11-19:
            // "remove crosshair from A10 in first person"); the chase camera keeps the gun marker.
            if (jetPilot && !pod && !minecraft.options.getCameraType().isFirstPerson()) {
                renderJetGunMarker(guiGraphics, minecraft, vehicle);
            }
        }
        if (hmd) {
            HelicopterHmd.render(guiGraphics, minecraft, vehicle);
        }

        // The information layer.
        float scale = HudKit.scale(minecraft);
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        Layer layer = new Layer(guiGraphics, minecraft, minecraft.font, vehicle,
                Mth.floor(guiGraphics.guiWidth() / scale), Mth.floor(guiGraphics.guiHeight() / scale), scale);
        cardTopGui = -1;
        pendingStrip = null;
        try {
            renderOperatorPanel(layer);
            renderCrew(layer);
            renderWeaponList(layer);
            if (vehicle.isJet()) {
                renderJetInstruments(layer);
            }
            renderVehicleWarning(layer);
            // The status card shows in a helicopter's first person too (2026-11-19: the helmet display's own green
            // status block in the corner read as a regression from the clean card every other vehicle has).
            boolean card = !focusedSight || vehicle.vehicleData().defaults().vehicleType() == VehicleType.BOAT;
            if (card) {
                renderVehicleCard(layer, focusedSight);
            }
        } finally {
            pose.popMatrix();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The vehicle card: name, hull, energy, the selected weapon, decoys, damage.

    private static void renderVehicleCard(Layer l, boolean focusedSight) {
        Minecraft mc = l.mc();
        VehicleEntity vehicle = l.vehicle();
        // Fixed width, so nothing in it moves as a reading changes (the speed card grew from 9.9 to 10.0: review, 2026-11-18).
        HudPanel panel = new HudPanel(l.font()).minWidth(132);
        float hull = vehicle.maxVehicleHealth() <= 0.0F ? 0.0F : Mth.clamp(vehicle.vehicleHealth() / vehicle.maxVehicleHealth(), 0.0F, 1.0F);
        // The vehicle's name heads the card again (the user, 2026-11-19: names should stay in the HUD), its lamp the
        // hull's state.
        panel.header(HudKit.health(hull), vehicle.getName(), null);
        panel.bar(HudKit.ICON_HULL, Component.translatable("hud.jeg.vehicle.hull"), hull, HudKit.health(hull),
                Component.literal(Math.round(vehicle.vehicleHealth()) + "/" + Math.round(vehicle.maxVehicleHealth())), HudKit.TEXT);
        if (vehicle.maxVehicleEnergy() > 0) {
            float energy = Mth.clamp((float) vehicle.vehicleEnergy() / (float) vehicle.maxVehicleEnergy(), 0.0F, 1.0F);
            // A share, not the raw store (a full dozer holds 5,000,000).
            panel.bar(HudKit.ICON_ENERGY, Component.translatable("hud.jeg.vehicle.energy_label"), energy,
                    energy > 0.2F ? HudKit.INFO : HudKit.BAD, Component.literal(Math.round(energy * 100) + "%"), HudKit.TEXT);
        }
        if (moves(vehicle) && !vehicle.isJet()) {
            // Speed in the same column as the hull's and energy's readings (the user, 2026-11-26: it sat apart, against
            // the card's right edge, while the others start after their bars).
            panel.readingAfterBars(HudKit.ICON_SPEED, Component.translatable("hud.jeg.vehicle.speed_label"),
                    Component.literal(String.format(Locale.ROOT, "%.1f ", vehicleSpeedBlocksPerSecond(vehicle)))
                            .append(Component.translatable("hud.jeg.vehicle.speed_unit").withColor(HudKit.MUTED)), HudKit.TEXT);
        }
        boolean showWeaponInfo = vehicle.hasVehicleWeapons()
                && vehicle.canPassengerUseSelectedVehicleWeapon(mc.player)
                && (!focusedSight || vehicle.vehicleData().defaults().vehicleType() == VehicleType.BOAT);
        Identifier selectedWeaponId = showWeaponInfo ? vehicle.selectedVehicleWeaponId(mc.player) : null;
        if (selectedWeaponId != null) {
            // The weapon and its rounds are in the weapon list (bottom right); only what changes now is said here.
            if (vehicle.selectedVehicleWeaponReloading(mc.player)) {
                int seconds = Math.max(1, Math.ceilDiv(vehicle.selectedVehicleWeaponReloadTicks(mc.player), 20));
                panel.note(HudKit.ICON_RELOAD, Component.translatable("hud.jeg.vehicle.reloading", seconds), HudKit.WARN);
            }
            if (vehicle.isSelectedVehicleWeaponLockOn(mc.player)) {
                boolean seeking = VehicleClientState.isRidingVehicle()
                        && VehicleClientState.vehicleId() == vehicle.getId()
                        && VehicleClientState.seekDown();
                Component lock = seeking
                        ? Component.translatable(vehicle.hasMissileLock() ? "hud.jeg.vehicle.locked" : "hud.jeg.vehicle.locking")
                        : Component.translatable("hud.jeg.vehicle.seek_prompt", HudKit.keyName(KeyBindings.VEHICLE_SEEK));
                panel.note(HudKit.ICON_TARGET, lock, seeking ? vehicle.hasMissileLock() ? HudKit.BAD : HudKit.WARN : HudKit.INFO);
            }
        }
        if (showDecoyStatus(vehicle)) {
            boolean ready = vehicle.vehicleDecoyCooldown() == 0;
            panel.note(HudKit.ICON_DECOY, decoyLine(vehicle), ready ? HudKit.INFO : HudKit.WARN);
        }
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.AutoDefenceEntity defence && !KeyBindings.VEHICLE_TOW.isUnbound()) {
            // Its automatic mode, with the key that switches it (also a switch on its hold screen).
            panel.note(HudKit.ICON_TARGET, Component.translatable(defence.automatic() ? "hud.jeg.auto_defence.on" : "hud.jeg.auto_defence.off",
                    HudKit.keyName(KeyBindings.VEHICLE_TOW)), defence.automatic() ? HudKit.OK : HudKit.INFO);
        }
        Component damage = damagedParts(vehicle);
        if (damage != null) {
            panel.note(HudKit.ICON_WARN, Component.translatable("hud.jeg.vehicle.damaged", damage), HudKit.BAD);
        }

        int w = panel.width(), h = panel.height();
        int x, y;
        if (vehicleInfoInCorner(mc, vehicle)) {
            x = l.width() - HudKit.MARGIN - w;
            y = l.height() - HudKit.MARGIN - h;
        } else if (helicopterHmd(mc, vehicle, focusedSight) && vehicle.cockpitViewDown() > 0.0F) {
            // The view looks down at a full-width instrument panel (the CH-47's): the lower corners are its displays, so
            // the card goes up over the windscreen, top right (the crew list has the top left).
            x = l.width() - HudKit.MARGIN - w;
            y = HudKit.MARGIN;
        } else if (helicopterHmd(mc, vehicle, focusedSight)) {
            // In a helicopter's first person the top middle is the helmet display's heading tape, the middle its speed and
            // height boxes, the bottom middle the instrument panel: the card takes the crew list's corner, bottom left
            // (the crew list goes to the top left).
            x = HudKit.MARGIN;
            y = Mth.floor((l.g().guiHeight() - CREW_BOTTOM_GUI) / l.scale()) - h;
        } else if (vehicleInfoAtTop(mc, vehicle)) {
            x = (l.width() - w) / 2;
            y = HudKit.MARGIN;
        } else {
            // Bottom middle: where the hotbar was (it is hidden for whoever works the vehicle), or above a passenger's own
            // hotbar and status bars.
            int bottom = shouldReplaceHotbar(mc.player, vehicle) ? l.height() - HudKit.MARGIN
                    : Mth.floor((l.g().guiHeight() - 62) / l.scale());
            x = (l.width() - w) / 2;
            y = bottom - h;
            cardTopGui = l.toGui(y);
        }
        panel.draw(l.g(), x, y);
        HudPanel strip = pendingStrip;
        pendingStrip = null;
        if (strip != null) {
            boolean below = y + h + 4 + strip.height() < l.height() / 2;
            strip.draw(l.g(), (l.width() - strip.width()) / 2, below ? y + h + 4 : HudKit.MARGIN);
        }
    }

    /** The Chinook pilot's strip in a chase view, drawn under the status card once its place is known. */
    private static HudPanel pendingStrip;

    /**
     * The Chinook pilot's strip: the ramp, the crew door, the sling and who is aboard, with the keys for each for a few
     * seconds after boarding. It had none: a pilot saw no sign of its twenty-second
     * start, nor which keys work the ramp and the sling (two-player play-test, 2026-11-25). Two short lines, so it fits
     * between the crew list and the helmet display's speed box on a small screen.
     */
    private static void chinookRows(Layer l, HudPanel panel, ttv.migami.jeg.vehicle.entity.ChinookEntity chinook) {
        Minecraft mc = l.mc();
        // No engine start-up or shut-down readout: it starts as the other helicopters do, heard and seen in its rotors
        // (the user, 2026-11-26: "remove the chinook startup time indicator and preserve it like the other helis").
        int aboard = (int) chinook.getPassengers().stream().filter(p -> p != mc.player).count()
                + ttv.migami.jeg.vehicle.cabin.CabinClient.othersAboard(chinook.getId());
        int ramp = chinook.rampTarget();
        boolean moving = chinook.rampMoving();
        Component rampState = Component.translatable("hud.jeg.ch47.ramp_" + ramp);
        boolean door = chinook.crewDoorTarget();
        var sling = chinook.sling();
        Component slingState;
        int slingColour;
        if (sling.state() == ttv.migami.jeg.vehicle.entity.ChinookSling.CARRYING && sling.loadEntityId() >= 0
                && mc.level.getEntity(sling.loadEntityId()) instanceof VehicleEntity load) {
            slingState = load.getName();
            slingColour = HudKit.ACCENT;
        } else if (sling.state() == ttv.migami.jeg.vehicle.entity.ChinookSling.HANGING && chinook.slingCandidate() >= 0
                && mc.level.getEntity(chinook.slingCandidate()) instanceof VehicleEntity reachable) {
            // The hook would take it now: one press hooks it.
            slingState = Component.translatable("hud.jeg.ch47.sling_in_reach", reachable.getName(), HudKit.keyName(KeyBindings.VEHICLE_TOW));
            slingColour = HudKit.OK;
        } else {
            boolean stowed = sling.state() == ttv.migami.jeg.vehicle.entity.ChinookSling.STOWED;
            slingState = Component.translatable(stowed ? "hud.jeg.ch47.sling_stowed" : "hud.jeg.ch47.sling_lowered");
            slingColour = stowed ? HudKit.MUTED : HudKit.WARN;
        }
        MutableComponent parts = Component.empty()
                .append(Component.translatable("hud.jeg.ch47.ramp").withColor(HudKit.MUTED)).append(" ")
                .append((moving ? Component.translatable("hud.jeg.ch47.moving", rampState) : rampState.copy())
                        .withColor(moving ? HudKit.WARN : ramp == 0 ? HudKit.TEXT : HudKit.ACCENT))
                .append("   ").append(Component.translatable("hud.jeg.ch47.door").withColor(HudKit.MUTED)).append(" ")
                .append(Component.translatable(door ? "hud.jeg.ch47.door_open" : "hud.jeg.ch47.door_shut").withColor(door ? HudKit.ACCENT : HudKit.TEXT))
                .append("   ").append(Component.translatable("hud.jeg.ch47.sling").withColor(HudKit.MUTED)).append(" ")
                .append(slingState.copy().withColor(slingColour));
        if (aboard > 0) {
            parts.append("   ").append(Component.translatable("hud.jeg.ch47.aboard_n", aboard).withColor(HudKit.MUTED));
        }
        panel.note(HudKit.ICON_GEAR, parts, HudKit.TEXT);
        float hints = hintAlpha(mc, chinook);
        panel.hints(hints,
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.collective"), HudKit.keyName(mc.options.keyJump), HudKit.keyName(KeyBindings.VEHICLE_BRAKE_DESCEND)));
        panel.hints(hints,
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.ramp"), HudKit.keyName(KeyBindings.VEHICLE_SWITCH_WEAPON)),
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.sling"), HudKit.keyName(KeyBindings.VEHICLE_TOW)));
    }

    /** The damaged parts, named, or null when nothing is damaged. */
    private static Component damagedParts(VehicleEntity vehicle) {
        java.util.List<Component> parts = new java.util.ArrayList<>();
        if (vehicle.isEngineDamaged()) parts.add(Component.translatable("hud.jeg.vehicle.part.engine"));
        if (vehicle.isLeftWheelDamaged()) parts.add(Component.translatable("hud.jeg.vehicle.part.left"));
        if (vehicle.isRightWheelDamaged()) parts.add(Component.translatable("hud.jeg.vehicle.part.right"));
        if (vehicle.isTurretDamaged()) parts.add(Component.translatable("hud.jeg.vehicle.part.turret"));
        if (parts.isEmpty()) return null;
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(" · ");
            out.append(parts.get(i));
        }
        return out;
    }

    public static void renderArmorValueBar(GuiGraphicsExtractor guiGraphics, int x, int y, float ratio) {
        int filled = Mth.clamp(Math.round(60.0F * ratio), 0, 60);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ARMOR_ICON, x, y, 0, 0, 10, 10, 10, 10);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, VALUE_FRAME, x + 11, y + 2, 0, 0, 60, 6, 120, 12, 120, 12);
        if (filled > 0) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, VALUE_BAR, x + 11, y + 2, 0, 0, filled, 6, 60, 6);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Crew and weapons.

    private static final int CREW_BOTTOM_GUI = 26;

    private static HudPanel crewPanel(Minecraft mc, Font font, VehicleEntity vehicle) {
        HudPanel panel = new HudPanel(font).minWidth(64);
        int seats = vehicle.vehicleData().defaults().seats().size();
        for (int seat = 0; seat < seats; seat++) {
            Entity passenger = vehicle.passengerForSeat(seat);
            Component name = passenger == null ? Component.literal("—") : passenger.getName();
            int colour = passenger == null ? HudKit.FAINT : passenger == mc.player ? HudKit.ACCENT : HudKit.TEXT;
            panel.value(vehicle.isDriverSeat(seat) ? HudKit.ICON_DRIVER : HudKit.ICON_SEAT, Component.literal(String.valueOf(seat + 1)), name, colour);
        }
        return panel;
    }

    /** The crew list, bottom left, clear of the chat input line. */
    private static void renderCrew(Layer l) {
        boolean others = l.vehicle().getPassengers().stream().anyMatch(p -> p != l.mc().player);
        if (!others && hintAlpha(l.mc(), l.vehicle()) <= 0.0F) {
            return;
        }
        HudPanel panel = crewPanel(l.mc(), l.font(), l.vehicle());
        if (helicopterHmd(l.mc(), l.vehicle(), isFocusedVehicleSight(l.vehicle()))) {
            // Its corner is the vehicle card's in a helicopter's first person.
            panel.draw(l.g(), HudKit.MARGIN, HudKit.MARGIN);
            return;
        }
        int bottom = Mth.floor((l.g().guiHeight() - CREW_BOTTOM_GUI) / l.scale());
        panel.draw(l.g(), HudKit.MARGIN, bottom - panel.height());
    }

    private static final int WEAPON_ROW = 15;

    /**
     * The weapon list, bottom right: each usable weapon's number key, the ammo it takes (as the item), its icon (or
     * name) and its own ammo count, so what is left of each is clear without switching. The selected one is lit.
     */
    private static void renderWeaponList(Layer l) {
        VehicleEntity vehicle = l.vehicle();
        Minecraft mc = l.mc();
        if (!hasWeaponSelectorHud(vehicle)) {
            return;
        }
        var weapons = vehicle.vehicleData().defaults().weapons();
        int selected = vehicle.selectedVehicleWeaponIndex(mc.player);
        java.util.List<Integer> usable = new java.util.ArrayList<>();
        for (int index = 0; index < weapons.size() && usable.size() < 9; index++) {
            if (vehicle.canPassengerUseVehicleWeapon(mc.player, index)) {
                usable.add(index);
            }
        }
        if (usable.isEmpty()) {
            return;
        }
        Font font = l.font();
        int art = WEAPON_ICON_W, ammoW = 0;
        SlotAmmo[] ammo = new SlotAmmo[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            int index = usable.get(i);
            ammo[i] = slotAmmo(vehicle, index, index == selected);
            ammoW = Math.max(ammoW, HudKit.width(font, Component.literal(ammo[i].label())));
            if (weaponIcon(weapons.get(index).weaponId()) == null) {
                art = Math.max(art, HudKit.width(font, weaponName(weapons.get(index).weaponId())));
            }
        }
        // Number, ammo item, icon, count.
        int pad = 5, numberW = 6, itemW = 12;
        int w = pad + numberW + 3 + itemW + 4 + art + 8 + ammoW + pad;
        int h = usable.size() * WEAPON_ROW + 4;
        int x = l.width() - HudKit.MARGIN - w;
        int y = l.height() - HudKit.MARGIN - h;
        GuiGraphicsExtractor g = l.g();
        HudKit.panel(g, x, y, w, h);
        for (int i = 0; i < usable.size(); i++) {
            int index = usable.get(i);
            boolean on = index == selected;
            int ry = y + 2 + i * WEAPON_ROW;
            if (on) {
                g.fill(x + 2, ry, x + w - 2, ry + WEAPON_ROW, HudKit.CHIP);
                g.fill(x + 2, ry + 2, x + 3, ry + WEAPON_ROW - 2, HudKit.ACCENT);
            }
            int ty = ry + (WEAPON_ROW - HudKit.CAP) / 2;
            int cx = x + pad;
            HudKit.text(g, font, String.valueOf(index + 1), cx, ty, on ? HudKit.ACCENT : HudKit.FAINT);
            cx += numberW + 3;
            ItemStack ammoItem = ammoItem(weapons.get(index).ammoId());
            if (!ammoItem.isEmpty()) {
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(cx, ry + (WEAPON_ROW - itemW) / 2.0F);
                pose.scale(itemW / 16.0F, itemW / 16.0F);
                g.item(ammoItem, 0, 0);
                pose.popMatrix();
            }
            cx += itemW + 4;
            Identifier icon = weaponIcon(weapons.get(index).weaponId());
            int tint = on ? HudKit.TEXT : HudKit.MUTED;
            if (icon != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, icon, cx, ry + (WEAPON_ROW - WEAPON_ICON_H) / 2, 0, 0, WEAPON_ICON_W, WEAPON_ICON_H,
                        WEAPON_ICON_TEXTURE_WIDTH, WEAPON_ICON_TEXTURE_HEIGHT, WEAPON_ICON_TEXTURE_WIDTH, WEAPON_ICON_TEXTURE_HEIGHT, tint);
            } else {
                HudKit.text(g, font, weaponName(weapons.get(index).weaponId()), cx, ty, tint);
            }
            HudKit.rightAligned(g, font, Component.literal(ammo[i].label()), x + w - pad, ty, ammo[i].color());
        }
    }

    private static Component weaponName(Identifier weaponId) {
        return Component.translatable("item." + weaponId.getNamespace() + "." + weaponId.getPath());
    }

    private record SlotAmmo(String label, int color) {}

    /**
     * One weapon's ammo: loaded / spare for a magazine weapon, the rounds aboard for a belt-fed one, the seconds left
     * while it reloads. Red when it has nothing left, amber when selected.
     */
    private static SlotAmmo slotAmmo(VehicleEntity vehicle, int slot, boolean selected) {
        var weapons = vehicle.vehicleData().defaults().weapons();
        if (slot < 0 || slot >= weapons.size()) {
            return new SlotAmmo("", HudKit.TEXT);
        }
        var stats = VehicleWeaponStats.get(weapons.get(slot).weaponId());
        boolean magazine = stats != null && stats.usesMagazine() && !stats.isInventoryFed();
        int loaded = vehicle.vehicleWeaponSlotAmmo(slot);
        int reserve = magazine ? vehicle.vehicleWeaponSlotReserveAmmo(slot) : 0;
        int reloadTicks = vehicle.vehicleWeaponSlotReloadTicks(slot);
        String label;
        int color;
        if (reloadTicks > 0) {
            label = Component.translatable("hud.jeg.vehicle.reloading_short", Math.max(1, Math.ceilDiv(reloadTicks, 20))).getString()
                    + (magazine ? " " + reserve : "");
            color = HudKit.WARN;
        } else {
            label = magazine ? loaded + "/" + reserve : String.valueOf(loaded);
            color = loaded + reserve <= 0 ? HudKit.BAD : selected ? HudKit.ACCENT : HudKit.TEXT;
        }
        return new SlotAmmo(label, color);
    }

    private static ItemStack ammoItem(Identifier ammoId) {
        if (ammoId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(ammoId);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Identifier weaponIcon(Identifier weaponId) {
        return switch (weaponId.getPath()) {
            case "vehicle_20mm_cannon" -> WEAPON_ICON_CANNON_20MM;
            // The 2A42's two feeds told apart at a glance (the user's review, 2026-11-19: "the same turret twice").
            case "vehicle_30mm_cannon" -> Reference.id("textures/overlay/vehicle/weapon/icons/cannon_30mm_ap.png");
            case "vehicle_30mm_cannon_he" -> Reference.id("textures/overlay/vehicle/weapon/icons/cannon_30mm_he.png");
            case "vehicle_coax_machine_gun", "light_machine_gun" -> WEAPON_ICON_COAX_762;
            case "vehicle_gau8_cannon" -> Reference.id("textures/overlay/vehicle/weapon/icons/cannon_30mm.png");
            case "vehicle_agm65_missile" -> Reference.id("textures/overlay/vehicle/weapon/icons/agm_65d.png");
            case "vehicle_agm65g_missile" -> Reference.id("textures/overlay/vehicle/weapon/icons/agm_65g.png");
            case "vehicle_70mm_rocket", "vehicle_80mm_rocket" -> Reference.id("textures/overlay/vehicle/weapon/icons/small_rocket.png");
            case "vehicle_bmp2_missile" -> Reference.id("textures/overlay/vehicle/weapon/icons/missile_9m113.png");
            case "vehicle_9m120_driver_missile", "vehicle_9m120_passenger_missile" -> Reference.id("textures/overlay/vehicle/weapon/icons/missile_9m120.png");
            case "vehicle_kh39_missile" -> Reference.id("textures/overlay/vehicle/weapon/icons/kh_39.png");
            case "vehicle_9m336_missile" -> Reference.id("textures/overlay/vehicle/weapon/icons/ru_9m336.png");
            default -> null;
        };
    }

    private static Component vehicleAmmoComponent(VehicleEntity vehicle, Entity passenger) {
        int slot = vehicle.selectedVehicleWeaponIndex(passenger);
        var weapons = vehicle.vehicleData().defaults().weapons();
        if (slot < 0 || slot >= weapons.size()) {
            return Component.empty();
        }
        String ammoCounts = vehicle.selectedVehicleWeaponAmmo(passenger) + "/" + vehicle.selectedVehicleWeaponReserveAmmo(passenger);
        // Named for the weapon, not the ammo item: weapons that share an ammo (the MI-28 pilot's thermobaric 9M-120F and
        // the gunner's plain 9M-120 both load medium anti-ground missiles) read differently. The weapon list beside it
        // still shows which ammo each one takes.
        Identifier weaponId = weapons.get(slot).weaponId();
        return Component.translatable("item." + weaponId.getNamespace() + "." + weaponId.getPath())
                .append(Component.literal(": " + ammoCounts));
    }

    private static boolean vehicleUsesLoadedAmmo(VehicleEntity vehicle, Entity passenger) {
        Identifier selectedWeaponId = vehicle.selectedVehicleWeaponId(passenger);
        if (selectedWeaponId == null) {
            return false;
        }
        var stats = VehicleWeaponStats.get(selectedWeaponId);
        return stats != null && stats.usesMagazine() && !stats.isInventoryFed();
    }

    private static boolean showDecoyStatus(VehicleEntity vehicle) {
        return vehicle.hasBuiltInDecoy() || vehicle.vehicleFlareAmmo() > 0 || vehicle.vehicleDecoyCooldown() > 0;
    }

    private static boolean builtInDecoyUsesSmoke(VehicleEntity vehicle) {
        VehicleType type = vehicle.vehicleData().defaults().vehicleType();
        return vehicle.hasBuiltInDecoy() && type != VehicleType.HELICOPTER && type != VehicleType.AIRCRAFT;
    }

    private static Component decoyStatus(VehicleEntity vehicle) {
        return vehicle.vehicleDecoyCooldown() == 0
                ? Component.translatable("hud.jeg.vehicle.ready")
                : Component.literal(Math.ceilDiv(vehicle.vehicleDecoyCooldown(), 20) + "s");
    }

    /** Whether the vehicle drives, sails or flies at all (a gun on the ground has no speed to show). */
    private static boolean moves(VehicleEntity vehicle) {
        return vehicle.vehicleData().defaults().engine().maxForwardSpeed() > 0.0D;
    }

    /** Each jet's shown speed (m/s) and the tick it was last measured. */
    private static final java.util.Map<VehicleEntity, double[]> JET_SPEEDS = new java.util.WeakHashMap<>();

    /**
     * A jet's airspeed, m/s: how far it actually moved over the last tick, eased so a server correction doesn't jolt the
     * number (the user's review, 2026-11-19: "the A10 speed counter seems to incorrectly count the A10 speed").
     */
    public static double jetSpeed(VehicleEntity jet) {
        double[] shown = JET_SPEEDS.computeIfAbsent(jet, j -> new double[]{j.getDeltaMovement().length() * 20.0D, -1.0D});
        if (shown[1] != jet.tickCount) {
            shown[1] = jet.tickCount;
            double moved = jet.position().distanceTo(new net.minecraft.world.phys.Vec3(jet.xo, jet.yo, jet.zo)) * 20.0D;
            shown[0] += (moved - shown[0]) * 0.35D;
        }
        return shown[0];
    }

    private static double vehicleSpeedBlocksPerSecond(VehicleEntity vehicle) {
        return Math.sqrt(vehicle.distanceToSqr(vehicle.xOld, vehicle.getY(), vehicle.zOld)) * 20.0D;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Instruments and warnings.

    /** Ground speed, top right: m/s, as every vehicle's speed reads. */
    private static void renderSpeed(Layer l) {
        Font font = l.font();
        Component value = Component.literal(String.format(Locale.ROOT, "%.1f", vehicleSpeedBlocksPerSecond(l.vehicle())));
        Component unit = Component.translatable("hud.jeg.vehicle.speed_unit");
        int w = 5 + 11 + HudKit.width(font, value) + 3 + HudKit.width(font, unit) + 5;
        int h = HudKit.CAP + 10;
        int x = l.width() - HudKit.MARGIN - w, y = HudKit.MARGIN;
        HudKit.panel(l.g(), x, y, w, h);
        HudKit.icon(l.g(), HudKit.ICON_SPEED, x + 5, y + 5, HudKit.MUTED);
        HudKit.text(l.g(), font, value, x + 16, y + 5, HudKit.TEXT);
        HudKit.text(l.g(), font, unit, x + 16 + HudKit.width(font, value) + 3, y + 5, HudKit.MUTED);
    }

    /** The flaps are out below their speed (AircraftInfo), as the flight model and the renderer have them. */
    private static boolean flapsOut(VehicleEntity jet) {
        var aircraft = jet.vehicleData().aircraft();
        return aircraft.hasFlaps() && jet.getDeltaMovement().length() < aircraft.flapSpeedFactor() * jet.vehicleData().defaults().engine().jetStallSpeed();
    }

    /**
     * A jet's instruments, top right: airspeed, height above the ground, throttle and gear (or flaps), the speed brake,
     * the cruise setting and what the brake key is doing. The pilot also gets a stall or pull-up warning under the
     * crosshair.
     */
    private static void renderJetInstruments(Layer l) {
        Minecraft minecraft = l.mc();
        VehicleEntity jet = l.vehicle();
        double speed = jetSpeed(jet);
        double altitude = jet.exactAltitudeAboveGround();
        boolean grounded = jet.onGround();
        float gear = jet.jetGear();
        boolean pilot = minecraft.player != null && jet.getControllingPassenger() == minecraft.player;
        HudPanel panel = new HudPanel(l.font()).minWidth(96);
        // As the vehicle card's speed: the number against the right edge, its unit muted after it (2026-11-19: the jet's
        // m/s sat loose after the label and the card resized with its digits).
        panel.reading(HudKit.ICON_SPEED, Component.translatable("hud.jeg.jet.speed"),
                Component.literal(Math.round(speed) + " ").append(Component.translatable("hud.jeg.vehicle.speed_unit").withColor(HudKit.MUTED)), HudKit.TEXT);
        panel.reading(HudKit.ICON_ALT, Component.translatable("hud.jeg.jet.altitude"),
                Component.literal((grounded ? "0" : String.valueOf(Math.round(altitude))) + " ").append(Component.literal("m").withColor(HudKit.MUTED)), HudKit.TEXT);
        panel.bar(HudKit.ICON_THROTTLE, Component.translatable("hud.jeg.jet.throttle"), jet.jetThrottle(), HudKit.ACCENT,
                Component.literal(Math.round(jet.jetThrottle() * 100.0F) + "%"), HudKit.TEXT);
        if (jet.vehicleData().aircraft().fixedGear()) {
            // Its wheels never move: what it does have, flaps, in their place.
            boolean out = flapsOut(jet);
            panel.value(HudKit.ICON_GEAR, Component.translatable("hud.jeg.jet.flaps"),
                    Component.translatable(out ? "hud.jeg.jet.out" : "hud.jeg.jet.in"), out ? HudKit.WARN : HudKit.TEXT);
        } else {
            boolean down = gear >= 0.99F, up = gear <= 0.01F;
            panel.value(HudKit.ICON_GEAR, Component.translatable("hud.jeg.jet.gear"),
                    Component.translatable(down ? "hud.jeg.jet.down" : up ? "hud.jeg.jet.up" : "hud.jeg.jet.moving"),
                    down ? HudKit.OK : up ? HudKit.TEXT : HudKit.WARN);
        }
        if (jet.jetSpeedBrake() > 0.05F) {
            panel.note(HudKit.ICON_WARN, Component.translatable("hud.jeg.jet.speed_brake"), HudKit.WARN);
        }
        float cruise = ttv.migami.jeg.vehicle.client.VehicleInputHandler.jetCruiseTarget();
        if (pilot && cruise >= 0.0F) {
            panel.note(HudKit.ICON_SPEED, Component.translatable("hud.jeg.jet.cruise", Math.round(cruise * 20.0F)), HudKit.INFO);
        }
        if (pilot && KeyBindings.VEHICLE_BRAKE_DESCEND.isDown()) {
            panel.note(HudKit.ICON_WARN, Component.translatable(grounded ? "hud.jeg.vehicle.jet_wheel_brake" : "hud.jeg.vehicle.jet_airbrake"), HudKit.WARN);
        }
        panel.draw(l.g(), l.width() - HudKit.MARGIN - panel.width(), HudKit.MARGIN);
        if (!pilot) {
            return;
        }
        String warning = null;
        if (jet.jetPullUpWarning()) {
            warning = "message.jeg.vehicle.jet_pull_up";
        } else if (jet.jetStallWarning()) {
            warning = "message.jeg.vehicle.jet_stall_warning";
        }
        if (warning != null) {
            warningChip(l, Component.translatable(warning), l.height() / 2 + 28, jet.tickCount);
        }
    }

    private static void renderVehicleWarning(Layer l) {
        VehicleEntity vehicle = l.vehicle();
        if (l.mc().player == null) {
            return;
        }
        String warningKey = vehicle.activeWarningMessageKey();
        if (warningKey.isEmpty() && vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER) {
            warningKey = helicopterWarningKey(vehicle);
        }
        if (warningKey == null || warningKey.isEmpty()) {
            return;
        }
        warningChip(l, Component.translatable(warningKey), l.height() / 2 + 12, vehicle.tickCount);
    }

    /**
     * A warning under the crosshair: a red chip with a warning sign, its fill breathing slowly rather than flashing, so it
     * catches the eye without strobing.
     */
    private static void warningChip(Layer l, Component text, int y, int tick) {
        Font font = l.font();
        float pulse = 0.5F + 0.5F * Mth.sin(tick * 0.35F);
        int w = 5 + 11 + HudKit.width(font, text) + 6;
        int h = HudKit.CAP + 8;
        int x = (l.width() - w) / 2;
        int fill = (Math.round(0xB0 + 0x40 * pulse) << 24) | 0xC0322C;
        HudKit.panel(l.g(), x, y, w, h, fill);
        HudKit.icon(l.g(), HudKit.ICON_WARN, x + 5, y + 4, 0xFFFFFFFF);
        HudKit.text(l.g(), font, text, x + 16, y + 4, 0xFFFFFFFF);
    }

    private static String helicopterWarningKey(VehicleEntity vehicle) {
        if (vehicle.vehicleHealth() < Math.min(HELICOPTER_CRITICAL_DAMAGE_WARNING_HEALTH, vehicle.maxVehicleHealth())) {
            return "message.jeg.vehicle.helicopter_critical_damage_warning";
        }
        if (vehicle.maxVehicleEnergy() > 0 && vehicle.vehicleEnergy() <= 0) {
            return "message.jeg.vehicle.helicopter_power_loss_warning";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Operator panels, top left.

    private static int hintVehicleId = -1;
    private static long hintStart, hintLastSeen;
    /** How long the key hints stay after boarding (ticks), then fade over {@link #HINT_FADE}. */
    private static final int HINT_TICKS = 140, HINT_FADE = 20;

    /** The key hints' opacity: full for a few seconds after boarding (or changing vehicle), then fading out. */
    private static float hintAlpha(Minecraft mc, VehicleEntity vehicle) {
        long now = mc.level.getGameTime();
        if (vehicle.getId() != hintVehicleId || now - hintLastSeen > 20) {
            hintVehicleId = vehicle.getId();
            hintStart = now;
        }
        hintLastSeen = now;
        long shown = now - hintStart;
        return shown < HINT_TICKS ? 1F : Math.max(0F, 1F - (shown - HINT_TICKS) / (float) HINT_FADE);
    }

    private static void renderOperatorPanel(Layer l) {
        Minecraft mc = l.mc();
        VehicleEntity vehicle = l.vehicle();
        HudPanel panel = null;
        if (vehicle instanceof BulldozerEntity dozer && vehicle.getControllingPassenger() == mc.player) {
            panel = dozerPanel(l, dozer);
        } else if (vehicle instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity support && vehicle.getControllingPassenger() == mc.player) {
            panel = supportPanel(l, support);
        } else if (vehicle instanceof ttv.migami.jeg.vehicle.entity.MortarEntity mortar && vehicle.getControllingPassenger() == mc.player) {
            panel = mortarPanel(l, mortar);
        } else if (vehicle instanceof ttv.migami.jeg.vehicle.entity.ShellGunEntity gun && gun.laysGun(mc.player)) {
            panel = shellGunPanel(l, gun);
        } else if (vehicle instanceof ttv.migami.jeg.vehicle.entity.ChinookEntity chinook && vehicle.getControllingPassenger() == mc.player) {
            panel = new HudPanel(l.font()).minWidth(0);
            chinookRows(l, panel, chinook);
            if (helicopterHmd(mc, vehicle, isFocusedVehicleSight(vehicle))) {
                // Top left, under the crew list: the status card has the top right and the helmet display's height box
                // sits under it (a taller card covered it), the instrument panel fills the bottom.
                // Always under the crew list's place, shown or not: higher, it ran into the heading tape's end.
                int top = HudKit.MARGIN + crewPanel(mc, l.font(), vehicle).height() + 4;
                panel.draw(l.g(), HudKit.MARGIN, top);
                return;
            }
            // From the chase cameras the strip hangs under the status card (renderVehicleCard): at the bottom middle
            // it covered the slung load, which hangs there in that view.
            pendingStrip = panel;
            return;
        }
        if (panel != null) {
            panel.draw(l.g(), HudKit.MARGIN, HudKit.MARGIN);
        }
    }

    /** "North", "East"... for a yaw (0 faces south, 90 west). */
    private static Component compassName(float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90.0F), 4);
        return Component.translatable(switch (quarter) {
            case 0 -> "hud.jeg.dir.south";
            case 1 -> "hud.jeg.dir.west";
            case 2 -> "hud.jeg.dir.north";
            default -> "hud.jeg.dir.east";
        });
    }

    /**
     * The dozer operator's panel: work state, blade height (on its scale, the travel band shaded and grade ticked),
     * load, hold, grade lock, lane lock and the ripper (laying or raised, what is in its bay and how much more of it the
     * operator carries, which it restocks from). The key hints show for a few seconds after boarding, then fade.
     */
    private static HudPanel dozerPanel(Layer l, BulldozerEntity dozer) {
        Minecraft mc = l.mc();
        float hints = hintAlpha(mc, dozer);
        int status = dozer.workStatus();
        boolean starting = dozer.powered() && !dozer.engineRunning();
        int lamp = starting ? HudKit.WARN : switch (status) {
            case BulldozerTerrainWork.WORKING, BulldozerTerrainWork.TREE -> HudKit.OK;
            case BulldozerTerrainWork.BUSY, BulldozerTerrainWork.SPOIL, BulldozerTerrainWork.RAISED_BLOCKED,
                 BulldozerTerrainWork.TURN_BLOCKED_RAISED -> HudKit.WARN;
            case BulldozerTerrainWork.SOLID, BulldozerTerrainWork.PROTECTED, BulldozerTerrainWork.NO_POWER,
                 BulldozerTerrainWork.TURN_BLOCKED -> HudKit.BAD;
            default -> HudKit.FAINT;
        };
        HudPanel panel = new HudPanel(l.font()).minWidth(132);
        panel.header(lamp, Component.translatable(starting ? "hud.jeg.dozer.starting" : "hud.jeg.dozer.status_" + status), null);

        float lift = dozer.bladeLift();
        float min = BulldozerRig.LIFT_MIN, max = BulldozerRig.LIFT_MAX;
        Component liftValue = lift > BulldozerRig.TRAVEL_LIFT ? Component.translatable("hud.jeg.dozer.travel")
                : Component.literal((lift >= 0 ? "+" : "") + Math.round(lift) + "°");
        panel.slider(HudKit.ICON_BLADE, Component.translatable("hud.jeg.dozer.blade"), (lift - min) / (max - min),
                (BulldozerRig.TRAVEL_LIFT - min) / (max - min), (0 - min) / (max - min), HudKit.ACCENT, liftValue, HudKit.TEXT);
        float load = dozer.load();
        panel.bar(HudKit.ICON_LOAD, Component.translatable("hud.jeg.dozer.load"), load, load > .85F ? HudKit.BAD : HudKit.ACCENT,
                Component.literal(Math.round(Mth.clamp(load, 0, 1) * 100) + "%"), HudKit.TEXT);
        boolean full = dozer.occupiedCargoSlots() >= dozer.vehicleContainerSlots();
        panel.bar(HudKit.ICON_HOLD, Component.translatable("hud.jeg.dozer.hold"),
                dozer.occupiedCargoSlots() / (float) Math.max(1, dozer.vehicleContainerSlots()), full ? HudKit.BAD : 0xFFB4BDC6,
                Component.literal(dozer.occupiedCargoSlots() + "/" + dozer.vehicleContainerSlots()), full ? HudKit.WARN : HudKit.TEXT);

        boolean cutting = dozer.cutFloor() != BulldozerEntity.NO_FLOOR;
        panel.value(HudKit.ICON_GRADE, Component.translatable("hud.jeg.dozer.grade"),
                cutting ? Component.translatable("hud.jeg.dozer.cutting_to", dozer.cutFloor())
                        : Component.translatable(dozer.gradeAssist() ? "hud.jeg.dozer.on" : "hud.jeg.dozer.off"),
                cutting ? HudKit.WARN : dozer.gradeAssist() ? HudKit.OK : HudKit.MUTED);
        Component lane = !dozer.laneLock() ? Component.translatable("hud.jeg.dozer.off")
                : dozer.laneTurning() ? Component.translatable("hud.jeg.dozer.lane_turning", compassName(dozer.laneYaw()))
                : Component.translatable("hud.jeg.dozer.lane_on", compassName(dozer.laneYaw()));
        panel.value(HudKit.ICON_LANE, Component.translatable("hud.jeg.dozer.lane"), lane,
                !dozer.laneLock() ? HudKit.MUTED : dozer.laneTurning() ? HudKit.WARN : HudKit.OK);

        var paving = dozer.pavingStack();
        int spare = 0;
        if (!paving.isEmpty()) {
            for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
                if (stack.is(paving.getItem())) spare += stack.getCount();
            }
        }
        boolean down = dozer.ripperDown();
        // ▼ shanks down and laying (amber), ▲ raised (grey); then what is in the bay and how much more is carried.
        MutableComponent ripper = Component.literal(down ? "▼ " : "▲ ");
        if (paving.isEmpty()) {
            ripper.append(Component.translatable("hud.jeg.dozer.ripper_none").withColor(down ? HudKit.BAD : HudKit.FAINT));
        } else {
            ripper.append(Component.literal(dozer.pavingCount() + " ").append(paving.getHoverName()).withColor(down ? HudKit.TEXT : HudKit.MUTED));
            if (spare > 0) ripper.append(Component.translatable("hud.jeg.dozer.ripper_spare", spare).withColor(HudKit.FAINT));
        }
        panel.value(HudKit.ICON_RIPPER, Component.translatable("hud.jeg.dozer.ripper"), ripper, down ? HudKit.ACCENT : HudKit.MUTED);

        panel.hints(hints,
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.blade"), HudKit.keyName(mc.options.keyJump), HudKit.keyName(mc.options.keyShift)),
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.grade"), HudKit.keyName(KeyBindings.VEHICLE_DEPLOY_DECOY)),
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.lane"), HudKit.keyName(KeyBindings.VEHICLE_SWITCH_WEAPON)));
        panel.hints(hints,
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.ripper"), HudKit.keyName(KeyBindings.RELOAD)),
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.horn"), HudKit.keyName(mc.options.keyAttack)));
        return panel;
    }

    /**
     * The mortar gunner's fire-control panel: the drill state, elevation (on its 45-85 degree scale), bearing, the
     * predicted range and time of flight (MortarAim, against terrain while it is loaded), rounds carried, and the keys.
     */
    private static HudPanel mortarPanel(Layer l, ttv.migami.jeg.vehicle.entity.MortarEntity mortar) {
        Minecraft mc = l.mc();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var prediction = ttv.migami.jeg.vehicle.client.MortarAim.predict(mortar, partial);
        int rounds = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            var stack = mc.player.getInventory().getItem(slot);
            if (stack.is(ttv.migami.jeg.init.ModItems.MORTAR_ROUND.get())) {
                rounds += stack.getCount();
            }
        }
        boolean creative = mc.player.getAbilities().instabuild;
        Component status;
        int lamp;
        if (mortar.settingUp()) {
            status = Component.translatable("hud.jeg.mortar.status_setup");
            lamp = HudKit.WARN;
        } else if (mortar.roundInTube()) {
            status = Component.translatable("hud.jeg.mortar.status_away");
            lamp = HudKit.BAD;
        } else if (mortar.readyInTicks() > 0) {
            status = Component.translatable("hud.jeg.mortar.status_reload", String.format(Locale.ROOT, "%.1f", mortar.readyInTicks() / 20.0F));
            lamp = HudKit.WARN;
        } else if (rounds == 0 && !creative) {
            status = Component.translatable("message.jeg.mortar.no_rounds");
            lamp = HudKit.BAD;
        } else {
            status = Component.translatable("hud.jeg.mortar.status_ready");
            lamp = HudKit.OK;
        }
        if (mortar.isTurretDamaged()) {
            status = Component.translatable("hud.jeg.mortar.status_jammed");
            lamp = HudKit.BAD;
        }
        HudPanel panel = new HudPanel(l.font()).minWidth(132);
        panel.header(lamp, status, Component.translatable("hud.jeg.mortar.title"));
        float elevation = mortar.elevation(partial);
        float min = ttv.migami.jeg.vehicle.util.MortarRig.ELEVATION_MIN, max = ttv.migami.jeg.vehicle.util.MortarRig.ELEVATION_MAX;
        panel.slider(HudKit.ICON_ELEVATION, Component.translatable("hud.jeg.mortar.elevation"), (elevation - min) / (max - min),
                Float.NaN, Float.NaN, HudKit.ACCENT, Component.literal(String.format(Locale.ROOT, "%.1f°", elevation)), HudKit.TEXT);
        int bearing = Math.floorMod(Math.round(mortar.getYRot() + 180.0F), 360);
        panel.value(HudKit.ICON_BEARING, Component.translatable("hud.jeg.mortar.bearing"), Component.literal(bearing + "°"), HudKit.TEXT);
        panel.value(HudKit.ICON_TARGET, Component.translatable("hud.jeg.mortar.range"),
                Component.translatable("hud.jeg.mortar.range_value", Math.round(prediction.range()),
                        String.format(Locale.ROOT, "%.1f", prediction.ticks() / 20.0F)),
                prediction.assumedLevel() ? HudKit.WARN : HudKit.TEXT);
        panel.value(HudKit.ICON_AMMO, Component.translatable("hud.jeg.mortar.rounds"),
                Component.literal(creative ? "∞" : Integer.toString(rounds)), rounds == 0 && !creative ? HudKit.BAD : HudKit.TEXT);
        if (prediction.assumedLevel()) {
            panel.note(HudKit.ICON_WARN, Component.translatable("hud.jeg.mortar.assumed"), HudKit.WARN);
        }
        float hints = hintAlpha(mc, mortar);
        panel.hints(hints,
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.elevate"), HudKit.keyName(mc.options.keyUp), HudKit.keyName(mc.options.keyDown)),
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.traverse"), HudKit.keyName(mc.options.keyLeft), HudKit.keyName(mc.options.keyRight)));
        panel.hints(hints,
                HudPanel.Hint.of(Component.translatable("hud.jeg.hint.fire"), HudKit.keyName(KeyBindings.RELOAD), HudKit.keyName(mc.options.keyAttack)));
        return panel;
    }

    /**
     * A shell gun's fire-control panel (the Mk42's, the BL-132's): the shell selected, the gun's elevation (on its own
     * scale) and bearing, and where a shell fired now lands (ShellGunAim, against terrain while it is loaded). Kept
     * narrow so the vehicle card at the top middle clears it.
     */
    private static HudPanel shellGunPanel(Layer l, ttv.migami.jeg.vehicle.entity.ShellGunEntity gun) {
        Minecraft mc = l.mc();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var prediction = ttv.migami.jeg.vehicle.client.ShellGunAim.predict(gun, partial);
        var shell = gun.selectedShell(mc.player);
        HudPanel panel = new HudPanel(l.font()).minWidth(112);
        float elevation = gun.elevation(partial);
        float min = gun.minElevation(), max = gun.maxElevation();
        panel.slider(HudKit.ICON_ELEVATION, Component.translatable("hud.jeg.mk42.elevation"), (Mth.clamp(elevation, min, max) - min) / (max - min),
                Float.NaN, Float.NaN, HudKit.ACCENT, Component.literal(String.format(Locale.ROOT, "%.1f°", elevation)), HudKit.TEXT);
        panel.value(HudKit.ICON_BEARING, Component.translatable("hud.jeg.mk42.bearing"), Component.literal(gun.bearing(partial) + "°"), HudKit.TEXT);
        panel.value(HudKit.ICON_TARGET, Component.translatable("hud.jeg.mk42.range"),
                Component.translatable("hud.jeg.mortar.range_value", Math.round(prediction.range()),
                        String.format(Locale.ROOT, "%.1f", prediction.ticks() / 20.0F)),
                prediction.assumedLevel() ? HudKit.WARN : HudKit.TEXT);
        for (var row : gun.panelRows(mc.player, partial)) {
            panel.value(HudKit.NO_ICON, row.label(), row.value(), row.colour());
        }
        if (prediction.assumedLevel()) {
            panel.note(HudKit.ICON_WARN, Component.translatable("hud.jeg.mk42.assumed"), HudKit.WARN);
        }
        var warhead = shell.warhead();
        boolean dangerClose = !warhead.kinetic() && warhead.dispense() == null
                && !ttv.migami.jeg.vehicle.projectile.ShellEntity.safeDistance(gun, prediction.impact(), warhead);
        if (dangerClose) {
            panel.note(HudKit.ICON_WARN, Component.translatable("hud.jeg.mk42.too_close"), HudKit.BAD);
        }
        return panel;
    }

    /**
     * The FSV-6 driver's panel: the body's state (packed, deploying, deployed) with its progress, what powers it, the
     * hold, the tow hitch, the R-hold bar while the key is held, why a deploy or pack-up was refused, and the key hints
     * for a few seconds after boarding.
     */
    private static HudPanel supportPanel(Layer l, ttv.migami.jeg.vehicle.entity.SupportVehicleEntity truck) {
        Minecraft mc = l.mc();
        long now = mc.level.getGameTime();
        float hints = hintAlpha(mc, truck);
        var status = truck.status();
        boolean refusal = status != ttv.migami.jeg.vehicle.support.SupportShell.Status.OK && truck.statusAge() < 80;
        boolean starting = truck.powered() && !truck.engineRunning() && truck.isPacked();
        float progress = truck.deployProgress();
        int percent = Math.round(progress * 100);
        Component state = starting ? Component.translatable("hud.jeg.support.starting")
                : truck.isDeployed() ? Component.translatable("hud.jeg.support.deployed")
                : truck.isPacked() ? Component.translatable("hud.jeg.support.packed")
                : Component.translatable(truck.deployTarget() ? "hud.jeg.support.deploying" : "hud.jeg.support.packing", percent);
        int lamp = refusal ? HudKit.BAD : starting || truck.isMovingParts() ? HudKit.WARN : truck.isDeployed() ? HudKit.OK : HudKit.FAINT;
        HudPanel panel = new HudPanel(l.font()).minWidth(132);
        panel.header(lamp, state, HudKit.TEXT, Component.translatable("hud.jeg.support.title"), truck.isMovingParts() ? progress : Float.NaN);
        boolean powered = truck.generatorRunning() || truck.engineRunning();
        panel.value(HudKit.ICON_POWER, Component.translatable("hud.jeg.support.power"),
                Component.translatable(truck.generatorRunning() ? "hud.jeg.support.generator" : truck.engineRunning() ? "hud.jeg.support.engine" : "hud.jeg.support.off"),
                powered ? HudKit.OK : HudKit.MUTED);
        boolean full = truck.occupiedCargoSlots() >= truck.vehicleContainerSlots();
        panel.bar(HudKit.ICON_HOLD, Component.translatable("hud.jeg.support.hold"),
                truck.occupiedCargoSlots() / (float) Math.max(1, truck.vehicleContainerSlots()), full ? HudKit.BAD : 0xFFB4BDC6,
                Component.literal(truck.occupiedCargoSlots() + "/" + truck.vehicleContainerSlots()), full ? HudKit.WARN : HudKit.TEXT);
        var towed = truck.towedEntityId() >= 0 ? mc.level.getEntity(truck.towedEntityId()) : null;
        var towable = towed == null ? truck.towCandidate() : null;
        if (towed != null) {
            panel.value(HudKit.ICON_TOW, Component.translatable("hud.jeg.support.towing"), towed.getType().getDescription(), HudKit.WARN);
        }
        if (truck.holdTicks() > 0) {
            Component key = HudKit.keyName(KeyBindings.RELOAD);
            panel.bar(HudKit.ICON_CLOCK, Component.translatable("hud.jeg.support.hold_key", key),
                    truck.holdTicks() / (float) ttv.migami.jeg.vehicle.entity.SupportVehicleEntity.HOLD_TICKS, HudKit.ACCENT,
                    Component.empty(), HudKit.TEXT);
        }
        if (refusal) {
            BlockPosText at = new BlockPosText(truck.statusPos());
            Component refusalText = status == ttv.migami.jeg.vehicle.support.SupportShell.Status.PLAYERS_INSIDE
                    ? Component.translatable("hud.jeg.support.status.PLAYERS_INSIDE", truck.statusPos().getX())
                    : Component.translatable("hud.jeg.support.status." + status.name(), at.text());
            panel.note(HudKit.ICON_WARN, refusalText, HudKit.BAD);
        }
        // The tow hitch: always shown while something is in reach or in tow (a pulsing hint), not only after boarding.
        Component tow = KeyBindings.VEHICLE_TOW.isUnbound() ? null : HudKit.keyName(KeyBindings.VEHICLE_TOW);
        if (tow != null && towed != null) {
            panel.hints(1F, HudPanel.Hint.of(Component.translatable("hud.jeg.hint.release_tow"), tow));
        } else if (tow != null && towable != null) {
            float pulse = (now / 10) % 2 == 0 ? 1F : 0.65F;
            panel.hints(pulse, HudPanel.Hint.of(Component.translatable("hud.jeg.hint.hitch", towable.getType().getDescription()), tow));
        }
        Component key = HudKit.keyName(KeyBindings.RELOAD);
        if (truck.isPacked()) {
            panel.hints(hints, HudPanel.Hint.of(Component.translatable("hud.jeg.hint.deploy"), key),
                    HudPanel.Hint.of(Component.translatable("hud.jeg.hint.horn"), HudKit.keyName(mc.options.keyAttack)));
        } else {
            panel.hints(hints, HudPanel.Hint.of(Component.translatable("hud.jeg.hint.pack"), key));
        }
        return panel;
    }

    private record BlockPosText(net.minecraft.core.BlockPos pos) {
        String text() {
            return this.pos.getX() + " " + this.pos.getY() + " " + this.pos.getZ();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sights and markers, at the GUI's own scale.

    /** A ground vehicle's zoomed sight: the TV frame, compass strip and roll indicator. */
    private static void renderFocusedSight(GuiGraphicsExtractor guiGraphics, Minecraft minecraft) {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        int addW = (screenWidth / screenHeight) * 48;
        int addH = (screenWidth / screenHeight) * 27;
        preciseBlit(guiGraphics, LAND_FRAME, -addW / 2.0F, -addH / 2.0F, screenWidth + addW, screenHeight + addH, 0.0F, 0.0F, 1920.0F, 1080.0F, 1920.0F, 1080.0F);
        int compassOffset = Mth.floor(128.0F + 64.0F / 45.0F * minecraft.player.getYRot());
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, COMPASS, screenWidth / 2 - 128, 10, compassOffset, 0.0F, 256, 16, 1024, 32);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ROLL_INDICATOR, screenWidth / 2 - 8, 30, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /**
     * Where a slew-limited gun points now (the Mk42's): the gunner's view leads and the gun follows at its real rate,
     * so this marker, 250 blocks along the bore, shows when the gun has caught up with the crosshair.
     */
    private static void renderBoreMarker(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, VehicleEntity vehicle) {
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var turret = vehicle.vehicleData().defaults().turret();
        net.minecraft.world.phys.Vec3 pivot = vehicle.barrelPoint(new net.minecraft.world.phys.Vec3(turret.barrelX(),
                turret.originY() + turret.barrelY(), turret.originZ() + turret.barrelZ()), partialTick);
        net.minecraft.world.phys.Vec3 point = pivot.add(vehicle.barrelDirection(partialTick).scale(250.0D));
        net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 eye = camera.position();
        org.joml.Vector3f local = new org.joml.Vector3f((float) (point.x - eye.x), (float) (point.y - eye.y), (float) (point.z - eye.z));
        camera.rotation().conjugate(new org.joml.Quaternionf()).transform(local);
        if (local.z >= -0.5F) {
            return;
        }
        float x = local.x / -local.z;
        float y = local.y / -local.z;
        float width = guiGraphics.guiWidth();
        float height = guiGraphics.guiHeight();
        float tanHalf = (float) Math.tan(Math.toRadians(camera.getFov() * 0.5D));
        float screenX = width * 0.5F + x / (tanHalf * width / height) * width * 0.5F;
        float screenY = height * 0.5F - y / tanHalf * height * 0.5F;
        // Where the gun points while it slews after the view: its own mark (four corner brackets), never a second copy of
        // the reticle, and gone once the gun lies on the reticle, so the two never sit on top of each other.
        if (Math.hypot(screenX - width * 0.5F, screenY - height * 0.5F) < BORE_MARKER_HIDE_PX) {
            return;
        }
        // A ring of dots, labelled: the yellow corner brackets it was meant nothing to the user ("what is the yellow
        // box?", review 2026-11-18). Drawn in the HUD's own smooth dots and font.
        float scale = HudKit.scale(minecraft);
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(screenX, screenY);
        pose.scale(scale, scale);
        int colour = 0xE0FFD24A;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0D;
            HudKit.dot(guiGraphics, (int) Math.round(Math.cos(a) * 8.0D) - 1, (int) Math.round(Math.sin(a) * 8.0D) - 1, colour);
        }
        HudKit.centred(guiGraphics, minecraft.font, Component.translatable("hud.jeg.vehicle.bore_marker"), 0, 12, colour);
        pose.popMatrix();
    }

    /** The bore marker is hidden within this many GUI pixels of the screen centre (the gun is on the reticle). */
    private static final float BORE_MARKER_HIDE_PX = 7.0F;

    /**
     * The jet's gun marker: where its rounds go, 250 blocks down the gun's bore, which points a little below the nose
     * (with the jet's own speed on them), drawn where that point is on screen and turned with the jet's bank against the
     * view. The chase camera lags the jet a little, so in a turn the marker swings ahead of the screen's centre with the
     * nose.
     */
    private static void renderJetGunMarker(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, VehicleEntity jet) {
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 point = jet.jetGunAimPoint(partialTick, 250.0D);
        net.minecraft.world.phys.Vec3 eye = camera.position();
        org.joml.Vector3f local = new org.joml.Vector3f((float) (point.x - eye.x), (float) (point.y - eye.y), (float) (point.z - eye.z));
        camera.rotation().conjugate(new org.joml.Quaternionf()).transform(local);
        if (local.z >= -0.5F) {
            return;
        }
        // Tangent-space position on the view plane (x right, y up).
        float x = local.x / -local.z;
        float y = local.y / -local.z;
        boolean pod = jet.isJetPodView(minecraft.player);
        if (!camera.isDetached() && !pod) {
            // The cockpit view's roll goes on after the camera (VehicleGameRendererMixin); turn the point with it.
            float roll = jet.cameraRoll(partialTick) * Mth.DEG_TO_RAD;
            float rx = x * Mth.cos(roll) - y * Mth.sin(roll);
            y = x * Mth.sin(roll) + y * Mth.cos(roll);
            x = rx;
        }
        float width = guiGraphics.guiWidth();
        float height = guiGraphics.guiHeight();
        float tanHalf = (float) Math.tan(Math.toRadians(camera.getFov() * 0.5D));
        float screenX = width * 0.5F + x / (tanHalf * width / height) * width * 0.5F;
        float screenY = height * 0.5F - y / tanHalf * height * 0.5F;
        float viewRoll = camera.isDetached() && !pod ? (float) jet.aircraftCameraRotationFor(partialTick).z
                : pod ? 0.0F : jet.cameraRoll(partialTick);
        float bank = Mth.wrapDegrees((float) jet.poseAngles(partialTick).z - viewRoll);
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(screenX, screenY);
        pose.rotate(bank * Mth.DEG_TO_RAD);
        preciseBlit(guiGraphics, CROSSHAIR_THIRD_CAMERA, -12.0F, -12.0F, 24, 24, 0.0F, 0.0F, 64.0F, 64.0F, 64.0F, 64.0F);
        pose.popMatrix();
    }

    private static final int POD_TEXT = 0xFFE4EEE4;
    private static final int POD_LINE = 0xE6F2F7F2;
    private static final int POD_SHADOW = 0x8C000000;
    private static final double POD_RANGE = 640.0D;
    private static final double POD_TRACK_RADIUS = 7.0D;

    /**
     * Zoomed in a jet, the screen is the targeting pod's camera, laid out like a real aerial sensor feed: a sensor-grey
     * wash with scan lines and a dark vignette, the camera's frame corners, a gapped crosshair with brackets (where the
     * missiles go), and its readouts: recording, mode and zoom; heading; where the pod points from the nose; and the
     * slant range and position of what is under the crosshair, with TRACK when that is a vehicle or creature.
     */
    private static void renderPodCamera(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, VehicleEntity jet) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int cx = width / 2;
        int cy = height / 2;
        // The feed: a faint sensor tint, scan lines, and the edges falling off into the dark.
        guiGraphics.fill(0, 0, width, height, 0x22142218);
        for (int y = 0; y < height; y += 3) {
            guiGraphics.fill(0, y, width, y + 1, 0x12000000);
        }
        int bands = 10;
        int band = Math.max(2, Math.min(width, height) / 36);
        for (int i = 0; i < bands; i++) {
            int inset = i * band;
            int alpha = (bands - i) * 9;
            int colour = alpha << 24;
            guiGraphics.fill(inset, inset, width - inset, inset + band, colour);
            guiGraphics.fill(inset, height - inset - band, width - inset, height - inset, colour);
            guiGraphics.fill(inset, inset + band, inset + band, height - inset - band, colour);
            guiGraphics.fill(width - inset - band, inset + band, width - inset, height - inset - band, colour);
        }
        // The camera's frame corners.
        int frame = 14;
        int corner = 22;
        bracket(guiGraphics, frame, frame, corner, 1, 1);
        bracket(guiGraphics, width - frame - 1, frame, corner, -1, 1);
        bracket(guiGraphics, frame, height - frame - 1, corner, 1, -1);
        bracket(guiGraphics, width - frame - 1, height - frame - 1, corner, -1, -1);

        // The crosshair: four arms around an open centre, and brackets boxing the aim.
        int gap = 5;
        int arm = 13;
        crossArm(guiGraphics, cx - gap - arm, cy, cx - gap, cy + 1);
        crossArm(guiGraphics, cx + gap + 1, cy, cx + gap + arm + 1, cy + 1);
        crossArm(guiGraphics, cx, cy - gap - arm, cx + 1, cy - gap);
        crossArm(guiGraphics, cx, cy + gap + 1, cx + 1, cy + gap + arm + 1);
        int box = 30;
        int boxCorner = 8;
        bracket(guiGraphics, cx - box, cy - box, boxCorner, 1, 1);
        bracket(guiGraphics, cx + box, cy - box, boxCorner, -1, 1);
        bracket(guiGraphics, cx - box, cy + box, boxCorner, 1, -1);
        bracket(guiGraphics, cx + box, cy + box, boxCorner, -1, -1);

        // What the pod is looking at.
        net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        net.minecraft.world.phys.Vec3 eye = camera.position();
        net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(POD_RANGE));
        net.minecraft.world.phys.BlockHitResult hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player));
        boolean ground = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
        net.minecraft.world.phys.Vec3 point = ground ? hit.getLocation() : end;
        boolean tracking = false;
        if (ground) {
            for (Entity entity : minecraft.level.getEntities(jet, new net.minecraft.world.phys.AABB(point, point).inflate(POD_TRACK_RADIUS))) {
                if (entity.isAlive() && entity != minecraft.player && entity.getRootVehicle() != jet
                        && (entity instanceof net.minecraft.world.entity.LivingEntity || entity instanceof VehicleEntity)) {
                    tracking = true;
                    break;
                }
            }
        }

        var font = minecraft.font;
        int left = frame + 8;
        int top = frame + 6;
        // Top left: recording, sensor mode, zoom.
        boolean blink = (jet.tickCount / 10) % 2 == 0;
        if (blink) {
            guiGraphics.fill(left, top + 2, left + 4, top + 6, 0xFFFF3030);
        }
        podText(guiGraphics, font, "REC", left + 7, top);
        podText(guiGraphics, font, "EO/IR  WHT", left, top + 11);
        double zoom = Math.tan(Math.toRadians(minecraft.options.fov().get() * 0.5D)) / Math.tan(Math.toRadians(camera.getFov() * 0.5D));
        podText(guiGraphics, font, String.format(Locale.ROOT, "ZOOM %.1fx", Math.max(1.0D, zoom)), left, top + 22);
        // Top centre: heading, with a tick under it.
        int heading = Math.floorMod(Math.round(yaw + 180.0F), 360);
        String headingText = String.format(Locale.ROOT, "HDG %03d", heading);
        podText(guiGraphics, font, headingText, cx - font.width(headingText) / 2, top);
        guiGraphics.fill(cx, top + 11, cx + 1, top + 16, POD_LINE);
        // Left of centre: where the pod points from the jet's nose.
        float azimuth = Mth.wrapDegrees(yaw - jet.getYRot());
        podText(guiGraphics, font, String.format(Locale.ROOT, "AZ %+04d", Math.round(azimuth)), left, cy - 11);
        podText(guiGraphics, font, String.format(Locale.ROOT, "EL %+03d", Math.round(-pitch)), left, cy + 1);
        // Under the crosshair: range to, and position of, what it is on.
        String range = ground ? String.format(Locale.ROOT, "RNG %d m", Math.round(eye.distanceTo(point))) : "RNG ----";
        podText(guiGraphics, font, range, cx - font.width(range) / 2, cy + box + 6);
        if (ground) {
            String position = String.format(Locale.ROOT, "TGT %d / %d / %d", Math.round(point.x), Math.round(point.y), Math.round(point.z));
            podText(guiGraphics, font, position, cx - font.width(position) / 2, cy + box + 17);
        }
        if (tracking) {
            String track = "TRACK";
            int colour = blink ? 0xFFFFC040 : 0xFFFFE080;
            guiGraphics.text(font, Component.literal(track), cx - font.width(track) / 2, cy - box - 14, colour);
        }
    }

    private static void podText(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y) {
        guiGraphics.text(font, Component.literal(text), x, y, POD_TEXT);
    }

    /** One arm of the crosshair, from (x0, y0) to (x1, y1) exclusive, with a dark shadow to stand out on bright ground. */
    private static void crossArm(GuiGraphicsExtractor guiGraphics, int x0, int y0, int x1, int y1) {
        guiGraphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, POD_SHADOW);
        guiGraphics.fill(x0, y0, x1, y1, POD_LINE);
    }

    /** An L-shaped corner at (x, y), its arms running {@code length} pixels in the directions dx and dy. */
    private static void bracket(GuiGraphicsExtractor guiGraphics, int x, int y, int length, int dx, int dy) {
        int hx0 = dx > 0 ? x : x - length + 1;
        int vy0 = dy > 0 ? y : y - length + 1;
        crossArm(guiGraphics, hx0, y, hx0 + length, y + 1);
        crossArm(guiGraphics, x, vy0, x + 1, vy0 + length);
    }

    private static void renderReticle(GuiGraphicsExtractor guiGraphics, VehicleEntity vehicle) {
        if (vehicle instanceof BulldozerEntity) {
            return;
        }
        int size = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        int x = (guiGraphics.guiWidth() - size) / 2;
        int y = (guiGraphics.guiHeight() - size) / 2;
        Identifier texture = reticleTexture(vehicle);
        if (texture == CROSSHAIR_THIRD_CAMERA) {
            // 64x64 icon that fills its canvas, unlike the 512x512 full-screen reticles; draw it at crosshair size.
            float centerX = guiGraphics.guiWidth() / 2.0F;
            float centerY = guiGraphics.guiHeight() / 2.0F;
            preciseBlit(guiGraphics, texture, centerX - 12.0F, centerY - 12.0F, 24, 24, 0.0F, 0.0F, 64.0F, 64.0F, 64.0F, 64.0F);
            return;
        }
        preciseBlit(guiGraphics, texture, x, y, size, size, 0.0F, 0.0F, 512.0F, 512.0F, 512.0F, 512.0F);
    }

    private static Identifier reticleTexture(VehicleEntity vehicle) {
        String vehiclePath = vehicle.vehicleDataId().getPath();
        Entity passenger = Minecraft.getInstance().player;
        Identifier selectedWeaponId = passenger == null ? vehicle.selectedVehicleWeaponId() : vehicle.selectedVehicleWeaponId(passenger);
        String weaponPath = selectedWeaponId == null ? "" : selectedWeaponId.getPath();
        boolean zooming = VehicleClientState.isRidingVehicle()
                && VehicleClientState.vehicleId() == vehicle.getId()
                && VehicleClientState.zoomDown();
        if (!zooming && Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) {
            return CROSSHAIR_THIRD_CAMERA;
        }
        if (zooming && "hpj11".equals(vehiclePath)) {
            return CROSSHAIR_CN_HPJ_ZOOMING;
        }
        if (passenger != null && vehicle.isSelectedVehicleWeaponLockOn(passenger)) {
            return CROSSHAIR_SEEK_MISSILE;
        }
        if (passenger != null && vehicle.isSelectedVehicleWeaponGuided(passenger)) {
            return CROSSHAIR_MISSILE;
        }
        if ("hypersonic_cannon".equals(weaponPath) || "grenade_launcher".equals(weaponPath)) {
            return zooming ? CROSSHAIR_CANNON_ZOOMING : CROSSHAIR_CANNON;
        }
        if ("laser_tower".equals(vehiclePath) || "waveforce_tower".equals(vehiclePath)) {
            return CROSSHAIR_LASER_CANNON;
        }
        if (vehicle.hasFocusedDriverSightHud()) {
            return CROSSHAIR_US_APC;
        }
        return CROSSHAIR_GUN;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Placement and shared readings.

    /**
     * The vehicle card leaves the lower middle of the screen where that is the work: the FSV-6's dashboard in its cab,
     * the dozer's blade from the seat (in F5, the machine itself), the mortar's baseplate. These have their own panel at
     * the top left and no weapon list in the bottom right corner, so they use that corner in every view.
     */
    private static boolean vehicleInfoInCorner(Minecraft minecraft, VehicleEntity vehicle) {
        return vehicle instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity
                || vehicle instanceof BulldozerEntity
                || vehicle instanceof ttv.migami.jeg.vehicle.entity.MortarEntity;
    }

    /**
     * The vehicle card goes to the top middle (open sky) where the lower middle is the vehicle: in a chase view (F5) it
     * covered the vehicle itself, and from an aircraft's seat it covered the cockpit's live instruments. The bottom
     * corners already hold the crew list and the weapon list.
     */
    private static boolean vehicleInfoAtTop(Minecraft minecraft, VehicleEntity vehicle) {
        if (vehicleInfoInCorner(minecraft, vehicle)) return false;
        boolean aircraft = vehicle.isJet() || vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER;
        // The Mk42's gunner looks along the barrel, which the lower middle covered; its panel is kept narrow so the top
        // middle stays clear of it.
        return !minecraft.options.getCameraType().isFirstPerson() || aircraft
                || vehicle instanceof ttv.migami.jeg.vehicle.entity.ShellGunEntity
                || vehicle instanceof ttv.migami.jeg.vehicle.entity.TowEntity;
    }

    /**
     * From a helicopter's seat in first person (not through a zoomed sight), the vehicle info is a helmet display
     * ({@link HelicopterHmd}) around the reticle rather than the card, which sat over the windscreen.
     */
    private static boolean helicopterHmd(Minecraft minecraft, VehicleEntity vehicle, boolean focusedSight) {
        return vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && minecraft.options.getCameraType().isFirstPerson() && !focusedSight;
    }

    /** The selected weapon and its ammo, as the card words it, for the helmet display. */
    static Component selectedWeaponLine(VehicleEntity vehicle, Entity passenger) {
        Identifier selectedWeaponId = vehicle.selectedVehicleWeaponId(passenger);
        if (selectedWeaponId == null) {
            return null;
        }
        return vehicleUsesLoadedAmmo(vehicle, passenger)
                ? vehicleAmmoComponent(vehicle, passenger)
                : Component.translatable("hud.jeg.vehicle.weapon", Component.translatable("item." + selectedWeaponId.getNamespace() + "." + selectedWeaponId.getPath()),
                String.valueOf(vehicle.selectedVehicleWeaponAmmo(passenger)));
    }

    static Component decoyLine(VehicleEntity vehicle) {
        if (!showDecoyStatus(vehicle)) {
            return null;
        }
        return builtInDecoyUsesSmoke(vehicle)
                ? Component.translatable("hud.jeg.vehicle.decoy_smoke", HudKit.keyName(KeyBindings.VEHICLE_DEPLOY_DECOY), decoyStatus(vehicle))
                : Component.translatable("hud.jeg.vehicle.decoy_flare", HudKit.keyName(KeyBindings.VEHICLE_DEPLOY_DECOY), decoyStatus(vehicle));
    }

    static double speedMetresPerSecond(VehicleEntity vehicle) {
        return vehicleSpeedBlocksPerSecond(vehicle);
    }

    private static boolean hasWeaponSelectorHud(VehicleEntity vehicle) {
        return vehicle.hasVehicleWeapons() && (vehicle.hasFocusedDriverSightHud() || vehicle.vehicleData().defaults().weapons().size() > 1
                || vehicle.vehicleData().defaults().vehicleType() == VehicleType.BOAT);
    }

    private static boolean isFocusedVehicleSight(VehicleEntity vehicle) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && player.getVehicle() == vehicle
                && vehicle.hasFocusedSightHud(player)
                && VehicleClientState.isRidingVehicle()
                && VehicleClientState.vehicleId() == vehicle.getId()
                && VehicleClientState.zoomDown();
    }

    private static void preciseBlit(GuiGraphicsExtractor guiGraphics, Identifier texture, float x, float y, float width, float height, float uOffset, float vOffset, float uWidth, float vHeight, float textureWidth, float textureHeight) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), uOffset, vOffset, Math.round(width), Math.round(height), Math.round(textureWidth), Math.round(textureHeight), Math.round(textureWidth), Math.round(textureHeight));
    }
}
