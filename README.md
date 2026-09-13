<div align="center">

<img src="https://raw.githubusercontent.com/zeiad6/PromptForge/main/art/playstore_512.png" width="140" alt="PromptForge logo" />

# 🔥 PromptForge — برومبت فورج

**Your complete on-device AI assistant — live chat, app auditing, and a professional prompt studio. Entirely from your phone, even offline.**

**مساعدك الذكي المتكامل على جهازك — محادثة حية، فحص التطبيقات، واستوديو هندسة أوامر احترافي. من هاتفك بالكامل، حتى بلا إنترنت.**

[![License](https://img.shields.io/badge/License-Personal%20Use%20Only-red)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-green)]()
[![Dual Engine](https://img.shields.io/badge/Engine-MediaPipe%20%2B%20LiteRT--LM-blueviolet)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple)]()
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-orange)]()

</div>

---

<div align="center">

| | |
|:---:|:---:|
| ![Home](https://raw.githubusercontent.com/zeiad6/PromptForge/main/art/shots/home.png) | ![Builder](https://raw.githubusercontent.com/zeiad6/PromptForge/main/art/shots/builder.png) |
| **Home — AI assistant front door** | **Builder — enhance, research, creativity** |
| ![Lab](https://raw.githubusercontent.com/zeiad6/PromptForge/main/art/shots/lab.png) | ![Device models](https://raw.githubusercontent.com/zeiad6/PromptForge/main/art/shots/models.png) |
| **Lab — live streaming chat, fully offline** | **On-device models — store, editor, downloads** |

</div>

---

## 🇬🇧 English

### ✨ What is PromptForge?

A **complete AI app that lives on your phone**. Chat with LLMs running **on the device
itself** (no internet, no PC), audit any installed app with your phone's own AI,
download and manage models with one tap — and use the professional **prompt
studio** (builder, templates, scoring, library) as a dedicated section.

### 🚀 Highlights

| | |
|---|---|
| 🤖 **Dual on-device engines** | `.task` bundles via **MediaPipe LLM Inference** + `.litertlm` models via **LiteRT-LM** — automatic dispatch, GPU→CPU self-healing, auto conversation trimming |
| 🛒 **Built-in model store** | One-tap downloads (SmolLM2 135M · OLMo 2 1B · Qwen2.5 1.5B) with **status-bar progress, pause/resume, auto-resume, true background operation** |
| 🛠️ **Model editor** | Rename installed models, tune max tokens (up to 8192), top-K, GPU/CPU — **no artificial limits** |
| 🔍 **App Scanner** | Facts extracted locally → your phone's AI writes a **deep audit report** with a 0–100 trust score |
| 🧪 **Lab** | Live streaming chat with any provider or on-device model; edit & resend, regenerate, stop, file attachments |
| 🧱 **App Builder** | Describe an app → the AI **builds it** (Android/Compose, desktop-ready, or a self-contained web app) → **live preview & test inside the app**, then export |
| 🧩 **Extensions** | **Skills** (translation, code, math, CV, research…) + **MCP servers** (Model Context Protocol tools) + **Smart Tools** that auto-apply to any task — even unasked |
| 📄 **Documents** | Open PDF / DOCX / MD / TXT from your phone: read, edit, **AI-summarize**, convert Markdown→HTML — mostly fully offline |
| 🏷️ **Model capabilities** | Every model shows what it can do: 📝 text · 💻 code · 🧠 reasoning · ⚡ fast |
| ✍️ **Prompt studio** | AI enhance, research pack with citations, **creativity-boost directives**, unlimited execution, live 0–100 scoring |
| 🎚️ **Creativity dial** | Temperature 0 → 1.5, presets **Precise / Balanced / Creative / Wild** |
| ☁️ **6 cloud providers** | Gemini · Groq · OpenRouter · Mistral · Cerebras · Ollama (LAN) — graceful 429 handling, auto model switching |
| 🔔 **Background work** | Foreground service + wake locks: downloads and inference survive app switching |
| 🌍 **Bilingual** | Full Arabic/English UI, instant switch, system-language detection |
| 🛡️ **Private by design** | No accounts, no analytics, no tracking — keys and models stay on the device |

> **About "Arena" models:** LMArena does not publish a public API for its models
> (verified), so PromptForge's agent features run on **your own models + real local
> tools** (skills, MCP, file & app tooling) — the same agentic pattern, fully private.

### 📲 Install

Grab the latest APK from [**Releases**](../../releases) and install it directly
(allow "install from unknown sources" when prompted).

### 🧠 On-device models in 3 steps

1. **Settings → On-device** → pick a model from the built-in store → one-tap download
2. When the download completes the model **activates automatically**
3. Open the **Lab** (or tap the assistant card on Home) and chat — **fully offline**

> Supports both model formats: **`.task`** (MediaPipe) and **`.litertlm`** (LiteRT-LM),
> dispatched automatically per file. Google's Gemma repos require accepting their
> license in a browser first — the app links to them with a clear note.

### 🛠️ Build from source

```bash
# Requirements: JDK 17, Android SDK (compileSdk 36), Gradle 8.x
git clone https://github.com/zeiad6/PromptForge.git
cd PromptForge
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleRelease
# APK → app/build/outputs/apk/release/
```

### 🏗️ Tech stack

Kotlin 2.x · Jetpack Compose Material 3 · MediaPipe tasks-genai 0.10.35 ·
LiteRT-LM 0.10.0 · PDFBox-Android · WebView · DataStore · OkHttp/Retrofit · Coroutines & StateFlow ·
IBM Plex Sans Arabic (OFL)

---

## 🇾🇪 العربية

### ✨ ما هو برومبت فورج؟

**تطبيق ذكاء اصطناعي متكامل يسكن في هاتفك**: تحدث مع موديلات LLM تعمل **داخل الجهاز
نفسه** (بلا إنترنت وبلا حاسوب)، افحص أي تطبيق مثبّت بذكاء هاتفك نفسه، نزّل الموديلات
وأدرها بنقرة واحدة — واستخدم **استوديو الأوامر الاحترافي** (الباني، القوالب، التقييم،
المكتبة) كقسم مخصص داخل التطبيق.

### 🚀 أبرز المميزات

| | |
|---|---|
| 🤖 **محركان على الجهاز** | ملفات **`.task`** عبر **MediaPipe** + موديلات **`.litertlm`** عبر **LiteRT-LM** — توزيع تلقائي حسب الصيغة، تعافٍ ذاتي GPU→CPU، وتقصير تلقائي للمحادثة |
| 🛒 **متجر موديلات مدمج** | تنزيل بنقرة (سمول إل‌إم 135M · أولمو 2 1B · كوين 2.5 1.5B) مع **إشعار تقدم، إيقاف/استكمال، استكمال تلقائي، وعمل حقيقي في الخلفية** |
| 🛠️ **محرر الموديلات** | إعادة تسمية، ضبط الحد الأقصى للمخرجات (حتى 8192)، top-K، محرك GPU/CPU — **بلا قيود اصطناعية** |
| 🔍 **فاحص التطبيقات** | الحقائق تُستخرج محلياً ← ذكاء هاتفك يكتب **تقرير تدقيق عميقاً** بدرجة ثقة من 100 |
| 🧪 **المختبر** | محادثة حية متدفقة مع أي مزوّد أو موديل محلي؛ تحرير وإعادة إرسال، تجديد، إيقاف، إرفاق ملفات |
| 🧱 **باني التطبيقات** | صِف تطبيقاً ← الذكاء الاصطناعي **يبنيه** (أندرويد/Compose، جاهز لسطح المكتب، أو تطبيق ويب مكتفٍ) ← **معاينة حية وتجربة داخل التطبيق** ثم تصدير |
| 🧩 **الإضافات** | **مهارات** (ترجمة، برمجة، حساب، سيرة ذاتية، بحث…) + **سيرفرات MCP** (أدوات بروتوكول Model Context Protocol) + **أدوات ذكية** تُطبَّق تلقائياً على أي مهمة — حتى لو لم تطلب |
| 📄 **المستندات** | افتح PDF / DOCX / MD / TXT من هاتفك: قراءة، تحرير، **تلخيص بالذكاء**، تحويل ماركداون←HTML — معظمه أوفلاين بالكامل |
| 🏷️ **قدرات الموديلات** | كل موديل يعرض ما يمكن أن يفعله: 📝 نص · 💻 كود · 🧠 استدلال · ⚡ سريع |
| ✍️ **استوديو الأوامر** | تعزيز بالذكاء الاصطناعي، حزمة بحث بالتوثيق، **توجيهات الإبداع**، تنفيذ بلا حدود، تقييم لحظي 0–100 |
| 🎚️ **مقبض الإبداع** | حرارة 0 → 1.5 مع مستويات **دقيق / متوازن / إبداعي / جامح** |
| ☁️ **6 مزودين سحابيين** | Gemini · Groq · OpenRouter · Mistral · Cerebras · Ollama (الشبكة المحلية) |
| 🔔 **العمل في الخلفية** | خدمة أمامية + أقفال استيقاظ: التنزيلات والاستدلال يكملان أثناء التنقل بين التطبيقات |
| 🌍 **ثنائي اللغة** | عربي/إنجليزي كامل، تبديل فوري، كشف لغة النظام |
| 🛡️ **خصوصية بالتصميم** | بلا حسابات أو تتبع أو تحليلات — المفاتيح والموديلات تبقى في جهازك |

> **بشأن موديلات Arena:** منصة LMArena لا توفر API عاماً لموديلاتها (تم التحقق)،
> لذلك تعمل ميزات الوكيل في برومبت فورج على **موديلاتك أنت + أدوات محلية حقيقية**
> (مهارات، MCP، أدوات ملفات وتطبيقات) — النمط الوكيلي نفسه وبخصوصية كاملة.

### 📲 التثبيت

حمّل أحدث APK من صفحة [**Releases**](../../releases) وثبّته مباشرة
(فعّل «التثبيت من مصادر غير معروفة» عند السؤال).

### 🧠 الموديلات المحلية في 3 خطوات

1. **الإعدادات ← على الجهاز** ← اختر موديلاً من المتجر المدمج ← تنزيل بنقرة
2. عند اكتمال التنزيل يُ**فعَّل الموديل تلقائياً**
3. افتح **المختبر** ( أو اضغط بطاقة المساعد في الرئيسية) — **أوفلاين بالكامل**

> يدعم التطبيق صيغتي الموديلات: **`.task`** (MediaPipe) و**`.litertlm`** (LiteRT-LM)
> مع توزيع تلقائي حسب الامتداد. مستودعات Gemma تتطلب قبول الترخيص من المتصفح أولاً،
> والتطبيق يوفر روابطها مع تنبيه واضح.

### 🛠️ البناء من المصدر

```bash
# المتطلبات: JDK 17، حزمة Android SDK (compileSdk 36)، Gradle 8.x
git clone https://github.com/zeiad6/PromptForge.git
cd PromptForge
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleRelease
# ملف APK → app/build/outputs/apk/release/
```

---

## 📄 License — الترخيص

This project is source-available for **personal use only** under the
[Personal Use License](LICENSE):

هذا المشروع متاح المصدر **للاستخدام الشخصي فقط** بموجب [رخصة الاستخدام الشخصي](LICENSE):

- ✅ **Personal**: use, study, modify for yourself, share unmodified copies
- ✅ **شخصي**: الاستخدام والدراسة والتعديل الخاص ومشاركة نسخ غير معدّلة
- ❌ **Commercial use strictly prohibited** — selling, paid services, or any revenue-generating use
- ❌ **الاستخدام التجاري ممنوع منعاً باتاً** — البيع أو الخدمات المدفوعة أو أي استغلال ربحي
- ©️ **All rights reserved by the author**, including reserved **patent rights** on its mechanisms and inventions — no patent rights are granted
- ©️ **جميع الحقوق محفوظة للمطوّر**، بما فيها حقوق **براءة الاختراع** المحفوظة على آلياته وابتكاراته — لا تُمنح أي حقوق براءة

---

<div align="center">

## 👨‍💻 Author — المطوّر

**زياد الحمادي — Ziad Al-hammadi**

📞 ‎+967 784 908 515 · ✉️ z30432981@gmail.com

*Made with ❤️ in Yemen — صُنع بحب في اليمن 🇾🇪*

</div>
