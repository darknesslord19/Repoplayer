// Node'da WebView'u taklit ederek runtime.js'yi test eder.
// Kullanım: node js/test/runtime.test.js [libs.js yolu]
const fs = require('fs'), vm = require('vm'), path = require('path'), assert = require('assert');
const root = path.join(__dirname, '..', '..');
const runtime = fs.readFileSync(path.join(root, 'player/src/main/assets/js/runtime.js'), 'utf8');
const libsPath = process.argv[2];

const win = { setTimeout, clearTimeout, Promise, JSON, Math, Date, URLSearchParams, URL, Uint8Array, Symbol, Object, Array, String, encodeURIComponent, decodeURIComponent, atob, btoa, crypto: globalThis.crypto };
win.window = win;
const store = {}, codes = {}, results = {};
const ctx = vm.createContext(win);
win.NativeBridge = {
  fetch(id, req) {
    const r = JSON.parse(req);
    setTimeout(() => {
      store[id] = JSON.stringify(r.url.includes('fail') ? { error: 'boom' } :
        { status: 200, url: r.url, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ echo: r.url, method: r.method, body: r.body, ua: r.headers['User-Agent'] || null }) });
      vm.runInContext(`__rp.onFetch(${JSON.stringify(id)})`, ctx);
    }, 5);
  },
  take(id) { const v = store[id]; delete store[id]; return v; },
  code(runId) { return codes[runId]; },
  done(runId, payload) { results[runId](JSON.parse(payload)); },
  log(m) { console.log('  [js]', m); },
};
if (libsPath) vm.runInContext(fs.readFileSync(libsPath, 'utf8'), ctx, { filename: 'libs.js' });
else win.__rpLibs = { CryptoJS: { MD5: (s) => ({ toString: () => 'md5:' + s }) }, cheerio: null };
vm.runInContext(runtime, ctx, { filename: 'runtime.js' });

let n = 0;
function run(code, args, key) {
  const id = 'r' + (++n); codes[id] = code;
  return new Promise((res) => { results[id] = res; vm.runInContext(`__rp.run(${JSON.stringify(id)}, ${JSON.stringify(key || id)}, ${JSON.stringify(args)})`, ctx); });
}

// Nuvio derleyicisinin ürettiği __async/generator kalıbı
const provider = `
var __async = (__this, __arguments, generator) => new Promise((resolve, reject) => {
  var fulfilled = (v) => { try { step(generator.next(v)); } catch (e) { reject(e); } };
  var rejected = (v) => { try { step(generator.throw(v)); } catch (e) { reject(e); } };
  var step = (x) => x.done ? resolve(x.value) : Promise.resolve(x.value).then(fulfilled, rejected);
  step((generator = generator.apply(__this, __arguments)).next());
});
const CryptoJS = require('crypto-js');
const axios = require('axios');
function getStreams(tmdbId, mediaType, season, episode) {
  return __async(this, null, function* () {
    const r = yield fetch('https://api.example/' + mediaType + '/' + tmdbId, { headers: { 'User-Agent': 'X' } });
    const j = yield r.json();
    const p = yield axios.post('https://api.example/post', { a: 1 });
    let failed = false;
    try { yield fetch('https://fail.example'); } catch (e) { failed = true; }
    console.log('hash', CryptoJS.MD5('x').toString());
    return [{ name: 'Test', title: j.echo + '|' + p.data.method + '|' + p.data.body + '|' + failed + '|' + j.ua, url: 'https://cdn/x.m3u8', quality: '1080p', headers: { Referer: 'https://r' } }];
  });
}
module.exports = { getStreams };`;

(async () => {
  const r1 = await run(provider, [123, 'tv', 1, 2], 'p1');
  assert.strictEqual(r1.ok, true, JSON.stringify(r1));
  assert.strictEqual(r1.value[0].title, 'https://api.example/tv/123|POST|{"a":1}|true|X');
  // önbellekten (kod tekrar istenmeden) çalışma
  const r2 = await run('throw new Error("kod tekrar yüklenmemeli")', [5, 'movie'], 'p1');
  assert.strictEqual(r2.ok, true, JSON.stringify(r2));
  // native async/await ve hata yakalama
  const r3 = await run('module.exports = { getStreams: async () => { throw new Error("patladi") } }', [], 'p3');
  assert.strictEqual(r3.ok, false); assert.ok(r3.value.includes('patladi'));
  const r4 = await run('module.exports.getStreams = function(){ return null }', [], 'p4');
  assert.deepStrictEqual(r4, { ok: true, value: [] });
  if (libsPath) {
    const r5 = await run(`const cheerio = require('cheerio-without-node-native'); const C = require('crypto-js');
      module.exports = { getStreams: () => { const $ = cheerio.load('<div><a class="x" href="/v.m3u8">Hi</a></div>');
      return [{ title: $('a.x').text() + ':' + $('a.x').attr('href') + ':' + C.MD5('abc').toString() + ':' + C.AES.decrypt(C.AES.encrypt('gizli','k').toString(),'k').toString(C.enc.Utf8) }]; } }`, [], 'p5');
    assert.strictEqual(r5.ok, true, JSON.stringify(r5));
    assert.strictEqual(r5.value[0].title, 'Hi:/v.m3u8:900150983cd24fb0d6963f7d28e17f72:gizli');
  }
  console.log('Tüm runtime testleri geçti' + (libsPath ? ' (cheerio + crypto-js dahil)' : ''));
})().catch((e) => { console.error(e); process.exit(1); });
