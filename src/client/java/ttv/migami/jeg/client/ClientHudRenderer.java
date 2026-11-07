package ttv.migami.jeg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.Config;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.item.MagazineItem;

public final class ClientHudRenderer {
    private static final int AMMO_MAX_CURRENT = 999;
    private static final int AMMO_MAX_RESERVE = 9999;

    private ClientHudRenderer() {}

    public static void render(GuiGraphicsExtractor guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !Config.showAmmoHud()) {
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        HudData data = null;
        if (main.getItem() instanceof GunItem gun) {
            data = buildGunHud(player, main, gun);
        } else if (main.getItem() instanceof MagazineItem magazine) {
            data = new HudData(main.getHoverName().getString(), magazine.getAmmoCount(main), formatReserve(magazine.getCapacity()), rarityColor(main));
        } else if (offhand.getItem() instanceof MagazineItem magazine) {
            data = new HudData(offhand.getHoverName().getString(), magazine.getAmmoCount(offhand), formatReserve(magazine.getCapacity()), rarityColor(offhand));
        }

        if (data == null) {
            return;
        }

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        String current = formatCurrent(data.currentAmmo());
        int currentWidth = minecraft.font.width(current) * 2;
        int reserveOffset = currentWidth + 6;
        int ammoWidth = reserveOffset + minecraft.font.width(data.reserveText());
        int textWidth = Math.min(Math.max(ammoWidth, minecraft.font.width(data.name())), Math.max(0, width - 16));
        int startX = Math.max(8, width - textWidth - 8);
        int startY = Math.max(20, height - 65);

        guiGraphics.text(minecraft.font, fit(minecraft, data.name(), textWidth), startX, startY - 10, data.nameColor(), true);

        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(startX, startY);
        pose.scale(2.0F, 2.0F);
        guiGraphics.text(minecraft.font, current, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();

        guiGraphics.text(minecraft.font, data.reserveText(), startX + reserveOffset, startY, 0xFF878787, true);
    }

    private static HudData buildGunHud(LocalPlayer player, ItemStack stack, GunItem gun) {
        int current = gun.usesLoadedAmmo() ? gun.getMagazineAmmo(stack) : gun.countInventoryAmmo(player);
        String reserveText;
        if (gun.usesMagazineSwapReload()) {
            GunItem.MagazineInventorySummary summary = gun.getMagazineInventorySummary(player);
            reserveText = player.getAbilities().instabuild ? "INF MAG" : formatMagazineReserve(summary.loadedMagazineCount());
        } else {
            int reserve = gun.countInventoryAmmo(player);
            if (player.getAbilities().instabuild || reserve == Integer.MAX_VALUE) {
                reserve = AMMO_MAX_RESERVE;
            }
            reserveText = formatReserve(reserve);
        }
        return new HudData(stack.getHoverName().getString(), current, reserveText, rarityColor(stack));
    }

    private static int rarityColor(ItemStack stack) {
        Integer color = stack.getRarity().color().getColor();
        return color != null ? 0xFF000000 | color : 0xFFFFFFFF;
    }

    private static String formatCurrent(int ammo) {
        return String.format("%03d", Math.max(0, Math.min(AMMO_MAX_CURRENT, ammo)));
    }

    private static String formatReserve(int ammo) {
        return String.format("%04d", Math.max(0, Math.min(AMMO_MAX_RESERVE, ammo)));
    }

    private static String formatMagazineReserve(int magazines) {
        return String.format("%02d MAG", Math.max(0, Math.min(99, magazines)));
    }

    private static String fit(Minecraft minecraft, String text, int width) {
        if (minecraft.font.width(text) <= width) {
            return text;
        }
        String clipped = minecraft.font.plainSubstrByWidth(text, Math.max(0, width - minecraft.font.width("...")));
        return clipped + "...";
    }

    private record HudData(String name, int currentAmmo, String reserveText, int nameColor) {}
}
