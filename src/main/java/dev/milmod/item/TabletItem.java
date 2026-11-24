package dev.milmod.item;

import dev.milmod.client.DroneTabletScreen;
import dev.milmod.entity.VehicleEntity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class TabletItem extends Item {
    private final boolean mavicController;

    public TabletItem() { this(false); }

    public TabletItem(boolean mavicController) {
        super(new Properties().stacksTo(1));
        this.mavicController = mavicController;
    }

    public boolean accepts(VehicleEntity craft) {
        return mavicController == (craft.profile() == dev.milmod.model.VehicleProfile.MAVIC3T);
    }

    public static UUID linked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.getItem() instanceof TabletItem && tag != null && tag.hasUUID("Drone")
                ? tag.getUUID("Drone") : null;
    }

    public static void bind(ItemStack stack, VehicleEntity vehicle, Player player) {
        stack.getOrCreateTag().putUUID("Drone", vehicle.getUUID());
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("message.milmod.bound", vehicle.getName()), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DroneTabletScreen.open(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
