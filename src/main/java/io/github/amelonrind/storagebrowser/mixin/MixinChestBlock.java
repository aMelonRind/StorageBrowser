package io.github.amelonrind.storagebrowser.mixin;

import io.github.amelonrind.storagebrowser.listener.InvPosPair;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlock.class)
public class MixinChestBlock {

    @Shadow @Final public static EnumProperty<ChestType> CHEST_TYPE;

    @Shadow @Final public static DirectionProperty FACING;

    @Inject(at = @At("RETURN"), method = "onUse")
    public void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (MinecraftClient.getInstance().player != player) return;
        BlockPos other = null;
        ChestType type = state.get(CHEST_TYPE);
        if (type != ChestType.SINGLE) {
            Direction dir = state.get(FACING);
            if (type == ChestType.LEFT) dir = dir.rotateYClockwise();
            else dir = dir.rotateYCounterclockwise();
            other = pos.offset(dir);
        }
        if (type != ChestType.LEFT) InvPosPair.onClick(pos, "generic", "chest", other);
        else InvPosPair.onClick(other, "generic", "chest", pos);
    }

}
