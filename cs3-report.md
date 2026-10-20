# CS3 eklenti teşhis raporu
Çalıştırma: GitHub Actions (ABD) · 2026-10-20T20:22:34.486083317Z

## DiziBox
- yüklendi: !DiziBox
### !DiziBox  (https://www.dizibox.live) · ana sayfa=true
- getMainPage HATA: NotImplementedError: An operation is not implemented: Not yet implemented @ WebViewResolver.resolveUsingWebView
- search HATA: NotImplementedError: An operation is not implemented: Not yet implemented @ WebViewResolver.resolveUsingWebView
- search("a"): 0 sonuç

## DiziYou
- yüklendi: !DiziYou
### !DiziYou  (https://www.diziyou.one) · ana sayfa=true
- getMainPage("Dizi Arşivi"): 1 liste, 0 öğe · 261 ms
- search("a"): 0 sonuç

## Dizilla
- yüklendi: !Dizilla
### !Dizilla  (https://dizilla.now) · ana sayfa=true
- getMainPage HATA: ErrorLoadingException: Dizilla yanıtı alınamadı (HTTP 403) @ DizillaProvider.request
- search HATA: ErrorLoadingException: Dizilla yanıtı alınamadı (HTTP 403) @ DizillaProvider.request
- search("a"): 0 sonuç

## Dizipal
- yüklendi: !Dizipal
### !Dizipal  (https://dizipal2134.com) · ana sayfa=true
- getMainPage("Yeni Diziler"): 1 liste, 0 öğe · 295 ms
- search("a"): 0 sonuç

## FilmMakinesi
- yüklendi: !FilmMakinesi
### !FilmMakinesi  (https://filmmakinesi.to) · ana sayfa=true
- getMainPage("Son Filmler"): 1 liste, 0 öğe · 203 ms
- search("a"): 0 sonuç

## FullHDFilmizlesene
- yüklendi: !FullHDFilmizlesene
### !FullHDFilmizlesene  (https://www.fullhdfilmizlesene.now) · ana sayfa=true
- getMainPage HATA: NotImplementedError: An operation is not implemented: Not yet implemented @ WebViewResolver.resolveUsingWebView
- search HATA: NotImplementedError: An operation is not implemented: Not yet implemented @ WebViewResolver.resolveUsingWebView
- search("a"): 0 sonuç

## HDFilmCehennemi
- yüklendi: !HDFilmCehennemi
### !HDFilmCehennemi  (https://www.hdfilmcehennemi.nl) · ana sayfa=true
- getMainPage("Bugün Ne İzlesem?"): 1 liste, 0 öğe · 309 ms
- search("a"): 0 sonuç

## SelcukFlix
- yüklendi: !SelcukFlix
### !SelcukFlix  (https://selcukflix.com) · ana sayfa=true
- getMainPage HATA: ErrorLoadingException: SelcukFlix/kaynak yanıtı: 403. Site doğrulaması gerekiyorsa yeniden deneyin. @ SelcukFlixProvider.request
- search HATA: ErrorLoadingException: SelcukFlix/kaynak yanıtı: 403. Site doğrulaması gerekiyorsa yeniden deneyin. @ SelcukFlixProvider.request
- search("a"): 0 sonuç

## SetFilm
- yüklendi: !SetFilm
### !SetFilm  (https://www.setfilmizle.ltd) · ana sayfa=true
- getMainPage("Filmler"): 1 liste, 24 öğe · 1170 ms
- Afiş denemesi:
  - Doğaüstü → `https://www.setfilmizle.ltd/wp-content/uploads/2026/10/chronicle-2012-izle.webp`
      başlıksız: 206 image/webp [webp] · Referer: 206 image/webp [webp]
  - Hitchcock → `https://www.setfilmizle.ltd/wp-content/uploads/2026/10/hitchcock-2012-izle.webp`
      başlıksız: 206 image/webp [webp] · Referer: 206 image/webp [webp]
  - Ağzımdan Kaçtı → `https://www.setfilmizle.ltd/wp-content/uploads/2025/12/i-swear-2025-izle.webp`
      başlıksız: 206 image/webp [webp] · Referer: 206 image/webp [webp]
- load("Doğaüstü") `https://www.setfilmizle.ltd/film/dogaustu-2012-izle/`
  tür=MovieLoadResponse · afiş=`https://www.setfilmizle.ltd/wp-content/uploads/2026/10/chronicle-2012-…` · posterHeaders=null
  loadLinks=true · 1 bağlantı, 4 altyazı · 2255 ms
  - **SetFilm - fastplay.mom** · M3U8 · q=400 · `https://fastplay.mom/manifests/aM4IGegzwasd/master.txt?verify=1792519200-%2FAYVJvQyXQYbdpE…`
      başlıklar=[User-Agent, Referer] · başlıklı: 404 application/octet-stream [] · başlıksız: 200 application/vnd.apple.mpegurl; charset=utf-8 [m3u8]

## WebDramaTurkey
- yüklendi: !WebDramaTurkey
### !WebDramaTurkey  (https://webdramaturkey2.com) · ana sayfa=true
- getMainPage("Diziler"): 1 liste, 20 öğe · 678 ms
- Afiş denemesi:
  - Extraordinary Attorney Minami → `https://cdn.webdrama.io/cover/extraordinary-attorney-minami-vtn1egt8976.jpeg`
      başlıksız: 403 text/html; charset=UTF-8 [HTML!] · Referer: 403 text/html; charset=UTF-8 [HTML!]
  - Gift → `https://cdn.webdrama.io/cover/gift-vtn1e7g1fdb.jpeg`
      başlıksız: 403 text/html; charset=UTF-8 [HTML!] · Referer: 403 text/html; charset=UTF-8 [HTML!]
  - Miracles of the ER → `https://cdn.webdrama.io/cover/miracles-of-the-er-vtn1dts4c89.jpeg`
      başlıksız: 403 text/html; charset=UTF-8 [HTML!] · Referer: 403 text/html; charset=UTF-8 [HTML!]
- load("Extraordinary Attorney Minami") `https://webdramaturkey2.com/dizi/extraordinary-attorney-minami`
  dizi: 1 bölüm
  tür=TvSeriesLoadResponse · afiş=`https://cdn.webdrama.io/cover/extraordinary-attorney-minami-vtn1egt897…` · posterHeaders=null
  loadLinks=true · 2 bağlantı, 0 altyazı · 3046 ms
  - **!WebDramaTurkey - WDT** · M3U8 · q=400 · `https://dtpasn.asia/cdn/hls/a796045557f268b26764d131267df235/master.m3u8?md5=WDXnSarB2G3hj…`
      başlıklar=[referer] · başlıklı: 200 application/x-mpegURL [m3u8] · başlıksız: 200 application/x-mpegURL [m3u8]
  - **!WebDramaTurkey - WDT** · M3U8 · q=400 · `https://dtpasn.asia/cdn/hls/a796045557f268b26764d131267df235/master.txt`
      başlıklar=[referer] · başlıklı: 200 text/html; charset=UTF-8 [security err] · başlıksız: 200 text/html; charset=UTF-8 [security err]

## YesilcamTV
- yüklendi: !YesilcamTV
### !YesilcamTV  (https://yesilcamtv.com.tr) · ana sayfa=true
- getMainPage("Aile"): 1 liste, 20 öğe · 1162 ms
- Afiş denemesi:
  - Gelinlik Kızlar → `https://yesilcamtv.com.tr/wp-content/uploads/2025/01/gelinlik-kizlar-180x270.jpg`
      başlıksız: 206 image/jpeg [jpg] · Referer: 206 image/jpeg [jpg] · posterHeaders[User-Agent, Referer]: 206 image/jpeg [jpg]
  - Ayşecik Yuvanın Bekçileri → `https://yesilcamtv.com.tr/wp-content/uploads/2026/01/aysecik-yuvanin-bekcileri-180x270.jpg`
      başlıksız: 206 image/jpeg [jpg] · Referer: 206 image/jpeg [jpg] · posterHeaders[User-Agent, Referer]: 206 image/jpeg [jpg]
  - Biçaklar fora → `https://yesilcamtv.com.tr/wp-content/uploads/2025/02/MV5BMTQ5YjQ4YjMtODI0NS00NTg3LTg5ZTItM…`
      başlıksız: 206 image/jpeg [jpg] · Referer: 206 image/jpeg [jpg] · posterHeaders[User-Agent, Referer]: 206 image/jpeg [jpg]
- load("Gelinlik Kızlar") `https://yesilcamtv.com.tr/gelinlik-kizlar-izle/`
  load HATA: NoSuchMethodError: 'int android.util.Log.w(java.lang.String, java.lang.String, java.lang.Throwable)' @ YesilcamTVProvider.optional
- load("Ayşecik Yuvanın Bekçileri") `https://yesilcamtv.com.tr/aysecik-yuvanin-bekcileri-izle-2/`
  load HATA: NoSuchMethodError: 'int android.util.Log.w(java.lang.String, java.lang.String, java.lang.Throwable)' @ YesilcamTVProvider.optional

