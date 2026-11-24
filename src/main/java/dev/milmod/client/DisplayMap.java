package dev.milmod.client;

import dev.milmod.entity.DisplaySequence;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.network.DroneLink;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

final class DisplayMap extends Screen {
    private static final int TILES = 32;
    private static final int STEP = 3;
    private final VehicleEntity vehicle;
    private final BlockPos origin;
    private final int[][] terrain = new int[TILES][TILES];
    private final boolean[][] selectable = new boolean[TILES][TILES];
    private int left, top, size;
    private BlockPos selected;
    private Button show;

    DisplayMap(VehicleEntity vehicle) {
        super(Component.translatable("screen.milmod.display_map"));
        this.vehicle = vehicle;
        origin = vehicle.blockPosition();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        size = Math.min(224, Math.min(width - 12, height - 61));
        left = (width - size) / 2;
        top = (height - size - 59) / 2 + 20;
        show = addRenderableWidget(Button.builder(Component.translatable("screen.milmod.display_start"), button -> {
            if (selected != null) {
                DroneLink.displayAt(selected);
                onClose();
            }
        }).bounds(left, top + size + 18, size, 18).build());
        var level = vehicle.level();
        var sample = new BlockPos.MutableBlockPos();
        for (int z = 0; z < TILES; z++) {
            for (int x = 0; x < TILES; x++) {
                int wx = origin.getX() + (x - TILES / 2) * STEP;
                int wz = origin.getZ() + (z - TILES / 2) * STEP;
                sample.set(wx, origin.getY(), wz);
                int dx = wx - origin.getX(), dz = wz - origin.getZ();
                selectable[z][x] = dx * dx + dz * dz < DisplaySequence.RANGE * DisplaySequence.RANGE
                        && level.getWorldBorder().isWithinBounds(sample) && level.hasChunkAt(sample);
                int color = 0x26322f;
                if (selectable[z][x]) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
                    sample.setY(y - 1);
                    var block = level.getBlockState(sample);
                    color = block.getFluidState().isEmpty() ? block.getMapColor(level, sample).col : 0x456d82;
                    if (color == 0) color = 0x414b45;
                }
                terrain[z][x] = 0xff000000 | color;
            }
        }
        show.active = selected != null && vehicle.displayTime(0) == 0;
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.getVehicle() != vehicle) {
            onClose();
            return;
        }
        show.active = selected != null && vehicle.displayTime(0) == 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= left && mouseX < left + size && mouseY >= top && mouseY < top + size) {
            int x = Math.min(TILES - 1, (int) Math.ceil((Math.floor(mouseX - left) + 1) * TILES / size) - 1);
            int z = Math.min(TILES - 1, (int) Math.ceil((Math.floor(mouseY - top) + 1) * TILES / size) - 1);
            selected = selectable[z][x] ? origin.offset((x - TILES / 2) * STEP, 0, (z - TILES / 2) * STEP) : null;
            show.active = selected != null && vehicle.displayTime(0) == 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        renderBackground(gui);
        gui.fill(left - 4, top - 16, left + size + 4, top + size + 39, 0xff1c2726);
        gui.drawCenteredString(font, title, width / 2, top - 12, 0xffd9e8dd);
        for (int z = 0; z < TILES; z++) {
            for (int x = 0; x < TILES; x++) {
                gui.fill(left + x * size / TILES, top + z * size / TILES,
                        left + (x + 1) * size / TILES, top + (z + 1) * size / TILES, terrain[z][x]);
            }
        }
        for (int i = 1; i < 8; i++) {
            int offset = i * size / 8;
            gui.fill(left + offset, top, left + offset + 1, top + size, 0x3ce0eddd);
            gui.fill(left, top + offset, left + size, top + offset + 1, 0x3ce0eddd);
        }
        gui.drawString(font, "N", left + size - 12, top + 4, 0xfff4f7ea);
        gui.fill(left + size / 2 - 2, top + size / 2 - 2,
                left + size / 2 + 3, top + size / 2 + 3, 0xffbaf0e5);
        if (selected != null) {
            int x = left + ((selected.getX() - origin.getX()) / STEP * 2 + TILES + 1) * size / (2 * TILES);
            int z = top + ((selected.getZ() - origin.getZ()) / STEP * 2 + TILES + 1) * size / (2 * TILES);
            gui.fill(x - 5, z, x + 6, z + 1, 0xffffcf72);
            gui.fill(x, z - 5, x + 1, z + 6, 0xffffcf72);
        }
        gui.drawCenteredString(font, selected == null ? Component.translatable("screen.milmod.display_pick")
                        : Component.literal("X " + selected.getX() + "   Z " + selected.getZ()),
                width / 2, top + size + 5, 0xffd9e8dd);
        super.render(gui, mouseX, mouseY, partial);
    }
}
