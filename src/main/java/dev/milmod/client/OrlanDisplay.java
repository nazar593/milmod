package dev.milmod.client;

import dev.milmod.entity.VehicleEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class OrlanDisplay {
    private static final int SIGHT = 0xe798e4bb;
    private static final int COMPASS = 0xff9cc8ec;
    private static final int FRAME = 0xff09100f;

    private OrlanDisplay() {}

    static void draw(GuiGraphics gui, Font font, int width, int height, VehicleEntity craft,
                     float zoom, float heading, float pitch) {
        int cx = width / 2, cy = height / 2;
        int edge = Math.max(8, width / 20);
        int band = Math.max(6, height / 18);
        frame(gui, width, height, edge, band);

        int reticle = Mth.clamp(height / 11, 11, 27);
        int arm = Math.max(4, reticle / 3);
        for (int side = -1; side <= 1; side += 2) {
            for (int vertical = -1; vertical <= 1; vertical += 2) {
                int x = cx + side * reticle, y = cy + vertical * reticle;
                gui.hLine(Math.min(x, x - side * arm), Math.max(x, x - side * arm), y, SIGHT);
                gui.vLine(x, Math.min(y, y - vertical * arm), Math.max(y, y - vertical * arm), SIGHT);
            }
        }
        gui.fill(cx, cy, cx + 1, cy + 1, SIGHT);

        String[] directions = {"N", "E", "S", "W"};
        int compassRadius = reticle + 17;
        for (int i = 0; i < directions.length; i++) {
            double angle = Math.toRadians(Mth.wrapDegrees(180F + i * 90F - heading));
            int x = cx + (int) (Math.sin(angle) * compassRadius);
            int y = cy - (int) (Math.cos(angle) * compassRadius);
            gui.drawCenteredString(font, directions[i], x, y - 4, COMPASS);
        }

        int scaleX = edge + 13, halfScale = Math.max(13, height / 5);
        gui.vLine(scaleX, cy - halfScale, cy + halfScale, SIGHT);
        for (int i = -4; i <= 4; i++) {
            int y = cy + i * halfScale / 4;
            gui.hLine(scaleX - (i % 2 == 0 ? 4 : 2), scaleX, y, SIGHT);
        }
        int marker = cy + (int) (Mth.clamp(pitch / 45F, -1, 1) * halfScale);
        gui.hLine(scaleX + 2, scaleX + 9, marker, SIGHT);
        gui.fill(scaleX + 6, marker - 1, scaleX + 9, marker + 2, SIGHT);

        gui.drawString(font, Component.translatable("hud.milmod.orlan_optic"), edge + 12, band + 8, SIGHT);
        String magnification = MavicDisplay.zoomLabel(zoom);
        gui.drawString(font, magnification, width - edge - 12 - font.width(magnification), band + 8, SIGHT);
        gui.drawString(font, Component.translatable("screen.milmod.throttle", craft.throttlePercent()),
                edge + 12, height - band - 16, SIGHT);
    }

    private static void frame(GuiGraphics gui, int width, int height, int edge, int band) {
        for (int y = 0; y < height; y += 2) {
            float t = 2F * y / height - 1F;
            int x = edge + (int) (edge * .8F * t * t);
            int next = Math.min(height, y + 2);
            gui.fill(0, y, x, next, FRAME);
            gui.fill(width - x, y, width, next, FRAME);
            gui.fillGradient(x, y, x + 3, next, 0x4b17201c, 0x4b17201c);
            gui.fillGradient(width - x - 3, y, width - x, next, 0x4b17201c, 0x4b17201c);
        }
        for (int x = edge; x < width - edge; x += 3) {
            float t = 2F * x / width - 1F;
            int y = band + (int) (band * .9F * t * t);
            gui.fill(x, 0, x + 3, y, FRAME);
            gui.fill(x, height - y, x + 3, height, FRAME);
        }
    }
}
