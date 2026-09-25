package com.danilfb123.aasmovement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MovementHandler {
    // --- ФИЗИКА (без изменений) ---
    private float fatigue = 0.0f;
    private float currentTilt = 0.0f;
    private float prevTilt = 0.0f;
    private float landingStun = 0.0f;
    private float swayTime = 0.0f;
    private boolean wasInAir = false;

    // --- ИНТЕРФЕЙС (без изменений) ---
    private int hotbarTimer = 0;
    private int lastSelectedSlot = -1;
    private float hotbarAlpha = 0.0f, prevHotbarAlpha = 0.0f;
    private float hotbarSlide = 0.0f, prevHotbarSlide = 0.0f;
    private float[] slotH = new float[9], prevSlotH = new float[9];

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // --- ДВИЖЕНИЕ (без изменений) ---
        prevTilt = currentTilt;
        currentTilt = Mth.lerp(0.12f, currentTilt, -player.xxa * 1.33f);
        if (player.onGround()) {
            if (wasInAir) { landingStun = 0.65f; wasInAir = false; }
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.95, 1.0, 0.95));
        } else { wasInAir = true; }
        if (landingStun > 0.01f) {
            float slow = 1.0f - (landingStun * 0.45f);
            player.setDeltaMovement(player.getDeltaMovement().multiply(slow, 1.0, slow));
            landingStun *= 0.88f;
        }
        fatigue = (player.isSprinting() && player.zza != 0)
                ? Math.min(fatigue + 0.012f, 1.0f)
                : Math.max(fatigue - 0.008f, 0.0f);

        // --- ОТСТАЮЩЕЕ НАПРАВЛЕНИЕ ПРИЦЕЛА ---
        CursorState.tick(player.getYRot(), player.getXRot());

        // ГЛАВНЫЙ ФИКС, СЛОЙ 1: ваниль уже посчитала hitResult ОТ КАМЕРЫ
        // (GameRenderer#tick -> pick(1.0F) прошёл раньше в этом же тике).
        // Пересчитываем его ОТ ПРИЦЕЛА. Клики (handleKeybinds) и outline
        // читают именно это поле.
        if (!player.isCreative() && mc.level != null && mc.gameMode != null) {
            overrideHitResult(mc, player, 1.0F);
        }

        // --- ЛОГИКА ИНТЕРФЕЙСА (без изменений) ---
        int currentSlot = player.getInventory().selected;
        if (currentSlot != lastSelectedSlot) {
            hotbarTimer = 50;
            lastSelectedSlot = currentSlot;
        }
        if (hotbarTimer > 0) hotbarTimer--;

        prevHotbarAlpha = hotbarAlpha;
        prevHotbarSlide = hotbarSlide;

        boolean shouldShow = (hotbarTimer > 0 || player.isCreative());
        if (shouldShow) {
            hotbarSlide = 1.0f;
            hotbarAlpha = Mth.lerp(0.35f, hotbarAlpha, 1.0f);
        } else {
            hotbarSlide = Mth.lerp(0.12f, hotbarSlide, 0.0f);
            hotbarAlpha = Mth.lerp(0.12f, hotbarAlpha, 0.0f);
        }

        for (int i = 0; i < 9; i++) {
            prevSlotH[i] = slotH[i];
            float targetH = (i == currentSlot) ? 10.0f : 0.0f;
            slotH[i] = Mth.lerp(0.4f, slotH[i], targetH);
        }
    }

    // ГЛАВНЫЙ ФИКС, СЛОЙ 2: RenderTickEvent.Pre файрится ПОСЛЕ
    // gameRenderer.tick()/pick(), но ДО рендера кадра (это видно прямо в
    // Forge-патче Minecraft#runTick). Перезаписываем hitResult ещё раз,
    // уже с кадровым partialTick — подсветка блока и наведение на моба
    // следуют прицелу даже если миксины не применились.
    @SubscribeEvent
    public void onRenderTickPre(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative()) return;
        if (mc.level == null || mc.gameMode == null) return;

        overrideHitResult(mc, mc.player, event.renderTickTime);
    }

    /**
     * Полный пересчёт наведения по направлению отстающего прицела.
     * Реплика ванильного GameRenderer#pick: сначала клип по блокам,
     * затем луч по сущностям, ограниченный дистанцией до блока.
     */
    private void overrideHitResult(Minecraft mc, Player player, float pt) {
        double reach = mc.gameMode.getPickRange();

        Vec3 eye = player.getEyePosition(pt);
        Vec3 dir = CursorState.getInterpolatedDirection(pt);
        Vec3 end = eye.add(dir.x * reach, dir.y * reach, dir.z * reach);

        // 1) блоки (реплика Entity#pick)
        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eye, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));

        // 2) сущности не дальше, чем найденный блок (реплика GameRenderer#pick)
        double maxSqr = blockHit.getLocation().distanceToSqr(eye);
        AABB box = player.getBoundingBox()
                .expandTowards(dir.scale(reach))
                .inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable(),
                maxSqr);

        if (entityHit != null) {
            mc.hitResult = entityHit;
            mc.crosshairPickEntity = entityHit.getEntity();
        } else {
            mc.hitResult = blockHit;
            mc.crosshairPickEntity = null;
        }
    }

    // Сброс прицела при выходе из мира
    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CursorState.reset();
    }

    @SubscribeEvent
    public void onRenderGuiPre(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) ||
                id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) ||
                id.equals(VanillaGuiOverlay.HOTBAR.id()) ||
                id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) ||
                id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())) {
            event.setCanceled(true);
        }
        Minecraft mc = Minecraft.getInstance();
        if (id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                && mc.player != null && !mc.player.isCreative()) {
            event.setCanceled(true);
        }
    }

    // Крестик рисуем в RenderGuiEvent.Post — Post отменённого оверлея
    // CROSSHAIR не вызывается никогда (это уже починено, крестик виден).
    @SubscribeEvent
    public void onRenderCrosshair(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isCreative()) return;

        float pt = event.getPartialTick();

        float camYaw = mc.player.yRotO
                + Mth.wrapDegrees(mc.player.getYRot() - mc.player.yRotO) * pt;
        float camPitch = Mth.lerp(pt, mc.player.xRotO, mc.player.getXRot());
        float curYaw = CursorState.getInterpolatedYaw(pt);
        float curPitch = CursorState.getInterpolatedPitch(pt);

        float deltaYaw = Mth.wrapDegrees(curYaw - camYaw);
        float deltaPitch = curPitch - camPitch;

        double halfFovRad = Math.toRadians(mc.options.fov().get() / 2.0);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double pxPerRadian = (h / 2.0) / Math.tan(halfFovRad);

        int cx = w / 2 + (int) (Math.tan(Math.toRadians(deltaYaw)) * pxPerRadian);
        int cy = h / 2 + (int) (Math.tan(Math.toRadians(deltaPitch)) * pxPerRadian);

        cx = Mth.clamp(cx, 6, w - 6);
        cy = Mth.clamp(cy, 6, h - 6);

        GuiGraphics gui = event.getGuiGraphics();
        int size = 4;
        int t = 1;
        int color = 0xCCFFFFFF;
        gui.fill(cx - size, cy - t, cx + size, cy + t, color);
        gui.fill(cx - t, cy - size, cx + t, cy + size, color);
    }

    // Хотбар — без изменений (Post оверлея CHAT_PANEL, он не отменён)
    @SubscribeEvent
    public void onRenderGuiPost(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float pt = event.getPartialTick();
        float renderAlpha = Mth.lerp(pt, prevHotbarAlpha, hotbarAlpha);
        float renderSlide = Mth.lerp(pt, prevHotbarSlide, hotbarSlide);

        if (renderAlpha <= 0.001f) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int slotSize = 22;
        int gap = 4;
        int totalWidth = 9 * (slotSize + gap);
        int startX = (screenWidth - totalWidth) / 2;

        float baseY = (screenHeight + 40) - (renderSlide * 72);
        int alpha = (int) (renderAlpha * 200);
        int color = (alpha << 24);

        for (int i = 0; i < 9; i++) {
            int x = startX + i * (slotSize + gap);
            float currentH = Mth.lerp(pt, prevSlotH[i], slotH[i]);
            int y = (int) (baseY - currentH);

            gui.fill(x, y, x + slotSize, y + slotSize, color);

            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                gui.renderItem(stack, x + 3, y + 3);
                gui.renderItemDecorations(mc.font, stack, x + 3, y + 3);
            }
        }
    }
}