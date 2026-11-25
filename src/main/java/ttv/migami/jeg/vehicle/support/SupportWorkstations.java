package ttv.migami.jeg.vehicle.support;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;

/**
 * The vanilla workstations fitted in a support vehicle's bays (and placed in a walk-in cabin, vehicle/cabin/CabinBlocks):
 * each opens its own vanilla screen, bound to the vehicle (usable aboard or within reach) instead of to a block. Notes:
 * <ul>
 *     <li>An anvil fitted here does not chip with use (there is no anvil block for the vanilla code to damage).</li>
 *     <li>An enchanting table has no bookshelves round it, so it offers the bookshelf-less levels (up to 8).</li>
 * </ul>
 */
public final class SupportWorkstations {
    private SupportWorkstations() {}

    /** Open a menu-based workstation. Returns the menu opened, or null if this kind has no menu. */
    @Nullable
    static AbstractContainerMenu open(SupportModules modules, SupportModules.Kind kind, ContainerLevelAccess access, ServerPlayer player) {
        return open(kind, access, player, modules::canUse);
    }

    /**
     * Open a menu-based workstation whose screen stays open while {@code valid} holds for its user. Returns the menu
     * opened, or null if this kind has no menu.
     */
    @Nullable
    public static AbstractContainerMenu open(SupportModules.Kind kind, ContainerLevelAccess access, ServerPlayer player, Predicate<Player> valid) {
        var title = switch (kind) {
            case ANVIL -> net.minecraft.network.chat.Component.translatable("container.repair");
            case STONECUTTER -> net.minecraft.network.chat.Component.translatable("container.stonecutter");
            case SMITHING -> net.minecraft.network.chat.Component.translatable("container.upgrade");
            case GRINDSTONE -> net.minecraft.network.chat.Component.translatable("container.grindstone_title");
            case LOOM -> net.minecraft.network.chat.Component.translatable("container.loom");
            case CARTOGRAPHY -> net.minecraft.network.chat.Component.translatable("container.cartography_table");
            case ENCHANTING -> net.minecraft.network.chat.Component.translatable("container.enchant");
            default -> null;
        };
        if (title == null) return null;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> switch (kind) {
            case ANVIL -> new AnvilMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
            case STONECUTTER -> new StonecutterMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
            case SMITHING -> new SmithingMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
            case GRINDSTONE -> new GrindstoneMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
            case LOOM -> new LoomMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
            case CARTOGRAPHY -> new CartographyTableMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
            default -> new EnchantmentMenu(id, inventory, access) {
                @Override public boolean stillValid(Player who) { return valid.test(who); }
            };
        }, title));
        return player.containerMenu;
    }

    /** Modules used without a menu of their own (the vehicle handles them); false if nothing happened. */
    static boolean use(SupportVehicleEntity vehicle, SupportModules modules, int slot, SupportModules.Kind kind, ServerPlayer player) {
        return vehicle.useModule(player, slot, kind);
    }
}
