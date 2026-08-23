# Panta Voice Final Project

Persian voice-control test app for Android 7-8 era devices.

## Notes

- `SpeechRecognizer` may rely on Google services or network availability on some devices.
- The continuous restart loop here is not a certified wake-word engine; it is a lightweight polling approach.
- Offline production wake-word detection should move to a dedicated TFLite or similar on-device pipeline.
- No UART, hardware relay, or safety-chain integration is included.
