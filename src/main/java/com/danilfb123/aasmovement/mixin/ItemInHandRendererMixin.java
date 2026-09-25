package com.danilfb123.aasmovement.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.danilfb123.aasmovement.CursorState;

/**
 * Разворачивает руки первого лица на дельту между реальным поворотом камеры
 * и направлением CursorState — только survival/adventure, только для
 * локального игрока. В креативе руки как в ванили.
 *
 * Дельта берётся между РЕАЛЬНЫМ yaw/pitch камеры на этот кадр и
 * интерполированным yaw/pitch курсора — не между кадрами рук, иначе будет
 * плавать при резких движениях мыши.
 *
 * MAX_LAG_DEGREES — клэмп максимального угла "отставания", чтобы при быстром
 * вращении мышью руки не улетали в сторону неестественно далеко.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    private static final float MAX_LAG_DEGREES = 20.0F;

    // Замени "renderHandsWithItems" на актуальное имя метода в твоей версии
    // маппингов, если оно другое (может отличаться между версиями MC).
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void yourmod$offsetHandsPoseStack(float partialTick, PoseStack poseStack,
                                              Object multiBufferSource, Object player,
                                              int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) {
            return;
        }
        if (localPlayer.isCreative() || localPlayer.isSpectator()) {
            return;
        }

        float realYaw = Mth.lerp(partialTick, localPlayer.yRotO, localPlayer.getYRot());
        float realPitch = Mth.lerp(partialTick, localPlayer.xRotO, localPlayer.getXRot());

        float cursorYaw = CursorState.getInterpolatedYaw(partialTick);
        float cursorPitch = CursorState.getInterpolatedPitch(partialTick);

        float deltaYaw = Mth.wrapDegrees(cursorYaw - realYaw);
        float deltaPitch = Mth.wrapDegrees(cursorPitch - realPitch);

        deltaYaw = Mth.clamp(deltaYaw, -MAX_LAG_DEGREES, MAX_LAG_DEGREES);
        deltaPitch = Mth.clamp(deltaPitch, -MAX_LAG_DEGREES, MAX_LAG_DEGREES);

        // Разворачиваем матрицу рук на дельту. Знаки/оси подбери по факту —
        // зависит от того, в какой момент относительно остальных трансформаций
        // рук стоит этот инжект (до/после mulPose(camera rotation)).
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-deltaYaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(deltaPitch));
    }
}