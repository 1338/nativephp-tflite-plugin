<?php

namespace OneThreeThreeEight\NativephpTflite;

class Tflite
{
    public static function loadModelFromAsset(string $asset): ?array
    {
        return self::call('Tflite.LoadModel', ['asset' => $asset]);
    }

    public static function run(array $input, int $outputIndex = 0): ?array
    {
        return self::call('Tflite.Run', [
            'input' => $input,
            'outputIndex' => $outputIndex,
        ]);
    }

    public static function modelInfo(): ?array
    {
        return self::call('Tflite.ModelInfo');
    }

    public static function getVersion(): ?string
    {
        return self::call('Tflite.GetVersion')['version'] ?? null;
    }

    public static function addModel(string $name, string $base64Data): ?array
    {
        return self::call('Tflite.AddModel', ['name' => $name, 'data' => $base64Data]);
    }

    public static function listModels(): array
    {
        return self::call('Tflite.ListModels')['models'] ?? [];
    }

    public static function deleteModel(string $name): bool
    {
        return (self::call('Tflite.DeleteModel', ['name' => $name])['deleted'] ?? false) === true;
    }

    public static function loadModelFromFile(string $filename): ?array
    {
        return self::call('Tflite.LoadModelFromFile', ['filename' => $filename]);
    }

    public static function copyAssetToStorage(string $assetName, string $targetName): ?array
    {
        return self::call('Tflite.CopyAssetToStorage', ['asset' => $assetName, 'target' => $targetName]);
    }

    private static function call(string $method, array $payload = []): ?array
    {
        if (!function_exists('nativephp_call')) {
            return null;
        }

        $result = nativephp_call($method, json_encode($payload));
        $decoded = json_decode($result, true);

        return $decoded['data'] ?? null;
    }
}
