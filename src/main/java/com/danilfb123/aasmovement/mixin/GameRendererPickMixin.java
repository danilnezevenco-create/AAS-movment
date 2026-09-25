package com.danilfb123.aasmovement.mixin;

import com.danilfb123.aasmovement.CursorState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Рейкаст по СУЩНОСТЯМ: GameRenderer#pick строит луч прямым вызовом
 * player.getViewVector(partialTick) — подменяем на отстающий прицел.
 *
 * ВЕРСИЯ 2: тоже НЕОБЯЗАТЕЛЕН — событийный override в MovementHandler
 * пересчитывает и блок, и сущность. Миксин добавляет покадровую точность.
 */
@Mixin(GameRenderer.class)
public class GameRendererPickMixin {

    @Redirect(
            method = "pick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 aasmovement$redirectViewVector(LocalPlayer player, float partialTick) {
        if (player.isCreative()) {
            return player.getViewVector(partialTick);
        }
        CursorState.redirectCalls++;
        return CursorState.getInterpolatedDirection(partialTick);
    }
}