package com.wpreborn.services.music

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** A preset curve, as gains in dB at the reference frequencies [EqPreset.FREQS]. */
enum class EqPreset(val id: String, val gains: FloatArray) {
    Flat("flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Pop("pop", floatArrayOf(-1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -1f)),
    Rock("rock", floatArrayOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
    Jazz("jazz", floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    Classical("classical", floatArrayOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 2f, 3f, 4f)),
    Electronic("electronic", floatArrayOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 1f, 4f, 5f)),
    BassBoost("bass", floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
    Vocal("vocal", floatArrayOf(-2f, -3f, -2f, 1f, 4f, 5f, 4f, 2f, 0f, -1f)),
    ;

    /** The preset's gain at [hz], interpolated on a log scale between reference points. */
    fun gainAt(hz: Float): Float {
        if (hz <= FREQS.first()) return gains.first()
        if (hz >= FREQS.last()) return gains.last()
        val i = FREQS.indexOfLast { it <= hz }
        val t = (ln(hz) - ln(FREQS[i])) / (ln(FREQS[i + 1]) - ln(FREQS[i]))
        return gains[i] + (gains[i + 1] - gains[i]) * t
    }

    companion object {
        val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** What the user set: on/off, the band gains in dB (one per device band), the preset and the pre-amp. */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: String? = EqPreset.Flat.id,
    val bands: List<Float> = emptyList(),
    val preampDb: Float = 0f,
)

/** The device's equalizer: how many bands, at which frequencies, and the gain range in dB. */
data class EqDevice(val centersHz: List<Float>, val minDb: Float, val maxDb: Float)

/**
 * Real audio processing for ZUNEX Music: Android's [Equalizer] and [LoudnessEnhancer] are
 * attached to the player's audio session (one process-wide instance, shared by the service
 * and the equalizer screen). Settings are saved and re-applied whenever playback restarts.
 */
object AudioEffects {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId = 0
    private var prefs: android.content.SharedPreferences? = null

    private val _settings = MutableStateFlow(EqSettings())
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()

    private val _device = MutableStateFlow<EqDevice?>(null)
    /** Null until a player exists, or when the device has no equalizer. */
    val device: StateFlow<EqDevice?> = _device.asStateFlow()

    private val _supported = MutableStateFlow(true)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    /** The audio session to visualise (0 when nothing is attached). */
    val audioSession get() = sessionId

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
        prefs = p
        _settings.value = runCatching {
            val o = JSONObject(p.getString("settings", "{}") ?: "{}")
            val arr = o.optJSONArray("bands") ?: JSONArray()
            EqSettings(
                enabled = o.optBoolean("enabled", false),
                preset = if (o.has("preset") && !o.isNull("preset")) o.getString("preset") else EqPreset.Flat.id,
                bands = List(arr.length()) { arr.getDouble(it).toFloat() },
                preampDb = o.optDouble("preamp", 0.0).toFloat(),
            )
        }.getOrDefault(EqSettings())
    }

    fun attach(context: Context, audioSessionId: Int) {
        load(context)
        if (audioSessionId == 0 || audioSessionId == sessionId && equalizer != null) return
        releaseEffects()
        sessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            val range = eq.bandLevelRange
            _device.value = EqDevice(
                centersHz = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000f },
                minDb = range[0] / 100f,
                maxDb = range[1] / 100f,
            )
            _supported.value = true
        } catch (_: Exception) {
            equalizer = null
            _device.value = null
            _supported.value = false
        }
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        // Fill in band gains from the preset the first time we learn the band layout.
        val s = _settings.value
        val dev = _device.value
        if (dev != null && s.bands.size != dev.centersHz.size) {
            _settings.value = s.copy(bands = bandsFor(EqPreset.byId(s.preset) ?: EqPreset.Flat, dev))
        }
        apply()
    }

    fun release() {
        releaseEffects()
        sessionId = 0
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
    }

    fun bandsFor(preset: EqPreset, device: EqDevice) = device.centersHz.map { preset.gainAt(it).coerceIn(device.minDb, device.maxDb) }

    fun setEnabled(on: Boolean) = update(_settings.value.copy(enabled = on))

    fun setPreset(preset: EqPreset) {
        val dev = _device.value ?: return update(_settings.value.copy(preset = preset.id))
        update(_settings.value.copy(preset = preset.id, bands = bandsFor(preset, dev), enabled = true))
    }

    /** A manual change of one band turns the preset into "custom". */
    fun setBand(index: Int, db: Float) {
        val s = _settings.value
        val bands = s.bands.toMutableList()
        if (index !in bands.indices) return
        bands[index] = db
        update(s.copy(bands = bands, preset = null, enabled = true))
    }

    fun setPreamp(db: Float) = update(_settings.value.copy(preampDb = db))

    private fun update(s: EqSettings) {
        _settings.value = s
        apply()
        prefs?.edit()?.putString(
            "settings",
            JSONObject()
                .put("enabled", s.enabled)
                .put("preset", s.preset ?: JSONObject.NULL)
                .put("bands", JSONArray(s.bands.map { it.toDouble() }))
                .put("preamp", s.preampDb.toDouble())
                .toString(),
        )?.apply()
    }

    /** Pushes the settings into the audio effects. A negative pre-amp lowers every band. */
    private fun apply() {
        val s = _settings.value
        val eq = equalizer
        val dev = _device.value
        runCatching {
            if (eq != null && dev != null) {
                eq.setEnabled(s.enabled)
                val cut = if (s.preampDb < 0) s.preampDb else 0f
                s.bands.forEachIndexed { i, db ->
                    if (i < eq.numberOfBands) {
                        val level = ((db + cut).coerceIn(dev.minDb, dev.maxDb) * 100).roundToInt()
                        eq.setBandLevel(i.toShort(), level.toShort())
                    }
                }
            }
        }
        runCatching {
            loudness?.let { le ->
                val boost = if (s.enabled && s.preampDb > 0) s.preampDb else 0f
                le.setTargetGain((boost * 100).roundToInt())
                le.setEnabled(abs(boost) > 0.01f)
            }
        }
    }
}
