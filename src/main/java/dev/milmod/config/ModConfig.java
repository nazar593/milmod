package dev.milmod.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModConfig {
    private static final ForgeConfigSpec SERVER_SPEC;
    private static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue MESH_RANGE;
    public static final ForgeConfigSpec.BooleanValue TEMPORARY_LIGHT;
    public static final ForgeConfigSpec.IntValue WORLD_DRONE_LIMIT;
    public static final ForgeConfigSpec.IntValue PLAYER_DRONE_LIMIT;
    public static final ForgeConfigSpec.IntValue PARTICLE_BUDGET;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue CHARGE_BLOCK_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue BLAST_LIGHT;
    public static final ForgeConfigSpec.DoubleValue SCREEN_FLASH_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue CONCUSSION_BLUR;
    public static final ForgeConfigSpec.BooleanValue CONCUSSION_AUDIO;
    public static final ForgeConfigSpec.DoubleValue SOUND_SPEED;
    public static final ForgeConfigSpec.IntValue DRONE_AUDIO_RANGE;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        server.push("limits");
        WORLD_DRONE_LIMIT = server.comment("Maximum loaded aircraft in this dimension.")
                .defineInRange("worldDroneLimit", 48, 1, 512);
        PLAYER_DRONE_LIMIT = server.comment("Maximum loaded aircraft placed by one player in this dimension.")
                .defineInRange("playerDroneLimit", 6, 1, 64);
        server.pop();
        server.push("gameplay");
        MESH_RANGE = server.comment("Maximum radius for Minecraft mission markers and radio control.")
                .defineInRange("meshRange", 3000, 32, 12000);
        TEMPORARY_LIGHT = server.comment("Use short-lived invisible light blocks in ordinary air.")
                .define("temporaryLight", true);
        CHARGE_BLOCK_DAMAGE = server.comment("Allow MilMod impact charge to damage Minecraft blocks.")
                .define("chargeBlockDamage", true);
        server.pop();
        SERVER_SPEC = server.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.push("effects");
        PARTICLE_BUDGET = client.comment("Maximum particles emitted by a MilMod effect.")
                .defineInRange("particleBudget", 256, 0, 4096);
        CAMERA_SHAKE = client.comment("Apply a brief camera impulse on nearby impacts.")
                .define("cameraShake", true);
        BLAST_LIGHT = client.comment("Render a brief diffuse surface flash near MilMod impacts.")
                .define("blastLight", true);
        SCREEN_FLASH_STRENGTH = client.comment("Opacity multiplier for the full-screen blast flash.")
                .defineInRange("screenFlashStrength", 1D, 0D, 1D);
        CONCUSSION_BLUR = client.comment("Apply screen blur while the concussion effect recovers.")
                .define("concussionBlur", true);
        CONCUSSION_AUDIO = client.comment("Enable the low-pass effect, tinnitus and heartbeat.")
                .define("concussionAudio", true);
        client.pop();
        client.push("droneAudio");
        SOUND_SPEED = client.comment("Gameplay Doppler speed in Minecraft blocks per second.")
                .defineInRange("soundSpeed", 165D, 60D, 400D);
        DRONE_AUDIO_RANGE = client.comment("Maximum airframe audio distance in blocks.")
                .defineInRange("range", 112, 24, 112);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private ModConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, SERVER_SPEC);
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
