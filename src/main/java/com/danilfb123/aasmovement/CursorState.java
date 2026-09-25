package com.danilfb123.aasmovement;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Хранит направление курсора (крестика) отдельно от реальной камеры.
 * Полностью статический класс — используется как CursorState.tick(...),
 * CursorState.getInterpolatedYaw(...) и т.д., без instance/getInstance().
 *
 * Два набора значений:
 *  - yawTick/pitchTick — "истина" на момент конца тика N. Используется там,
 *    где логика тиковая (стрельба, shootFromRotation) — без интерполяции,
 *    иначе результат станет недетерминированным относительно тика.
 *  - prevYawTick/prevPitchTick — снимок с тика N-1, нужен только для интерполяции.
 *
 * getInterpolatedYaw/Pitch(partialTick) — для всего, что рисуется в рендере
 * (pick() -> hitResult, руки, крестик), чтобы курсор двигался плавно между
 * тиками синхронно с камерой, а не дёргался по тикам.
 */
public class CursorState {

    private static float yawTick;
    private static float pitchTick;
    private static float prevYawTick;
    private static float prevPitchTick;

    private static boolean initialized = false;

    /**
     * Время "довоза" курсора к реальному повороту, в секундах — насколько
     * сильно крестик/руки/прицел отстают от настоящей камеры перед тем как
     * её догнать. ДЛЯ ТЕСТОВ поставлено 3.0f (абсурдно много, чтобы отставание
     * было хорошо видно). Для нормальной игры это будет что-то около 0.1–0.2f.
     */
    private static float delaySeconds = 3.0f;

    /** Тиков в секунду — используется для перевода delaySeconds в коэффициент сглаживания. */
    private static final float TICKS_PER_SECOND = 20.0f;

    /** Счётчик телеметрии — сколько раз EntityPickMixin подменил getViewVector. */
    public static long redirectCalls = 0;

    private CursorState() {
    }

    /**
     * Вызывать один раз в конце каждого тика (ClientTickEvent.END) с
     * АКТУАЛЬНЫМ поворотом игрока (player.getYRot()/getXRot()) — это цель,
     * к которой курсор плавно "довозится", а не значение, которое просто
     * копируется.
     */
    public static void tick(float targetYaw, float targetPitch) {
        if (!initialized) {
            // на первом тике (например, вход в мир) не тащим курсор откуда-то
            // из нуля — сразу ставим его туда же, где реальный поворот.
            yawTick = targetYaw;
            pitchTick = targetPitch;
            prevYawTick = targetYaw;
            prevPitchTick = targetPitch;
            initialized = true;
            return;
        }

        prevYawTick = yawTick;
        prevPitchTick = pitchTick;

        float alpha = tickAlpha();

        // yaw цикличен (переход через ±180°) — двигаемся к цели по кратчайшей
        // дуге, а не по прямой разнице.
        float yawDiff = Mth.wrapDegrees(targetYaw - yawTick);
        yawTick = yawTick + yawDiff * alpha;

        pitchTick = pitchTick + (targetPitch - pitchTick) * alpha;
    }

    /**
     * Доля пути к цели, проходимая за один тик, при текущем delaySeconds.
     * Экспоненциальное сглаживание: alpha = 1 - e^(-1 / (delaySeconds * tps)).
     * При delaySeconds -> 0 alpha -> 1 (мгновенно, как раньше).
     */
    private static float tickAlpha() {
        if (delaySeconds <= 0.0f) {
            return 1.0f;
        }
        return 1.0f - (float) Math.exp(-1.0 / (delaySeconds * TICKS_PER_SECOND));
    }

    /** Позволяет менять задержку в рантайме (например, командой) без пересборки. */
    public static void setDelaySeconds(float seconds) {
        delaySeconds = Math.max(0.0f, seconds);
    }

    public static float getDelaySeconds() {
        return delaySeconds;
    }

    /**
     * Сброс состояния (например, при выходе из мира — ClientPlayerNetworkEvent.LoggingOut),
     * чтобы после захода в новый мир не было скачка интерполяции от старых значений.
     */
    public static void reset() {
        initialized = false;
        yawTick = 0f;
        pitchTick = 0f;
        prevYawTick = 0f;
        prevPitchTick = 0f;
    }

    /** Чисто тиковое значение — для shootFromRotation и прочей тиковой логики. */
    public static float getYawTick() {
        return yawTick;
    }

    public static float getPitchTick() {
        return pitchTick;
    }

    /**
     * Интерполированный yaw с учётом перехода через ±180°.
     * Использовать в рендере (pick(), руки, крестик), partialTick брать из
     * того же места, откуда его берёт ваниль в конкретной точке инжекта.
     */
    public static float getInterpolatedYaw(float partialTick) {
        return rotLerp(partialTick, prevYawTick, yawTick);
    }

    /** Pitch не цикличен в игре, обычный lerp корректен. */
    public static float getInterpolatedPitch(float partialTick) {
        return Mth.lerp(partialTick, prevPitchTick, pitchTick);
    }

    /**
     * Аналог Mth.rotLerp — интерполяция угла с учётом цикличности (кратчайший путь).
     * В новых маппингах может уже называться Mth.rotLerp — если есть, используй
     * ванильный, этот дублирует его логику на случай отсутствия в твоей версии.
     */
    private static float rotLerp(float delta, float start, float end) {
        float diff = Mth.wrapDegrees(end - start);
        return start + delta * diff;
    }

    /**
     * Готовый view-вектор (а не отдельно yaw/pitch) — для мест типа
     * EntityPickMixin, который редиректит Entity#getViewVector(float).
     */
    public static Vec3 getInterpolatedDirection(float partialTick) {
        float yaw = getInterpolatedYaw(partialTick);
        float pitch = getInterpolatedPitch(partialTick);
        return viewVectorFromRotation(yaw, pitch);
    }

    private static Vec3 viewVectorFromRotation(float yaw, float pitch) {
        float f = pitch * ((float) Math.PI / 180F);
        float f1 = -yaw * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3((double) (f3 * f4), (double) (-f5), (double) (f2 * f4));
    }
}