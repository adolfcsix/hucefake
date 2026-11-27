import assert from 'node:assert/strict'
import { readFileSync, readdirSync, existsSync } from 'node:fs'
const root = new URL('../src/main/java/vn/huce/', import.meta.url)
const expected = readFileSync(new URL('./expected-routes.txt', import.meta.url), 'utf8').trim().split(/\r?\n/).sort()
const actual = []
for (const name of readdirSync(root, { recursive: true }).filter(x => x.endsWith('.java'))) {
  const code = readFileSync(new URL(name.replaceAll('\\\\', '/'), root), 'utf8')
  const prefix = code.match(/@RequestMapping\("([^"]+)"\)/)?.[1] || ''
  for (const [,verb,path] of code.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]+)"\))?/g))
    actual.push(verb.toUpperCase()+' '+prefix+(path||''))
  for (const [,path] of code.matchAll(/\.logoutUrl\("([^"]+)"\)/g)) actual.push('POST '+path)
}
assert.deepEqual(actual.sort(),expected)
for(const path of ['../package.json','../src/server.js','../../spring-backend'])
  assert.equal(existsSync(new URL(path,import.meta.url)),false,path+' must not exist')
console.log('PASS: '+expected.length+' legacy HTTP routes mapped to Spring Boot, no legacy Express runtime')
