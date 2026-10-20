// Nuvio sağlayıcılarını uygulamadaki çalışma ortamının (runtime.js + libs.js) aynısıyla
// gerçek sitelere karşı çalıştırır ve markdown rapor yazar.
// Kullanım: node diag/nuvio-diag.js <manifest-url> <rapor.md>
const fs = require('fs'), vm = require('vm'), path = require('path');
const root = path.join(__dirname, '..', '..');
const [manifestUrl, reportPath] = process.argv.slice(2);
const UA = 'Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';
let out = '';
const p = (s = '') => { console.log(s); out += s + '\n'; };
const short = (s, n = 100) => (s == null ? '—' : String(s).length > n ? String(s).slice(0, n) + '…' : String(s));

const win = { setTimeout, clearTimeout, setInterval, clearInterval, Promise, JSON, Math, Date, URLSearchParams, URL, Uint8Array, ArrayBuffer, TextEncoder, TextDecoder, Symbol, Object, Array, String, Number, RegExp, Error, encodeURIComponent, decodeURIComponent, escape, unescape, atob, btoa, crypto: globalThis.crypto, AbortController };
win.window = win;
const ctx = vm.createContext(win);
const store = {}, codes = {}, results = {};
const textTypes = ['text', 'json', 'xml', 'javascript', 'html', 'mpegurl', 'x-www-form', 'vtt', 'srt'];
win.NativeBridge = {
  fetch(id, req) {
    const r = JSON.parse(req);
    const h = Object.assign({}, r.headers);
    if (!Object.keys(h).some((k) => k.toLowerCase() === 'user-agent')) h['User-Agent'] = UA;
    fetch(r.url, { method: r.method, headers: h, body: r.body == null ? undefined : r.body, redirect: r.followRedirects === false ? 'manual' : 'follow', signal: AbortSignal.timeout(30000) })
      .then(async (res) => {
        const buf = Buffer.from(await res.arrayBuffer());
        const type = (res.headers.get('content-type') || '').toLowerCase();
        const isText = !type || textTypes.some((t) => type.includes(t));
        const headers = {}; res.headers.forEach((v, k) => { headers[k] = v; });
        store[id] = JSON.stringify({ status: res.status, statusText: res.statusText, url: res.url, headers, redirected: res.redirected,
          body: isText ? buf.toString('utf8') : buf.toString('latin1'), bodyBase64: isText ? null : buf.toString('base64') });
      })
      .catch((e) => { store[id] = JSON.stringify({ error: String(e && e.cause ? e.cause.code || e.cause.message : e) }); })
      .finally(() => vm.runInContext(`__rp.onFetch(${JSON.stringify(id)})`, ctx));
  },
  take(id) { const v = store[id]; delete store[id]; return v; },
  code(runId) { return codes[runId]; },
  done(runId, payload) { results[runId] && results[runId](JSON.parse(payload)); },
  log(m) { console.log('    [js]', short(m, 200)); },
};
vm.runInContext(fs.readFileSync(path.join(root, 'player/src/main/assets/js/libs.js'), 'utf8'), ctx);
vm.runInContext(fs.readFileSync(path.join(root, 'player/src/main/assets/js/runtime.js'), 'utf8'), ctx);

let n = 0;
function call(code, key, fn, args, scraperId, timeout = 60000) {
  const id = 'c' + (++n); codes[id] = code;
  return Promise.race([
    new Promise((res) => { results[id] = res; vm.runInContext(`__rp.call(${JSON.stringify(id)}, ${JSON.stringify(key)}, ${JSON.stringify(fn)}, ${JSON.stringify(args)}, ${JSON.stringify({ scraperId })})`, ctx); }),
    new Promise((res) => setTimeout(() => res({ ok: false, value: 'ZAMAN AŞIMI' }), timeout)),
  ]);
}

async function probe(url, headers = {}) {
  try {
    const h = Object.assign({ 'User-Agent': UA, Range: 'bytes=0-4095' }, headers);
    const r = await fetch(url, { headers: h, signal: AbortSignal.timeout(20000) });
    const b = Buffer.from(await r.arrayBuffer()).subarray(0, 32).toString('latin1');
    const sig = b.startsWith('#EXTM3U') ? 'm3u8' : b.startsWith('\x89PNG') ? 'png' : b.startsWith('\xff\xd8') ? 'jpg' : b.startsWith('RIFF') ? 'webp' : b.trimStart().startsWith('<') ? 'HTML!' : b.includes('ftyp') ? 'mp4' : '';
    return `${r.status} ${r.headers.get('content-type') || '?'} [${sig}]`;
  } catch (e) { return 'HATA ' + String(e.cause ? e.cause.code || e.cause.message : e.message); }
}

(async () => {
  p('# Nuvio sağlayıcı teşhis raporu');
  p(`Manifest: ${manifestUrl} · ${new Date().toISOString()}`);
  const manifest = await (await fetch(manifestUrl)).json();
  const base = manifestUrl.substring(0, manifestUrl.lastIndexOf('/') + 1);
  for (const s of manifest.scrapers || []) {
    p(`\n## ${s.name} (${s.id})`);
    const code = await (await fetch(new URL(s.filename, base).toString())).text();
    const key = s.id + '@' + s.version;
    let sample = [];
    for (const c of (s.catalogs || []).slice(0, 2)) {
      const t0 = Date.now();
      const type = c.type === 'tv' ? 'series' : c.type;
      const r = await call(code, key, 'getCatalog', [{ catalogId: c.id, type, pageToken: null, language: 'tr-TR' }], s.id);
      const items = (r.value && (r.value.items || r.value.metas)) || [];
      p(`- getCatalog(${c.id}/${type}): ${r.ok ? items.length + ' öğe' : 'HATA ' + short(r.value, 200)} · next=${r.value && r.value.nextPageToken} · ${Date.now() - t0} ms`);
      if (items[0]) p('  ilk öğe alanları: ' + Object.keys(items[0]).join(', ') + ' · externalIds=' + JSON.stringify(items[0].externalIds || null));
      sample = sample.concat(items.map((i) => Object.assign({ _type: type }, i)));
    }
    if (s.supportsSearch) {
      const r = await call(code, key, 'search', [{ query: 'kurt', language: 'tr-TR' }], s.id);
      const items = (r.value && r.value.items) || [];
      p(`- search("kurt"): ${r.ok ? items.length + ' sonuç' : 'HATA ' + short(r.value, 200)}`);
      if (!sample.length) sample = items.map((i) => Object.assign({ _type: i.type }, i));
    }
    p('- Afiş denemesi:');
    for (const it of sample.slice(0, 3)) {
      p(`  - ${short(it.name, 30)} → \`${short(it.poster, 90)}\``);
      if (it.poster) p(`      başlıksız: ${await probe(it.poster)}`);
    }
    const movie = sample.find((i) => i._type === 'movie' || i.type === 'movie');
    const series = sample.find((i) => i._type === 'series' || i.type === 'series');
    for (const it of [movie, series].filter(Boolean)) {
      const isSeries = it === series;
      const id = (it.externalIds && (it.externalIds.tmdbId || it.externalIds.imdbId)) || it.id;
      const t0 = Date.now();
      const r = await call(code, key, 'getStreams', [String(id), isSeries ? 'tv' : 'movie', isSeries ? 1 : null, isSeries ? 1 : null, it.url || null], s.id, 90000);
      const streams = Array.isArray(r.value) ? r.value : [];
      p(`- getStreams(${short(it.name, 30)}${isSeries ? ' S1B1' : ''}): ${r.ok ? streams.length + ' akış' : 'HATA ' + short(r.value, 200)} · ${Date.now() - t0} ms`);
      for (const st of streams.slice(0, 4)) {
        const u = typeof st.url === 'object' && st.url ? st.url.url : st.url;
        p(`  - **${short(st.name, 30)}** ${short(st.title, 40)} · q=${st.quality} · \`${short(u, 90)}\``);
        p(`      başlıklar=${JSON.stringify(Object.keys(st.headers || {}))} · başlıklı: ${await probe(u, st.headers || {})} · başlıksız: ${await probe(u)}`);
      }
    }
    fs.writeFileSync(reportPath, out);
  }
  fs.writeFileSync(reportPath, out);
  process.exit(0);
})().catch((e) => { p('GENEL HATA ' + e.stack); fs.writeFileSync(reportPath, out); process.exit(0); });
