# ساخت فایل‌های صوتی تأیید (آفلاین؛ فقط روی کامپیوتر اجرا می‌شود، نه داخل APK)
# نیازمند: pip install piper-tts ، ffmpeg ، و مدل فارسی piper (fa_IR-ganji-medium یا fa_IR-amir-medium)
# اجرا: python3 tools/make_voice.py /path/to/fa_IR-ganji-medium.onnx
import sys, wave, subprocess, os, io
from piper import PiperVoice, SynthesisConfig

MODEL = sys.argv[1]
pv = PiperVoice.load(MODEL)

ORD = {1: "اول", 2: "دوم", 3: "سوم", 4: "چهارم", 5: "پنجم", 6: "ششم", 7: "هفتم", 8: "هشتم", 9: "نهم",
       10: "دهم", 11: "یازدهم", 12: "دوازدهم", 13: "سیزدهم", 14: "چهاردهم", 15: "پانزدهم",
       16: "شانزدهم", 17: "هفدهم", 18: "هجدهم", 19: "نوزدهم"}
TENS = {20: "بیست", 30: "سی", 40: "چهل"}
TENS_ORD = {20: "بیستم", 30: "سی ام", 40: "چهلم"}
CARD = {1: "یک", 2: "دو", 3: "سه", 4: "چهار", 5: "پنج", 6: "شش", 7: "هفت", 8: "هشت", 9: "نه", 10: "ده"}

def ordinal(n):
    if n in ORD: return ORD[n]
    if n in TENS_ORD: return TENS_ORD[n]
    t, u = n // 10 * 10, n % 10
    return TENS[t] + " و " + ORD[u]

clips = {}
for n in range(1, 46): clips[f"f_{n}"] = "طبقه " + ordinal(n)
for n in range(1, 11):
    clips[f"b_{n}"] = "طبقه منفی " + CARD[n]
    clips[f"p_{n}"] = "پارکینگ شماره " + CARD[n]
    clips[f"m_{n}"] = "نیم طبقه شماره " + CARD[n]
clips.update({"sp_g": "طبقه همکف", "sp_roof": "پشت بام", "sp_rest": "طبقه رستوران", "sp_lobby": "لابی",
              "sp_mezz": "نیم طبقه", "sp_p": "پارکینگ"})

for i, t in enumerate(["چشم، انجام می شود", "باشه، همین الان", "حتما، بفرمایید", "بله، متوجه شدم", "در خدمتتان هستم", "الان انجام می دهم", "فرمان شما دریافت شد"], 1): clips[f"ack_{i}"] = t
for i, t in enumerate(["در حال حرکت هستیم", "به زودی می رسیم", "لطفا کمی صبر کنید"], 1): clips[f"post_{i}"] = t

CMDS = {
    "door_open": ["درب باز می شود", "چشم، در حال باز کردن درب", "حتما، درب باز می شود"],
    "door_close": ["درب بسته می شود", "چشم، در حال بستن درب", "حتما، درب بسته می شود"],
    "cancel": ["لغو شد", "باشه، لغو کردم", "فرمان لغو شد"],
    "bell": ["زنگ هشدار فعال شد", "زنگ هشدار زده شد", "چشم، زنگ هشدار فعال شد"],
    "fan_on": ["فن روشن شد", "چشم، فن روشن می شود", "باشه، فن را روشن کردم"],
    "fan_off": ["فن خاموش شد", "چشم، فن خاموش می شود", "باشه، فن را خاموش کردم"],
    "light_on": ["نور روشن شد", "چشم، چراغ روشن می شود", "باشه، چراغ را روشن کردم"],
    "light_off": ["نور خاموش شد", "چشم، چراغ خاموش می شود", "باشه، چراغ را خاموش کردم"],
    "light_up": ["نور بیشتر شد", "چشم، نور را زیاد کردم", "باشه، روشن تر شد"],
    "light_down": ["نور کمتر شد", "چشم، نور را کم کردم", "باشه، ملایم تر شد"],
    "scent": ["رایحه پخش می شود", "چشم، رایحه پخش می شود", "باشه، عطر پخش شد"],
    "music_next": ["آهنگ بعدی", "چشم، آهنگ بعدی", "باشه، آهنگ بعدی"],
    "music_prev": ["آهنگ قبلی", "چشم، آهنگ قبلی", "باشه، آهنگ قبلی"],
    "music_toggle": ["موزیک", "چشم، موزیک", "باشه، موزیک"],
    "vol_up": ["صدا بیشتر شد", "چشم، صدا را زیاد کردم", "باشه، صدا بالا رفت"],
    "vol_down": ["صدا کمتر شد", "چشم، صدا را کم کردم", "باشه، صدا پایین آمد"],
}
for k, vs in CMDS.items():
    for i, t in enumerate(vs, 1): clips[f"c_{k}_{i}"] = t

TRIM = ("silenceremove=start_periods=1:start_threshold=-60dB,areverse,"
        "silenceremove=start_periods=1:start_threshold=-60dB,areverse,"
        "loudnorm=I=-16:TP=-1.5,adelay=100,apad=pad_dur=0.15")
pack = bytearray(); idx = []
for name, text in clips.items():
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w: pv.synthesize_wav(text, w, syn_config=SynthesisConfig(length_scale=1.2))
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "wav", "-i", "pipe:0", "-af", TRIM,
                        "-ar", "24000", "-ac", "1", "-c:a", "libopus", "-b:a", "24k", "-application", "audio",
                        "-f", "ogg", "pipe:1"], input=buf.getvalue(), check=True, stdout=subprocess.PIPE)
    idx.append(f"{name} {len(pack)} {len(r.stdout)}")
    pack += r.stdout
ASSETS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
os.makedirs(ASSETS, exist_ok=True)
open(os.path.join(ASSETS, "voice.bin"), "wb").write(pack)
open(os.path.join(ASSETS, "voice.txt"), "w").write("\n".join(idx))
print(len(clips), "clips", len(pack), "bytes")
