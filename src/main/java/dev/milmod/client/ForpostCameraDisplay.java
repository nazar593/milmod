package dev.milmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.milmod.MilMod;
import dev.milmod.entity.VehicleEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Client-only instrument overlay for the Forpost onboard camera. */
final class ForpostCameraDisplay {
    static final int CONNECTION_TICKS = 8;

    private static final ResourceLocation FRAME = ResourceLocation.tryBuild(MilMod.ID,
            "textures/gui/forpost_camera.png");
    private static final int FRAME_SIZE = 512;
    private static final int MARK = 0xDDE3EADB;
    private static final int TEXT = 0xFFF0F4EA;
    private static final int MUTED = 0xFFB5D0CF;
    private static final int WARNING = 0xFFFFBA87;
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private ForpostCameraDisplay() {}

    static void draw(GuiGraphics gui, Font font, int width, int height, VehicleEntity craft,
                     float zoom, float partial, int connecting, boolean instruments) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gui.blit(FRAME, 0, 0, width, height, 0.0F, 0.0F,
                FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
        RenderSystem.disableBlend();
        float fade = Mth.clamp((connecting - partial) / CONNECTION_TICKS, 0.0F, 1.0F);
        if (fade > 0.0F) {
            gui.fill(0, 0, width, height, Math.round(225 * fade * fade) << 24);
        }
        if (!instruments || width < 118 || height < 82) return;

        int x = width / 2;
        int y = height / 2;
        compass(gui, font, width, x, Math.max(11, height / 7),
                Mth.rotLerp(partial, craft.yRotO, craft.getYRot()) + 180.0F);
        brackets(gui, x, y, Math.max(11, Math.min(height, width) / 13));
        if (connecting > CONNECTION_TICKS / 2) {
            gui.drawCenteredString(font, Component.translatable("hud.milmod.camera_connecting"),
                    x, y + Math.min(height / 4, 32), TEXT);
        }

        int speed = (int) Math.round(Math.hypot(craft.getX() - craft.xo, craft.getZ() - craft.zo) * 20);
        int telemetryY = height - Math.max(21, height / 9);
        int blockWidth = Math.min(width * 43 / 100, 146);
        int left = x - blockWidth;
        int right = x + blockWidth;
        rightAligned(gui, font, Component.translatable("hud.milmod.speed", speed), x - 11, telemetryY, TEXT);
        rightAligned(gui, font, Component.translatable("hud.milmod.height", Mth.floor(craft.getY())),
                x - 11, telemetryY + 10, MUTED);
        String scale = zoom < 1.2F ? "1×" : zoom < 2F ? "1.6×" : "2.6×";
        gui.drawString(font, scale, x + 11, telemetryY, TEXT, true);
        if (width >= 224) {
            Component status = craft.damageMask() != 0 ? Component.translatable("hud.milmod.damage")
                    : Component.translatable("screen.milmod.throttle", craft.throttlePercent());
            gui.drawString(font, font.plainSubstrByWidth(status.getString(), right - x - 11),
                    x + 11, telemetryY + 10, craft.damageMask() != 0 ? WARNING : MUTED, true);
        }
        gui.vLine(x, telemetryY + 1, telemetryY + 16, MARK);
        gui.fill(left, telemetryY - 4, right, telemetryY - 3, 0x39BDCBC3);
    }

    private static void compass(GuiGraphics gui, Font font, int width, int x, int y, float heading) {
        int range = Math.min(width / 3, 124);
        int first = Mth.floor(heading / 15.0F);
        for (int i = first - 4; i <= first + 4; i++) {
            int bearing = i * 15;
            int mark = x + Math.round((bearing - heading) * range / 64.0F);
            if (Math.abs(mark - x) > range) continue;
            boolean major = bearing % 45 == 0;
            gui.vLine(mark, y + (major ? 8 : 10), y + 12, MARK);
            if (major) {
                gui.drawCenteredString(font, DIRECTIONS[Math.floorMod(bearing / 45, 8)], mark, y - 3, TEXT);
            }
        }
        gui.fill(x - 1, y + 14, x + 2, y + 17, MARK);
    }

    private static void brackets(GuiGraphics gui, int x, int y, int radius) {
        int length = Math.max(4, radius / 3);
        for (int side = -1; side <= 1; side += 2) {
            int edge = x + side * radius;
            int inside = edge - side * length;
            gui.hLine(Math.min(edge, inside), Math.max(edge, inside), y - radius, MARK);
            gui.hLine(Math.min(edge, inside), Math.max(edge, inside), y + radius, MARK);
            gui.vLine(edge, y - radius, y - radius + length, MARK);
            gui.vLine(edge, y + radius - length, y + radius, MARK);
        }
        gui.fill(x, y, x + 1, y + 1, MARK);
    }

    private static void rightAligned(GuiGraphics gui, Font font, Component text, int right, int y, int color) {
        gui.drawString(font, text, right - font.width(text), y, color, true);
    }
}
