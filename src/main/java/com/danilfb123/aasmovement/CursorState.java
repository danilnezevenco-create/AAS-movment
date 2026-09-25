package com.danilfb123.aasmovement;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * "Виртуальное" направление прицела, отстающее от камеры.
 *
 * ВЕРСИЯ 2: добавлена телеметрия миксинов. Редиректы в миксинах
 * инкрементируют redirectCalls; дебаг-строка раз в секунду печатает
 * mixin=ALIVE / mixin=DEAD — сразу видно, внедрились миксины или нет.
 * Механика при этом работает в любом случае: hitResult переписывается
 * событиями в MovementHandler (overrideHitResult).
 */
public class CursorState {

    // 0.02f — ТЕСТОВОЕ значение (лаг 2-3 секунды). Для игры: 0.25f-0.5f.
    public static float catchUpSpeed = 0.02f;

    public static float cursorYaw = 0.0f;
    public static float cursorPitch = 0.0f;
    public static float prevCursorYaw = 0.0f;
    public static float prevCursorPitch = 0.0f;

    // Счётчик вызовов редиректов из миксинов (см. EntityPickMixin и
    // GameRendererPickMixin). Растёт -> миксины живы и дают
    // покадровую точность рейкаста. Стоит на месте -> миксины не
    // внедрились, работает событийный override (тоже ок).
    public static long redirectCalls = 0;

    private static boolean initialized = false;
    private static long lastLoggedRedirects = 0;

    private static final Logger LOGGER = LogManager.getLogger("AASMovement/CursorState");
    private static int debugTickCounter = 0;

    public static void tick(float realYaw, float realPitch) {
        prevCursorYaw = cursorYaw;
        prevCursorPitch = cursorPitch;

        debugTickCounter++;
        if (debugTickCounter % 20 == 0) {
            boolean alive = redirectCalls > lastLoggedRedirects;
            lastLoggedRedirects = redirectCalls;
            LOGGER.info("[AASMovement DEBUG] realYaw={}, cursorYaw={}, delta={}, mixin={}",
                    realYaw, cursorYaw,
                    Mth.wrapDegrees(realYaw - cursorYaw),
                    alive ? "ALIVE (" + redirectCalls + ")" : "DEAD");
        }

        if (!initialized) {
            cursorYaw = realYaw;
            cursorPitch = realPitch;
            prevCursorYaw = realYaw;
            prevCursorPitch = realPitch;
            initialized = true;
            return;
        }

        cursorYaw += Mth.wrapDegrees(realYaw - cursorYaw) * catchUpSpeed;
        cursorPitch = Mth.lerp(catchUpSpeed, cursorPitch,
                Mth.clamp(realPitch, -90.0f, 90.0f));
    }

    public static void reset() {
        initialized = false;
        debugTickCounter = 0;
        redirectCalls = 0;
        lastLoggedRedirects = 0;
    }

    // Интерполяция по кратчайшей дуге (фикс кувырка на +/-180)
    public static float getInterpolatedYaw(float partialTick) {
        return prevCursorYaw
                + Mth.wrapDegrees(cursorYaw - prevCursorYaw) * partialTick;
    }

    public static float getInterpolatedPitch(float partialTick) {
        return Mth.lerp(partialTick, prevCursorPitch, cursorPitch);
    }

    public static Vec3 getInterpolatedDirection(float partialTick) {
        return calculateViewVector(
                getInterpolatedPitch(partialTick),
                getInterpolatedYaw(partialTick));
    }

    // Копия формулы Entity#calculateViewVector
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