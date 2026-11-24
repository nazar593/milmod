package dev.milmod.client;

import dev.milmod.entity.VehicleEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Instrument display drawn only while the player is in the onboard view. */
final class FlightDisplay {
    private static final int BACK = 0xC2172328;
    private static final int LINE = 0xCA90C7BB;
    private static final int TEXT = 0xFFF0F7EF;
    private static final int WARNING = 0xFFFFBD73;
    private static final String[] CARDINAL = {"S", "W", "N", "E"};

    private FlightDisplay() {}

    static void draw(GuiGraphics gui, Font font, int w, int h, VehicleEntity craft, float zoom) {
        int x = w / 2, y = h / 2;
        int tape = Math.min(127, (w - 18) / 3);
        int top = 13;
        gui.fill(x - tape, top, x + tape, top + 28, BACK);
        gui.fill(x - tape, top + 27, x + tape, top + 28, LINE);
        gui.drawCenteredString(font, craft.getName(), x, top + 4, TEXT);

        float heading = craft.getYRot();
        int base = Mth.floor(heading / 10);
        for (int step = -5; step <= 5; step++) {
            int bearing = (base + step) * 10;
            int mark = x + Math.round((bearing - heading) * tape / 68);
            if (mark < x - tape + 7 || mark > x + tape - 7) continue;
            boolean major = Math.floorMod(bearing, 30) == 0;
            gui.vLine(mark, top + (major ? 21 : 24), top + 26, LINE);
            if (major && Math.abs(mark - x) > 12) {
                String value = bearing % 90 == 0 ? CARDINAL[Math.floorMod(bearing / 90, 4)]
                        : Integer.toString(Math.floorMod(bearing + 180, 360));
                gui.drawCenteredString(font, value, mark, top + 13, LINE);
            }
        }
        gui.vLine(x, top + 20, top + 29, TEXT);

        double dx = craft.getX() - craft.xo, dz = craft.getZ() - craft.zo;
        int speed = (int) Math.round(Math.hypot(dx, dz) * 20);
        int inset = Math.max(9, Math.min(28, w / 14));
        badge(gui, font, Component.translatable("hud.milmod.speed", speed), inset, y - 9);
        Component height = Component.translatable("hud.milmod.height", Mth.floor(craft.getY()));
        badge(gui, font, height, w - inset - font.width(height) - 16, y - 9);
        gui.hLine(x - 22, x - 6, y, LINE);
        gui.hLine(x + 6, x + 22, y, LINE);
        gui.vLine(x, y - 2, y + 2, LINE);

        Component status = craft.damageMask() != 0 ? Component.translatable("hud.milmod.damage")
                : Component.translatable("screen.milmod.throttle", craft.throttlePercent());
        int bottom = h - 27;
        gui.fill(x - tape, bottom - 5, x + tape, bottom + 14, BACK);
        gui.fill(x - tape, bottom - 5, x + tape, bottom - 4, LINE);
        gui.drawCenteredString(font, status, x, bottom, craft.damageMask() != 0 ? WARNING : TEXT);
        String scale = zoom < 1.2F ? "1×" : zoom < 2F ? "1.6×" : "2.6×";
        gui.drawString(font, scale, x + tape - font.width(scale) - 6, bottom, LINE);
        gui.drawCenteredString(font, Component.translatable("hud.milmod.pilot_keys"), x, h - 10, LINE);
    }

    private static void badge(GuiGraphics gui, Font font, Component label, int x, int y) {
        gui.fill(x, y, x + font.width(label) + 16, y + 18, BACK);
        gui.fill(x, y, x + 2, y + 18, LINE);
        gui.drawString(font, label, x + 8, y + 5, TEXT);
    }
}
