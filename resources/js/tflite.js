const baseUrl = '/_native/api/call';

async function bridgeCall(method, params = {}) {
  const res = await fetch(baseUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ method, params })
  });
  return await res.json();
}

export async function loadModel(options = {}) {
  const r = await bridgeCall('Tflite.LoadModel', options);
  return r?.data ?? r;
}

export async function loadModelFromAsset(asset) {
  return loadModel({ asset });
}

export async function run(input, outputIndex = 0) {
  const r = await bridgeCall('Tflite.Run', { input, outputIndex });
  return r?.data ?? r;
}

export async function modelInfo() {
  const r = await bridgeCall('Tflite.ModelInfo', {});
  return r?.data ?? r;
}

export async function getVersion() {
  const r = await bridgeCall('Tflite.GetVersion', {});
  return r?.data ?? r;
}

export async function addModel(name, base64Data) {
  const r = await bridgeCall('Tflite.AddModel', { name, data: base64Data });
  return r?.data ?? r;
}

export async function listModels() {
  const r = await bridgeCall('Tflite.ListModels', {});
  return r?.data ?? r;
}

export async function deleteModel(name) {
  const r = await bridgeCall('Tflite.DeleteModel', { name });
  return r?.data ?? r;
}

export async function loadModelFromFile(filename) {
  const r = await bridgeCall('Tflite.LoadModelFromFile', { filename });
  return r?.data ?? r;
}

export async function copyAssetToStorage(asset, target) {
  const r = await bridgeCall('Tflite.CopyAssetToStorage', { asset, target });
  return r?.data ?? r;
}
