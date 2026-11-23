package com.cleanhome.tv;

import androidx.core.graphics.ColorUtils;

/**
 * Complete 24-Hour Living World schedule (00:00..23:00) for Fox Meadow.
 * Provides minute-by-minute 7-color terrain/sky interpolation, collision-free Sun & Moon arcs
 * (guaranteed clear of the center 33%-height clock digits), hourly ground prop states,
 * hourly fox poses/stories, and hourly synchronized World + Fox OK reactions.
 * Zero allocations during onDraw().
 */
final class FoxHourlySchedule {

    enum PropMode {
        CAMPFIRE_EMBERS, // 00..04: calm low glowing logs/coals + sparse embers (dark-room safe)
        MORNING_DEW,     // 05..09: rising golden dew lights & fresh dawn meadow
        WILDFLOWERS,     // 10..16: sunny meadow wildflowers & drifting seeds
        LANTERN_WARMING, // 17: lantern stands ready with dim amber glow + early fireflies
        LANTERN_GLOW,    // 18..19: rich warm glowing lantern + meandering fireflies
        CAMPFIRE_BRIGHT  // 20..23: cozy crackling 3-layer campfire + warm ground pool
    }

    enum FoxPose {
        CURLED_SLEEP,    // 22..04: curled sleeping ball facing campfire/embers
        WAKING_STRETCH,  // 05: upright waking stretch & dawn yawn
        SITTING_ALERT,   // 06..16: upright cheerful/curious sitting fox
        SITTING_DROWSY   // 17..21: softer half-lidded evening / fireside sitting fox
    }

    // 24 hourly palettes: {skyTop, skyMid, skyBot, farHill, midHill, pineMound, foreSoil}
    private static final int[][] PALETTE = {
        /* 00 */ {0xff070a15, 0xff0d1529, 0xff16223c, 0xff1d2944, 0xff162138, 0xff0e1728, 0xff131e2c},
        /* 01 */ {0xff060913, 0xff0c1428, 0xff15223d, 0xff1c2946, 0xff152139, 0xff0d1627, 0xff121d2b},
        /* 02 */ {0xff070b16, 0xff0e162b, 0xff172440, 0xff1f2c49, 0xff18243c, 0xff0f182a, 0xff131f2e},
        /* 03 */ {0xff080b18, 0xff111930, 0xff1b2847, 0xff243150, 0xff1c2842, 0xff121c30, 0xff162232},
        /* 04 */ {0xff14162c, 0xff292748, 0xff463b5e, 0xff3b3654, 0xff2c2944, 0xff1e2035, 0xff1f2734},
        /* 05 */ {0xff2b335c, 0xff78556c, 0xffd28d78, 0xff7c5e72, 0xff544960, 0xff3b3b4e, 0xff333d3a},
        /* 06 */ {0xff394975, 0xffad7374, 0xfff0b284, 0xffa67f89, 0xff70657b, 0xff515562, 0xff445340},
        /* 07 */ {0xff3d4e7a, 0xffb97e78, 0xfff5c08e, 0xffb28e95, 0xff786f83, 0xff585e68, 0xff495943},
        /* 08 */ {0xff386192, 0xff9394a2, 0xfff2cfa5, 0xff9fa0a4, 0xff6e7b84, 0xff4c6266, 0xff49664c},
        /* 09 */ {0xff30689c, 0xff73a0be, 0xffd6e0dc, 0xff8aa6ad, 0xff618386, 0xff406466, 0xff486d54},
        /* 10 */ {0xff2b699c, 0xff5fa0c6, 0xffb8dce8, 0xff7da0ab, 0xff588284, 0xff386264, 0xff477359},
        /* 11 */ {0xff296798, 0xff549bc2, 0xffa8d5e5, 0xff759aa6, 0xff537d7f, 0xff355e5f, 0xff467359},
        /* 12 */ {0xff245f92, 0xff4d96bf, 0xffa1d2e3, 0xff7096a4, 0xff4f7b7e, 0xff325b5d, 0xff447257},
        /* 13 */ {0xff2c699a, 0xff589ec4, 0xffa9d6e5, 0xff779ca7, 0xff557f80, 0xff365f60, 0xff47735a},
        /* 14 */ {0xff316b98, 0xff65a2c2, 0xffbadce3, 0xff81a1a8, 0xff5d827f, 0xff3c6360, 0xff4c7558},
        /* 15 */ {0xff3a6a91, 0xff7c9eb5, 0xffd4d9cf, 0xff92a0a0, 0xff6a807a, 0xff47625d, 0xff527051},
        /* 16 */ {0xff435880, 0xffa0828b, 0xffeec195, 0xffa88d8f, 0xff786e77, 0xff53575f, 0xff576449},
        /* 17 */ {0xff37365e, 0xffa65c67, 0xfff09f65, 0xffa57678, 0xff745d6c, 0xff4d4759, 0xff524d47},
        /* 18 */ {0xff262044, 0xff8e435a, 0xffeb8a52, 0xff9b6872, 0xff695366, 0xff443e53, 0xff493e47},
        /* 19 */ {0xff191936, 0xff593252, 0xffb36052, 0xff6c4b5d, 0xff4a3b52, 0xff312d42, 0xff322e3d},
        /* 20 */ {0xff10142a, 0xff282444, 0xff4d3b54, 0xff3b3854, 0xff2b3048, 0xff1c243a, 0xff212a3a},
        /* 21 */ {0xff0c1022, 0xff1a213b, 0xff2b3352, 0xff2d3856, 0xff222f4a, 0xff162238, 0xff1a293a},
        /* 22 */ {0xff090d1a, 0xff141d36, 0xff212e4c, 0xff283552, 0xff202e47, 0xff142137, 0xff182838},
        /* 23 */ {0xff080b18, 0xff111930, 0xff1c2946, 0xff24314e, 0xff1c2a42, 0xff121e33, 0xff162434}
    };

    // Collision-free Sun arc anchors on 960x540 artboard for hours 5..19 (clears clock box x:280..680, y:112..255)
    private static final float[] SUN_X = {145, 158, 176, 196, 228, 305, 395, 480, 565, 655, 732, 768, 790, 808, 822};
    private static final float[] SUN_Y = {368, 344, 305, 224, 126,  84,  64,  56,  64,  84, 126, 214, 298, 346, 376};

    // Collision-free Moon arc anchors on 960x540 artboard for hours 19..24..05 (index 0=19:00 .. 10=05:00)
    private static final float[] MOON_X = {152, 164, 188, 250, 355, 480, 605, 710, 772, 796, 808};
    private static final float[] MOON_Y = {240, 178, 120,  86,  66,  58,  66,  86, 120, 178, 245};

    static final class State {
        int hour;
        int minute;
        float hourFrac;
        final int[] colors = new int[7];

        boolean showSun;
        float sunX, sunY, sunAlpha, sunRadius;
        boolean showMoon;
        float moonX, moonY, moonAlpha;

        float starAlpha;        // 0..1
        float cloudAlpha;       // 0..1
        int cloudCount;         // 2..4
        float cloudSpeed;       // breeze multiplier
        float fireflyAlpha;     // 0..1
        float emberIntensity;   // 0..1 (higher 20..23, calm low 00..04)
        boolean darkRoomSafe;
        boolean brightSkyDate;
        PropMode propMode;
        FoxPose foxPose;
    }

    static void evaluate(int hour, int minute, State out) {
        hour = Math.floorMod(hour, 24);
        minute = Math.max(0, Math.min(59, minute));
        float frac = minute / 60f;
        float smooth = frac * frac * (3f - 2f * frac);
        int nextHour = (hour + 1) % 24;

        out.hour = hour;
        out.minute = minute;
        out.hourFrac = hour + frac;

        for (int i = 0; i < 7; i++) {
            out.colors[i] = ColorUtils.blendARGB(PALETTE[hour][i], PALETTE[nextHour][i], smooth);
        }

        // Sun arc (05:00 .. 19:00)
        if (out.hourFrac >= 5f && out.hourFrac <= 19f) {
            float pos = out.hourFrac - 5f;
            int idx = Math.min(13, (int) pos);
            float t = pos - idx;
            out.showSun = true;
            out.sunX = lerp(SUN_X[idx], SUN_X[idx + 1], t);
            out.sunY = lerp(SUN_Y[idx], SUN_Y[idx + 1], t);
            float edgeFade = Math.min(clamp01((out.hourFrac - 5f) / 0.8f), clamp01((19f - out.hourFrac) / 0.8f));
            out.sunAlpha = edgeFade;
            float horizonWarmth = 1f - clamp01((340f - out.sunY) / 240f);
            out.sunRadius = lerp(22f, 34f, horizonWarmth);
        } else {
            out.showSun = false;
            out.sunAlpha = 0f;
        }

        // Moon arc (19:00 .. 05:00)
        float nightPos = out.hourFrac >= 19f ? out.hourFrac - 19f : (out.hourFrac <= 5f ? out.hourFrac + 5f : -1f);
        if (nightPos >= 0f && nightPos <= 10f) {
            int idx = Math.min(9, (int) nightPos);
            float t = nightPos - idx;
            out.showMoon = true;
            out.moonX = lerp(MOON_X[idx], MOON_X[idx + 1], t);
            out.moonY = lerp(MOON_Y[idx], MOON_Y[idx + 1], t);
            out.moonAlpha = Math.min(clamp01(nightPos / 1.0f), clamp01((10f - nightPos) / 1.0f));
        } else {
            out.showMoon = false;
            out.moonAlpha = 0f;
        }

        // Stars smoothly emerge at 19:00 and fade by 05:30
        if (out.hourFrac >= 20f || out.hourFrac <= 4f) {
            out.starAlpha = 1f;
        } else if (out.hourFrac > 18.5f) {
            out.starAlpha = clamp01((out.hourFrac - 18.5f) / 1.5f);
        } else if (out.hourFrac < 5.5f) {
            out.starAlpha = clamp01((5.5f - out.hourFrac) / 1.5f);
        } else {
            out.starAlpha = 0f;
        }

        out.darkRoomSafe = hour >= 20 || hour <= 4;
        out.brightSkyDate = hour >= 5 && hour <= 16;
        out.cloudAlpha = out.darkRoomSafe ? 0.14f : (hour >= 17 && hour <= 19 ? 0.72f : 0.95f);
        out.cloudCount = (hour >= 11 && hour <= 15) ? 4 : (out.darkRoomSafe ? 2 : 3);
        out.cloudSpeed = (hour == 15 || hour == 8) ? 1.55f : (out.darkRoomSafe ? 0.55f : 1.0f);
        out.fireflyAlpha = hour == 17 ? 0.45f : (hour == 18 || hour == 19 ? 1.0f : 0f);
        out.emberIntensity = (hour >= 20 && hour <= 23) ? 1.0f : (hour <= 4 ? 0.48f : 0f);

        if (hour <= 4) out.propMode = PropMode.CAMPFIRE_EMBERS;
        else if (hour <= 9) out.propMode = PropMode.MORNING_DEW;
        else if (hour <= 16) out.propMode = PropMode.WILDFLOWERS;
        else if (hour == 17) out.propMode = PropMode.LANTERN_WARMING;
        else if (hour <= 19) out.propMode = PropMode.LANTERN_GLOW;
        else out.propMode = PropMode.CAMPFIRE_BRIGHT;

        if (hour >= 22 || hour <= 4) out.foxPose = FoxPose.CURLED_SLEEP;
        else if (hour == 5) out.foxPose = FoxPose.WAKING_STRETCH;
        else if (hour <= 16) out.foxPose = FoxPose.SITTING_ALERT;
        else out.foxPose = FoxPose.SITTING_DROWSY;
    }

    static boolean usesFirelightRim(int hour) {
        return hour >= 20 || hour <= 4;
    }

    static boolean usesMoonlightRim(int hour) {
        return hour >= 20 || hour <= 4;
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
