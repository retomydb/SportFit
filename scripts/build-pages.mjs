import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');const dest=path.join(root,'site');
await fs.mkdir(dest,{recursive:true});
const src=path.join(root,'src/main/resources/static');
for(const name of ['index.html','app.js','styles.css','vendor','images'])await fs.cp(path.join(src,name),path.join(dest,name),{recursive:true});
// Only an API origin is published. The private data directory is never copied.
const api=(process.env.API_BASE_URL||'').trim().replace(/\/$/,'');
if(api){const u=new URL(api);if(u.protocol!=='https:'&&!['localhost','127.0.0.1'].includes(u.hostname))throw Error('The public API must use HTTPS.');if(u.username||u.password||u.search||u.hash)throw Error('API_BASE_URL must not contain credentials, query parameters, or fragments.');}
await fs.writeFile(path.join(dest,'config.js'),'window.SPORTFIT_CONFIG = '+JSON.stringify({apiBaseUrl:api})+';\n');
await fs.writeFile(path.join(dest,'.nojekyll'),'');
console.log('Pages artifact built. API configured:',Boolean(api));
