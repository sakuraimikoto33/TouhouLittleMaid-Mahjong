package io.github.mahjongmaid.mixin;

import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import io.github.mahjongmaid.integration.MaidTableAccess;
import io.github.mahjongmaid.integration.TableMaidSession;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = MahjongTableBlockEntity.class, remap = false)
public abstract class MahjongTableBlockEntityMixin implements MaidTableAccess {
    @Shadow private List<MahjongTableBlockEntity.SeatInfo> seats;
    @Unique private TableMaidSession mahjongmaid$session;

    @Override
    public TableMaidSession mahjongmaid$session() {
        if (mahjongmaid$session == null) {
            mahjongmaid$session = new TableMaidSession((MahjongTableBlockEntity) (Object) this);
        }
        return mahjongmaid$session;
    }

    @Inject(method = "tryStartMatch", at = @At("RETURN"))
    private void mahjongmaid$start(long seed, CallbackInfoReturnable<MahjongTableBlockEntity.StartMatchResult> cir) {
        if (cir.getReturnValue() == MahjongTableBlockEntity.StartMatchResult.STARTED) mahjongmaid$session().start();
    }

    @Inject(method = "tryApplyPredefined", at = @At("RETURN"))
    private void mahjongmaid$predefined(CallbackInfoReturnable<MahjongTableBlockEntity.StartMatchResult> cir) {
        if (cir.getReturnValue() == MahjongTableBlockEntity.StartMatchResult.STARTED) mahjongmaid$session().start();
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void mahjongmaid$tick(CallbackInfo ci) {
        mahjongmaid$session().tick();
    }

    @Inject(method = "endGame", at = @At("HEAD"))
    private void mahjongmaid$cancel(CallbackInfo ci) {
        mahjongmaid$session().tick();
        mahjongmaid$session().cancel();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void mahjongmaid$save(CompoundTag tag, CallbackInfo ci) {
        mahjongmaid$session().save(tag);
    }

    @Inject(method = "load", at = @At("TAIL"), remap = true)
    private void mahjongmaid$load(CompoundTag tag, CallbackInfo ci) {
        mahjongmaid$session().load(tag);
    }

    // Riichi Mahjong 0.2.0 keeps four physical seats in sanma, but its driver has
    // only three players. Bound the human-player loops without trimming the
    // physical Seats NBT, which still needs to retain the disabled North seat.
    @Redirect(method = "serverTick()V", at = @At(value = "INVOKE",
            target = "Ljava/util/List;size()I", remap = false), require = 0)
    private int mahjongmaid$tickingSeatCount(List<?> list) {
        return mahjongmaid$activeSeatCount(list);
    }

    @Redirect(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V", remap = true,
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", remap = false), require = 0)
    private int mahjongmaid$savingSeatCount(List<?> list) {
        return mahjongmaid$activeSeatCount(list);
    }

    @Unique
    private int mahjongmaid$activeSeatCount(List<?> list) {
        var driver = ((MahjongTableBlockEntity) (Object) this).driver();
        // Only the table's seat list is affected. Newer upstream versions may
        // already bound these loops, so the redirects are optional.
        return list == seats && driver != null
                ? Math.min(list.size(), driver.match().playerCount()) : list.size();
    }

    @Shadow
    private static String fallbackSeatName(int seat) { throw new AssertionError("Mixin shadow"); }

    @Redirect(method = "playFinalWinEffects", at = @At(value = "INVOKE",
            target = "Lcom/riichimahjongforge/mahjongtable/MahjongTableBlockEntity;fallbackSeatName(I)Ljava/lang/String;"))
    private String mahjongmaid$winnerName(int seat) {
        String name = mahjongmaid$name(seat);
        return name == null ? fallbackSeatName(seat) : name;
    }
}
