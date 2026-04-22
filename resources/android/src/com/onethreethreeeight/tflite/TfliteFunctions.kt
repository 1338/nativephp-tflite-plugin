package com.onethreethreeeight.tflite

import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse

object TfliteFunctions {
    class LoadModel : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val assetName = parameters["asset"] as? String ?: return BridgeResponse.error("missing_asset")
                // initialize model from assets (uses global `activity` provided by NativePHP runtime)
                WakewordEngine.initModel(activity?.assets, assetName)
                return BridgeResponse.success(mapOf("status" to "model_loaded", "asset" to assetName))
            } catch (e: Exception) {
                return BridgeResponse.error("failed_to_load_model: ${e.message}")
            }
        }
    }

    class StartListening : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val sampleRate = (parameters["sampleRate"] as? Number)?.toInt() ?: 16000
                val threshold = (parameters["threshold"] as? Number)?.toFloat() ?: 0.7f
                val sensitivity = (parameters["sensitivity"] as? Number)?.toFloat() ?: 0.5f
                // Initialize model if asset or filename provided
                if (parameters.containsKey("asset")) {
                    val assetName = parameters["asset"] as? String ?: return BridgeResponse.error("invalid_asset")
                    WakewordEngine.initModel(activity?.assets, assetName)
                } else if (parameters.containsKey("filename")) {
                    val filename = parameters["filename"] as? String ?: return BridgeResponse.error("invalid_filename")
                    val modelsDir = java.io.File(activity.filesDir, "tflite_models")
                    val target = java.io.File(modelsDir, filename)
                    if (!target.exists()) return BridgeResponse.error("file_not_found")
                    WakewordEngine.initModelFromFile(target.absolutePath)
                }

                if (parameters.containsKey("preprocessing")) {
                    val mode = parameters["preprocessing"] as? String ?: "auto"
                    WakewordEngine.setPreprocessingMode(mode)
                }

                if (!WakewordEngine.isModelLoaded()) return BridgeResponse.error("model_not_loaded")

                WakewordEngine.setSensitivity(sensitivity)
                WakewordEngine.startListening(sampleRate, threshold)
                return BridgeResponse.success(mapOf("status" to "listening_started"))
            } catch (e: Exception) {
                return BridgeResponse.error("failed_to_start_listening: ${e.message}")
            }
        }
    }

    class StopListening : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            WakewordEngine.stopListening()
            return BridgeResponse.success(mapOf("status" to "listening_stopped"))
        }
    }

    class SetSensitivity : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val sensitivity = (parameters["sensitivity"] as? Number)?.toFloat() ?: 0.5f
            WakewordEngine.setSensitivity(sensitivity)
            return BridgeResponse.success(mapOf("status" to "sensitivity_set", "sensitivity" to sensitivity))
        }
    }

    class GetVersion : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.success(mapOf("version" to "0.1.0"))
        }
    }

    class AddModel : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val name = parameters["name"] as? String ?: return BridgeResponse.error("missing_name")
                val data = parameters["data"] as? String ?: return BridgeResponse.error("missing_data")
                val modelsDir = java.io.File(activity.filesDir, "tflite_models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                val outFile = java.io.File(modelsDir, name)
                val decoded = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                outFile.writeBytes(decoded)
                return BridgeResponse.success(mapOf("status" to "saved", "path" to outFile.absolutePath, "name" to outFile.name))
            } catch (e: Exception) {
                return BridgeResponse.error("add_model_failed: ${e.message}")
            }
        }
    }

    class ListModels : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val modelsDir = java.io.File(activity.filesDir, "tflite_models")
                val list = modelsDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: listOf<String>()
                return BridgeResponse.success(mapOf("models" to list))
            } catch (e: Exception) {
                return BridgeResponse.error("list_models_failed: ${e.message}")
            }
        }
    }

    class DeleteModel : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val name = parameters["name"] as? String ?: return BridgeResponse.error("missing_name")
                val modelsDir = java.io.File(activity.filesDir, "tflite_models")
                val target = java.io.File(modelsDir, name)
                val deleted = if (target.exists()) target.delete() else false
                return BridgeResponse.success(mapOf("deleted" to deleted))
            } catch (e: Exception) {
                return BridgeResponse.error("delete_model_failed: ${e.message}")
            }
        }
    }

    class LoadModelFromFile : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val filename = parameters["filename"] as? String ?: return BridgeResponse.error("missing_filename")
                val modelsDir = java.io.File(activity.filesDir, "tflite_models")
                val target = java.io.File(modelsDir, filename)
                if (!target.exists()) return BridgeResponse.error("file_not_found")
                WakewordEngine.initModelFromFile(target.absolutePath)
                return BridgeResponse.success(mapOf("status" to "model_loaded", "path" to target.absolutePath))
            } catch (e: Exception) {
                return BridgeResponse.error("load_model_failed: ${e.message}")
            }
        }
    }

    class CopyAssetToStorage : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val asset = parameters["asset"] as? String ?: return BridgeResponse.error("missing_asset")
                val targetName = parameters["target"] as? String ?: asset
                val modelsDir = java.io.File(activity.filesDir, "tflite_models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                val outFile = java.io.File(modelsDir, targetName)
                val input = activity.assets.open(asset)
                val bytes = input.readBytes()
                input.close()
                outFile.writeBytes(bytes)
                return BridgeResponse.success(mapOf("status" to "copied", "path" to outFile.absolutePath))
            } catch (e: Exception) {
                return BridgeResponse.error("copy_asset_failed: ${e.message}")
            }
        }
    }

    class SetPreprocessing : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            try {
                val mode = parameters["mode"] as? String ?: return BridgeResponse.error("missing_mode")
                WakewordEngine.setPreprocessingMode(mode)
                return BridgeResponse.success(mapOf("status" to "preprocessing_set", "mode" to mode))
            } catch (e: Exception) {
                return BridgeResponse.error("set_preprocessing_failed: ${e.message}")
            }
        }
    }
}
