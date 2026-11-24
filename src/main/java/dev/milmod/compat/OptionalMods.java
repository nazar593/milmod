package dev.milmod.compat;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

public final class OptionalMods {
    private OptionalMods() {}

    public static Component displayVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .<Component>map(container -> Component.literal(container.getModInfo().getVersion().toString()))
                .orElseGet(() -> Component.translatableWithFallback("commands.milmod.mod_absent", "absent"));
    }
}
