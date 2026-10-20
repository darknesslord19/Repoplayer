// Nuvio sağlayıcılarının require() ile istediği kütüphaneler.
// CI'da esbuild ile tek dosyaya paketlenip player/src/main/assets/js/libs.js olur.
window.__rpLibs = {
  cheerio: require('cheerio-without-node-native'),
  CryptoJS: require('crypto-js'),
};
