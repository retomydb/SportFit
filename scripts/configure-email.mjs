import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');
const source=process.argv[2]?await fs.readFile(process.argv[2],'utf8'):'';
function read(name){return source.match(new RegExp('^'+name+'\\s*=\\s*(.*)$','m'))?.[1]?.trim().replace(/^['"]|['"]$/g,'')||'';}
const key=process.env.SPORTFIT_RESEND_API_KEY||read('SPORTFIT_RESEND_API_KEY')||read('NEWSLETTER_RESEND_API_KEY');
const rawFrom=process.env.SPORTFIT_EMAIL_FROM||read('SPORTFIT_EMAIL_FROM')||read('NEWSLETTER_FROM_EMAIL');
const email=rawFrom.match(/[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+/)?.[0];
if(!key||!email)throw Error('Provide an existing Resend key and verified sender through environment variables or an env-file path.');
await fs.mkdir(path.join(root,'.runtime'),{recursive:true,mode:0o700});
await fs.writeFile(path.join(root,'.runtime/resend-api-key'),key,{mode:0o600});
await fs.writeFile(path.join(root,'.runtime/email-from'),'SportFit Pro <'+email+'>',{mode:0o600});
console.log('SportFit email secrets configured locally. Credentials were not printed.');
