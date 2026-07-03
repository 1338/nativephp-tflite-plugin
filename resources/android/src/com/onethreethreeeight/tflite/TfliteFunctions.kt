package com.onethreethreeeight.tflite

import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import java.io.File

object TfliteFunctions {
    class LoadModel(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val assetName = parameters["asset"] as? String ?: return error("MISSING_ASSET", "Missing asset parameter")
                return BridgeResponse.success(TfliteEngine.loadFromAsset(activity.assets, assetName))
            } catch (e: Exception) {
                return error("FAILED_TO_LOAD_MODEL", e.message)
            }
        }
    }

    class Run(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val input = parameters["input"] ?: parameters["inputs"]
                val outputIndex = (parameters["outputIndex"] as? Number)?.toInt() ?: 0
                return BridgeResponse.success(TfliteEngine.run(input, outputIndex))
            } catch (e: Exception) {
                return error("INFERENCE_FAILED", e.message)
            }
        }
    }

    class ModelInfo(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.success(TfliteEngine.modelInfo())
        }
    }

    class GetVersion(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.success(mapOf("version" to "0.1.0"))
        }
    }

    class AddModel(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val name = parameters["name"] as? String ?: return error("MISSING_NAME", "Missing name parameter")
                val data = parameters["data"] as? String ?: return error("MISSING_DATA", "Missing data parameter")
                val modelsDir = File(activity.filesDir, "tflite_models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                val outFile = File(modelsDir, sanitizeFilename(name))
                val decoded = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                outFile.writeBytes(decoded)
                return BridgeResponse.success(modelFileInfo(outFile))
            } catch (e: Exception) {
                return error("ADD_MODEL_FAILED", e.message)
            }
        }
    }

    class ListModels(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val modelsDir = File(activity.filesDir, "tflite_models")
                val list = modelsDir.listFiles()
                    ?.filter { it.isFile }
                    ?.map { modelFileInfo(it) }
                    ?: listOf<Map<String, Any>>()
                return BridgeResponse.success(mapOf("models" to list))
            } catch (e: Exception) {
                return error("LIST_MODELS_FAILED", e.message)
            }
        }
    }

    class DeleteModel(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val name = parameters["name"] as? String ?: return error("MISSING_NAME", "Missing name parameter")
                val modelsDir = File(activity.filesDir, "tflite_models")
                val target = File(modelsDir, sanitizeFilename(name))
                val deleted = if (target.exists()) target.delete() else false
                return BridgeResponse.success(mapOf("deleted" to deleted))
            } catch (e: Exception) {
                return error("DELETE_MODEL_FAILED", e.message)
            }
        }
    }

    class LoadModelFromFile(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val filename = parameters["filename"] as? String ?: return error("MISSING_FILENAME", "Missing filename parameter")
                val modelsDir = File(activity.filesDir, "tflite_models")
                val target = File(modelsDir, sanitizeFilename(filename))
                if (!target.exists()) return error("FILE_NOT_FOUND", "Model file not found")
                return BridgeResponse.success(TfliteEngine.loadFromFile(target))
            } catch (e: Exception) {
                return error("LOAD_MODEL_FAILED", e.message)
            }
        }
    }

    class CopyAssetToStorage(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val asset = parameters["asset"] as? String ?: return error("MISSING_ASSET", "Missing asset parameter")
                val targetName = parameters["target"] as? String ?: asset
                val modelsDir = File(activity.filesDir, "tflite_models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                val outFile = File(modelsDir, sanitizeFilename(targetName))
                val input = activity.assets.open(asset)
                val bytes = input.readBytes()
                input.close()
                outFile.writeBytes(bytes)
                return BridgeResponse.success(modelFileInfo(outFile))
            } catch (e: Exception) {
                return error("COPY_ASSET_FAILED", e.message)
            }
        }
    }

    private fun error(code: String, message: String?): Map<String, Any> {
        return BridgeResponse.error(code, message ?: "Unknown error")
    }

    private fun sanitizeFilename(name: String): String {
        val fileName = File(name).name
        require(fileName.isNotBlank()) { "Invalid filename" }
        return fileName
    }

    private fun modelFileInfo(file: File): Map<String, Any> {
        return mapOf(
            "name" to file.name,
            "size" to file.length(),
            "lastModified" to file.lastModified()
        )
    }
}
