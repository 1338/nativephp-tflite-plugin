import Foundation

enum TfliteFunctions {
    class LoadModel: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class StartListening: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class StopListening: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class SetSensitivity: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class GetVersion: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.success(data: ["version": "0.1.0"])
        }
    }

    class AddModel: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class ListModels: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class DeleteModel: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class LoadModelFromFile: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class CopyAssetToStorage: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }

    class SetPreprocessing: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(message: "iOS not implemented")
        }
    }
}
