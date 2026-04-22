<?php

namespace OneThreeThreeEight\NativephpTflite;

class Tflite
{
    public static function loadModel(array $options = []): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.LoadModel', json_encode($options));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function startListening(array $options = []): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.StartListening', json_encode($options));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function stopListening(): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.StopListening', json_encode([]));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function setSensitivity(float $sensitivity): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.SetSensitivity', json_encode(['sensitivity' => $sensitivity]));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function getVersion(): ?string
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.GetVersion', json_encode([]));
            $decoded = json_decode($result);
            return $decoded->data->version ?? null;
        }
        return null;
    }

    public static function addModel(string $name, string $base64Data): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.AddModel', json_encode(['name' => $name, 'data' => $base64Data]));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function listModels(): array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.ListModels', json_encode([]));
            $decoded = json_decode($result);
            return $decoded->data->models ?? [];
        }
        return [];
    }

    public static function deleteModel(string $name): bool
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.DeleteModel', json_encode(['name' => $name]));
            $decoded = json_decode($result);
            return ($decoded->data->deleted ?? false) === true;
        }
        return false;
    }

    public static function loadModelFromFile(string $filename): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.LoadModelFromFile', json_encode(['filename' => $filename]));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function copyAssetToStorage(string $assetName, string $targetName): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.CopyAssetToStorage', json_encode(['asset' => $assetName, 'target' => $targetName]));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }

    public static function setPreprocessing(string $mode): ?array
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Tflite.SetPreprocessing', json_encode(['mode' => $mode]));
            $decoded = json_decode($result);
            return $decoded->data ?? null;
        }
        return null;
    }
}
