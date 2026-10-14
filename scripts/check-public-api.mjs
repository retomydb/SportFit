// Tests the deployed API with a synthetic account; removes that account's current file afterward.
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
const base=(process.env.TEST_URL||await fs.readFile(new URL('../.runtime/public-api-url',import.meta.url),'utf8')).trim();
if(!base.startsWith('https://'))throw Error('The public deployment check requires HTTPS.');
const origin='https://retomydb.github.io';
const email=`deployment-check-${Date.now()}@example.test`;
const password=crypto.randomBytes(24).toString('base64url');
let session='',csrf=null,created=false;
async function request(path,method='GET',body,form=false){const headers={Origin:origin};if(session)headers['X-Auth-Token']=session;if(method!=='GET')headers[csrf.headerName]=csrf.token;if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';const r=await fetch(base+'/api'+path,{method,headers,body:body?(form?new URLSearchParams(body):JSON.stringify(body)):undefined,signal:AbortSignal.timeout(45000)});if(r.headers.get('x-auth-token')!==null)session=r.headers.get('x-auth-token');if(r.headers.get('access-control-allow-origin')!==origin)throw Error(`CORS headers missing (${path}, ${r.status}).`);if(!r.ok)throw Error(`${path} failed with HTTP ${r.status}`);return r.status===204?null:r.json();}
try{
 csrf=await request('/csrf');
 await request('/register','POST',{name:'Deployment verification',email,password});created=true;
 await request('/login','POST',{username:email,password},true);csrf=await request('/csrf');
 let state=await request('/state');if(state.email!==email)throw Error('Login did not establish the correct account.');
 const workout=await request('/workouts','POST',{activity:'walking',duration:60,reps:0,sets:0,distance:0.05});
 const date=new Date().toISOString().slice(0,10);
 const plan=await request('/plans','POST',{date,activity:'yoga',duration:20,completed:false});
 await request('/plans/'+plan.id,'PUT',{date,activity:'yoga',duration:25,completed:true});
 await request('/wellness','PUT',{date,water:250,sleep:8,intensity:3,heartRate:0,recovery:'great',restDay:false});
 state=await request('/state');if(!state.workouts.some(w=>w.id===workout.id)||!state.plans.some(p=>p.id===plan.id&&p.completed)||state.wellness[0].water!==250)throw Error('Persisted state did not match the saved records.');
 await request('/plans/'+plan.id,'DELETE');await request('/logout','POST');
 console.log('PASS: public HTTPS, Pages CORS, registration, login, session rotation, GitHub-backed workout/plan/wellness storage, and logout.');
}finally{
 if(created){const path='data/accounts/'+crypto.createHash('sha256').update(email).digest('hex')+'.json.enc';const route='repos/retomydb/SportFit/contents/'+path;const metadata=JSON.parse(execFileSync('gh',['api',route],{encoding:'utf8'}));execFileSync('gh',['api','--method','DELETE',route,'--input','-'],{input:JSON.stringify({message:'Remove synthetic deployment verification account',sha:metadata.sha,branch:'main'}),stdio:['pipe','pipe','pipe']});console.log('Synthetic account removed; existing user records were unchanged.');}
}
