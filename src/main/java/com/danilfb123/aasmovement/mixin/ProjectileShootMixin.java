package com.danilfb123.aasmovement.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.danilfb123.aasmovement.CursorState;

/**
 * shootFromRotation(Entity shooter, float rotX, float rotY, float rotZOffset,
 *                    float velocity, float inaccuracy)
 * — общая точка для лука/арбалета/трезубца/снежков/яиц/зелий/жемчуга/стрелы опыта.
 *
 * Подменяем rotX/rotY на ТИКОВОЕ (не интерполированное) направление курсора,
 * только если стрелок — локальный игрок и не в креативе. rotZOffset не трогаем —
 * это угловой оффсет multishot у арбалета, применяется поверх базового rotX/rotY
 * уже дальше по коду ванили, так что multishot не ломается.
 *
 * @ModifyVariable с argsOnly=true нумерует именно параметры метода, без "this":
 *   0 = shooter, 1 = rotX, 2 = rotY, 3 = rotZOffset, 4 = velocity, 5 = inaccuracy.
 * Если маппинги другие и порядок параметров отличается — проверь сигнатуру
 * и поправь index.
 */
@Mixin(Projectile.class)
public abstract class ProjectileShootMixin {

    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), argsOnly = true, index = 1)
    private static float yourmod$modifyRotX(float rotX, Entity shooter) {
        if (!shouldOverride(shooter)) {
            return rotX;
        }
        return CursorState.getPitchTick();
    }

    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), argsOnly = true, index = 2)
    private static float yourmod$modifyRotY(float rotY, Entity shooter) {
        if (!shouldOverride(shooter)) {
            return rotY;
        }
        return CursorState.getYawTick();
    }

    private static boolean shouldOverride(Entity shooter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        // Именно локальный игрок, не instanceof Player — иначе логика могла бы
        // случайно задеть других игроков/сущностей (в т.ч. на сервере).
        if (shooter != mc.player) {
            return false;
        }
        return !mc.player.isCreative();
    }
}