package dev.milmod.client;

import dev.milmod.entity.VehicleEntity;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

final class MavicDisplay {
    private static final int LIGHT = 0xfff7f5e9;
    private static final int SECONDARY = 0xffd3dbd7;
    private static final int AMBER = 0xfff3ca82;
    private static final int PANEL = 0xb31a201f;
    private static final int RETICLE = 0xb5ffe2ae;

    private MavicDisplay() {}

    static void draw(GuiGraphics gui, Font font, int w, int h, VehicleEntity craft, float zoom, Player operator,
                     boolean monochrome, Component modeKey, Component zoomKey, Component screenshotKey) {
        int pad = Math.max(7, Math.min(w, h) / 22);
        var phase = Component.translatable("flight.milmod." + craft.flightPhase().name().toLowerCase(Locale.ROOT));
        if (w >= 235) tag(gui, font, pad, pad, Component.literal("MAVIC"), AMBER);
        int labelX = w >= 235 ? pad + font.width("MAVIC") + 18 : pad;
        tag(gui, font, labelX, pad, phase, LIGHT);

        int right = w - pad;
        int power = craft.throttlePercent();
        var throttle = Component.translatable("hud.milmod.mavic_output", power);
        if (right - font.width(throttle) - 12 > labelX + font.width(phase) + 16) {
            int start = rightTag(gui, font, right, pad, throttle, AMBER);
            int meter = (right - start) * power / 100;
            if (meter > 0) gui.fill(start, pad + 15, start + meter, pad + 16, AMBER);
        }

        int cx = w / 2, cy = h / 2;
        gui.hLine(cx - 12, cx - 4, cy, RETICLE);
        gui.hLine(cx + 4, cx + 12, cy, RETICLE);
        gui.vLine(cx, cy - 10, cy - 4, RETICLE);
        gui.vLine(cx, cy + 4, cy + 10, RETICLE);
        gui.fill(cx, cy, cx + 1, cy + 1, RETICLE);

        int controlsY = cy - 29;
        var view = Component.translatable(monochrome ? "hud.milmod.mavic_mono" : "hud.milmod.mavic_color");
        rightTag(gui, font, right, controlsY, Component.translatable("hud.milmod.mavic_control", view, modeKey), AMBER);
        rightTag(gui, font, right, controlsY + 20,
                Component.translatable("hud.milmod.mavic_control", zoomLabel(zoom), zoomKey), LIGHT);
        rightTag(gui, font, right, controlsY + 40,
                Component.translatable("hud.milmod.mavic_control",
                        Component.translatable("hud.milmod.mavic_capture"), screenshotKey), LIGHT);

        double altitude = operator == null ? craft.getY() : craft.getY() - operator.getY();
        double distance = operator == null ? 0 : Math.hypot(craft.getX() - operator.getX(), craft.getZ() - operator.getZ());
        double speed = 20 * Math.hypot(craft.getX() - craft.xo, craft.getZ() - craft.zo);
        double climb = 20 * (craft.getY() - craft.yo);
        int bottom = h - pad;
        var height = Component.translatable("hud.milmod.mavic_height", Mth.floor(altitude));
        var climbSpeed = Component.translatable("hud.milmod.mavic_velocity", decimal(climb, true));
        int afterAltitude = telemetry(gui, font, pad, bottom, height, climbSpeed);
        if (operator != null) {
            var range = Component.translatable("hud.milmod.mavic_distance", Mth.floor(distance));
            int afterRange = telemetry(gui, font, afterAltitude + 3, bottom, range,
                    Component.translatable("hud.milmod.mavic_velocity", decimal(speed, false)));
            int margin = Mth.clamp(Mth.floor(100 * (1 - craft.distanceTo(operator) / craft.radioRange())), 0, 100);
            var link = Component.translatable("hud.milmod.mavic_link_margin", margin);
            if (right - font.width(link) - 12 > afterRange + 4) rightTag(gui, font, right, bottom - 15, link, SECONDARY);
        }
    }

    private static String decimal(double value, boolean signed) {
        if (Math.abs(value) < .05) value = 0;
        return String.format(Locale.ROOT, signed ? "%+.1f" : "%.1f", value);
    }

    private static int telemetry(GuiGraphics gui, Font font, int x, int bottom, Component value, Component speed) {
        int right = x + Math.max(font.width(value), font.width(speed)) + 12;
        gui.fill(x, bottom - 29, right, bottom, PANEL);
        gui.fill(x, bottom - 29, x + 1, bottom, AMBER);
        gui.drawString(font, speed, x + 6, bottom - 24, SECONDARY);
        gui.drawString(font, value, x + 6, bottom - 12, LIGHT);
        return right;
    }

    private static void tag(GuiGraphics gui, Font font, int x, int y, Component label, int color) {
        gui.fill(x, y, x + font.width(label) + 12, y + 15, PANEL);
        gui.drawString(font, label, x + 6, y + 4, color);
    }

    private static int rightTag(GuiGraphics gui, Font font, int right, int y, Component label, int color) {
        int x = right - font.width(label) - 12;
        tag(gui, font, x, y, label, color);
        return x;
    }

    static String zoomLabel(float zoom) {
        return zoom < 1.2F ? "1×" : zoom < 2F ? "1.6×" : "2.6×";
    }
}
