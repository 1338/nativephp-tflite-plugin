package com.onethreethreeeight.tflite

import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import java.io.File

object TfliteFunctions {
    class LoadModel(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val assetName = parameters["asset"] as? String ?: return BridgeResponse.error("missing_asset")
                return BridgeResponse.success(TfliteEngine.loadFromAsset(activity.assets, assetName))
            } catch (e: Exception) {
                return BridgeResponse.error("failed_to_load_model: ${e.message}")
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
                return BridgeResponse.error("inference_failed: ${e.message}")
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
                val name = parameters["name"] as? String ?: return BridgeResponse.error("missing_name")
                val data = parameters["data"] as? String ?: return BridgeResponse.error("missing_data")
                val modelsDir = File(activity.filesDir, "tflite_models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                val outFile = File(modelsDir, sanitizeFilename(name))
                val decoded = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                outFile.writeBytes(decoded)
                return BridgeResponse.success(modelFileInfo(outFile))
            } catch (e: Exception) {
                return BridgeResponse.error("add_model_failed: ${e.message}")
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
                return BridgeResponse.error("list_models_failed: ${e.message}")
            }
        }
    }

    class DeleteModel(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val name = parameters["name"] as? String ?: return BridgeResponse.error("missing_name")
                val modelsDir = File(activity.filesDir, "tflite_models")
                val target = File(modelsDir, sanitizeFilename(name))
                val deleted = if (target.exists()) target.delete() else false
                return BridgeResponse.success(mapOf("deleted" to deleted))
            } catch (e: Exception) {
                return BridgeResponse.error("delete_model_failed: ${e.message}")
            }
        }
    }

    class LoadModelFromFile(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val filename = parameters["filename"] as? String ?: return BridgeResponse.error("missing_filename")
                val modelsDir = File(activity.filesDir, "tflite_models")
                val target = File(modelsDir, sanitizeFilename(filename))
                if (!target.exists()) return BridgeResponse.error("file_not_found")
                return BridgeResponse.success(TfliteEngine.loadFromFile(target))
            } catch (e: Exception) {
                return BridgeResponse.error("load_model_failed: ${e.message}")
            }
        }
    }

    class CopyAssetToStorage(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val asset = parameters["asset"] as? String ?: return BridgeResponse.error("missing_asset")
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
                return BridgeResponse.error("copy_asset_failed: ${e.message}")
            }
        }
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
