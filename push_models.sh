#!/bin/bash

# Define absolute ADB path just in case
ADB_CMD="/home/henryv/Android/Sdk/platform-tools/adb"

if ! command -v "$ADB_CMD" &> /dev/null
then
    echo "Error: adb command not found at $ADB_CMD"
    # Fallback to system adb
    ADB_CMD="adb"
fi

APP_PACKAGE="com.checkscam.app"
WHISPER_MODEL="stt-engine/src/main/assets/ggml-base.bin"
LLAMA_MODEL="scam-classifier/src/main/assets/Llama-3.2-1B-Instruct-Q4_K_M.gguf"

echo "=== Pushing CheckScam ML Models to Device ==="

# 1. Push Whisper Model
echo "Pushing Whisper STT Model (ggml-base.bin)..."
$ADB_CMD push "$WHISPER_MODEL" /data/local/tmp/ggml-base.bin
$ADB_CMD shell chmod 666 /data/local/tmp/ggml-base.bin
$ADB_CMD shell run-as "$APP_PACKAGE" mkdir -p files
$ADB_CMD shell "cat /data/local/tmp/ggml-base.bin | run-as $APP_PACKAGE sh -c 'cat > files/ggml-base.bin'"
$ADB_CMD shell rm /data/local/tmp/ggml-base.bin

# 2. Push Llama Model
echo "Pushing Llama Classifier Model (Llama-3.2-1B-Instruct-Q4_K_M.gguf)..."
$ADB_CMD push "$LLAMA_MODEL" /data/local/tmp/Llama-3.2-1B-Instruct-Q4_K_M.gguf
$ADB_CMD shell chmod 666 /data/local/tmp/Llama-3.2-1B-Instruct-Q4_K_M.gguf
$ADB_CMD shell "cat /data/local/tmp/Llama-3.2-1B-Instruct-Q4_K_M.gguf | run-as $APP_PACKAGE sh -c 'cat > files/Llama-3.2-1B-Instruct-Q4_K_M.gguf'"
$ADB_CMD shell rm /data/local/tmp/Llama-3.2-1B-Instruct-Q4_K_M.gguf

echo "=== Verifying files on device ==="
$ADB_CMD shell run-as "$APP_PACKAGE" ls -lh files/

echo "=== Success! Models are now in the app's internal storage. ==="
echo "You can now run your tests or launch the app without the ADB install stall."
