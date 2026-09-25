package com.danilfb123.aasmovement.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.danilfb123.aasmovement.CursorState;

/**
 * Гарантированно последний пересчёт mc.hitResult в каждом кадре, где ваниль
 * его считает. Инжект в TAIL, а не в forge-эвент до render() — так убираем
 * саму гонку событий, а не подстраиваемся под неё.
 *
 * Только survival/adventure — в креативе ведём себя как ваниль.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickMixin {

    // Дальность пикинга — как у ванили (adventure/survival копия того,
    // что берёт сама pick(); при необходимости синхронизируй с реальным
    // значением из твоего EntityPickMixin/атрибутов игрока).
    private static final double PICK_RANGE = 20.0D;

    @Inject(method = "pick", at = @At("TAIL"))
    private void yourmod$overrideHitResultWithCursor(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) {
            return;
        }

        // Креатив не трогаем вообще — ваниль как есть.
        if (player.isCreative()) {
            return;
        }

        // pick() может дёргаться не только из основного кадра рендера мира
        // (например, из GUI-контекстов) — если камеры/hitResult в этот момент
        // нет смысла трогать, лучше выйти. Минимальная защита:
        if (mc.hitResult == null) {
            return;
        }

        float yaw = CursorState.getInterpolatedYaw(partialTick);
        float pitch = CursorState.getInterpolatedPitch(partialTick);

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eyePos = camera.getPosition();

        Vec3 viewVector = getViewVectorForRotation(yaw, pitch);
        Vec3 endPos = eyePos.add(viewVector.scale(PICK_RANGE));

        ClipContext.Block blockClip = player.isSpectator()
                ? ClipContext.Block.VISUAL
                : ClipContext.Block.COLLIDER;

        HitResult result = level.clip(new ClipContext(
                eyePos,
                endPos,
                blockClip,
                ClipContext.Fluid.NONE,
                player
        ));

        // Тут при необходимости добавляешь entity-pick аналогично тому, что
        // делает твой EntityPickMixin — если он уже перехватывает getViewVector,
        // возможно, entity-часть отработает корректно сама по интерполированному
        // направлению, и здесь достаточно только блочной части.
        mc.hitResult = result;
    }

    private static Vec3 getViewVectorForRotation(float yaw, float pitch) {
        float f = pitch * ((float) Math.PI / 180F);
        float f1 = -yaw * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3((double) (f3 * f4), (double) (-f5), (double) (f2 * f4));
    }
}