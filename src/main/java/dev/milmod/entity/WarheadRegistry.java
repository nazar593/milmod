package dev.milmod.entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Индивидуальные параметры боевой части (БЧ) для каждого дрона:
 * мощность взрыва, радиус ударной волны, огонь, плотность дыма/искр и звук.
 */
public final class WarheadRegistry {

    public record WarheadProfile(
            String id,
            float blastPower,
            float shockwaveRadius,
            boolean causesFire,
            int sparkCount,
            int smokeCount,
            String soundId,
            float soundVolume,
            float soundPitch
    ) {}

    private static final Map<String, WarheadProfile> PROFILES = new HashMap<>();

    static {
        // Разведывательные / легкие (малый хлопок, без большого кратера)
        register(new WarheadProfile("mavic3t",    1.8f,  6.0f, false,  18,  10, "milmod:mavic3t_crash",    1.2f, 1.15f));
        register(new WarheadProfile("orlan_10",   2.5f,  9.0f, false,  25,  16, "milmod:orlan_10_crash",   1.6f, 1.05f));

        // Средние ударные / барражирующие БПЛА
        register(new WarheadProfile("fp1",        6.5f, 22.0f, true,   70,  45, "milmod:explosion_crack",  3.2f, 0.95f));
        register(new WarheadProfile("lyutyi",     7.5f, 26.0f, true,   85,  55, "milmod:explosion_crack",  3.6f, 0.88f));
        register(new WarheadProfile("forpost_r",  5.5f, 18.0f, true,   55,  35, "milmod:forpost_r_crash",  2.8f, 0.92f));
        register(new WarheadProfile("mohajer_6",  6.0f, 20.0f, true,   60,  40, "milmod:mohajer_6_crash",  3.0f, 0.90f));

        // Тяжелые БЧ (Герань-4, Shahed-238, Искандер-К)
        register(new WarheadProfile("geran4",     9.5f, 34.0f, true,  130,  85, "milmod:impact_blast",     4.5f, 0.82f));
        register(new WarheadProfile("shahed_238", 10.5f, 38.0f, true, 150, 100, "milmod:impact_blast",     5.0f, 0.76f));
        register(new WarheadProfile("iskander_k", 16.0f, 55.0f, true, 240, 160, "milmod:explosion_muffled",6.5f, 0.65f));
    }

    private WarheadRegistry() {}

    private static void register(WarheadProfile profile) {
        PROFILES.put(profile.id().toLowerCase(Locale.ROOT), profile);
    }

    public static WarheadProfile get(String droneId) {
        if (droneId == null) {
            return PROFILES.get("fp1");
        }
        String key = droneId.toLowerCase(Locale.ROOT).replace("milmod:", "");
        return PROFILES.getOrDefault(key, PROFILES.get("fp1"));
    }
}
