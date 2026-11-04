# نقشه قابلیت‌های صبا استودیو

این جدول وجود پیاده‌سازی را از عملکرد تأییدشده جدا می‌کند. نتایج اجرا در
REPORT.md و فایل‌های شواهد ثبت می‌شوند. هیچ سرویس نیازمند کلید، بدون کلید
شخصی آزمایش نشده است.

| قابلیت | پیاده‌سازی اندروید | نسبت با سورس‌های واردشده | محدودیت |
|---|---|---|---|
| انتخاب مدل و ارائه‌دهنده | ProviderRepository، ModelCatalog، ProviderSheet | آدرس‌های FCC و ChatAnywhere در فهرست بومی | فهرست مدل، اثبات تولید پاسخ نیست |
| چت استریم و فراخوانی ابزار | OpenAiCompatProvider، AnthropicProvider، GeminiProvider، AgentEngine | جایگزین بومی برای API/proxy پایتون | هر ارائه‌دهنده نیازمند آزمون مجاز جداگانه است |
| مدل رایگان بدون کلید | HarnessProvider → Kilo | مسیر مستقیم بومی؛ وابسته به FCC Python نیست | سهمیه و دسترسی خارجی متغیر است |
| GPT API Free | ChatAnywhere presets → OpenAiCompatProvider | اتصال بومی به سرویس؛ Python demos در APK اجرا نمی‌شوند | کلید شخصی، حساب و محدودیت سرویس لازم است |
| ارائه‌دهندگان FCC | ProviderBrands → native transport | preset و پروتکل‌های سازگار، نه اجرای کامل FCC | OAuth اختصاصی/حساب Copilot، Claude و سایر حساب‌ها خودکار منتقل نمی‌شود |
| پروژه‌ها، نشست‌ها و سابقه | WorkspaceManager، SessionRepository، Room | جایگزین بومی برای مدیریت نشست دسکتاپ | آزمون مهاجرت و بازکردن مجدد جداگانه |
| فایل و ابزار کدنویسی | FileTools، Sora editor، checkpoints | مستقل از CLIهای دسکتاپ FCC | مجوز پوشه، SAF، حالت خواندنی و rollback باید رعایت شوند |
| تنظیمات کلیدها | KeyStoreManager، EncryptedSharedPreferences | کلید شخصی روی دستگاه، نه سورس یا APK | مدیریت Secrets مخزن با دسترسی فعلی مسدود است |
| فارسی، RTL، تاریک | values-fa، supportsRtl، Compose theme | رابط بومی | بازبینی تصویری و آزمون گوشی فیزیکی مستقل است |
| مدل محلی | LocalModelProvider، JNI/llama.cpp | جایگزین اندرویدی، نه torch/CUDA پایتون | NDK، ABI سازگار، مدل GGUF و آزمون inference لازم است |
| محیط Linux، SSH، root، Shizuku، ADB | پیاده‌سازی‌های بومی موجود | جایگزین اجرای کورکورانه ابزار دسکتاپ | دسترسی و راه‌اندازی صریح روی دستگاه لازم است |
| Telegram | زیرسیستم بومی موجود | جدا از python-telegram-bot در FCC | توکن و آزمون سرویس لازم است؛ این اجرا پیام ارسال نکرده است |
| GitHub و حساب ChatGPT داخل اپ | زیرسیستم بومی مدیریت حساب/مخزن | مستقل از دسترسی GitHub همین اجرای Codex | ورود و عملیات واقعی داخل اپ با حساب شخصی در این اجرا آزموده نشده است |
| MCP، افزونه، SSH/SFTP/FTP و سرویس‌های پس‌زمینه | کد بومی موجود و حفظ‌شده | نمونه آزمون‌های JVM بخشی از پوشش کلی‌اند | اتصال زنده به سرور/کلید/مجوزهای متناظر جداگانه لازم است |
| گفتار و صدا | Android STT/TTS و مسیرهای دارای ارائه‌دهنده | جایگزین‌های بومی؛ نه اجرای torch/Riva دسکتاپ | میکروفون واقعی، پخش صدا و سرویس دارای کلید آزموده نشده‌اند |
| FCC Python/FastAPI server | فقط سورس vendored و launcher تشخیصی guarded | نسخه دقیق Python 3.14.10 و وابستگی‌های upstream | در APK نصب یا اجرا نشده؛ تکمیل‌شده اعلام نمی‌شود |
| FCC desktop tray، CLI launchers، Discord، Riva/torch voice | آرشیو third_party | سورس حفظ شده | قابلیت فعال اپ اندروید محسوب نمی‌شود |
| GPT API Free تصویر/صوت/Transcription demos | آرشیو third_party/GPT_API_free/demo | صرفاً نمونه کلاینت API | این نمونه‌ها به‌تنهایی قابلیت فعال رابط اندروید نیستند |

ساختار: ماژول اصلی `:app` (Kotlin، Compose، Room، DataStore، OkHttp، JNI)،
ماژول اختیاری Play `:local_gpu`، ابزار JavaScript ویرایشگر در
`scripts/editor-tools`، دو درخت واردشده بدون تغییر. ۵۹۰ فایل Kotlin اصلی،
۲۹۰ فایل تست JVM و ۱۳۰ فایل تست اندروید در زمان ممیزی وجود داشت؛ تعداد فایل
تست به معنای اجرای آن‌ها نیست.

امنیت: کلیدها در EncryptedSharedPreferences مبتنی بر Android Keystore؛ قواعد
backup فایل کلیدها و Linux token-bearing paths را خارج می‌کنند. SecretRedactor
خروجی ابزارها را پالایش می‌کند. اسکن محدود literalهای شناخته‌شده در
`app/src/main` موردی پیدا نکرد؛ این نتیجه جایگزین ممیزی جامع امنیت نیست.
Cleartext برای سرویس محلی/SSH و گردش‌کارهای موجود مجاز مانده و حذف نشده است؛
برای ارائه‌دهندگان آنلاین از HTTPS استفاده می‌شود.

مجوزها: مجوز MIT پروژه اصلی و مجوزهای third_party حفظ شده‌اند. FCC دارای
AGPL-3.0-only است؛ پیش از انتشار عمومی باید تعهدات توزیع هر جزء بررسی شوند.
