package com.danilfb123.aasmovement;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Хранит "виртуальное" направление прицела, которое с задержкой следует за
 * реальным направлением камеры игрока (реальными yaw/pitch).
 *
 * Используется:
 *  - MovementHandler#onRenderCrosshair — чтобы нарисовать смещённое перекрестие;
 *  - MinecraftMixin — чтобы реальный рейкаст (наведение/ломание/взаимодействие/атака)
 *    шёл именно по этому, отстающему, направлению, а не по направлению реальной камеры.
 */
public class CursorState {

    // Насколько быстро прицел "догоняет" камеру за тик.
    // Меньше значение -> больше ощущаемая задержка.
    // ВРЕМЕННО выставлено ОЧЕНЬ маленькое значение для теста — прицел
    // будет догонять камеру секунды 2-3, эффект должен быть невозможно не заметить.
    public static float catchUpSpeed = 0.02f;

    public static float cursorYaw = 0.0f;
    public static float cursorPitch = 0.0f;
    public static float prevCursorYaw = 0.0f;
    public static float prevCursorPitch = 0.0f;

    private static boolean initialized = false;

    private static final Logger LOGGER = LogManager.getLogger("AASMovement/CursorState");
    private static int debugTickCounter = 0;

    /** Вызывать раз в тик из MovementHandler с реальными углами игрока. */
    public static void tick(float realYaw, float realPitch) {
        prevCursorYaw = cursorYaw;
        prevCursorPitch = cursorPitch;

        // DEBUG: раз в секунду печатаем в лог, что метод вообще вызывается,
        // и насколько сильно cursorYaw отстаёт от realYaw. Если этих строк
        // нет в логе (latest.log) вообще — значит onClientTick/CursorState.tick
        // не вызывается (проблема в регистрации ивента), а не в миксине.
        debugTickCounter++;
        if (debugTickCounter % 20 == 0) {
            LOGGER.info("[AASMovement DEBUG] realYaw={}, cursorYaw={}, delta={}",
                    realYaw, cursorYaw, Mth.wrapDegrees(realYaw - cursorYaw));
        }

        if (!initialized) {
            // Первый тик после захода в мир/респавна — не даём прицелу "лететь" издалека.
            cursorYaw = realYaw;
            cursorPitch = realPitch;
            prevCursorYaw = realYaw;
            prevCursorPitch = realPitch;
            initialized = true;
            return;
        }

        cursorYaw = lerpAngle(catchUpSpeed, cursorYaw, realYaw);
        cursorPitch = Mth.lerp(catchUpSpeed, cursorPitch, realPitch);
    }

    /** Сбросить состояние (например, при выходе из мира), чтобы не тянуть старые углы. */
    public static void reset() {
        initialized = false;
    }

    private static float lerpAngle(float factor, float current, float target) {
        float delta = Mth.wrapDegrees(target - current);
        return current + delta * factor;
    }

    public static float getInterpolatedYaw(float partialTick) {
        return Mth.lerp(partialTick, prevCursorYaw, cursorYaw);
    }

    public static float getInterpolatedPitch(float partialTick) {
        return Mth.lerp(partialTick, prevCursorPitch, cursorPitch);
    }

    /** Интерполированное между тиками направление взгляда "прицела" (для рейкаста). */
    public static Vec3 getInterpolatedDirection(float partialTick) {
        float yaw = getInterpolatedYaw(partialTick);
        float pitch = getInterpolatedPitch(partialTick);
        return calculateViewVector(pitch, yaw);
    }

    // Копия формулы Entity#calculateViewVector — переводит yaw/pitch в единичный вектор направления.
    private static Vec3 calculateViewVector(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180F);
        float f1 = -yaw * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3((double) (f3 * f4), (double) (-f5), (double) (f2 * f4));
    }
}