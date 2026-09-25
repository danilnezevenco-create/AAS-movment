package com.danilfb123.aasmovement.mixin;

import com.danilfb123.aasmovement.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Рейкаст по БЛОКАМ: Entity#pick вызывает getViewVector — подменяем
 * на отстающий прицел. Твой миксин был корректен, добавлен только
 * счётчик для телеметрии (CursorState.redirectCalls).
 *
 * ВЕРСИЯ 2: этот миксин теперь НЕОБЯЗАТЕЛЕН — hitResult и так
 * переписывается событиями. Он даёт покадровую точность между тиками.
 */
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
        CursorState.redirectCalls++;
        return CursorState.getInterpolatedDirection(partialTick);
    }
}