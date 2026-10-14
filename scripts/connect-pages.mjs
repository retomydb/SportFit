import {execFileSync} from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');
const run=(cmd,args)=>execFileSync(cmd,args,{cwd:root,encoding:'utf8',maxBuffer:4*1024*1024});
let url='';
for(let attempt=0;attempt<50;attempt++){
  const logs=run('docker',['compose','logs','--no-color','tunnel']);
  const found=[...logs.matchAll(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/g)];
  if(found.length){url=found.at(-1)[0];try{const r=await fetch(url+'/api/csrf',{headers:{Origin:'https://retomydb.github.io'},signal:AbortSignal.timeout(6000)});if(r.ok&&r.headers.get('access-control-allow-origin')==='https://retomydb.github.io')break;}catch{}}
  if(attempt===49)throw Error('The public API is not responding. Check docker compose logs.');
  await new Promise(r=>setTimeout(r,2000));
}
await fs.writeFile(path.join(root,'.runtime/public-api-url'),url+'\n',{mode:0o600});
console.log('Public backend is ready:',url);
run('gh',['variable','set','API_BASE_URL','--repo','retomydb/SportFit','--body',url]);
run('gh',['workflow','run','pages.yml','--repo','retomydb/SportFit']);
console.log('GitHub Pages deployment requested with the connected backend.');
