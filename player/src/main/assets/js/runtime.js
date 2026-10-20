/*
 * RepoPlayer — Nuvio sağlayıcı çalışma ortamı.
 * WebView içinde çalışır. Ağ istekleri CORS'a takılmamak için Android
 * tarafındaki NativeBridge üzerinden yapılır.
 */
(function () {
  var g = (typeof window !== 'undefined') ? window : this;
  g.global = g;
  if (typeof g.globalThis === 'undefined') g.globalThis = g;
  if (!g.SCRAPER_SETTINGS) g.SCRAPER_SETTINGS = {};
  if (typeof g.self === 'undefined') g.self = g;
  g.Nuvio = g.Nuvio || {};
  g.Nuvio.showMessage = function (message, title) { try { g.NativeBridge.log('[mesaj] ' + (title ? title + ': ' : '') + message); } catch (e) {} };

  var libs = g.__rpLibs || {};
  var pending = {};
  var seq = 0;

  // ---------- Headers ----------
  function RPHeaders(init) {
    this._m = {};
    var self = this;
    if (!init) return;
    if (typeof init.forEach === 'function' && !Array.isArray(init)) {
      init.forEach(function (v, k) { self.set(k, v); });
    } else if (Array.isArray(init)) {
      init.forEach(function (p) { self.set(p[0], p[1]); });
    } else {
      Object.keys(init).forEach(function (k) { self.set(k, init[k]); });
    }
  }
  RPHeaders.prototype.get = function (k) { var v = this._m[String(k).toLowerCase()]; return v === undefined ? null : v; };
  RPHeaders.prototype.has = function (k) { return String(k).toLowerCase() in this._m; };
  RPHeaders.prototype.set = function (k, v) { this._m[String(k).toLowerCase()] = String(v); };
  RPHeaders.prototype.append = function (k, v) { var o = this.get(k); this.set(k, o ? o + ', ' + v : v); };
  RPHeaders.prototype.delete = function (k) { delete this._m[String(k).toLowerCase()]; };
  RPHeaders.prototype.forEach = function (cb) { var m = this._m; Object.keys(m).forEach(function (k) { cb(m[k], k); }); };
  RPHeaders.prototype.entries = function () { var m = this._m; return Object.keys(m).map(function (k) { return [k, m[k]]; })[Symbol.iterator](); };
  RPHeaders.prototype.keys = function () { return Object.keys(this._m)[Symbol.iterator](); };
  RPHeaders.prototype[Symbol.iterator] = RPHeaders.prototype.entries;

  function makeResponse(r) {
    var body = r.body == null ? '' : String(r.body);
    return {
      ok: r.status >= 200 && r.status < 300,
      status: r.status,
      statusText: r.statusText || '',
      url: r.url,
      redirected: !!r.redirected,
      headers: new RPHeaders(r.headers || {}),
      text: function () { return Promise.resolve(body); },
      json: function () {
        return new Promise(function (res, rej) { try { res(JSON.parse(body)); } catch (e) { rej(e); } });
      },
      arrayBuffer: function () {
        var bin = r.bodyBase64 ? atob(r.bodyBase64) : body;
        var buf = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i) & 0xff;
        return Promise.resolve(buf.buffer);
      },
      blob: function () { return this.arrayBuffer(); },
      clone: function () { return makeResponse(r); }
    };
  }

  function normalizeHeaders(h) {
    var out = {};
    if (!h) return out;
    if (typeof h.forEach === 'function' && !Array.isArray(h)) h.forEach(function (v, k) { out[k] = String(v); });
    else if (Array.isArray(h)) h.forEach(function (p) { out[p[0]] = String(p[1]); });
    else Object.keys(h).forEach(function (k) { if (h[k] != null) out[k] = String(h[k]); });
    return out;
  }

  function hasHeader(h, name) {
    name = name.toLowerCase();
    return Object.keys(h).some(function (k) { return k.toLowerCase() === name; });
  }

  // ---------- fetch ----------
  function rpFetch(input, init) {
    init = init || {};
    var url = typeof input === 'string' ? input : (input && input.url) ? input.url : String(input);
    var headers = normalizeHeaders(init.headers || (input && input.headers));
    var body = init.body;
    if (body != null && typeof body !== 'string') {
      if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
        body = body.toString();
        if (!hasHeader(headers, 'content-type')) headers['Content-Type'] = 'application/x-www-form-urlencoded';
      } else if (typeof FormData !== 'undefined' && body instanceof FormData) {
        var parts = [];
        body.forEach(function (v, k) { parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v)); });
        body = parts.join('&');
        if (!hasHeader(headers, 'content-type')) headers['Content-Type'] = 'application/x-www-form-urlencoded';
      } else {
        body = JSON.stringify(body);
        if (!hasHeader(headers, 'content-type')) headers['Content-Type'] = 'application/json';
      }
    }
    return new Promise(function (resolve, reject) {
      var id = 'f' + (++seq);
      pending[id] = { resolve: resolve, reject: reject };
      g.NativeBridge.fetch(id, JSON.stringify({
        url: url,
        method: (init.method || 'GET').toUpperCase(),
        headers: headers,
        body: body == null ? null : body,
        followRedirects: init.redirect !== 'manual'
      }));
    });
  }
  g.fetch = rpFetch;

  // ---------- axios uyumluluk katmanı ----------
  function axiosRequest(config) {
    config = config || {};
    var url = config.url || '';
    if (config.baseURL && !/^https?:/i.test(url)) url = config.baseURL.replace(/\/$/, '') + '/' + url.replace(/^\//, '');
    if (config.params) {
      var q = Object.keys(config.params).filter(function (k) { return config.params[k] != null; })
        .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(config.params[k]); }).join('&');
      if (q) url += (url.indexOf('?') >= 0 ? '&' : '?') + q;
    }
    return rpFetch(url, { method: config.method || 'GET', headers: config.headers, body: config.data })
      .then(function (res) {
        return res.text().then(function (text) {
          var data = text;
          if (config.responseType !== 'text') { try { data = JSON.parse(text); } catch (e) { data = text; } }
          var headers = {};
          res.headers.forEach(function (v, k) { headers[k] = v; });
          var out = { data: data, status: res.status, statusText: res.statusText, headers: headers, config: config, request: { responseURL: res.url } };
          var ok = config.validateStatus ? config.validateStatus(res.status) : res.ok;
          if (!ok) { var err = new Error('Request failed with status code ' + res.status); err.response = out; throw err; }
          return out;
        });
      });
  }
  function makeAxios(defaults) {
    defaults = defaults || {};
    function merge(c) {
      var o = {}; Object.keys(defaults).forEach(function (k) { o[k] = defaults[k]; });
      Object.keys(c || {}).forEach(function (k) { o[k] = c[k]; });
      o.headers = Object.assign({}, defaults.headers || {}, (c && c.headers) || {});
      return o;
    }
    var ax = function (a, b) { return typeof a === 'string' ? axiosRequest(merge(Object.assign({}, b, { url: a }))) : axiosRequest(merge(a)); };
    ax.request = function (c) { return axiosRequest(merge(c)); };
    ['get', 'delete', 'head', 'options'].forEach(function (m) {
      ax[m] = function (url, c) { return axiosRequest(merge(Object.assign({}, c, { url: url, method: m }))); };
    });
    ['post', 'put', 'patch'].forEach(function (m) {
      ax[m] = function (url, data, c) { return axiosRequest(merge(Object.assign({}, c, { url: url, method: m, data: data }))); };
    });
    ax.create = function (c) { return makeAxios(merge(c)); };
    ax.defaults = defaults;
    ax.interceptors = { request: { use: function () {} }, response: { use: function () {} } };
    ax.default = ax;
    return ax;
  }
  var axios = makeAxios({});

  // ---------- require ----------
  function rpRequire(name) {
    switch (name) {
      case 'cheerio-without-node-native':
      case 'cheerio':
      case 'react-native-cheerio':
        if (!libs.cheerio) throw new Error('cheerio yüklenemedi');
        return libs.cheerio;
      case 'crypto-js':
        return libs.CryptoJS;
      case 'axios':
        return axios;
    }
    if (name.indexOf('crypto-js/') === 0) return libs.CryptoJS;
    throw new Error('Modül bulunamadı: ' + name);
  }
  g.require = rpRequire;

  var origLog = g.console && g.console.log;
  function log() {
    try { g.NativeBridge.log(Array.prototype.slice.call(arguments).map(function (a) { return typeof a === 'string' ? a : JSON.stringify(a); }).join(' ')); } catch (e) {}
  }
  g.console = g.console || {};
  g.console.log = g.console.info = g.console.debug = log;
  g.console.warn = g.console.error = log;

  var rp = g.__rp = { modules: {}, version: 1 };

  rp.onFetch = function (id) {
    var p = pending[id];
    if (!p) return;
    delete pending[id];
    var r;
    try { r = JSON.parse(g.NativeBridge.take(id)); } catch (e) { p.reject(e); return; }
    if (r.error) { p.reject(new TypeError('Network request failed: ' + r.error)); return; }
    p.resolve(makeResponse(r));
  };

  function done(runId, ok, value) {
    var payload;
    try { payload = JSON.stringify({ ok: ok, value: value }); }
    catch (e) { payload = JSON.stringify({ ok: false, value: 'Sonuç serileştirilemedi: ' + e }); }
    g.NativeBridge.done(runId, payload);
  }

  function errText(e) { return String((e && (e.stack || e.message)) || e); }

  function loadModule(runId, key) {
    var mod = rp.modules[key];
    if (!mod) {
      var code = g.NativeBridge.code(runId);
      var module = { exports: {} };
      var fn = new Function('module', 'exports', 'require', code);
      fn(module, module.exports, rpRequire);
      mod = rp.modules[key] = module.exports;
    }
    return mod;
  }

  /**
   * Genel çağrı: getCatalog / search / getStreams / comments.
   * ctx: { scraperId, settings, tmdbKey }
   */
  rp.call = function (runId, key, fnName, args, ctx) {
    try {
      ctx = ctx || {};
      g.SCRAPER_ID = ctx.scraperId || g.SCRAPER_ID;
      g.SCRAPER_SETTINGS = ctx.settings || g.SCRAPER_SETTINGS || {};
      if (ctx.tmdbKey) g.TMDB_API_KEY = ctx.tmdbKey;
      var mod = loadModule(runId, key);
      var f = mod[fnName] || (mod.default && mod.default[fnName]) || g[fnName];
      if (typeof f !== 'function') { done(runId, true, null); return; }
      Promise.resolve(f.apply(null, args)).then(
        function (r) { done(runId, true, r === undefined ? null : r); },
        function (e) { done(runId, false, errText(e)); }
      );
    } catch (e) {
      done(runId, false, errText(e));
    }
  };

  /** key: sağlayıcı kimliği + sürüm; kod gerekirse NativeBridge.code ile alınır */
  rp.run = function (runId, key, args) {
    try {
      var mod = rp.modules[key];
      if (!mod) {
        var code = g.NativeBridge.code(runId);
        var module = { exports: {} };
        var fn = new Function('module', 'exports', 'require', code);
        fn(module, module.exports, rpRequire);
        mod = rp.modules[key] = module.exports;
      }
      var f = mod.getStreams || (mod.default && mod.default.getStreams) || (typeof mod === 'function' ? mod : null);
      if (typeof f !== 'function') throw new Error('getStreams bulunamadı');
      Promise.resolve(f.apply(null, args)).then(
        function (r) { done(runId, true, Array.isArray(r) ? r : (r ? [r] : [])); },
        function (e) { done(runId, false, errText(e)); }
      );
    } catch (e) {
      done(runId, false, errText(e));
    }
  };
})();
