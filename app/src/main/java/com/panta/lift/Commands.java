package com.panta.lift;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** گرامر و تبدیل متن تشخیص‌داده‌شده به JSON فرمان. */
public class Commands {
    static final String[] WAKE = {"پنتا", "بنتا", "چنتا", "چندتا", "چند تا", "حتی"};
    // کلمه‌های هم‌آوا: فقط برای جذب صدا در گرامر استندبای؛ هرگز بیدار نمی‌کنند
    static final String[] DECOY = {"با", "تا", "پا", "بتا", "پتا", "ته", "تی", "بی", "پی", "چی", "چه", "چند", "پنج", "پنجه", "بنده", "پنیر", "حتما", "حالا", "هستی", "ما", "را", "که", "این", "آن", "بله", "نه", "آ", "اوه", "هی", "خب", "بفرما", "سلام"};

    // فرمان‌ها: action -> عبارت‌ها
    static final LinkedHashMap<String, String[]> CMD = new LinkedHashMap<>();
    static {
        CMD.put("DOOR_OPEN", new String[]{"باز", "باز کن", "در باز", "درب باز", "در را باز کن", "اپن", "در اپن", "درب اپن", "باز شو"});
        CMD.put("DOOR_CLOSE", new String[]{"ببند", "بسته", "در بسته", "درب بسته", "کلوز", "در کلوز", "درب کلوز", "در را ببند", "بسته شو"});
        CMD.put("CANCEL", new String[]{"کنسل", "لغو", "لغو کن", "کنسل کن", "انصراف"});
        CMD.put("BELL", new String[]{"زنگ", "زنگ خطر", "آلارم"});
        CMD.put("FAN_ON", new String[]{"فن روشن", "فن روشن کن", "فن را روشن کن", "پنکه روشن", "پنکه را روشن کن", "روشن کردن فن"});
        CMD.put("FAN_OFF", new String[]{"فن خاموش", "فن خاموش کن", "فن را خاموش کن", "پنکه خاموش", "پنکه را خاموش کن", "خاموش کردن فن"});
        CMD.put("LIGHT_ON", new String[]{"نور روشن", "نور روشن کن", "نور را روشن کن", "چراغ روشن", "چراغ را روشن کن", "روشن کردن نور"});
        CMD.put("LIGHT_OFF", new String[]{"نور خاموش", "نور خاموش کن", "نور را خاموش کن", "چراغ خاموش", "چراغ را خاموش کن", "خاموش کردن نور"});
        CMD.put("LIGHT_UP", new String[]{"نور بیشتر", "نور زیاد", "زیاد کردن نور", "نور را زیاد کن", "نور زیاد کن", "روشن تر", "نور بالا"});
        CMD.put("LIGHT_DOWN", new String[]{"نور کمتر", "نور کم", "کم کردن نور", "نور را کم کن", "نور کم کن", "تاریک تر", "نور پایین"});
        CMD.put("SCENT", new String[]{"رایحه", "پخش رایحه", "عطر", "بو", "خوشبو کن"});
        CMD.put("MUSIC_NEXT", new String[]{"موزیک بعدی", "آهنگ بعدی", "بعدی"});
        CMD.put("MUSIC_PREV", new String[]{"موزیک قبلی", "آهنگ قبلی", "قبلی"});
        CMD.put("MUSIC_TOGGLE", new String[]{"موزیک", "پخش موزیک", "آهنگ", "موزیک پخش کن", "قطع موزیک", "موزیک خاموش"});
        CMD.put("VOL_UP", new String[]{"صدا بیشتر", "صدا زیاد", "زیاد کردن صدا", "صدا را زیاد کن", "صدای موزیک را زیاد کن", "صدا بالا"});
        CMD.put("VOL_DOWN", new String[]{"صدا کمتر", "صدا کم", "کم کردن صدا", "صدا را کم کن", "صدای موزیک را کم کن", "صدا پایین"});
    }

    static final Map<String, String> SPECIAL = new LinkedHashMap<>();
    static {
        SPECIAL.put("همکف", "G"); SPECIAL.put("طبقه همکف", "G"); SPECIAL.put("زمین", "G"); SPECIAL.put("صفر", "G");
        SPECIAL.put("روف", "ROOF"); SPECIAL.put("طبقه روف", "ROOF"); SPECIAL.put("بام", "ROOF"); SPECIAL.put("پشت بام", "ROOF");
        SPECIAL.put("رستوران", "RESTAURANT"); SPECIAL.put("طبقه رستوران", "RESTAURANT");
        SPECIAL.put("لابی", "LOBBY"); SPECIAL.put("طبقه لابی", "LOBBY");
        SPECIAL.put("نیم طبقه", "MEZZANINE"); SPECIAL.put("پارکینگ", "P"); SPECIAL.put("طبقه پارکینگ", "P"); SPECIAL.put("گراند", "G"); SPECIAL.put("گراند فلور", "G"); SPECIAL.put("ارضی", "G"); SPECIAL.put("الارضی", "G"); SPECIAL.put("زمین کات", "G"); SPECIAL.put("ریستوران", "RESTAURANT"); SPECIAL.put("ریستورنت", "RESTAURANT"); SPECIAL.put("مزانین", "MEZZANINE");
    }

    // ---------- زبان‌ها (املای فارسیِ تلفظ) ----------
    static class Lang {
        String[] ones, teens, tens, ord, ordSfx, pre, neg, park; String ten, joiner; boolean onesFirst;
    }
    static String[] v(String s) { return s.isEmpty() ? new String[0] : s.split("\\|"); }
    static String[] L(String s) { return s.split(","); }
    static final List<Lang> LANGS = new ArrayList<>();
    static final Map<String, Integer> NUM = new HashMap<>();
    static final Set<String> FILLER = new HashSet<>(Arrays.asList("طبقه", "فلور", "کات", "الطابق", "طابق", "دور", "شماره", "نامبر"));
    static final Set<String> BMARK = new HashSet<>(Arrays.asList("منفی", "ماینوس", "سالب", "اکسی", "بیسمنت"));
    static final String PMARK = "پارکینگ";

    static Lang lang(String ones, String ten, String teens, String tens, String ord, String ordSfx, String pre,
                     String neg, String park, String joiner, boolean onesFirst) {
        Lang l = new Lang();
        l.ones = L(ones); l.ten = ten; l.teens = L(teens); l.tens = L(tens);
        l.ord = ord.isEmpty() ? new String[0] : L(ord);
        l.ordSfx = ordSfx.isEmpty() ? new String[0] : L(ordSfx);
        l.pre = L(pre); l.neg = neg.isEmpty() ? new String[0] : L(neg); l.park = park.isEmpty() ? new String[0] : L(park);
        l.joiner = joiner; l.onesFirst = onesFirst;
        LANGS.add(l);
        return l;
    }
    static {
        // فارسی (+ محاوره و کردی نزدیک). ord: ترتیبی 1..19 ، سپس 20/30/40
        lang("یک|یه,دو|دوو,سه,چهار|چار|چوار,پنج|پینج,شش|شیش,هفت,هشت,نه|نو",
             "ده", "یازده,دوازده,سیزده,چهارده|چارده,پانزده|پونزده,شانزده|شونزده,هفده,هجده|هیجده,نوزده",
             "بیست,سی,چهل",
             "یکم|اول,دوم,سوم,چهارم,پنجم,ششم,هفتم,هشتم,نهم,دهم,یازدهم,دوازدهم,سیزدهم,چهاردهم,پانزدهم,شانزدهم,هفدهم,هجدهم,نوزدهم,بیستم,سی ام,چهلم",
             "", "طبقه", "منفی,طبقه منفی", "پارکینگ,پارکینگ شماره", "و", false);
        // انگلیسی
        lang("وان,تو,تری,فور,فایو|فایف,سیکس,سون,ایت,ناین", "تن",
             "ایلون,تولو,ترتین,فورتین,فیفتین,سیکستین,سونتین,ایتین,نایتین", "توانتی,ترتی,فورتی",
             "فرست,سکند,ترد,فورث,فیفث", "فلور", "فلور", "ماینوس,بیسمنت", "", "", false);
        // عربی
        lang("واحد,اثنین|اثنان,ثلاثه,اربعه,خمسه,سته,سبعه,ثمانیه,تسعه", "عشره",
             "احد عشر|احدی عشر,اثنا عشر|اثنی عشر,ثلاثه عشر,اربعه عشر,خمسه عشر,سته عشر,سبعه عشر,ثمانیه عشر,تسعه عشر",
             "عشرین,ثلاثین,اربعین",
             "الاول|الاولی,الثانی|الثانیه,الثالث|الثالثه,الرابع|الرابعه,الخامس,السادس,السابع,الثامن,التاسع,العاشر",
             "", "الطابق,طابق", "سالب", "", "و", true);
        // ترکی
        lang("بیر,ایکی,اوچ,دورت,بش,آلتی,یدی,سکیز,دوقوز", "اون",
             "اون بیر,اون ایکی,اون اوچ,اون دورت,اون بش,اون آلتی,اون یدی,اون سکیز,اون دوقوز",
             "یرمی,اوتوز,قیرق",
             "بیرینجی,ایکینجی,اوچونجو,دوردونجو,بشینجی", "کات", "کات", "اکسی", "", "", false);
        // ثبت کلمه‌های تکی برای تبدیل به عدد
        for (Lang l : LANGS) {
            for (int i = 0; i < l.ones.length; i++) for (String w : v(l.ones[i])) NUM.put(w, i + 1);
            NUM.put(l.ten, 10);
            for (int i = 0; i < l.teens.length; i++) for (String w : v(l.teens[i])) if (!w.contains(" ")) NUM.put(w, 11 + i);
            for (int i = 0; i < l.tens.length; i++) for (String w : v(l.tens[i])) NUM.put(w, 20 + 10 * i);
        }
        Lang fa = LANGS.get(0);
        for (int i = 0; i < 19; i++) for (String w : v(fa.ord[i])) NUM.put(w, i + 1);
        NUM.put("بیستم", 20); NUM.put("چهلم", 40); NUM.put("ام", 0);
        Lang ar = LANGS.get(2);
        for (int i = 0; i < ar.ord.length; i++) for (String w : v(ar.ord[i])) NUM.put(w, i + 1);
        Lang en = LANGS.get(1), tr = LANGS.get(3);
        for (int i = 0; i < en.ord.length; i++) NUM.put(en.ord[i], i + 1);
        for (int i = 0; i < tr.ord.length; i++) NUM.put(tr.ord[i], i + 1);
        NUM.put("احد", 1); NUM.put("احدی", 1); NUM.put("اثنا", 2); NUM.put("اثنی", 2); NUM.put("عشر", 10);
        NUM.put("و", -1);
    }

    /** همه گفتنِ کاردینالِ n در زبان l */
    static List<String> words(Lang l, int n) {
        List<String> r = new ArrayList<>();
        if (n <= 9) r.addAll(Arrays.asList(v(l.ones[n - 1])));
        else if (n == 10) r.add(l.ten);
        else if (n < 20) r.addAll(Arrays.asList(v(l.teens[n - 11])));
        else {
            int o = n % 10;
            for (String t : v(l.tens[n / 10 - 2])) {
                if (o == 0) { r.add(t); continue; }
                for (String w : v(l.ones[o - 1])) {
                    String j = l.joiner.isEmpty() ? " " : " " + l.joiner + " ";
                    r.add(l.onesFirst ? w + j + t : t + j + w);
                }
            }
        }
        return r;
    }

    static List<String> ordinals(Lang l, int n) {
        List<String> r = new ArrayList<>();
        if (l == LANGS.get(0)) {
            if (n <= 19) r.addAll(Arrays.asList(v(l.ord[n - 1])));
            else if (n % 10 == 0) r.add(l.ord[19 + n / 10 - 2 + (n == 40 ? 0 : 0)]);
            else r.add(words(l, n - n % 10).get(0) + " و " + v(l.ord[n % 10 - 1])[0]);
        } else if (n <= l.ord.length) r.addAll(Arrays.asList(v(l.ord[n - 1])));
        return r;
    }

    static List<String> floorPhrases() {
        Set<String> s = new LinkedHashSet<>();
        for (Lang l : LANGS) {
            for (int n = 1; n <= 45; n++) {
                for (String w : words(l, n)) { s.add(w); for (String p : l.pre) s.add(p + " " + w); }
                for (String w : ordinals(l, n)) {
                    s.add(w); for (String p : l.pre) s.add(p + " " + w);
                    for (String x : l.ordSfx) s.add(w + " " + x);
                }
            }
            for (int n = 1; n <= 10; n++) for (String w : words(l, n)) {
                for (String p : l.neg) s.add(p + " " + w);
                for (String p : l.park) s.add(p + " " + w);
            }
        }
        Lang fa = LANGS.get(0);
        for (int n = 1; n <= 10; n++) {
            for (String w : words(fa, n)) s.add("نیم طبقه " + w);
            for (String w : ordinals(fa, n)) { s.add("نیم طبقه " + w); }
        }
        s.addAll(SPECIAL.keySet());
        return new ArrayList<>(s);
    }

    static String grammarJson(boolean wake) {
        JSONArray a = new JSONArray();
        if (wake) { for (String w : WAKE) a.put(w); for (String w : DECOY) a.put(w); }
        else {
            // یک تشخیص‌دهندهٔ واحد: بیدارباش + فرمان در یک جمله («پنتا طبقه سه»)
            for (String w : WAKE) a.put(w);
            for (String w : DECOY) a.put(w);
            for (String[] ps : CMD.values()) for (String p : ps) a.put(p);
            for (String p : floorPhrases()) a.put(p);
        }
        a.put("[unk]");
        return a.toString();
    }

    static String norm(String s) {
        if (s == null) return "";
        s = s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", " ").replace("ـ", "")
             .replace("ۀ", "ه").replace("أ", "ا").replace("إ", "ا");
        return s.replaceAll("\\[unk\\]", " ").replaceAll("\\s+", " ").trim();
    }

    /** کلمه‌های بیدارباش و هم‌آوا را از متن حذف می‌کند تا فقط فرمان بماند. */
    static String stripWake(String text) {
        String t = " " + norm(text) + " ";
        for (String w : WAKE) t = t.replace(" " + w + " ", "  ");
        for (String w : DECOY) t = t.replace(" " + w + " ", "  ");
        return t.replaceAll("\\s+", " ").trim();
    }

    static boolean isWake(String text) {
        // کلمهٔ بیدارباش هرجای جمله باشد (مثلاً «آقا پنتا طبقه سوم») بیدار می‌کند.
        // کلمه‌های هم‌آوا (DECOY) در گرامر هستند و صدای مشابه را جذب می‌کنند.
        String t = " " + norm(text) + " ";
        for (String w : WAKE) if (t.contains(" " + w + " ")) return true;
        return false;
    }

    /** خروجی: JSONObject فرمان یا null. */
    static JSONObject parse(String raw) {
        try {
            String t = norm(raw);
            if (t.isEmpty()) return null;
            JSONObject o = new JSONObject();
            o.put("text", t);
            for (Map.Entry<String, String[]> e : CMD.entrySet())
                for (String p : e.getValue()) if (t.equals(p)) { o.put("cmd", e.getKey()); return o; }
            if (t.startsWith("نیم طبقه ")) {
                int m = 0;
                for (String w : t.substring(9).split(" ")) {
                    Integer x = NUM.get(w);
                    if (x == null) return null;
                    if (x > 0) m += x;
                }
                if (m < 1 || m > 10) return null;
                return floor(o, "M" + m);
            }
            String f = t.replaceFirst("^طبقه ", "");
            String sp = SPECIAL.get(t); if (sp == null) sp = SPECIAL.get(f);
            if (sp != null) return floor(o, sp);
            List<String> tk = new ArrayList<>();
            for (String w : f.split(" ")) if (!FILLER.contains(w)) tk.add(w);
            if (tk.isEmpty()) return null;
            String prefix = "";
            if (BMARK.contains(tk.get(0))) { prefix = "B"; tk.remove(0); }
            else if (tk.get(0).equals(PMARK)) { prefix = "P"; tk.remove(0); }
            if (tk.isEmpty()) return null;
            String joined = TextJoin(tk);
            if (prefix.equals("") && SPECIAL.get(joined) != null) return floor(o, SPECIAL.get(joined));
            int n = 0;
            for (String w : tk) {
                Integer x = NUM.get(w);
                if (x == null) return null;
                if (x > 0) n += x;
            }
            if (n <= 0) return null;
            if (prefix.equals("") && n > 45) return null;
            if (!prefix.equals("") && n > 10) return null;
            return floor(o, prefix.equals("") ? "F" + n : prefix + n);
        } catch (Exception e) { return null; }
    }

    private static String TextJoin(List<String> t) { StringBuilder b = new StringBuilder(); for (String w : t) { if (b.length() > 0) b.append(' '); b.append(w); } return b.toString(); }

    private static JSONObject floor(JSONObject o, String target) throws Exception {
        o.put("cmd", "FLOOR"); o.put("target", target); return o;
    }
}
