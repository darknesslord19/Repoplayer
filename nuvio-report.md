# Nuvio sağlayıcı teşhis raporu
Manifest: https://raw.githubusercontent.com/manitux-app/nuvio-plugins/refs/heads/main/manifest.json · 2026-10-20T20:21:25.040Z

## !HDFilmCehennemi (hdfilmcehennemi)
- getCatalog(new-movies/movie): 0 öğe · next=undefined · 407 ms
- getCatalog(new-series/series): 0 öğe · next=undefined · 72 ms
- search("kurt"): 0 sonuç
- Afiş denemesi:

## !FilmMakinesi (filmmakinesi)
- getCatalog(latest-movies/movie): 0 öğe · next=undefined · 240 ms
- getCatalog(must-watch/movie): 0 öğe · next=undefined · 90 ms
- search("kurt"): 0 sonuç
- Afiş denemesi:

## !FilmEkseni (filmekseni)
- getCatalog(movies/movie): 24 öğe · next=2 · 761 ms
  ilk öğe alanları: id, name, type, url, poster · externalIds=null
- getCatalog(series/series): 24 öğe · next=2 · 584 ms
  ilk öğe alanları: id, name, type, url, poster · externalIds=null
- search("kurt"): 24 sonuç
- Afiş denemesi:
  - Bad Apples → `https://filmekseni.vip/uploads/posters/webp/bad-apples-3ju9e8.webp`
      başlıksız: 206 image/webp [webp]
  - Nutcrackers → `https://filmekseni.vip/uploads/posters/webp/nutcrackers.webp`
      başlıksız: 206 image/webp [webp]
  - Balina Düşüşü → `https://filmekseni.vip/uploads/posters/webp/balina-dususu-tuw8ys.webp`
      başlıksız: 206 image/webp [webp]
- getStreams(Bad Apples): 0 akış · 573 ms
- getStreams(Organize İşler: Karun Hazinesi S1B1): 0 akış · 220 ms

## !DiziBal (dizibal)
- getCatalog(latest-movies/movie): 24 öğe · next=2 · 622 ms
  ilk öğe alanları: id, name, type, url, poster · externalIds=null
- getCatalog(latest-series/series): 24 öğe · next=2 · 475 ms
  ilk öğe alanları: id, name, type, url, poster · externalIds=null
- search("kurt"): 12 sonuç
- Afiş denemesi:
  - Örümcek-Adam: Yepyeni Bir Gün → `https://dizibal.org/storage/tmdb/posters/w342/bVdxrH4BQc3YLT2heDo4g0I9oGa.webp`
      başlıksız: 206 image/webp [webp]
  - Saplantı → `https://dizibal.org/storage/tmdb/posters/w342/rjQ0GbYsLX6WDWefPrxGMCqPqx6.jpg`
      başlıksız: 206 image/jpeg [jpg]
  - Backrooms → `https://dizibal.org/storage/tmdb/posters/w342/pR7HAA2STN78NN7egkrp9DsKI7c.jpg`
      başlıksız: 206 image/jpeg [jpg]
- getStreams(Örümcek-Adam: Yepyeni Bir Gün): 0 akış · 343 ms
- getStreams(The Scandal S1B1): 1 akış · 811 ms
  - **!DiziBal - Orijinal** manitux · q=1080p · `https://play2.pilavyerplay.top/api/stream.php?v=a53ff7ccbff6e1ac517875a5629ca2ee&token=eyJ…`
      başlıklar=["User-Agent","Accept","Referer","Accept-Language"] · başlıklı: 200 application/vnd.apple.mpegurl [m3u8] · başlıksız: 200 application/vnd.apple.mpegurl [m3u8]

## !DiziYou (diziyou)
- getCatalog(family/series): 0 öğe · next=undefined · 194 ms
- getCatalog(action/series): 0 öğe · next=undefined · 67 ms
- search("kurt"): 0 sonuç
- Afiş denemesi:

## !DexterPW (dexterpw)
- getCatalog(action-movies/movie): 40 öğe · next=2 · 285 ms
  ilk öğe alanları: id, name, type, url, poster, score · externalIds=null
- getCatalog(adventure-movies/movie): 40 öğe · next=2 · 279 ms
  ilk öğe alanları: id, name, type, url, poster, score · externalIds=null
- search("kurt"): 0 sonuç
- Afiş denemesi:
  - Spider-Man: Brand New Day → `https://image.tmdb.org/t/p/w500/bjiS5ipwxb9JFy3XRRN4OAilSeX.jpg`
      başlıksız: 206 image/jpeg [jpg]
  - Matchbox the Movie → `https://image.tmdb.org/t/p/w500/lFKA4GMz36dImEy3DQPdPF7G4V9.jpg`
      başlıksız: 206 image/jpeg [jpg]
  - The Fix → `https://image.tmdb.org/t/p/w500/jlP8XDjZN89GrpM3je5Uf8Grqx8.jpg`
      başlıksız: 206 image/jpeg [jpg]
- getStreams(Spider-Man: Brand New Day): 1 akış · 76 ms
  - **!DexterPW - Server 21** manitux · q=1080p · `https://dexter.pw/api/s/21/movie/969681?v=3`
      başlıklar=["User-Agent","Accept","Referer","Accept-Language"] · başlıklı: 200 application/vnd.apple.mpegurl; charset=utf-8 [m3u8] · başlıksız: 403 text/html; charset=UTF-8 [HTML!]

## !RareFilm (rarefilm)
- getCatalog(latest/movie): HATA SyntaxError: Unexpected token ':'
    at new Function (<anonymous>)
    at loadModule (evalmachine.<anonymous>:214:16)
    at rp.call (evalmachine.<anonymous>:231:17)
    at evalmachine.<anonymous>:1:… · next=undefined · 0 ms
- getCatalog(action/movie): HATA SyntaxError: Unexpected token ':'
    at new Function (<anonymous>)
    at loadModule (evalmachine.<anonymous>:214:16)
    at rp.call (evalmachine.<anonymous>:231:17)
    at evalmachine.<anonymous>:1:… · next=undefined · 0 ms
- search("kurt"): HATA SyntaxError: Unexpected token ':'
    at new Function (<anonymous>)
    at loadModule (evalmachine.<anonymous>:214:16)
    at rp.call (evalmachine.<anonymous>:231:17)
    at evalmachine.<anonymous>:1:…
- Afiş denemesi:
