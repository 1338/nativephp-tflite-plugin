# 1338/nativephp-tflite

NativePHP plugin that exposes TensorFlow Lite model management and inference on Android.

This repository provides a NativePHP plugin that can load TFLite models, run realtime inference on device audio, and dispatch detection events to PHP. It is intended as a library you can include in another NativePHP app and use to store and manage your own TFLite models.

## Requirements

- NativePHP (mobile) v3.x
- PHP 8+
- Android device/emulator with microphone (plugin adds `android.permission.RECORD_AUDIO`)

## Install (development / path repo)

1. Add a path repository in your app `composer.json` that points to this plugin folder and require it:

```bash
# in your app root
composer require 1338/nativephp-tflite
```

2. Publish and register the plugin service provider:

```bash
php artisan vendor:publish --tag=nativephp-plugins-provider
php artisan native:plugin:register 1338/nativephp-tflite
```

3. Validate the plugin manifest and native mappings:

```bash
php artisan native:plugin:validate
```

4. (Re)generate native projects and run the mobile build:

```bash
php artisan native:install --force
php artisan native:run
# or open the generated Android project and run `./gradlew assembleDebug`
```

## Quick model workflow (PHP)

Store a model on-device (base64 upload):

```php

use OneThreeThreeEight\NativephpTflite\Tflite;

$base64 = base64_encode(file_get_contents('/local/path/to/your_model.tflite'));
Tflite::addModel('my_model.tflite', $base64);

$models = Tflite::listModels(); // array of filenames
Tflite::loadModelFromFile('my_model.tflite'); // loads model from app storage
Tflite::startListening(['sampleRate' => 16000, 'threshold' => 0.7, 'sensitivity' => 0.5]);
```

// If you distribute example models as app assets, use `copyAssetToStorage()` to copy them into app storage, then `loadModelFromFile()` to load.

Stop listening:

```php
Tflite::stopListening();
```

## Quick model workflow (JS)

Import the provided JS shim (the exact import path depends on how you bundle plugin JS):

```js
import { addModel, listModels, loadModelFromFile, startListening, stopListening, copyAssetToStorage } from '/_native/plugins/tflite/tflite.js';

// add model (base64)
await addModel('my_model.tflite', base64Data);
const models = await listModels();
// If you have an asset bundled with your app, copy it into storage and then load it by filename.
// await copyAssetToStorage('asset_name.tflite', 'target_name.tflite');
// await loadModelFromFile('target_name.tflite');
await startListening({ sampleRate: 16000, threshold: 0.7, sensitivity: 0.5 });
```

## Events

When the engine detects the event it dispatches the PHP event `OneThreeThreeEight\\NativephpTflite\\Events\\WakewordDetected` with two public properties: `$score` and `$timestamp`.

Example PHP listener:

```php
use OneThreeThreeEight\NativephpTflite\Events\WakewordDetected;
use Illuminate\Support\Facades\Event;

Event::listen(WakewordDetected::class, function(WakewordDetected $e) {
    logger()->info('Wakeword detected', ['score' => $e->score, 'timestamp' => $e->timestamp]);
});
```

Note: NativePHP dispatches events to Livewire/PHP. Receiving realtime events in plain JS depends on your app wiring; typically you handle PHP events and update UI via Livewire or custom bridges.

## Where files live

- Plugin manifest: [nativephp.json](nativephp.json)
- PHP facade: [src/Tflite.php](src/Tflite.php)
- Android bridge + engine: [resources/android/src/com/onethreethreeeight/tflite](resources/android/src/com/onethreethreeeight/tflite)
- Model storage on device: `{app_files_dir}/tflite_models/` (plugin uses `activity.filesDir/tflite_models`)

## Model compatibility

- The plugin's engine expects TFLite models that accept a single 1-D float waveform input and return a single float score (higher = stronger match). The current code feeds raw PCM normalized to [-1,1] directly to the model. For many wake-word models you will want to preprocess to MFCC/log-mel; see `WakewordEngine.kt` TODO comments for where to add preprocessing.

Preprocessing modes

- **auto** (default): the engine will inspect the model input shape and synthesize log-mel or MFCC features when the model expects feature inputs.
- **none**: feed the raw waveform to the model (resampled/truncated/padded to the model input length when possible).
- **logmel**: always compute log-mel filterbank features and format them for the model input.
- **mfcc**: always compute MFCC features (DCT of log-mel) and format them for the model input.

You can control preprocessing via the PHP facade or JS shim, or pass the `preprocessing` option to `startListening`.

PHP example:

```php
Tflite::setPreprocessing('mfcc');
Tflite::startListening(['sampleRate' => 16000, 'threshold' => 0.7, 'preprocessing' => 'mfcc']);
```

JS example:

```js
await setPreprocessing('mfcc');
await startListening({ sampleRate: 16000, threshold: 0.7, preprocessing: 'mfcc' });
```
