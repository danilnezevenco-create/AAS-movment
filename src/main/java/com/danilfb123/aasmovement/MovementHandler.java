package com.danilfb123.aasmovement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MovementHandler {
    // --- ФИЗИКА ---
    private float fatigue = 0.0f;
    private float currentTilt = 0.0f;
    private float prevTilt = 0.0f;
    private float landingStun = 0.0f;
    private float swayTime = 0.0f;
    private boolean wasInAir = false;

    // --- ИНТЕРФЕЙС ---
    private int hotbarTimer = 0;
    private int lastSelectedSlot = -1;

    // Разделяем анимацию появления: отдельно альфа (прозрачность) и позиция (выезд)
    private float hotbarAlpha = 0.0f, prevHotbarAlpha = 0.0f;
    private float hotbarSlide = 0.0f, prevHotbarSlide = 0.0f;

    private float[] slotH = new float[9], prevSlotH = new float[9];

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // --- ДВИЖЕНИЕ ---
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
        fatigue = (player.isSprinting() && player.zza != 0) ? Math.min(fatigue + 0.012f, 1.0f) : Math.max(fatigue - 0.008f, 0.0f);

        // --- ОТСТАЮЩЕЕ НАПРАВЛЕНИЕ ПРИЦЕЛА ---
        // Реальные текущие углы камеры игрока. cursorYaw/cursorPitch в CursorState
        // будут "догонять" их с задержкой, и именно они реально используются
        // при рейкасте (см. MinecraftMixin).
        CursorState.tick(player.getYRot(), player.getXRot());

        // --- ЛОГИКА ИНТЕРФЕЙСА ---
        int currentSlot = player.getInventory().selected;
        if (currentSlot != lastSelectedSlot) {
            hotbarTimer = 50;
            lastSelectedSlot = currentSlot;
        }
        if (hotbarTimer > 0) hotbarTimer--;

        prevHotbarAlpha = hotbarAlpha;
        prevHotbarSlide = hotbarSlide;

        // 1) В креативе хотбар видно всегда
        boolean shouldShow = (hotbarTimer > 0 || player.isCreative());

        if (shouldShow) {
            // 2) Сразу на нужном месте, но быстро выходит из альфы (0.35f для скорости)
            hotbarSlide = 1.0f;
            hotbarAlpha = Mth.lerp(0.35f, hotbarAlpha, 1.0f);
        } else {
            // Уходит плавно вниз (как было)
            hotbarSlide = Mth.lerp(0.12f, hotbarSlide, 0.0f);
            hotbarAlpha = Mth.lerp(0.12f, hotbarAlpha, 0.0f);
        }

        // Анимация прыжка выбранного слота осталась без изменений
        for (int i = 0; i < 9; i++) {
            prevSlotH[i] = slotH[i];
            float targetH = (i == currentSlot) ? 10.0f : 0.0f;
            slotH[i] = Mth.lerp(0.4f, slotH[i], targetH);
        }
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
        if (id.equals(VanillaGuiOverlay.CROSSHAIR.id()) && mc.player != null && !mc.player.isCreative()) {
            event.setCanceled(true);
        }
    }

    /**
     * Рисуем собственное перекрестие со смещением, отражающим разницу между
     * реальным направлением камеры и "отстающим" направлением прицела (CursorState).
     * Ванильное перекрестие для survival отменено в onRenderGuiPre, поэтому
     * рисуем поверх его места в Post-событии того же оверлея.
     */
    @SubscribeEvent
    public void onRenderCrosshair(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isCreative()) return; // в креативе ванильный крестик не отменялся — не дублируем

        float pt = event.getPartialTick();

        float camYaw = Mth.lerp(pt, mc.player.yRotO, mc.player.getYRot());
        float camPitch = Mth.lerp(pt, mc.player.xRotO, mc.player.getXRot());
        float curYaw = CursorState.getInterpolatedYaw(pt);
        float curPitch = CursorState.getInterpolatedPitch(pt);

        float deltaYaw = Mth.wrapDegrees(curYaw - camYaw);
        float deltaPitch = curPitch - camPitch;

        double fov = mc.options.fov().get();
        double halfFovRad = Math.toRadians(fov / 2.0);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Приблизительная проекция угла в пиксели (вертикальный FOV как база для обеих осей;
        // для небольших смещений искажение по краям экрана несущественно).
        double pxPerRadian = (screenHeight / 2.0) / Math.tan(halfFovRad);

        int offsetX = (int) (Math.tan(Math.toRadians(deltaYaw)) * pxPerRadian);
        int offsetY = (int) (-Math.tan(Math.toRadians(deltaPitch)) * pxPerRadian);

        int cx = screenWidth / 2 + offsetX;
        int cy = screenHeight / 2 + offsetY;

        GuiGraphics gui = event.getGuiGraphics();
        int size = 4;
        int thickness = 1;
        int color = 0xCCFFFFFF;

        gui.fill(cx - size, cy - thickness, cx + size, cy + thickness, color);
        gui.fill(cx - thickness, cy - size, cx + thickness, cy + size, color);
    }

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

        // Позиция Y зависит от выезда
        float baseY = (screenHeight + 40) - (renderSlide * 72);

        // Цвет зависит от альфы
        int alpha = (int)(renderAlpha * 200);
        int color = (alpha << 24);

        for (int i = 0; i < 9; i++) {
            int x = startX + i * (slotSize + gap);
            float currentH = Mth.lerp(pt, prevSlotH[i], slotH[i]);
            float y = baseY - currentH;

            // Рисуем фон слота
            gui.fill(x, (int)y, x + slotSize, (int)y + slotSize, color);

            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                gui.renderItem(stack, x + 3, (int)y + 3);
                gui.renderItemDecorations(mc.font, stack, x + 3, (int)y + 3);
            }
        }
    }

    @SubscribeEvent
    public void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        event.setRoll(event.getRoll() + Mth.lerp(pt, prevTilt, currentTilt));
        swayTime += (pt * 0.035f);
        if (fatigue > 0.01f) {
            float amp = fatigue * 0.8f;
            event.setYaw(event.getYaw() + (float)(Math.sin(swayTime) + Math.sin(swayTime * 0.45f)) * amp);
            event.setPitch(event.getPitch() + (float)(Math.cos(swayTime * 0.65f) + Math.cos(swayTime * 0.25f)) * (amp * 0.5f));
        }
    }

    @SubscribeEvent
    public void onFOVUpdate(ViewportEvent.ComputeFov event) {
        Player p = Minecraft.getInstance().player;
        if (p != null && p.isSprinting()) {
            event.setFOV(event.getFOV() + ((float) p.getDeltaMovement().horizontalDistance() * 12.0f));
        }
    }
}