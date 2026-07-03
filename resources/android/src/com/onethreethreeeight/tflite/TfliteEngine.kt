package com.onethreethreeeight.tflite

import android.content.res.AssetManager
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TfliteEngine {
    private var interpreter: Interpreter? = null
    private var loadedModel: String? = null

    fun loadFromAsset(assetManager: AssetManager, assetName: String): Map<String, Any> {
        assetManager.openFd(assetName).use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                val mappedModel = input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                loadMappedModel(mappedModel, "asset:$assetName")
            }
        }

        return modelInfo()
    }

    fun loadFromFile(file: File): Map<String, Any> {
        FileInputStream(file).use { input ->
            val mappedModel = input.channel.map(FileChannel.MapMode.READ_ONLY, 0, input.channel.size())
            loadMappedModel(mappedModel, "file:${file.name}")
        }

        return modelInfo()
    }

    fun isLoaded(): Boolean = interpreter != null

    fun modelInfo(): Map<String, Any> {
        val current = interpreter ?: return mapOf(
            "loaded" to false,
            "model" to ""
        )

        return mapOf(
            "loaded" to true,
            "model" to (loadedModel ?: ""),
            "inputs" to tensorInfo(current, true),
            "outputs" to tensorInfo(current, false)
        )
    }

    fun run(input: Any?, outputIndex: Int = 0): Map<String, Any> {
        val current = interpreter ?: throw IllegalStateException("No model loaded")
        require(current.inputTensorCount == 1) {
            "Only single-input models are currently supported"
        }
        require(outputIndex >= 0 && outputIndex < current.outputTensorCount) {
            "Output index $outputIndex is out of range"
        }

        val inputTensor = current.getInputTensor(0)
        val inputBuffer = toInputBuffer(input, inputTensor)
        val outputTensor = current.getOutputTensor(outputIndex)
        val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())

        current.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        return mapOf(
            "outputIndex" to outputIndex,
            "shape" to outputTensor.shape().toList(),
            "dataType" to outputTensor.dataType().name,
            "data" to readOutput(outputBuffer, outputTensor)
        )
    }

    private fun loadMappedModel(mappedModel: MappedByteBuffer, modelName: String) {
        interpreter?.close()
        interpreter = Interpreter(mappedModel)
        loadedModel = modelName
    }

    private fun tensorInfo(interpreter: Interpreter, input: Boolean): List<Map<String, Any>> {
        val count = if (input) interpreter.inputTensorCount else interpreter.outputTensorCount
        return (0 until count).map { index ->
            val tensor = if (input) interpreter.getInputTensor(index) else interpreter.getOutputTensor(index)
            mapOf(
                "index" to index,
                "name" to tensor.name(),
                "shape" to tensor.shape().toList(),
                "dataType" to tensor.dataType().name,
                "bytes" to tensor.numBytes()
            )
        }
    }

    private fun toInputBuffer(input: Any?, tensor: Tensor): ByteBuffer {
        val numbers = flattenNumbers(input)
        val expectedElements = tensor.shape().fold(1) { acc, value -> acc * value.coerceAtLeast(1) }

        require(numbers.size == expectedElements) {
            "Input has ${numbers.size} values, model expects $expectedElements values for shape ${tensor.shape().toList()}"
        }

        val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        when (tensor.dataType()) {
            DataType.FLOAT32 -> numbers.forEach { buffer.putFloat(it.toFloat()) }
            DataType.INT32 -> numbers.forEach { buffer.putInt(it.toInt()) }
            DataType.UINT8 -> numbers.forEach { buffer.put(it.toInt().coerceIn(0, 255).toByte()) }
            DataType.INT8 -> numbers.forEach { buffer.put(it.toInt().coerceIn(-128, 127).toByte()) }
            else -> throw IllegalArgumentException("Unsupported input type: ${tensor.dataType().name}")
        }
        buffer.rewind()

        return buffer
    }

    private fun readOutput(buffer: ByteBuffer, tensor: Tensor): List<Any> {
        val values = mutableListOf<Any>()
        when (tensor.dataType()) {
            DataType.FLOAT32 -> {
                while (buffer.remaining() >= 4) values.add(buffer.float)
            }
            DataType.INT32 -> {
                while (buffer.remaining() >= 4) values.add(buffer.int)
            }
            DataType.UINT8 -> {
                while (buffer.remaining() >= 1) values.add(buffer.get().toInt() and 0xff)
            }
            DataType.INT8 -> {
                while (buffer.remaining() >= 1) values.add(buffer.get().toInt())
            }
            else -> throw IllegalArgumentException("Unsupported output type: ${tensor.dataType().name}")
        }

        return values
    }

    private fun flattenNumbers(value: Any?): List<Number> {
        return when (value) {
            null -> emptyList()
            is Number -> listOf(value)
            is List<*> -> value.flatMap { flattenNumbers(it) }
            is Array<*> -> value.flatMap { flattenNumbers(it) }
            else -> throw IllegalArgumentException("Input must be a number or nested array of numbers")
        }
    }
}
