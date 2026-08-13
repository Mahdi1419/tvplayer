# TV Player

یک اپلیکیشن اندروید تی‌وی (Kotlin + Media3 ExoPlayer + Material Design) که:

- لینک ویدیو می‌گیرد و پخش می‌کند (mp4, mkv, HLS/m3u8, DASH/mpd و ...).
- تراک‌های صدا و زیرنویس داخلیِ خود فایل ویدیو را به‌صورت خودکار شناسایی می‌کند. دکمه‌های «صدا» و «زیرنویس» فقط همراه با کنترل‌های خود پلیر ظاهر و مخفی می‌شوند (نه به‌صورت همیشگی روی صفحه).
- دو تب دارد:
  - **لیست آنلاین**: به‌صورت پیش‌فرض یک فایل `txt` را از آدرسی مشخص دانلود می‌کند و لینک‌های داخل آن را در قالب کارت‌هایی با آیکون، عنوان و برچسب نمایش می‌دهد.
  - **ویدیوهای دستگاه**: با استفاده از MediaStore، ویدیوهای موجود روی حافظه‌ی گوشی/تلویزیون را اسکن و به همراه تامبنیل واقعی و مدت‌زمان نمایش می‌دهد.
- ظاهر تیره و حرفه‌ای بر پایه‌ی Material Design، با افکت فوکوس (بزرگ‌نمایی + برجستگی) مناسب ناوبری با ریموت.

## ساختار پروژه

پروژه‌ی استاندارد Android Studio (Gradle) است:

```
tvplayer/
  app/
    src/main/
      java/com/example/tvplayer/
        MainActivity.kt        -> صفحه‌ی اصلی: تب آنلاین/دستگاه، تنظیمات، افزودن لینک
        PlayerActivity.kt      -> صفحه‌ی پخش ویدیو + انتخاب تراک صدا/زیرنویس
        VideoAdapter.kt        -> نمایش کارت‌های لیست در RecyclerView
        LocalVideoScanner.kt   -> اسکن ویدیوهای دستگاه از طریق MediaStore
        VideoItem.kt
      res/layout/...
      res/values/...
      AndroidManifest.xml
```

## گرفتن APK بدون نصب چیزی (پیشنهادی)

یک ورک‌فلوی GitHub Actions در مسیر `.github/workflows/build.yml` داخل پروژه گذاشته شده که به‌طور خودکار APK را می‌سازد. مراحل:

1. یک ریپازیتوری جدید در گیت‌هاب بسازید و محتوای این پوشه (`tvplayer`) را در آن push کنید:
   ```bash
   cd tvplayer
   git init
   git add .
   git commit -m "TV Player"
   git branch -M main
   git remote add origin https://github.com/USERNAME/REPO.git
   git push -u origin main
   ```
2. به تب **Actions** در صفحه‌ی ریپازیتوری بروید؛ ورک‌فلوی «Build APK» خودش اجرا می‌شود (چند دقیقه طول می‌کشد).
3. وقتی سبز شد، وارد همان اجرا (run) شوید؛ در پایین صفحه دو فایل ضمیمه (Artifacts) می‌بینید:
   - `tvplayer-debug-apk` → مستقیماً قابل نصب روی تلویزیون/دستگاه برای تست.
   - `tvplayer-release-apk-unsigned` → نسخه‌ی ریلیز، برای انتشار باید امضا (sign) شود.
4. Artifact را دانلود، از حالت zip خارج و APK داخلش را روی دستگاه اندروید تی‌وی نصب کنید (از طریق ADB یا یک فایل‌منیجر).

## راه‌اندازی محلی (Android Studio)

1. پوشه‌ی `tvplayer` را در Android Studio باز کنید (Open an existing project).
2. در فایل `app/src/main/java/com/example/tvplayer/MainActivity.kt` مقدار زیر را با آدرس واقعی فایل txt خودتان جایگزین کنید:

```kotlin
private val defaultListUrl = "https://example.com/videos.txt"
```

   (کاربر می‌تواند این آدرس را داخل خودِ برنامه هم موقتاً تغییر دهد و دوباره «بارگیری لیست» را بزند.)

3. برنامه را روی یک دستگاه اندروید تی‌وی یا شبیه‌ساز Android TV اجرا کنید (Run ▶).

## فرمت فایل txt

هر خط از فایل می‌تواند یکی از این دو شکل باشد:

```
https://example.com/video1.mp4
عنوان دلخواه ویدیو, https://example.com/video2.mkv
# این خط چون با # شروع می‌شود نادیده گرفته می‌شود
```

- اگر فقط لینک باشد، همان لینک به‌عنوان عنوان هم نمایش داده می‌شود.
- اگر قبل از لینک، یک عنوان و کاما بیاید، همان عنوان در لیست نشان داده می‌شود.

## دسترسی به ویدیوهای دستگاه

اولین بار که وارد تب «ویدیوهای دستگاه» شوید، برنامه یک درخواست دسترسی (READ_MEDIA_VIDEO در اندروید ۱۳ به بالا، یا READ_EXTERNAL_STORAGE در نسخه‌های قدیمی‌تر) نمایش می‌دهد. با تأیید آن، لیست ویدیوهای موجود روی حافظه (همراه با تامبنیل و مدت‌زمان واقعی) نمایش داده می‌شود.

## نکات فنی

- پخش‌کننده از **Media3 ExoPlayer** استفاده می‌کند. برای لینک‌های آنلاین از OkHttp استفاده شده تا روی Android TVهای قدیمی‌تر، خطاهای زنجیره‌ی گواهی TLS باعث توقف پخش نشوند. این حالت سازگاری فقط روی Android TV فعال است؛ موبایل همچنان اعتبارسنجی عادی گواهی را انجام می‌دهد.
- تشخیص تراک‌های صدا و زیرنویس با استفاده از `player.currentTracks` انجام می‌شود؛ نیازی به تنظیم دستی demuxer نیست، ExoPlayer به‌صورت خودکار همهٔ تراک‌های داخل container (مثل mkv یا mp4) را شناسایی می‌کند.
- دانلود فایل txt با `HttpURLConnection` ساده در یک ترد جداگانه انجام می‌شود (بدون وابستگی اضافه به کتابخانه‌های شبکه‌ای سنگین مثل Retrofit/OkHttp) تا حجم و وابستگی‌های برنامه کم بماند.
- اپ هم `LEANBACK_LAUNCHER` (برای نمایش روی صفحه‌ی اصلی اندروید تی‌وی) و هم `LAUNCHER` معمولی را پشتیبانی می‌کند، پس روی موبایل هم قابل نصب و اجراست.

## توسعه‌های پیشنهادی بعدی

- افزودن remember آخرین آدرس list url با `SharedPreferences`.
- افزودن قابلیت جست‌وجو/فیلتر در لیست ویدیوها.
- افزودن پشتیبانی از زیرنویس خارجی (`.srt`/`.vtt`) در صورت نیاز، از طریق `MediaItem.SubtitleConfiguration`.

## Build notes
- `assembleDebug` creates the installable debug APK.
- `assembleRelease` creates an optimized, R8/minified release APK. The GitHub workflow uploads it as `app-release-unsigned.apk` because no private signing key is stored in the repository.
- On Android TV firmware that does not expose USB media through MediaStore, the app uses the system document picker to grant persistent read access to the USB folder and then scans it recursively.
- TV player remote keys are handled explicitly for play/pause, stop, rewind/fast-forward and D-pad seeking.
