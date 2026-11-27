import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { navigation } from '../src/app/navigation.js'
const base=process.env.HUCE_TEST_URL||'http://127.0.0.1:8081'
const browser=await chromium.launch({headless:true})
const roles={STAFF:'Nhân viên đào tạo',MANAGER:'Quản lý',LECTURER:'Giảng viên',STUDENT:'Sinh viên'}
const usernames={STAFF:'daotao',MANAGER:'quanly',LECTURER:'giangvien',STUDENT:'sinhvien'}
const dir=new URL('../e2e-results/',import.meta.url)
await mkdir(dir,{recursive:true})
let pages=0
try{
 for(const width of [1440,390,320]){
  for(const [role,label] of Object.entries(roles)){
   const context=await browser.newContext({viewport:{width,height:900}})
   const page=await context.newPage()
   const failures=[]
   page.on('pageerror',e=>failures.push(e.message))
   page.on('response',r=>{if(r.url().includes('/api/')&&r.status()>=400&&!(r.status()===401&&r.url().endsWith('/api/me')))failures.push(r.status()+' '+r.url())})
   try{
    await page.goto(base)
    await page.locator('.demo-choice').first().waitFor()
    assert.equal(await page.locator('.demo-choice').count(),4)
    if(width===1440){
     await page.locator('.demo-choice').filter({hasText:label}).click()
    }else{
     await page.locator('input[autocomplete="username"]').fill(usernames[role])
     await page.locator('#login-password').fill('Fm05Demo@2026')
     await page.locator('.login-submit').click()
    }
    await page.locator('.workspace').waitFor()
    await page.waitForLoadState('networkidle')
    assert.equal(await page.locator('.identity strong').innerText(),usernames[role])
    for(const key of navigation[role].flatMap(x=>x[1])){
     if(width<768){
      const nav=page.locator('#workspace-navigation')
      if(!await nav.isVisible())await page.locator('.menu-toggle').click()
     }
     const link=page.locator('#workspace-navigation a[href="#'+key+'"]')
     await link.click()
     await page.waitForLoadState('networkidle')
     assert.equal(await link.getAttribute('aria-current'),'page')
     assert.equal(await page.locator('.content .error').count(),0,role+' '+key)
     assert.ok(await page.locator('.top h1').innerText())
     assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),role+' '+key+' overflow '+width)
     pages++
    }
    await page.locator('.chat-launcher').click()
    const panel=page.locator('.chat-panel')
    await panel.waitFor()
    await panel.getByPlaceholder('Nhập câu hỏi…').fill('Thông báo của tôi')
    await panel.getByRole('button',{name:'Gửi',exact:true}).click()
    await panel.locator('.chat-exchange').first().waitFor()
    await page.waitForLoadState('networkidle')
    assert.equal(await panel.locator('.error').count(),0)
    const rect=await panel.boundingBox()
    assert.ok(rect&&rect.x>=-1&&rect.x+rect.width<=width+1)
    await page.screenshot({path:new URL(role+'-'+width+'.png',dir).pathname,fullPage:true})
    await panel.getByRole('button',{name:'Đóng HUCEChat'}).click()
    const logout=page.locator('button[aria-label="Đăng xuất"]:visible').first()
    await logout.click()
    await page.locator('#login-title').waitFor()
    assert.deepEqual(failures,[])
   }finally{await context.close()}
  }
 }
 console.log('PASS: '+pages+' role-page visits, demo/manual login, chat, logout, desktop and mobile Chromium')
}finally{await browser.close()}
