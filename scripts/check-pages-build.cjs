const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');const {JSDOM}=require('../.tools/dom-tests/node_modules/jsdom');
const html=fs.readFileSync('site/index.html','utf8');
assert.ok(!html.includes('src="./config.js"'),'Pages must not depend on a separately cached API setting');
for(const ext of ['js','css']){const pattern=ext==='js'?/\.\/app\.[a-f0-9]{12}\.js/:/\.\/styles\.[a-f0-9]{12}\.css/;const match=html.match(pattern);assert.ok(match,'Content-versioned '+ext);assert.ok(fs.existsSync(path.join('site',match[0])));}
const dom=new JSDOM(html,{url:'https://retomydb.github.io/SportFit/',runScripts:'outside-only'});
dom.window.SPORTFIT_CONFIG={apiBaseUrl:''};
for(const s of dom.window.document.querySelectorAll('script:not([src])'))dom.window.eval(s.textContent);
assert.ok(dom.window.SPORTFIT_CONFIG.apiBaseUrl.startsWith('https://'),'Inline setting must replace an old empty setting');
assert.ok(!fs.existsSync('site/data'),'Account data must not be in the website artifact');
console.log('PASS: Pages embeds its HTTPS API setting, replaces stale configuration, versions JS/CSS, and excludes account files.');dom.window.close();
