import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { navigation } from '../src/app/navigation.js'

assert.equal(process.env.HUCE_BROWSER_CI,'true')
const base=process.env.HUCE_TEST_URL
assert.ok(base&&['localhost','127.0.0.1'].includes(new URL(base).hostname))
assert.equal((await fetch(base+'/api/auth/mode').then(r=>r.json())).demo,false)
const phase=process.env.HUCE_PRIVATE_PHASE
assert.ok(['empty','term'].includes(phase))
assert.ok(process.env.FM05_SETUP_PASSWORD)
const browser=await chromium.launch({headless:true})
const dir=new URL('../e2e-results/',import.meta.url)
await mkdir(dir,{recursive:true})
let visits=0
const failures=[]
const users={STAFF:'private_staff',MANAGER:'private_manager',LECTURER:'private_lecturer',STUDENT:'private_student'}
const button=(root,name)=>root.getByRole('button',{name,exact:true})
async function open(role,width){
 const context=await browser.newContext({viewport:{width,height:900}})
 const page=await context.newPage()
 page.on('pageerror',e=>failures.push(e.message))
 page.on('response',r=>{
  if(r.url().includes('/api/')&&r.status()>=400&&!(r.status()===401&&r.url().endsWith('/api/me')))failures.push(r.status()+' '+r.url())
 })
 const mode=page.waitForResponse(r=>r.url().endsWith('/api/auth/mode')&&r.ok())
 await page.goto(base)
 await mode
 await page.locator('#login-title').waitFor()
 assert.equal(await page.locator('.demo-choice').count(),0)
 await page.locator('input[autocomplete="username"]').fill(users[role])
 await page.locator('#login-password').fill(process.env.FM05_SETUP_PASSWORD)
 await page.locator('.login-submit').click()
 await page.locator('.workspace').waitFor()
 await page.waitForLoadState('networkidle')
 assert.equal(await page.locator('.identity strong').innerText(),users[role])
 return {context,page}
}
async function navigate(page,key,width){
 if(width<768&&!await page.locator('#workspace-navigation').isVisible())await page.locator('.menu-toggle').click()
 const link=page.locator('#workspace-navigation a[href="#'+key+'"]')
 await link.click()
 await page.waitForLoadState('networkidle')
 assert.equal(await link.getAttribute('aria-current'),'page')
 assert.equal(await page.locator('.content .error').count(),0,key)
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),key+' overflow '+width)
}
async function chat(page,question,answer){
 await page.locator('.chat-launcher').click()
 const panel=page.locator('.chat-panel')
 await panel.getByPlaceholder('Nhập câu hỏi…').fill(question)
 await button(panel,'Gửi').click()
 await panel.locator('.chat-exchange').last().getByText(answer,{exact:true}).waitFor()
 assert.equal(await panel.locator('.error').count(),0)
 await panel.getByRole('button',{name:'Đóng HUCEChat'}).click()
}
async function logout(page){
 await page.locator('button[aria-label="Đăng xuất"]:visible').first().click()
 await page.locator('#login-title').waitFor()
 assert.equal(await page.locator('.demo-choice').count(),0)
}
try{
 for(const width of [1440,390]){
  for(const role of Object.keys(users)){
   const {context,page}=await open(role,width)
   try{
    const keys=navigation[role].flatMap(x=>x[1])
    assert.equal(await page.locator('#workspace-navigation a').count(),keys.length)
    for(const key of keys){
     await navigate(page,key,width)
     if(phase==='empty'&&!['catalog','notifications'].includes(key)){
      await page.getByText('Chưa có học kỳ.',{exact:true}).waitFor()
      assert.equal(await page.locator('.term select').count(),0)
     }
     if(phase==='term'&&!['catalog','notifications'].includes(key)){
      await page.locator('.term select').waitFor()
      assert.equal(await page.locator('.term select option').count(),1)
      assert.equal(await page.getByText('Chưa có học kỳ.',{exact:true}).count(),0)
     }
     visits++
    }
    await navigate(page,'notifications',width)
    await page.getByText('Chưa có thông báo.',{exact:true}).waitFor()
    await chat(page,'Thông báo của tôi','Bạn chưa có thông báo.')
    if(phase==='empty'){
     await navigate(page,keys.find(k=>!['catalog','notifications'].includes(k)),width)
     await chat(page,'Thời khóa biểu tuần này','Hãy chọn học kỳ trong khu vực làm việc rồi thử lại.')
     await button(page,'Tải lại dữ liệu').click()
     await page.getByText('Chưa có học kỳ.',{exact:true}).waitFor()
    }
    await page.screenshot({path:new URL('private-'+phase+'-'+role+'-'+width+'.png',dir).pathname,fullPage:true})
    await logout(page)
   }finally{await context.close()}
  }
 }
 if(phase==='term'){
  // Both Compose applications run on 127.0.0.1; cookies are not isolated by port.
  const context=await browser.newContext()
  try{
   const demo='http://127.0.0.1:8081'
   const request=context.request
   async function mutation(origin,path,body){
    const tokenResponse=await request.get(origin+'/api/csrf')
    assert.equal(tokenResponse.status(),200)
    const token=await tokenResponse.json()
    return request.post(origin+path,{headers:{[token.header]:token.token},...(body===undefined?{}:{data:body})})
   }
   assert.equal((await mutation(demo,'/api/auth/demo-login',{role:'STUDENT'})).status(),200)
   assert.equal((await mutation(base,'/api/auth/login',{username:users.STUDENT,password:process.env.FM05_SETUP_PASSWORD})).status(),200)
   assert.equal((await request.get(demo+'/api/me').then(r=>r.json())).username,'sinhvien')
   assert.equal((await request.get(base+'/api/me').then(r=>r.json())).username,users.STUDENT)
   const names=(await context.cookies()).map(cookie=>cookie.name)
   assert.ok(names.includes('HUCE_DEMO_SID'))
   assert.ok(names.includes('HUCE_PRIVATE_SID'))
   assert.equal((await mutation(base,'/api/auth/logout')).status(),204)
   assert.equal((await request.get(base+'/api/me')).status(),401)
   assert.equal((await request.get(demo+'/api/me').then(r=>r.json())).username,'sinhvien')
   assert.equal((await mutation(demo,'/api/auth/logout')).status(),204)
   assert.equal((await request.get(demo+'/api/me')).status(),401)
   console.log('PASS: demo/private session isolation on one browser and host')
  }finally{await context.close()}
  const staff=await open('STAFF',1440)
  const page=staff.page
  try{
   await navigate(page,'sections',1440)
   await page.getByText('Không tìm thấy lớp học phần.',{exact:true}).waitFor()
   await button(page,'Thêm lớp học phần').click()
   const form=page.getByRole('dialog')
   await form.getByLabel('Mã lớp',{exact:true}).fill('PRIVATE.UI.01')
   const subject=form.getByLabel('Học phần',{exact:true})
   await subject.selectOption({label:'SUB · Học phần kiểm thử'})
   await form.getByLabel('Sĩ số tối đa').fill('30')
   await button(form,'Lưu').click()
   await form.waitFor({state:'hidden'})
   await page.getByRole('row').filter({hasText:'PRIVATE.UI.01'}).waitFor()
   const manager=await open('MANAGER',1440)
   try{
    await navigate(manager.page,'sections',1440)
    await manager.page.getByRole('row').filter({hasText:'PRIVATE.UI.01'}).waitFor()
    assert.equal(await button(manager.page,'Thêm lớp học phần').count(),0)
   }finally{await manager.context.close()}
  }finally{await staff.context.close()}
 }
 assert.deepEqual(failures,[])
 console.log('PASS: private browser '+phase+', '+visits+' role-page visits, no demo controls, role-scoped chat, '+(phase==='term'?'first real section and manager readback':'no-semester recovery'))
}finally{await browser.close()}
