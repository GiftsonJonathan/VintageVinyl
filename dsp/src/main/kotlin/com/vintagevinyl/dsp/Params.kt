package com.vintagevinyl.dsp

/**
 * Every independently controllable effect. Each one has an on/off switch *and* an amount (0..1).
 *
 * For the three "noise" effects ([SURFACE_NOISE], [CRACKLE], [POPS]) an amount of 0.5 means
 * "exactly what the selected [RecordCondition] specifies"; 1.0 is roughly twice as dense/loud
 * and 0.0 is silent. All other effects scale from "nothing" (0) to "strong" (1).
 */
enum class EffectId(val label: String, val group: Group) {
    WARMTH("Vinyl warmth", Group.TONE),
    ROLLOFF("High-frequency roll-off", Group.TONE),
    EQ_PROFILE("Vintage EQ profile", Group.TONE),
    SURFACE_NOISE("Surface noise", Group.SURFACE),
    CRACKLE("Crackle", Group.SURFACE),
    POPS("Pops", Group.SURFACE),
    WOW("Wow", Group.MECHANICS),
    FLUTTER("Flutter", Group.MECHANICS),
    HUM("Mechanical hum & rumble", Group.MECHANICS),
    SATURATION("Saturation", Group.COLOUR),
    TAPE("Tape saturation", Group.COLOUR),
    HARMONICS("Harmonic distortion", Group.COLOUR),
    DYNAMICS("Dynamic range colouring", Group.COLOUR),
    SPEAKER("Speaker coloration", Group.ACOUSTICS),
    RESONANCE("Acoustic resonance", Group.ACOUSTICS);

    enum class Group(val label: String) {
        TONE("Tone"), SURFACE("Record surface"), MECHANICS("Turntable mechanics"),
        COLOUR("Harmonic colour"), ACOUSTICS("Playback acoustics")
    }
}

/**
 * Stereo character. [width] scales the side (L-R) signal; [bassMono] folds low frequencies
 * to mono like the elliptical filter used when cutting real records (and like a narrow
 * single-groove pickup would).
 */
enum class StereoMode(val label: String, val width: Float, val bassMono: Float) {
    FULL("Full stereo", 1.0f, 0.0f),
    VINTAGE("Vintage stereo", 0.78f, 1.0f),
    NARROW("Narrow stereo", 0.38f, 1.0f),
    MONO("Mono", 0.0f, 0.0f)
}

enum class TurntableSpeed(val label: String, val rpm: Double) {
    RPM_33("33⅓ rpm", 100.0 / 3.0),
    RPM_45("45 rpm", 45.0),
    RPM_78("78 rpm", 78.26);

    val revolutionHz: Double get() = rpm / 60.0
}

enum class HumFrequency(val label: String, val hz: Double) {
    HZ_50("50 Hz mains", 50.0),
    HZ_60("60 Hz mains", 60.0)
}

/**
 * Condition of the imaginary record. Drives the *character* of the surface noise; the
 * sliders then scale it. Levels are peak dBFS (clicks) or RMS dBFS (hiss) at slider = 0.5.
 */
enum class RecordCondition(
    val label: String,
    val hissRmsDb: Float,
    val hissHighPassHz: Float,
    val hissLowPassHz: Float,
    val crackleRatePerSec: Float,
    val cracklePeakDb: Float,
    val popRatePerSec: Float,
    val popPeakDb: Float,
    val scratchesPerRevolution: Int,
) {
    MINT("Mint pressing", -70f, 900f, 11000f, 1.5f, -44f, 0.04f, -34f, 0),
    GOOD("Well-kept record", -64f, 800f, 9500f, 6f, -38f, 0.18f, -29f, 0),
    DUSTY("Dusty record", -58f, 700f, 8500f, 24f, -33f, 0.5f, -26f, 0),
    WORN("Worn record", -52f, 600f, 7000f, 60f, -29f, 1.0f, -22f, 2),
    SHELLAC_78("Shellac 78", -46f, 350f, 5200f, 95f, -25f, 1.6f, -19f, 3)
}

/** Parameters of one creative EQ voicing. Not a claim of exact historical equipment. */
data class EqProfileSpec(
    val hpHz: Float, val hpQ: Float,
    val lpHz: Float, val lpQ: Float,
    val lowShelfHz: Float, val lowShelfDb: Float,
    val pk1Hz: Float, val pk1Db: Float, val pk1Q: Float,
    val pk2Hz: Float, val pk2Db: Float, val pk2Q: Float,
    val highShelfHz: Float, val highShelfDb: Float,
    val makeupDb: Float,
)

enum class EqProfile(val label: String, val blurb: String, val spec: EqProfileSpec) {
    WARM_VINYL(
        "Warm vinyl", "Gentle bass bloom, softened highs",
        EqProfileSpec(28f, .707f, 15000f, .707f, 160f, 3.0f, 3200f, -1.5f, .8f, 1000f, .5f, .7f, 8000f, -3.5f, 0f)
    ),
    VINTAGE_RADIO(
        "Vintage radio", "Band-limited, nasal midrange",
        EqProfileSpec(320f, .9f, 4800f, .9f, 250f, 0f, 1100f, 4.0f, 1.0f, 2600f, 2.5f, 1.2f, 4000f, -2.0f, 3f)
    ),
    ANTIQUE_GRAMOPHONE(
        "Antique gramophone", "Horn honk, narrow bandwidth",
        EqProfileSpec(180f, .8f, 3600f, 1.1f, 300f, -1.0f, 950f, 5.5f, 1.6f, 2100f, 3.5f, 2.0f, 3000f, -3.0f, 4f)
    ),
    RECORD_PLAYER_1950S(
        "1950s record player", "Small-cabinet lift, rolled-off top",
        EqProfileSpec(70f, .7f, 8500f, .8f, 140f, 2.0f, 2800f, 2.5f, 1.0f, 450f, 1.5f, .9f, 6000f, -2.5f, 1.5f)
    ),
    HIFI_1960S(
        "1960s hi-fi turntable", "Full range with a warm tilt",
        EqProfileSpec(22f, .707f, 17000f, .707f, 110f, 2.0f, 2500f, -1.0f, .7f, 400f, .5f, .8f, 9000f, -1.5f, 0f)
    ),
    ANALOG_1970S(
        "1970s analogue stereo", "Punchy low end, airy presence",
        EqProfileSpec(20f, .707f, 18500f, .707f, 90f, 3.0f, 3000f, 1.5f, .8f, 250f, -1.0f, .9f, 8500f, 1.0f, 0f)
    )
}

/**
 * Complete, immutable snapshot of the vintage processing state. The UI edits copies of this,
 * the audio thread reads a volatile reference to the latest one.
 */
data class VintageSettings(
    val masterEnabled: Boolean = true,
    val amounts: Map<EffectId, Float> = emptyMap(),
    val enabled: Set<EffectId> = EffectId.values().toSet(),
    val eqProfile: EqProfile = EqProfile.WARM_VINYL,
    val stereoMode: StereoMode = StereoMode.FULL,
    val recordCondition: RecordCondition = RecordCondition.GOOD,
    val turntableSpeed: TurntableSpeed = TurntableSpeed.RPM_33,
    val humFrequency: HumFrequency = HumFrequency.HZ_50,
) {
    /** Effective amount: 0 when the effect is switched off. */
    fun amount(id: EffectId): Float = if (id in enabled) (amounts[id] ?: 0f).coerceIn(0f, 1f) else 0f

    /** Slider value regardless of the switch. */
    fun rawAmount(id: EffectId): Float = (amounts[id] ?: 0f).coerceIn(0f, 1f)

    fun isEnabled(id: EffectId): Boolean = id in enabled

    fun withAmount(id: EffectId, value: Float): VintageSettings =
        copy(amounts = amounts + (id to value.coerceIn(0f, 1f)))

    fun withEnabled(id: EffectId, on: Boolean): VintageSettings =
        copy(enabled = if (on) enabled + id else enabled - id)

    companion object {
        val DEFAULT: VintageSettings get() = VintagePresets.CLEAN_VINYL.settings
    }
}
