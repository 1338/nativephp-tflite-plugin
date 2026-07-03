# Basic NativePHP Usage

This example shows the minimum app-side calls. It assumes a `.tflite` model has been bundled into Android assets at `models/example.tflite`.

## Install

```bash
composer require 1338/nativephp-tflite
php artisan vendor:publish --tag=nativephp-plugins-provider
php artisan native:plugin:register 1338/nativephp-tflite
php artisan native:plugin:validate
```

Set the Android min SDK to at least 33 in your NativePHP app configuration.

## Route Example

```php
use Illuminate\Support\Facades\Route;
use OneThreeThreeEight\NativephpTflite\Tflite;

Route::get('/tflite-smoke', function () {
    $info = Tflite::loadModelFromAsset('models/example.tflite');

    $result = Tflite::run([
        0.1,
        0.2,
        0.3,
    ]);

    return [
        'model' => $info,
        'result' => $result,
    ];
});
```

## Build Check

The package has been checked by installing it from Packagist into a fresh NativePHP Mobile 3.3 app, registering it with `native:plugin:register`, validating it with `native:plugin:validate`, and compiling the generated Android project with:

```bash
./gradlew assembleRelease
```
