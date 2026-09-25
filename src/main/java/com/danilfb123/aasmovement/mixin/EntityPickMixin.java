package com.danilfb123.aasmovement.mixin;

import com.danilfb123.aasmovement.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public class EntityPickMixin {

    @Redirect(
            method = "pick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 aasmovement$redirectViewVector(Entity self, float partialTick) {
        if (self instanceof Player player && player.isCreative()) {
            return self.getViewVector(partialTick);
        }
        if (self != Minecraft.getInstance().player) {
            return self.getViewVector(partialTick);
        }
        return CursorState.getInterpolatedDirection(partialTick);
    }
}