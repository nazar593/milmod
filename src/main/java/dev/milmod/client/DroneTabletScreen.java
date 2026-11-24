package dev.milmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.milmod.entity.FlightMission;
import dev.milmod.entity.VehicleEntity;
import dev.milmod.item.TabletItem;
import dev.milmod.model.VehicleOperation;
import dev.milmod.network.DroneLink;
import dev.milmod.sound.ModSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;

public final class DroneTabletScreen extends Screen {
    private static final int COLUMNS = 72, ROWS = 48;
    private static final int[] STEP = {1, 2, 4, 8, 16, 48, 128};
    private static final int LIGHT = 0xffedf4e4, MUTED = 0xffa8bfad, AMBER = 0xffffd482;
    private final InteractionHand hand;
    private VehicleEntity drone;
    private UUID linked;
    private BlockPos marker;
    private DynamicTexture terrain;
    private ResourceLocation terrainId;
    private int zoom = 2, ticks, awaitingMount;
    private int mapX, mapY, mapWidth, mapHeight;
    private double centerX, centerZ;
    private boolean centered, terrainDirty;
    private Button loiter, strike, camera;

    private DroneTabletScreen(InteractionHand hand) {
        super(Component.translatable("screen.milmod.tablet"));
        this.hand = hand;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new DroneTabletScreen(hand));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        mapX = 22;
        mapY = 32;
        mapWidth = Math.max(1, width - 36);
        mapHeight = Math.max(1, height - 90);
        int controlsY = mapY + mapHeight + 14;
        int buttonWidth = Math.min(140, (mapWidth - 8) / 2);
        loiter = addRenderableWidget(Button.builder(modeLabel(FlightMission.Mode.LOITER),
                button -> submit(FlightMission.Mode.LOITER)).bounds(mapX, controlsY, buttonWidth, 20).build());
        strike = addRenderableWidget(Button.builder(modeLabel(FlightMission.Mode.STRIKE),
                button -> submit(FlightMission.Mode.STRIKE)).bounds(mapX + buttonWidth + 8, controlsY, buttonWidth, 20).build());
        camera = addRenderableWidget(Button.builder(Component.translatable("screen.milmod.camera_short"), button -> {
            if (controlled()) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                onClose();
            } else {
                DroneLink.send(false, hand);
                awaitingMount = 45;
                update();
            }
        }).bounds(width - 114, 8, 57, 17).build());
        addRenderableWidget(Button.builder(Component.literal("−"), button -> zoom(1)).bounds(width - 52, 8, 18, 17).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> zoom(-1)).bounds(width - 32, 8, 18, 17).build());
        if (terrain == null) {
            terrain = new DynamicTexture(COLUMNS, ROWS, false);
            terrainId = minecraft.getTextureManager().register("milmod_tablet_map", terrain);
        }
        update();
        sampleTerrain();
    }

    private void submit(FlightMission.Mode mode) {
        if (marker == null) return;
        playTabletSound(true);
        DroneLink.sendMission(hand, mode, marker);
        onClose();
    }

    private boolean controlled() {
        return drone != null && minecraft.player != null
                && (minecraft.player.getVehicle() == drone || PilotView.remoteVehicle() == drone);
    }

    @Override
    public void tick() {
        if (awaitingMount > 0) {
            if (controlled()) { onClose(); return; }
            if (--awaitingMount == 0) update();
        }
        boolean refresh = ++ticks % 10 == 0;
        if (refresh) update();
        if (refresh || terrainDirty) {
            sampleTerrain();
            terrainDirty = false;
        }
    }

    private void update() {
        if (minecraft.player == null || minecraft.level == null) return;
        UUID id = TabletItem.linked(minecraft.player.getItemInHand(hand));
        if (!Objects.equals(linked, id)) {
            linked = id;
            marker = null;
            centered = false;
        }
        drone = PilotView.remoteVehicle();
        if (drone == null && minecraft.player.getVehicle() instanceof VehicleEntity rider && rider.profile().air()) drone = rider;
        if (drone == null && id != null) {
            for (var entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof VehicleEntity craft && id.equals(craft.getUUID()) && craft.isAlive()) {
                    drone = craft;
                    break;
                }
            }
        }
        if (!centered) {
            var focus = drone == null ? minecraft.player : drone;
            centerX = focus.getX();
            centerZ = focus.getZ();
            centered = true;
        }
        boolean ready = awaitingMount == 0 && id != null && drone != null && id.equals(drone.getUUID())
                && !minecraft.player.isPassenger() && PilotView.remoteVehicle() == null
                && drone.flightPhase() != VehicleEntity.FlightPhase.LAUNCHING
                && drone.flightPhase() != VehicleEntity.FlightPhase.SPOOLING
                && (drone.docked() || drone.handStartReady())
                && (!VehicleOperation.railOnly(drone.profile()) || drone.docked()
                    || drone.flightPhase() == VehicleEntity.FlightPhase.AIRBORNE);
        double range = ready && drone.flightPhase() == VehicleEntity.FlightPhase.AIRBORNE ? drone.radioRange() : 8;
        camera.active = awaitingMount == 0 && drone != null && drone.hasFlightCamera()
                && (controlled() || ready && drone.distanceToSqr(minecraft.player) <= range * range);
        boolean mission = ready && drone.missionStatus() == 0 && VehicleOperation.remote(drone.profile())
                && drone.distanceToSqr(minecraft.player) <= 64 && validMarker();
        loiter.active = mission;
        strike.active = mission;
    }

    private boolean validMarker() {
        if (drone == null || marker == null || minecraft.level == null) return false;
        double range = drone.missionRange();
        double dx = marker.getX() + .5 - drone.getX(), dz = marker.getZ() + .5 - drone.getZ();
        return dx * dx + dz * dz <= range * range && minecraft.level.getWorldBorder().isWithinBounds(marker);
    }

    private double blocksPerPixel() { return COLUMNS * STEP[zoom] / (double) mapWidth; }

    private void sampleTerrain() {
        var level = minecraft.level;
        if (level == null || terrain == null) return;
        NativeImage pixels = terrain.getPixels();
        if (pixels == null) return;
        double scale = blocksPerPixel();
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < ROWS; z++) {
            int wz = Mth.floor(centerZ + ((z + .5) / ROWS - .5) * mapHeight * scale);
            for (int x = 0; x < COLUMNS; x++) {
                int wx = Mth.floor(centerX + (x + .5 - COLUMNS / 2.0) * STEP[zoom]);
                int rgb = 0x213128;
                if (level.hasChunk(wx >> 4, wz >> 4)) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                    pos.set(wx, y, wz);
                    var state = level.getBlockState(pos);
                    int color = state.getFluidState().isEmpty() ? state.getMapColor(level, pos).col : 0x4077a2;
                    if (color != 0) rgb = color;
                } else if (((x + z) & 3) == 0) rgb = 0x293b31;
                pixels.setPixelRGBA(x, z, 0xff000000 | ((rgb & 255) << 16) | (rgb & 0xff00) | (rgb >> 16 & 255));
            }
        }
        terrain.upload();
    }

    private void zoom(int direction) {
        zoom = Mth.clamp(zoom + direction, 0, STEP.length - 1);
        sampleTerrain();
    }

    private boolean inMap(double x, double y) {
        return x >= mapX && x < mapX + mapWidth && y >= mapY && y < mapY + mapHeight;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (!inMap(x, y) || delta == 0) return super.mouseScrolled(x, y, delta);
        zoom(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!inMap(x, y)) return super.mouseClicked(x, y, button);
        if (button == 1) return true;
        if (button == 2) {
            marker = null;
            update();
            return true;
        }
        if (button != 0) return false;
        double scale = blocksPerPixel();
        marker = new BlockPos(Mth.floor(centerX + (x - mapX - mapWidth * .5) * scale), 0,
                Mth.floor(centerZ + (y - mapY - mapHeight * .5) * scale));
        update();
        if (drone != null) playTabletSound(validMarker());
        return true;
    }

    private void playTabletSound(boolean accepted) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                (accepted ? ModSounds.TABLET_CLICK : ModSounds.TABLET_ERROR).get(), 1F, .45F));
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 1 || !inMap(x, y)) return super.mouseDragged(x, y, button, dx, dy);
        double scale = blocksPerPixel();
        centerX = Mth.clamp(centerX - dx * scale, -30_000_000, 30_000_000);
        centerZ = Mth.clamp(centerZ - dy * scale, -30_000_000, 30_000_000);
        terrainDirty = true;
        return true;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        gui.fill(7, 5, width - 7, height - 6, 0xff19241e);
        gui.fill(9, 5, width - 9, 6, 0xff61766a);
        gui.drawString(font, title, 14, 12, LIGHT);
        if (drone != null && width >= 305) {
            var label = drone.getName();
            gui.drawString(font, label, Math.max(95, width - 124 - font.width(label)), 12, MUTED);
        }
        gui.blit(terrainId, mapX, mapY, mapWidth, mapHeight, 0, 0, COLUMNS, ROWS, COLUMNS, ROWS);
        gui.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);
        for (int i = 0; i < 8; i++) {
            int x = mapX + i * mapWidth / 8, y = mapY + i * mapHeight / 8;
            gui.fill(x, mapY, x + 1, mapY + mapHeight, 0x5195b4a0);
            gui.fill(mapX, y, mapX + mapWidth, y + 1, 0x5195b4a0);
        }
        if (drone != null) pin(gui, drone.getX(), drone.getZ(), 0xffefffe5, false);
        if (marker != null) pin(gui, marker.getX() + .5, marker.getZ() + .5, validMarker() ? AMBER : 0xffff826d, true);
        gui.disableScissor();
        for (int i = 0; i < 8; i++) {
            gui.drawCenteredString(font, Character.toString('A' + i), mapX + (2 * i + 1) * mapWidth / 16, mapY - 8, MUTED);
            if (mapHeight >= 85) gui.drawString(font, Integer.toString(i + 1), 11, mapY + (2 * i + 1) * mapHeight / 16 - 4, MUTED);
        }
        Component detail;
        if (marker != null) detail = Component.translatable("screen.milmod.map_coords", marker.getX(), marker.getZ());
        else if (drone != null && drone.missionStatus() > 0) detail = modeLabel(FlightMission.Mode.values()[drone.missionStatus() - 1]);
        else detail = Component.translatable(drone == null ? "screen.milmod.link_hint" : "screen.milmod.map_marker");
        gui.drawString(font, font.plainSubstrByWidth(detail.getString(), mapWidth), mapX, mapY + mapHeight + 3, MUTED);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void pin(GuiGraphics gui, double worldX, double worldZ, int color, boolean selected) {
        double scale = blocksPerPixel();
        int x = mapX + mapWidth / 2 + Mth.floor((worldX - centerX) / scale);
        int y = mapY + mapHeight / 2 + Mth.floor((worldZ - centerZ) / scale);
        if (!inMap(x, y)) return;
        gui.fill(x - 2, y - 2, x + 3, y + 3, color);
        if (selected) gui.renderOutline(x - 6, y - 6, 13, 13, color);
    }

    private static Component modeLabel(FlightMission.Mode mode) {
        return Component.translatable("screen.milmod.mode_" + mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void removed() {
        if (terrainId != null) minecraft.getTextureManager().release(terrainId);
        terrainId = null;
        terrain = null;
    }
}
