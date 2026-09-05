package io.github.mahjongmaid.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjongforge.mahjongtable.client.MahjongTableRenderer;
import io.github.mahjongmaid.integration.MaidTableAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Uses the table's synchronized maid names without treating maids as human players. */
@Mixin(value = MahjongTableRenderer.class, remap = false)
public abstract class MahjongTableRendererMixin {
    @Shadow
    private static Component buildNameLine(MahjongTableBlockEntity.SeatInfo info, Level level) {
        throw new AssertionError("Mixin shadow");
    }

    @Redirect(
            method = "render(Lcom/riichimahjongforge/mahjongtable/MahjongTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/riichimahjongforge/mahjongtable/client/MahjongTableRenderer;buildNameLine(Lcom/riichimahjongforge/mahjongtable/MahjongTableBlockEntity$SeatInfo;Lnet/minecraft/world/level/Level;)Lnet/minecraft/network/chat/Component;"))
    private Component mahjongmaid$seatLabel(
            MahjongTableBlockEntity.SeatInfo info, Level level,
            MahjongTableBlockEntity table, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (table instanceof MaidTableAccess access) {
            List<MahjongTableBlockEntity.SeatInfo> seats = table.seats();
            for (int seat = 0; seat < seats.size(); seat++) {
                // Equal empty-seat records may belong to different seats. The copied
                // list preserves element identity, which identifies this exact seat.
                if (seats.get(seat) == info) {
                    String name = access.mahjongmaid$name(seat);
                    if (name != null) {
                        return Component.literal(name);
                    }
                    break;
                }
            }
        }
        return buildNameLine(info, level);
    }

    @Inject(method = "seatName", at = @At("HEAD"), cancellable = true)
    private void mahjongmaid$resultName(
            MahjongTableBlockEntity table, int seat, CallbackInfoReturnable<Component> callback) {
        if (table instanceof MaidTableAccess access) {
            String name = access.mahjongmaid$name(seat);
            if (name != null) {
                callback.setReturnValue(Component.literal(name));
            }
        }
    }
}
