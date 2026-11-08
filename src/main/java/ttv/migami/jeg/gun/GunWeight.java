package ttv.migami.jeg.gun;

import net.minecraft.resources.Identifier;

/**
 * How heavy a gun is to carry. {@code speed} is added to the player's walking speed while it is held (0.10 = 10% faster);
 * {@code handling} scales how it moves in the hands (1 = a rifle): heavier guns come up more slowly, swing further with
 * each stride and lag further behind the view.
 */
public enum GunWeight {
    PISTOL(0.08D, 0.55F),
    SMG(0.10D, 0.70F),
    BOW(0.00D, 0.80F),
    RIFLE(0.00D, 1.00F),
    SHOTGUN(-0.03D, 1.10F),
    MARKSMAN(-0.05D, 1.15F),
    MACHINE_GUN(-0.12D, 1.60F),
    LAUNCHER(-0.15D, 1.75F),
    MINIGUN(-0.28D, 2.40F);

    public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("jeg", "gun_weight");
    /** Extra drag on top of {@link #MINIGUN} while the barrels are turning. */
    public static final double SPINNING_MINIGUN_SPEED = -0.15D;
    /** Extra drag of a second minigun in the off hand. */
    public static final double SECOND_MINIGUN_SPEED = -0.12D;

    private final double speed;
    private final float handling;

    GunWeight(double speed, float handling) {
        this.speed = speed;
        this.handling = handling;
    }

    public double speed() {
        return this.speed;
    }

    public float handling() {
        return this.handling;
    }

    public static GunWeight of(Identifier gunId) {
        return switch (gunId.getPath()) {
            case "combat_pistol", "semi_auto_pistol", "revolver", "python", "flare_gun", "finger_gun" -> PISTOL;
            case "custom_smg", "phantom_smg" -> SMG;
            case "compound_bow", "primitive_bow" -> BOW;
            case "double_barrel_shotgun", "holy_shotgun", "pump_shotgun", "repeating_shotgun", "supersonic_shotgun", "waterpipe_shotgun" -> SHOTGUN;
            case "bolt_action_rifle", "semi_auto_rifle" -> MARKSMAN;
            case "light_machine_gun", "hollenfire_mk2", "soulhunter_mk2", "flamethrower" -> MACHINE_GUN;
            case "grenade_launcher", "rocket_launcher", "typhoonee", "hypersonic_cannon" -> LAUNCHER;
            case "minigun" -> MINIGUN;
            default -> RIFLE;
        };
    }
}
