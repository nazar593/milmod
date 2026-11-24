package dev.milmod.client;

import dev.milmod.entity.VehicleEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class GroundCabDisplay {
    private GroundCabDisplay() {}

    static void draw(GuiGraphics gui, Font font, int width, int height, VehicleEntity vehicle) {
        int consoleHeight = Math.max(32, height / 7);
        int side = Math.max(4, width / 62);
        gui.fill(0, height - consoleHeight, width, height, 0xf021292a);
        gui.fill(0, height - consoleHeight, width, height - consoleHeight + 2, 0xff6a7772);
        gui.fill(0, 0, side, height - consoleHeight, 0xad252d2c);
        gui.fill(width - side, 0, width, height - consoleHeight, 0xad252d2c);
        int speed = (int) Math.round(20 * Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo));
        gui.drawCenteredString(font, Component.translatable("hud.milmod.speed", speed),
                width / 2, height - consoleHeight + 8, 0xffc8edd6);
    }
}
