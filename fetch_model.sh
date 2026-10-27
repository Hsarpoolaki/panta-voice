#!/bin/sh
# دانلود مدل فارسی Vosk و قرار دادن در assets
set -e
D="$(cd "$(dirname "$0")" && pwd)/app/src/main/assets"
mkdir -p "$D"
cd "$D"
wget -q https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip -O m.zip
unzip -q -o m.zip && rm -rf model-fa && mv vosk-model-small-fa-0.42 model-fa && rm m.zip
uuidgen > model-fa/uuid 2>/dev/null || date +%s%N > model-fa/uuid
