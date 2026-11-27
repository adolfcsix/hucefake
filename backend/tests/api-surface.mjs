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


// Architecture regression checks: keep HTTP adapters separate from persistence
// and never depend on controllers from application or persistence layers.
const javaFiles = readdirSync(root, { recursive: true }).filter(name => name.endsWith('.java'))
for (const name of javaFiles) {
  const relative = name.replaceAll('\\', '/')
  const source = readFileSync(new URL(relative, root), 'utf8')
  const packageSuffix = relative.split('/').slice(0, -1).join('.')
  const expectedPackage = 'vn.huce' + (packageSuffix ? '.' + packageSuffix : '')
  assert.equal(source.match(/^package\s+([\w.]+);/m)?.[1], expectedPackage, relative + ': package/path mismatch')
  if (relative.startsWith('controller/')) {
    assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|DataSource|EntityManager)\b/, relative + ': direct database access')
    assert.doesNotMatch(source, /import\s+(?:static\s+)?vn\.huce\.repository\./, relative + ': repository dependency')
    assert.doesNotMatch(source, /\b(?:SELECT|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM)\b/, relative + ': inline SQL')
  }
  if (/^(service|repository)\//.test(relative)) {
    assert.doesNotMatch(source, /\bvn\.huce\.controller\./, relative + ': reverse dependency on HTTP controllers')
  }
  if (source.includes('@RestControllerAdvice')) {
    assert.ok(relative.startsWith('exception/'), relative + ': error handling belongs in exception package')
  } else if (/@RestController\b/.test(source)) {
    assert.ok(relative.startsWith('controller/'), relative + ': HTTP endpoint outside controller package')
  }
}
assert.deepEqual(javaFiles.filter(name => !name.includes('/') && !name.includes('\\')).sort(),
  ['Accounts.java', 'HuceApplication.java'])
console.log('PASS: package layout, controller boundaries, reverse dependency and exception placement checks')
