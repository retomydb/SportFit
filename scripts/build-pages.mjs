import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..');const dest=path.join(root,'site');
await fs.mkdir(dest,{recursive:true});
const src=path.join(root,'src/main/resources/static');
for(const name of ['index.html','app.js','styles.css','vendor','images'])await fs.cp(path.join(src,name),path.join(dest,name),{recursive:true});
// Only an API origin is published. The private data directory is never copied.
const api=(process.env.API_BASE_URL||'').trim().replace(/\/$/,'');
if(api){const u=new URL(api);if(u.protocol!=='https:'&&!['localhost','127.0.0.1'].includes(u.hostname))throw Error('The public API must use HTTPS.');if(u.username||u.password||u.search||u.hash)throw Error('API_BASE_URL must not contain credentials, query parameters, or fragments.');}
await fs.writeFile(path.join(dest,'config.js'),'window.SPORTFIT_CONFIG = '+JSON.stringify({apiBaseUrl:api})+';\n');
// Inline the deployment's public API setting so a cached config.js cannot disable sign-in.
// Content-derived filenames refresh changed JS/CSS without asking visitors to clear their cache.
let html=await fs.readFile(path.join(dest,'index.html'),'utf8');
const publicConfig=JSON.stringify({apiBaseUrl:api}).replaceAll('<','\\u003c');
html=html.replace('<script src="./config.js"></script>', '<script>window.SPORTFIT_CONFIG = '+publicConfig+';</script>');
for(const filename of ['app.js','styles.css']){
  const bytes=await fs.readFile(path.join(dest,filename));
  const digest=crypto.createHash('sha256').update(bytes).digest('hex').slice(0,12);
  const ext=path.extname(filename),versioned=filename.slice(0,-ext.length)+'.'+digest+ext;
  await fs.writeFile(path.join(dest,versioned),bytes);
  html=html.replace('./'+filename,'./'+versioned);
}
await fs.writeFile(path.join(dest,'index.html'),html);
await fs.writeFile(path.join(dest,'.nojekyll'),'');
console.log('Pages artifact built. API configured:',Boolean(api));
