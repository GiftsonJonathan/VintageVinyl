package com.vintagevinyl.dsp

/** A named, selectable sound profile. Built-in presets are creative voicings, not emulations. */
data class VintagePreset(
    val id: String,
    val name: String,
    val description: String,
    val settings: VintageSettings,
    val builtIn: Boolean = true,
)

object VintagePresets {

    private fun make(
        eq: EqProfile,
        condition: RecordCondition,
        stereo: StereoMode,
        speed: TurntableSpeed = TurntableSpeed.RPM_33,
        warmth: Float, rolloff: Float, eqAmt: Float,
        noise: Float, crackle: Float, pops: Float,
        wow: Float, flutter: Float, hum: Float,
        sat: Float, tape: Float, harm: Float, dyn: Float,
        speaker: Float, reson: Float,
    ): VintageSettings {
        val amounts = mapOf(
            EffectId.WARMTH to warmth, EffectId.ROLLOFF to rolloff, EffectId.EQ_PROFILE to eqAmt,
            EffectId.SURFACE_NOISE to noise, EffectId.CRACKLE to crackle, EffectId.POPS to pops,
            EffectId.WOW to wow, EffectId.FLUTTER to flutter, EffectId.HUM to hum,
            EffectId.SATURATION to sat, EffectId.TAPE to tape, EffectId.HARMONICS to harm,
            EffectId.DYNAMICS to dyn, EffectId.SPEAKER to speaker, EffectId.RESONANCE to reson,
        )
        return VintageSettings(
            masterEnabled = true,
            amounts = amounts,
            enabled = amounts.filterValues { it > 0f }.keys,
            eqProfile = eq,
            stereoMode = stereo,
            recordCondition = condition,
            turntableSpeed = speed,
        )
    }

    val CLEAN_VINYL = VintagePreset(
        "clean_vinyl", "Clean Vinyl",
        "A well-kept record on a good deck: warm, soft, barely any surface noise.",
        make(
            EqProfile.WARM_VINYL, RecordCondition.MINT, StereoMode.FULL,
            warmth = .35f, rolloff = .15f, eqAmt = .35f,
            noise = .30f, crackle = .30f, pops = .15f,
            wow = .12f, flutter = .10f, hum = 0f,
            sat = .15f, tape = 0f, harm = .10f, dyn = .10f,
            speaker = 0f, reson = 0f,
        )
    )

    val DUSTY_VINYL = VintagePreset(
        "dusty_vinyl", "Dusty Vinyl",
        "A well-loved record from the back of the shelf: steady crackle and the odd pop.",
        make(
            EqProfile.WARM_VINYL, RecordCondition.DUSTY, StereoMode.VINTAGE,
            warmth = .50f, rolloff = .30f, eqAmt = .55f,
            noise = .50f, crackle = .60f, pops = .45f,
            wow = .25f, flutter = .20f, hum = .10f,
            sat = .30f, tape = .10f, harm = .15f, dyn = .25f,
            speaker = 0f, reson = .10f,
        )
    )

    val OLD_GRAMOPHONE = VintagePreset(
        "old_gramophone", "Old Gramophone",
        "Wind-up horn gramophone and a worn shellac 78: narrow, honking, wobbling, mono.",
        make(
            EqProfile.ANTIQUE_GRAMOPHONE, RecordCondition.SHELLAC_78, StereoMode.MONO,
            speed = TurntableSpeed.RPM_78,
            warmth = .60f, rolloff = .60f, eqAmt = 1f,
            noise = .70f, crackle = .70f, pops = .50f,
            wow = .55f, flutter = .45f, hum = .25f,
            sat = .45f, tape = 0f, harm = .30f, dyn = .60f,
            speaker = .90f, reson = .70f,
        )
    )

    val ANTIQUE_RADIO = VintagePreset(
        "antique_radio", "Antique Radio",
        "A valve table radio from the living-room corner: band-limited, buzzy, compressed.",
        make(
            EqProfile.VINTAGE_RADIO, RecordCondition.GOOD, StereoMode.MONO,
            warmth = .30f, rolloff = .50f, eqAmt = 1f,
            noise = .55f, crackle = .20f, pops = .10f,
            wow = .20f, flutter = .30f, hum = .50f,
            sat = .50f, tape = .20f, harm = .35f, dyn = .70f,
            speaker = .85f, reson = .50f,
        )
    )

    val VINTAGE_1950S = VintagePreset(
        "vintage_1950s", "1950s Vintage",
        "Portable record player in a small cabinet: mono, lifted mids, rolled-off treble.",
        make(
            EqProfile.RECORD_PLAYER_1950S, RecordCondition.DUSTY, StereoMode.MONO,
            speed = TurntableSpeed.RPM_45,
            warmth = .55f, rolloff = .45f, eqAmt = .90f,
            noise = .45f, crackle = .45f, pops = .30f,
            wow = .30f, flutter = .25f, hum = .20f,
            sat = .35f, tape = .15f, harm = .20f, dyn = .45f,
            speaker = .50f, reson = .30f,
        )
    )

    val WARMTH_1960S = VintagePreset(
        "warmth_1960s", "1960s Warmth",
        "A tube-amp hi-fi: rich, round, gently narrowed stereo, light tape-style glow.",
        make(
            EqProfile.HIFI_1960S, RecordCondition.GOOD, StereoMode.VINTAGE,
            warmth = .60f, rolloff = .25f, eqAmt = .70f,
            noise = .30f, crackle = .30f, pops = .20f,
            wow = .15f, flutter = .12f, hum = .05f,
            sat = .30f, tape = .35f, harm = .15f, dyn = .30f,
            speaker = .15f, reson = .10f,
        )
    )

    val ANALOG_1970S = VintagePreset(
        "analog_1970s", "1970s Analog",
        "Direct-drive deck and big stereo: punchy bass, open top, analogue saturation.",
        make(
            EqProfile.ANALOG_1970S, RecordCondition.GOOD, StereoMode.FULL,
            warmth = .45f, rolloff = .15f, eqAmt = .80f,
            noise = .30f, crackle = .25f, pops = .15f,
            wow = .10f, flutter = .10f, hum = 0f,
            sat = .35f, tape = .50f, harm = .20f, dyn = .35f,
            speaker = 0f, reson = 0f,
        )
    )

    val DEEP_NOSTALGIA = VintagePreset(
        "deep_nostalgia", "Deep Nostalgia",
        "Everything turned up: a worn record, a tired deck, a faraway sound.",
        make(
            EqProfile.RECORD_PLAYER_1950S, RecordCondition.WORN, StereoMode.NARROW,
            warmth = .80f, rolloff = .55f, eqAmt = .80f,
            noise = .65f, crackle = .70f, pops = .50f,
            wow = .50f, flutter = .35f, hum = .30f,
            sat = .50f, tape = .40f, harm = .30f, dyn = .50f,
            speaker = .60f, reson = .50f,
        )
    )

    val ALL: List<VintagePreset> = listOf(
        CLEAN_VINYL, DUSTY_VINYL, OLD_GRAMOPHONE, ANTIQUE_RADIO,
        VINTAGE_1950S, WARMTH_1960S, ANALOG_1970S, DEEP_NOSTALGIA,
    )

    fun byId(id: String): VintagePreset? = ALL.firstOrNull { it.id == id }
}
