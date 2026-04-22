package com.onethreethreeeight.tflite

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

object WakewordEngine {
    private var interpreter: Interpreter? = null
    private var listening = false
    private var sensitivity = 0.5f
    private var job: Job? = null
    private var modelInputLength = 16000
    private var sampleRateHz = 16000
    private var preprocessingMode: String = "auto" // options: "auto", "none", "logmel", "mfcc"

    fun setPreprocessingMode(mode: String) {
        val m = mode.lowercase()
        preprocessingMode = when (m) {
            "none", "auto", "logmel", "mfcc" -> m
            else -> "auto"
        }
        Log.d("WakewordEngine", "Preprocessing mode set to $preprocessingMode")
    }

    fun initModel(assetManager: AssetManager?, modelPath: String) {
        if (assetManager == null) return
        try {
            val fd = assetManager.openFd(modelPath)
            val inputStream = FileInputStream(fd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fd.startOffset
            val declaredLength = fd.declaredLength
            val mappedModel: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(mappedModel)
            // try to infer model input length
            try {
                val shape = interpreter?.getInputTensor(0)?.shape()
                if (shape != null) {
                    if (shape.size >= 2) {
                        modelInputLength = shape[1]
                    } else if (shape.size == 1) {
                        modelInputLength = shape[0]
                    }
                }
            } catch (e: Exception) {
                Log.w("WakewordEngine", "Could not determine model input shape, using default: $modelInputLength")
            }
            Log.d("WakewordEngine", "Model loaded, inputLength=$modelInputLength")
        } catch (e: Exception) {
            Log.e("WakewordEngine", "Failed to load model: ${e.message}")
            interpreter = null
        }
    }

    fun isModelLoaded(): Boolean {
        return interpreter != null
    }

    fun initModelFromFile(filePath: String) {
        try {
            val inputStream = java.io.FileInputStream(java.io.File(filePath))
            val fileChannel = inputStream.channel
            val mappedModel: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
            interpreter = Interpreter(mappedModel)
            // try to infer model input length after loading
            try {
                val shape = interpreter?.getInputTensor(0)?.shape()
                if (shape != null) {
                    if (shape.size >= 2) {
                        modelInputLength = shape[1]
                    } else if (shape.size == 1) {
                        modelInputLength = shape[0]
                    }
                }
            } catch (e: Exception) {
                Log.w("WakewordEngine", "Could not determine model input shape, using default: $modelInputLength")
            }
            Log.d("WakewordEngine", "Model loaded from file: $filePath, inputLength=$modelInputLength")
        } catch (e: Exception) {
            Log.e("WakewordEngine", "Failed to load model from file: ${e.message}")
            interpreter = null
        }
    }

    fun startListening(sampleRate: Int = 16000, threshold: Float = 0.7f) {
        if (listening) return
        if (interpreter == null) {
            Log.w("WakewordEngine", "Interpreter not initialized; call initModel() first")
        }

        // store sample rate for preprocessing
        sampleRateHz = sampleRate

        listening = true
        job = CoroutineScope(Dispatchers.Default).launch {
            val hopSize = 512
            val windowSize = modelInputLength

            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val readBuffer = ShortArray(hopSize)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, hopSize * 2)
            )

            val circ = FloatArray(windowSize)
            var writeIdx = 0
            var filled = 0

            try {
                audioRecord.startRecording()
                while (listening && isActive) {
                    val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            circ[writeIdx] = readBuffer[i] / 32768.0f
                            writeIdx = (writeIdx + 1) % windowSize
                            if (filled < windowSize) filled++
                        }
                    }

                    if (filled >= windowSize) {
                        // build ordered window (oldest->newest)
                        val window = FloatArray(windowSize)
                        var idx = writeIdx
                        for (i in 0 until windowSize) {
                            window[i] = circ[idx]
                            idx = (idx + 1) % windowSize
                        }

                        val score = runInference(window)
                        if (score >= threshold) {
                            dispatchDetection(score)
                            // Avoid flooding: sleep a bit after detection
                            delay(800)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WakewordEngine", "Error during audio capture: ${e.message}")
            } finally {
                try {
                    audioRecord.stop()
                } catch (_: Exception) {}
                try {
                    audioRecord.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopListening() {
        listening = false
        job?.cancel()
        job = null
    }

    fun setSensitivity(s: Float) {
        sensitivity = s
    }

    private fun runInference(window: FloatArray): Float {
        if (interpreter == null) return 0.0f
        return try {
            val inputTensor = interpreter!!.getInputTensor(0)
            val shape = try { inputTensor.shape() } catch (e: Exception) { null }

            val mode = preprocessingMode

            val modelInput: Any = when (mode) {
                "none" -> {
                    // Raw waveform (resample/pad/truncate if shape requests different length)
                    if (shape == null) arrayOf(window)
                    else if (shape.size == 1) {
                        if (shape[0] == window.size) arrayOf(window) else arrayOf(resampleArray(window, shape[0]))
                    } else if (shape.size == 2 && shape[0] == 1) {
                        if (shape[1] == window.size) arrayOf(window) else arrayOf(resampleArray(window, shape[1]))
                    } else arrayOf(window)
                }
                "logmel" -> {
                    if (shape == null) arrayOf(computeFeaturesForShape(window, intArrayOf(1, 0)))
                    else if (shape.size == 2) arrayOf(computeFeaturesForShape(window, shape))
                    else if (shape.size >= 3) {
                        val frames = computeFeaturesAsFrames(window, shape)
                        if (shape.size == 3) Array(1) { frames } else {
                            val out = Array(1) { Array(frames.size) { Array(frames[0].size) { FloatArray(1) } } }
                            for (i in frames.indices) for (j in frames[i].indices) out[0][i][j][0] = frames[i][j]
                            out
                        }
                    } else arrayOf(window)
                }
                "mfcc" -> {
                    if (shape == null) arrayOf(computeFeaturesForShape(window, intArrayOf(1, 0)))
                    else if (shape.size == 2) {
                        // compute MFCC and flatten
                        val frames = computeLogMel(window, sampleRateHz, 512, 160, 40)
                        val mfcc = computeMFCCFromLogMel(frames, shape[1])
                        arrayOf(flattenFrames(mfcc))
                    } else if (shape.size >= 3) {
                        val frames = computeFeaturesAsFrames(window, shape)
                        Array(1) { frames }
                    } else arrayOf(window)
                }
                else -> {
                    // auto: existing behavior - synthesize features if model expects different shape
                    if (shape == null) {
                        arrayOf(window)
                    } else if (shape.size == 1) {
                        if (shape[0] == window.size) arrayOf(window) else arrayOf(resampleArray(window, shape[0]))
                    } else if (shape.size == 2) {
                        if (shape[0] == 1) {
                            if (shape[1] == window.size) arrayOf(window)
                            else {
                                val features = computeFeaturesForShape(window, shape)
                                arrayOf(features)
                            }
                        } else arrayOf(window)
                    } else if (shape.size == 3) {
                        val frames = computeFeaturesAsFrames(window, shape)
                        Array(1) { frames }
                    } else if (shape.size == 4) {
                        val frames = computeFeaturesAsFrames(window, shape)
                        val out = Array(1) { Array(frames.size) { Array(frames[0].size) { FloatArray(1) } } }
                        for (i in frames.indices) for (j in frames[i].indices) out[0][i][j][0] = frames[i][j]
                        out
                    } else arrayOf(window)
                }
            }

            // Prepare output assuming model returns single score
            val output = Array(1) { FloatArray(1) }
            interpreter?.run(modelInput, output)
            output[0][0]
        } catch (e: Exception) {
            Log.e("WakewordEngine", "Inference error: ${e.message}")
            0.0f
        }
    }

    // Compute feature vector flattened to 1D matching shape [features]
    private fun computeFeaturesForShape(window: FloatArray, shape: IntArray): FloatArray {
        val requested = shape[1]
        // Try to compute log-mel and then flatten/resample to requested length
        val nFft = 512
        val nMels = 40
        val frames = computeLogMel(window, sampleRateHz, nFft, nFft / 2, nMels)
        // flatten
        val flat = FloatArray(frames.size * frames[0].size)
        var idx = 0
        for (i in frames.indices) for (j in frames[i].indices) flat[idx++] = frames[i][j]
        if (flat.size == requested) return flat
        // Resize (simple linear resample)
        val out = FloatArray(requested)
        for (i in 0 until requested) {
            val srcPos = i.toDouble() * (flat.size - 1) / (requested - 1)
            val lo = floor(srcPos).toInt()
            val hi = ceil(srcPos).toInt()
            if (lo == hi) out[i] = flat[lo]
            else {
                val t = srcPos - lo
                out[i] = flat[lo] * (1 - t).toFloat() + flat[hi] * t.toFloat()
            }
        }
        return out
    }

    // Compute features as frames of size [frames][bins] matching shape like [1,frames,bins]
    private fun computeFeaturesAsFrames(window: FloatArray, shape: IntArray): Array<FloatArray> {
        val expectedFrames = shape[1]
        val expectedBins = shape[2]
        // If expectedBins is small (<=13) assume MFCCs, else log-mel
        return if (expectedBins <= 13) {
            val logmel = computeLogMel(window, sampleRateHz, 512, 160, 40)
            val mfcc = computeMFCCFromLogMel(logmel, expectedBins)
            adjustFramesToExpected(mfcc, expectedFrames)
        } else {
            val logmel = computeLogMel(window, sampleRateHz, 512, 160, expectedBins)
            adjustFramesToExpected(logmel, expectedFrames)
        }
    }

    private fun adjustFramesToExpected(frames: Array<FloatArray>, expected: Int): Array<FloatArray> {
        if (frames.size == expected) return frames
        val out = Array(expected) { FloatArray(frames[0].size) }
        if (frames.isEmpty()) return out
        for (i in 0 until expected) {
            val srcPos = i.toDouble() * (frames.size - 1) / (expected - 1)
            val lo = floor(srcPos).toInt()
            val hi = ceil(srcPos).toInt()
            val t = srcPos - lo
            for (j in frames[0].indices) {
                out[i][j] = if (lo == hi) frames[lo][j] else (frames[lo][j] * (1 - t) + frames[hi][j] * t).toFloat()
            }
        }
        return out
    }

    private fun computeLogMel(window: FloatArray, sampleRate: Int, nFft: Int, hop: Int, nMels: Int): Array<FloatArray> {
        if (window.isEmpty()) return arrayOf()
        val frameLen = min(nFft, window.size)
        val framesList = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameLen <= window.size) {
            val frame = FloatArray(frameLen)
            System.arraycopy(window, start, frame, 0, frameLen)
            val mags = magnitudeSpectrum(frame, nFft)
            framesList.add(magsToLogMel(mags, sampleRate, nFft, nMels))
            start += hop
        }
        // handle tail
        if (start < window.size) {
            val frame = FloatArray(frameLen)
            val remaining = window.size - start
            System.arraycopy(window, start, frame, 0, remaining)
            for (i in remaining until frameLen) frame[i] = 0f
            val mags = magnitudeSpectrum(frame, nFft)
            framesList.add(magsToLogMel(mags, sampleRate, nFft, nMels))
        }
        return framesList.toTypedArray()
    }

    private fun magsToLogMel(mags: DoubleArray, sampleRate: Int, nFft: Int, nMels: Int): FloatArray {
        val filterBank = melFilterBank(nFft, sampleRate, nMels)
        val out = FloatArray(nMels)
        for (m in 0 until nMels) {
            var sum = 0.0
            val fb = filterBank[m]
            val len = min(fb.size, mags.size)
            for (i in 0 until len) sum += mags[i] * fb[i]
            out[m] = log10(sum + 1e-8).toFloat()
        }
        return out
    }

    private fun computeMFCCFromLogMel(logMel: Array<FloatArray>, nMfcc: Int): Array<FloatArray> {
        val out = Array(logMel.size) { FloatArray(nMfcc) }
        for (i in logMel.indices) {
            val d = dct(logMel[i].map { it.toDouble() }.toDoubleArray(), nMfcc)
            for (k in 0 until nMfcc) out[i][k] = d[k].toFloat()
        }
        return out
    }

    private fun flattenFrames(frames: Array<FloatArray>): FloatArray {
        if (frames.isEmpty()) return FloatArray(0)
        val out = FloatArray(frames.size * frames[0].size)
        var idx = 0
        for (i in frames.indices) for (j in frames[i].indices) out[idx++] = frames[i][j]
        return out
    }

    private fun resampleArray(input: FloatArray, outLen: Int): FloatArray {
        if (outLen <= 0) return FloatArray(0)
        if (input.size == outLen) return input.copyOf()
        val out = FloatArray(outLen)
        if (outLen == 1) {
            out[0] = input.average().toFloat()
            return out
        }
        for (i in 0 until outLen) {
            val srcPos = i.toDouble() * (input.size - 1) / (outLen - 1)
            val lo = floor(srcPos).toInt().coerceIn(0, input.size - 1)
            val hi = ceil(srcPos).toInt().coerceIn(0, input.size - 1)
            val t = srcPos - lo
            out[i] = if (lo == hi) input[lo] else (input[lo] * (1 - t).toFloat() + input[hi] * t.toFloat())
        }
        return out
    }

    private fun magnitudeSpectrum(frame: FloatArray, nFft: Int): DoubleArray {
        val n = nFft
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        val window = hammingWindow(frame.size)
        for (i in frame.indices) real[i] = (frame[i] * window[i]).toDouble()
        for (i in frame.size until n) real[i] = 0.0
        for (i in 0 until n) imag[i] = 0.0
        fft(real, imag)
        val half = n / 2 + 1
        val mags = DoubleArray(half)
        for (i in 0 until half) mags[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        return mags
    }

    private fun hammingWindow(n: Int): FloatArray {
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = (0.54 - 0.46 * cos(2.0 * Math.PI * i / (n - 1))).toFloat()
        return out
    }

    private fun nextPowerOfTwo(v: Int): Int {
        var n = 1
        while (n < v) n = n shl 1
        return n
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n - 1) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpR = real[i]
                val tmpI = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tmpR
                imag[j] = tmpI
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wlenR = cos(angle)
            val wlenI = sin(angle)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                var k = 0
                while (k < len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWR = wR * wlenR - wI * wlenI
                    val nextWI = wR * wlenI + wI * wlenR
                    wR = nextWR
                    wI = nextWI
                    k++
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun melFilterBank(nFft: Int, sampleRate: Int, nMels: Int): Array<DoubleArray> {
        val nFreqs = nFft / 2 + 1
        val lowFreq = 0.0
        val highFreq = sampleRate / 2.0
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        val mels = DoubleArray(nMels + 2)
        for (i in 0 until nMels + 2) mels[i] = lowMel + (highMel - lowMel) * i / (nMels + 1)
        val hz = DoubleArray(nMels + 2)
        for (i in 0 until mels.size) hz[i] = melToHz(mels[i])
        val bins = IntArray(hz.size)
        for (i in hz.indices) bins[i] = floor((nFft + 1) * hz[i] / sampleRate).toInt().coerceIn(0, nFreqs - 1)
        val filterBank = Array(nMels) { DoubleArray(nFreqs) }
        for (m in 1..nMels) {
            val fMMinus = bins[m - 1]
            val fM = bins[m]
            val fMPlus = bins[m + 1]
            if (fMMinus == fM || fM == fMPlus) continue
            for (k in fMMinus until fM) filterBank[m - 1][k] = (k - fMMinus).toDouble() / (fM - fMMinus)
            for (k in fM until fMPlus) filterBank[m - 1][k] = (fMPlus - k).toDouble() / (fMPlus - fM)
        }
        return filterBank
    }

    private fun dct(input: DoubleArray, nOut: Int): DoubleArray {
        val n = input.size
        val out = DoubleArray(nOut)
        for (k in 0 until nOut) {
            var sum = 0.0
            for (i in 0 until n) sum += input[i] * cos(Math.PI * k * (2 * i + 1) / (2.0 * n))
            out[k] = sum
        }
        return out
    }

    private fun dispatchDetection(score: Float) {
        try {
            val payload = JSONObject()
            payload.put("score", score)
            payload.put("timestamp", System.currentTimeMillis())

            // Dispatch event on main thread using NativeActionCoordinator
            val eventName = "OneThreeThreeEight\\NativephpTflite\\Events\\WakewordDetected"
            Handler(Looper.getMainLooper()).post {
                try {
                    // Try direct call if available
                    try {
                        com.nativephp.mobile.actions.NativeActionCoordinator.dispatchEvent(activity, eventName, payload.toString())
                    } catch (_: Throwable) {
                        // Fallback via reflection
                        try {
                            val cls = Class.forName("com.nativephp.mobile.actions.NativeActionCoordinator")
                            val method = cls.getMethod("dispatchEvent", android.app.Activity::class.java, String::class.java, String::class.java)
                            method.invoke(null, activity, eventName, payload.toString())
                        } catch (ex: Exception) {
                            Log.e("WakewordEngine", "Failed to dispatch event: ${ex.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WakewordEngine", "Dispatch error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("WakewordEngine", "Failed to build event payload: ${e.message}")
        }
    }
}
