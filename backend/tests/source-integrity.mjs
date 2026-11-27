import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync,existsSync } from 'node:fs'
import { dirname,resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root=fileURLToPath(new URL('../../',import.meta.url))
const files=execFileSync('git',['ls-files','-z'],{cwd:root,encoding:'utf8'}).split('\0').filter(Boolean)
const forbidden=files.filter(p=>
  /(^|\/)(node_modules|dist|target|e2e-results|audit-results)(\/|$)/.test(p)||
  /(^|\/)\.env(?:\.|$)/.test(p)&&p!=='.env.example'||
  /^config\/.*\.json$/i.test(p)&&p!=='config/local.template.json'||
  /^\.ci-(?:maven-cache|package)\//.test(p)||
  /\.(dump|backup|sql\.gz)$/i.test(p)||
  /^spring-backend\/|^backend\/(?:package(?:-lock)?\.json|scripts\/|src\/server\.js|src\/modules\/)/.test(p))
assert.deepEqual(forbidden,[],'Do not track private data, build outputs or legacy backend')
const read=p=>readFileSync(resolve(root,p),'utf8')
const template=JSON.parse(read('config/local.template.json'))
assert.equal(template.format,'fm05-local')
assert.equal(template.version,1)
for(const key of ['units','campuses','semesters','subjects','groups','enrollments'])assert.deepEqual(template[key],[],key+' must be blank')
assert.equal(JSON.parse(read('frontend/package.json')).devDependencies.vite,JSON.parse(read('frontend/package-lock.json')).packages[''].devDependencies.vite)
const routes=read('backend/tests/expected-routes.txt').trim().split(/\r?\n/).map(s=>s.split(' ')[1])
let references=0,imports=0
for(const file of files.filter(p=>/^frontend\/src\/.*\.(js|jsx)$/.test(p))){
 const code=read(file)
 for(const [,spec] of code.matchAll(/(?:from\s*|import\s*)['"](\.[^'"]+)['"]/g)){
  const path=resolve(root,dirname(file),spec)
  assert.ok(['','.js','.jsx','.css','/index.js','/index.jsx'].some(s=>existsSync(path+s)),file+' missing import '+spec)
  imports++
 }
 // Static first argument only. Dynamic IDs, verbs and assembled paths are covered by integration/browser tests.
 for(const [,literal] of code.matchAll(/\b(?:api|request|usePagedQuery)\(\s*['"](\/[^'"]+)['"]/g)){
  const prefix='/api'+literal.split('?')[0]
  assert.ok(routes.some(r=>r===prefix||r.startsWith(prefix.endsWith('/')?prefix:prefix+'/')),file+' unknown API prefix '+prefix)
  references++
 }
}
assert.ok(references>30&&imports>15,'Unexpectedly low coverage')
console.log('PASS: tracked-file hygiene, blank template, Vite manifest/lock consistency, '+imports+' relative imports, '+references+' static API path references')


const sourceRoot = 'frontend/src/'
const frontendSource = files.filter(p => p.startsWith(sourceRoot))
assert.deepEqual(frontendSource.filter(p => !p.slice(sourceRoot.length).includes('/')), ['frontend/src/main.jsx'],
  'Keep only the React entry point at the source root')
for (const path of frontendSource) {
  const category = path.slice(sourceRoot.length).split('/')[0]
  if (category === 'main.jsx') continue
  assert.ok(['app','assets','components','constants','features','hooks','services','styles','utils'].includes(category),
    path + ' belongs in a named source folder')
}
console.log('PASS: frontend source folder organization')
