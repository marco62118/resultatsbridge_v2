package app.resultatsbridge.client

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer

class Yolo11Detector(context: Context) {

    companion object {
        const val MODEL_FILE = "best_yolo11n_v4_512.onnx"
        private const val INPUT_SIZE = 512
        private const val CONFIDENCE_THRESHOLD = 0.25f

        // À vérifier avec data.yaml du dataset v2 après entraînement Colab
        val LABELS = listOf(
            "10C","10K","10P","10T",
            "2C","2K","2P","2T",
            "3C","3K","3P","3T",
            "4C","4K","4P","4T",
            "5C","5K","5P","5T",
            "6C","6K","6P","6T",
            "7C","7K","7P","7T",
            "8C","8K","8P","8T",
            "9C","9K","9P","9T",
            "AC","AK","AP","AT",
            "DC","DK","DP","DT",
            "RC","RK","RP","RT",
            "VC","VK","VP","VT"
        )
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = File(context.filesDir, MODEL_FILE)
        if (!modelFile.exists()) {
            context.assets.open(MODEL_FILE).use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    // Retourne List<Triple(code, cx_normalisé, confiance)> trié par cx — même interface que RtDetrDetector
    fun detect(bitmap: Bitmap): List<Triple<String, Float, Float>> {
        val inputTensor = preprocess(bitmap)
        val inputName = session.inputNames.iterator().next()
        val results = session.run(mapOf(inputName to inputTensor))
        inputTensor.close()
        @Suppress("UNCHECKED_CAST")
        // YOLOv11 ONNX : [1, num_classes+4, num_anchors] = [1, 56, 8400]
        val raw = (results[0].value as Array<Array<FloatArray>>)[0]
        results.close()
        return postProcess(raw)
    }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val fb = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        for (p in pixels) fb.put(((p shr 16) and 0xFF) / 255.0f)
        for (p in pixels) fb.put(((p shr 8)  and 0xFF) / 255.0f)
        for (p in pixels) fb.put((p           and 0xFF) / 255.0f)
        fb.rewind()
        return OnnxTensor.createTensor(env, fb, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
    }

    // Format YOLOv11 : raw[ligne][ancre]
    //   lignes 0-3       = cx, cy, w, h en pixels (0..INPUT_SIZE)
    //   lignes 4..4+N-1  = score de chaque classe (N=52 cartes)
    // Pour chaque ancre : trouver la classe avec le score max, garder si > seuil.
    // Si une même classe est détectée plusieurs fois → garder la plus confiante.
    private fun postProcess(raw: Array<FloatArray>): List<Triple<String, Float, Float>> {
        val numAnchors = raw[0].size
        val numClasses = raw.size - 4

        data class Best(val score: Float, val cx: Float)
        val best = mutableMapOf<Int, Best>()

        for (j in 0 until numAnchors) {
            var bestCls = -1
            var bestScore = CONFIDENCE_THRESHOLD
            for (c in 0 until numClasses) {
                val score = raw[4 + c][j]
                if (score > bestScore) { bestScore = score; bestCls = c }
            }
            if (bestCls == -1 || bestCls !in LABELS.indices) continue
            val cxNorm = raw[0][j] / INPUT_SIZE
            val cur = best[bestCls]
            if (cur == null || bestScore > cur.score) best[bestCls] = Best(bestScore, cxNorm)
        }
        return best.entries.sortedBy { it.value.cx }.map { (cls, b) -> Triple(LABELS[cls], b.cx, b.score) }
    }

    fun close() { session.close(); env.close() }
}
