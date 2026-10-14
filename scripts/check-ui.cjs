// DOM integration checks against an isolated H2-backed server on port 8088.
const {JSDOM}=require('../.tools/dom-tests/node_modules/jsdom');
const fs=require('node:fs');const assert=require('node:assert/strict');
const base=process.env.TEST_URL||'http://127.0.0.1:8088';
const sleep=n=>new Promise(r=>setTimeout(r,n));
(async()=>{
 const html=await(await fetch(base)).text();
 const dom=new JSDOM(html,{url:base,runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window;w.scrollTo=()=>{};let cookie='';let exported=false;
 w.Headers=Headers;
 w.fetch=async(url,opt={})=>{const headers=new Headers(opt.headers||{});if(cookie)headers.set('Cookie',cookie);const res=await fetch(new URL(url,base),{...opt,headers});const next=res.headers.getSetCookie();if(next.length)cookie=next.map(c=>c.split(';')[0]).join('; ');return res;};
 w.Chart=class{constructor(el,config){assert.ok(config.data);this.config=config;}destroy(){}};
 w.URL.createObjectURL=()=>{exported=true;return 'blob:test';};w.URL.revokeObjectURL=()=>{};
 w.HTMLAnchorElement.prototype.click=function(){};
 w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;};
 w.eval(fs.readFileSync('src/main/resources/static/app.js','utf8'));
 const q=s=>w.document.querySelector(s),qa=s=>[...w.document.querySelectorAll(s)];
 async function until(fn,msg){for(let i=0;i<100;i++){if(fn())return;await sleep(30);}throw Error(msg+'\n'+w.document.body.textContent.slice(-1800));}
 async function click(s){assert.ok(q(s),s);q(s).click();await sleep(50);}
 function value(s,v){assert.ok(q(s),s);q(s).value=v;}
 async function submit(s){q(s).dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await sleep(200);}
 async function page(id){w.location.hash=id;await until(()=>q('.nav a.active')?.getAttribute('href')==='#'+id,'Navigate '+id);await sleep(20);}
 await until(()=>q('#auth-form'),'Auth render');await click('[data-auth="register"]');
 value('#name','DOM Test Athlete');value('#email','dom-'+Date.now()+'@example.test');value('#password','DOM-testing-123!');await submit('#auth-form');
 await until(()=>q('#profile-form'),'Registration + automatic login');
 value('#height','180');value('#weight','81');value('#weeklyTarget','4');q('#weight').dispatchEvent(new w.Event('input',{bubbles:true}));assert.equal(q('#bmi-value').textContent,'25.0');await submit('#profile-form');await until(()=>q('#toast').textContent.includes('profile has been saved'),'Profile saved');
 await page('activities');assert.equal(qa('.activity-card').length,10);await click('[data-action="save-activity"][data-id="yoga"]');await until(()=>q('[data-id="yoga"].save')?.classList.contains('saved'),'Save activity');await click('[data-action="activity-tab"][data-id="saved"]');assert.equal(qa('.activity-card').length,1);
 await click('[data-action="start-activity"][data-id="yoga"]');await until(()=>q('#live-timer'),'Tracker loaded');await click('[data-action="workout-start"]');await sleep(1200);await click('[data-action="rep-plus"]');await click('[data-action="set-plus"]');assert.equal(q('#rep-count').textContent,'1');await click('[data-action="workout-pause"]');assert.equal(q('#tracker-status').textContent,'PAUSED');await click('[data-action="workout-pause"]');await click('[data-action="workout-stop"]');await until(()=>q('#toast').textContent.includes('Session saved'),'Workout saved');
 await page('analytics');assert.equal(qa('tbody tr').length,1);assert.ok(q('tbody').textContent.includes('Yoga'));await click('[data-action="period"][data-id="month"]');await click('[data-action="export"]');assert.ok(exported);
 await page('planner');await click('[data-action="plan-add"]');value('#plan-activity','cycling');value('#plan-duration','35');await submit('#plan-form');await until(()=>qa('.plan-item').length===1,'Plan added');await click('[data-action="plan-complete"]');await until(()=>q('.plan-item.complete'),'Plan completion');await click('[data-action="plan-edit"]');value('#plan-duration','45');await submit('#plan-form');await until(()=>q('.plan-item')?.textContent.includes('45 min'),'Plan edit');await click('[data-action="plan-delete"]');await until(()=>qa('.plan-item').length===0,'Plan deletion');await click('[data-action="generate-plan"]');await until(()=>qa('.plan-item').length===4,'Personalised 4-session plan');
 await page('wellness');await click('[data-action="water-add"]');await until(()=>q('.water-count')?.textContent.includes('250'),'Water saved');value('#sleep','6');value('#intensity','9');value('#recovery','tired');await submit('#wellness-form');await until(()=>qa('.notice').length===3,'Wellness advice');
 await page('achievements');assert.equal(qa('.badge-card').length,8);assert.ok(qa('.badge-card:not(.locked)').length>=1);
 await page('overview');assert.ok(q('.stat-value').textContent.includes('1'));await click('[data-action="logout"]');await until(()=>q('#auth-form'),'Logout');
 console.log('PASS: registration, login, profile/BMI, save/filter activities, timer pause/resume, reps/sets, workout storage, analytics/export, plan CRUD/completion/generation, hydration/wellness, achievements, overview, logout.');
 dom.window.close();
})().catch(e=>{console.error(e);process.exit(1)});
