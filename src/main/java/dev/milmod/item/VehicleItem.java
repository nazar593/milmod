package dev.milmod.item;

import dev.milmod.entity.VehicleEntity;
import dev.milmod.entity.VehiclePlacement;
import dev.milmod.model.VehicleProfile;
import dev.milmod.model.VehicleOperation;
import net.minecraft.core.Direction;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class VehicleItem extends Item {
    private final VehicleProfile profile;

    public VehicleItem(VehicleProfile profile) {
        super(new Properties().stacksTo(1));
        this.profile = profile;
    }

    public VehicleProfile profile() { return profile; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.milmod." + profile.id).withStyle(ChatFormatting.GRAY));
        if (profile == VehicleProfile.MAVIC3T || profile == VehicleProfile.MOHAJER_6
                || profile == VehicleProfile.FORPOST_R) {
            tooltip.add(Component.translatable("tooltip.milmod.optical_control").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (VehicleOperation.railOnly(profile)) {
            if (player != null) player.displayClientMessage(Component.translatable("message.milmod.rail_required"), true);
            return InteractionResult.FAIL;
        }
        if ((profile.air() || profile == VehicleProfile.LAUNCHER) && (context.getClickedFace() != Direction.UP || !level.getBlockState(context.getClickedPos())
                .isFaceSturdy(level, context.getClickedPos(), Direction.UP))) return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        VehicleEntity vehicle = VehicleEntity.create(level, profile);
        vehicle.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player == null ? 0 : player.getYRot(), 0);
        if (!VehiclePlacement.add(server, vehicle, player)) return InteractionResult.FAIL;
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
