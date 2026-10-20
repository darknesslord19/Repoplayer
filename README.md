# RepoPlayer

Sade bir Android / Android TV oynatıcısı. Sadece iki şeyi yapar:

1. **CloudStream `repo.json`** (`.cs3` eklentileri) ve **Nuvio `manifest.json`** (JS sağlayıcıları) ekler — Stremio eklenti `manifest.json` adresleri de çalışır.
2. Alttaki seçiciden hangi kaynağı seçersen ana sayfayı, aramayı ve oynatmayı **o kaynağa göre** gösterir.

| Özellik | Durum |
|---|---|
| CS3 eklentileri (repo.json / plugins.json) | ✅ eklenti seçilince indirilir ve yüklenir |
| Nuvio sağlayıcı repoları (`scrapers` içeren manifest.json) | ✅ gizli WebView içinde çalışır (cheerio, crypto-js, axios hazır) |
| Stremio eklentileri (catalog / meta / stream / subtitles) | ✅ |
| Dahili oynatıcı (ExoPlayer: HLS, DASH, MP4) | ✅ başlık (Referer, User-Agent) desteğiyle |
| Altyazı: kaynak altyazıları + OpenSubtitles + dosyadan ekleme (SRT/VTT/ASS) | ✅ Türkçe otomatik seçilir |
| Kaldığın yerden devam, izleme geçmişi, kaydedilenler | ✅ |
| Hatalı kaynakta otomatik sonrakine geçiş, sonraki bölüm | ✅ |
| Harici oynatıcı (VLC, MX Player, seçici) | ✅ |
| Android TV (kumanda ile gezinme, TV başlatıcısı), PiP | ✅ |
| Torrent / magnet bağlantıları | ❌ atlanır |

---

## APK nasıl derlenir (bilgisayar gerekmez)

1. GitHub'da yeni bir depo aç (ör. `RepoPlayer`), bu klasördeki **tüm dosyaları** yükle
   (`.github` klasörü dahil — gizli klasör olduğu için yüklemeyi unutma).
2. Deponun **Actions** sekmesine gir. "APK derle" iş akışı kendiliğinden başlar
   (başlamazsa *Run workflow*'a bas). İlk derleme ~10–15 dakika sürer.
3. Bitince çalışmanın sayfasındaki **Artifacts → RepoPlayer-apk** dosyasını indir, zip'ten çıkar,
   APK'yı telefona/TV'ye kur.
4. Kalıcı sürüm istersen bir etiket gönder: `git tag v1.0.0 && git push --tags` →
   APK otomatik olarak **Releases** sayfasına eklenir.

> APK, depodaki `player/keystore.jks` ile imzalanır; böylece yeni sürümler eskisinin üzerine
> kurulur. Kendi anahtarını kullanmak istersen dosyayı değiştir ve `RP_STORE_PASSWORD`,
> `RP_KEY_ALIAS`, `RP_KEY_PASSWORD` ortam değişkenlerini Actions secret'ı olarak ekle.

### Android Studio ile (isteğe bağlı)

```bash
./setup-local.sh          # CloudStream kütüphanesini indirir, JS paketini üretir
cd cs && ./gradlew :player:assembleDebug
```
Android Studio'da `cs` klasörünü aç; `player` modülü orada görünür (bu repodaki `player`'a bağlıdır).

---

## Kullanım

1. **Ayarlar → Repo / manifest ekle** alanına adresi yapıştır:
   - CloudStream: `https://…/repo.json` (veya doğrudan `plugins.json`, `cloudstreamrepo://…`)
   - Nuvio: `https://raw.githubusercontent.com/<kullanıcı>/<repo>/main/manifest.json`
   - Stremio eklentisi: `https://…/manifest.json`
2. Ana sayfanın sağ altındaki **kaynak hapına** (ör. `● HDFilmCehennemi ⌄`) dokun, kaynağı seç.
   Ana sayfa, arama ve oynatma artık o kaynaktan gelir.
3. Bir içeriğe gir → **Oynat** / bölüm seç → açılan listeden kaynağı seç
   (veya **En iyi kaynağı oynat**). Sağdaki ↗ simgesi harici oynatıcıda açar.

**Nuvio için TMDB anahtarı:** Nuvio sağlayıcıları içerikleri TMDB kimliğiyle arar. Ana sayfa
ve arama için *Ayarlar → TMDB API anahtarı* alanına ücretsiz anahtarını gir
(themoviedb.org → Ayarlar → API). Hangi sağlayıcıların çalışacağını *Ayarlar → repo →
Sağlayıcılar* bölümünden açıp kapatabilirsin.

**Oynatıcı ekranı:** üst çubukta altyazı dosyası ekleme, kaynak değiştirme, görüntü boyutu
(sığdır/yakınlaştır/doldur), harici oynatıcı ve sonraki bölüm düğmeleri var. Ses/altyazı
izi ve hız ayarı alt çubuktaki ⚙ menüsünde. TV kumandasında sol/sağ 10 sn atlar.

---

## Nasıl çalışır

```
player/src/main/java/com/repoplayer/app/
├── source/
│   ├── ContentSource.kt      ← tüm kaynakların ortak arayüzü (home/search/details/links)
│   ├── SourceManager.kt      ← adresi indirir, türünü algılar (repo.json / manifest.json)
│   ├── cs3/Cs3.kt            ← .cs3 indirme + DexClassLoader ile yükleme + MainAPI köprüsü
│   ├── nuvio/                ← WebView JS motoru + Nuvio sağlayıcı çalıştırıcı
│   ├── stremio/              ← Stremio eklenti protokolü
│   └── Meta.kt               ← TMDB ve OpenSubtitles
├── player/                   ← ExoPlayer ekranı, harici oynatıcılar
└── ui/                       ← Compose ekranları (Ana sayfa, Ara, Kütüphane, Ayarlar)
player/src/main/java/com/lagradost/cloudstream3/plugins/Plugin.kt  ← .cs3'lerin beklediği sınıf
player/src/main/assets/js/runtime.js                               ← fetch/require/axios köprüsü
js/                                                                ← cheerio + crypto-js paketi (CI'da üretilir)
```

- **Neden CloudStream kaynak kodu indiriliyor?** `.cs3` dosyaları, CloudStream'in kütüphane
  sınıflarını (`MainAPI`, `ExtractorLink`, hazır video çıkarıcılar…) çağıran derlenmiş koddur.
  Uygulama sadece bu **kütüphaneyi** içerir; CloudStream arayüzü yoktur. Sürüm
  `.github/workflows/build.yml` içindeki `CS_REF` ile sabitlenmiştir.
- **Nuvio sağlayıcıları** gizli bir WebView'da çalışır; `fetch` istekleri Android tarafında
  yapıldığı için CORS sorunu olmaz.

## Bilinen sınırlamalar

- CloudStream uygulamasının kendi ekranlarına/ayarlarına (hesap girişi, eklenti ayar sayfası,
  `AcraApplication` vb.) bağımlı az sayıdaki eklenti yüklenebilir ama o özelliği kullanırken hata
  verebilir. Sadece kütüphaneyi kullanan eklentiler (çoğunluk) sorunsuz çalışır.
- Torrent/magnet akışları desteklenmez.
- Bazı eklentilerin özel video interceptor'ları dahili oynatıcıya aktarılmaz; o kaynakta sorun
  olursa listeden başka bir kaynak veya harici oynatıcı dene.
