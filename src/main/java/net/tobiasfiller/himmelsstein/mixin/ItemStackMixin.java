package net.tobiasfiller.himmelsstein.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.tobiasfiller.himmelsstein.core.utility.UpgradeHelper;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void himmelsstein$appendUpgradeSuffix(CallbackInfoReturnable<Component> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        int level = UpgradeHelper.getLevel(stack);
        if (level > 0) {
            callback.setReturnValue(UpgradeHelper.withUpgradeSuffix(callback.getReturnValue(), level));
        }
    }

    @Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V", at = @At("TAIL"))
    private void himmelsstein$clampUpgradeLevel(DataComponentMap components, CallbackInfo callback) {
        // Values outside 0-30 are clamped on load instead of letting the item codec fail.
        UpgradeHelper.normalize((ItemStack) (Object) this);
    }
}
