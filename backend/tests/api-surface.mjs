import assert from 'node:assert/strict'
import { readFileSync, readdirSync, existsSync } from 'node:fs'
const root = new URL('../src/main/java/vn/huce/', import.meta.url)
const expected = readFileSync(new URL('./expected-routes.txt', import.meta.url), 'utf8').trim().split(/\r?\n/).sort()
const actual = []
for (const name of readdirSync(root, { recursive: true }).filter(x => x.endsWith('.java'))) {
  const code = readFileSync(new URL(name.replaceAll('\\', '/'), root), 'utf8')
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

for (const name of ['ChatIntent', 'ChatDateRangeParser', 'ChatPermissions', 'ChatService', 'GeminiAdvisor', 'ProposalService']) {
  assert.ok(existsSync(new URL('service/chat/' + name + '.java', root)), name + ' belongs in service/chat')
}
console.log('PASS: HUCEChat package organization')


// The refactored section application service must not fall back to raw SQL.
const sectionService = readFileSync(new URL('service/SectionService.java', root), 'utf8')
assert.doesNotMatch(sectionService, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
assert.ok(existsSync(new URL('repository/SectionRepository.java', root)))
assert.ok(existsSync(new URL('model/SectionDraft.java', root)))
console.log('PASS: section domain repository and typed draft model')

const sectionMutationService = readFileSync(new URL('service/SectionMutationService.java', root), 'utf8')
assert.doesNotMatch(sectionMutationService, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
assert.ok(existsSync(new URL('repository/SchedulePatternRepository.java', root)))
assert.ok(existsSync(new URL('model/PatternDraft.java', root)))
console.log('PASS: section mutation persistence boundaries')

const catalogMutationService = readFileSync(new URL('service/CatalogMutationService.java', root), 'utf8')
assert.doesNotMatch(catalogMutationService, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
assert.ok(existsSync(new URL('repository/CatalogMutationRepository.java', root)))
for (const name of ['RoomDraft', 'HolidayDraft', 'PeriodDraft']) {
  assert.ok(existsSync(new URL('model/' + name + '.java', root)))
}
console.log('PASS: catalog mutation persistence boundaries')

for (const name of ['ApprovalService', 'NotificationService']) {
  const source = readFileSync(new URL('service/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
assert.ok(existsSync(new URL('repository/ApprovalRepository.java', root)))
assert.ok(existsSync(new URL('repository/NotificationRepository.java', root)))
console.log('PASS: approval and notification persistence boundaries')

for (const name of ['SessionMutationService', 'AvailabilityService']) {
  const source = readFileSync(new URL('service/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
assert.ok(existsSync(new URL('repository/SessionChangeRepository.java', root)))
assert.ok(existsSync(new URL('repository/AvailabilityRepository.java', root)))
console.log('PASS: session change and lecturer availability persistence boundaries')

const sessionReadService = readFileSync(new URL('service/SessionReadService.java', root), 'utf8')
assert.doesNotMatch(sessionReadService, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
assert.ok(existsSync(new URL('repository/SessionReadRepository.java', root)))
assert.ok(existsSync(new URL('model/ScheduleFilter.java', root)))
const chatService = readFileSync(new URL('service/chat/ChatService.java', root), 'utf8')
assert.doesNotMatch(chatService, /scheduleQuery\(|scoped\.sql\(|scoped\.args\(/)
console.log('PASS: typed schedule filters and shared read boundaries')

for (const name of ['ChatService', 'ProposalService']) {
  const source = readFileSync(new URL('service/chat/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
assert.ok(existsSync(new URL('repository/ChatRepository.java', root)))
assert.ok(existsSync(new URL('repository/ProposalRepository.java', root)))
console.log('PASS: chat and proposal persistence boundaries')

for (const name of ['ChatClassifier', 'ChatReplyService']) {
  const source = readFileSync(new URL('service/chat/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
assert.ok(existsSync(new URL('dto/ChatClassification.java', root)))
console.log('PASS: chat classification and reply boundaries')

for (const name of ['SchedulingService', 'ScheduleCalendar']) {
  const source = readFileSync(new URL('service/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
assert.ok(existsSync(new URL('repository/SchedulingRepository.java', root)))
console.log('PASS: scheduling persistence and calendar boundaries')

for (const name of ['RunService', 'RunWorker']) {
  const source = readFileSync(new URL('service/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
assert.ok(existsSync(new URL('repository/RunRepository.java', root)))
console.log('PASS: run lifecycle persistence boundaries')

for (const name of ['GeneticScheduler', 'GeneticInputBuilder', 'GeneticFitnessEvaluator']) {
  const source = readFileSync(new URL('service/' + name + '.java', root), 'utf8')
  assert.doesNotMatch(source, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
}
console.log('PASS: genetic input and fitness boundaries')

const geneticEvolution = readFileSync(new URL('service/GeneticEvolution.java', root), 'utf8')
assert.doesNotMatch(geneticEvolution, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
console.log('PASS: genetic evolution persistence boundary')

const mutationSource = readFileSync(new URL('service/MutationService.java', root), 'utf8')
assert.doesNotMatch(mutationSource, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
assert.ok(existsSync(new URL('repository/MutationRepository.java', root)))
assert.ok(existsSync(new URL('model/IdempotencyRecord.java', root)))
const runRepositorySource = readFileSync(new URL('repository/RunRepository.java', root), 'utf8')
assert.doesNotMatch(runRepositorySource, /pg_advisory_xact_lock/)
console.log('PASS: shared mutation locking and typed idempotency records')

const chatControllerSource = readFileSync(new URL('controller/ChatController.java', root), 'utf8')
assert.doesNotMatch(chatControllerSource, /\bGeminiAdvisor\b|gemini\.(?:take|classify)\(/)
assert.ok(existsSync(new URL('service/chat/ScheduleAssistantService.java', root)))
console.log('PASS: schedule assistant classification stays in service layer')

const constraintSource = readFileSync(new URL('service/ScheduleConstraints.java', root), 'utf8')
assert.doesNotMatch(constraintSource, /\b(?:SqlStore|JdbcTemplate|SELECT|INSERT\s+INTO|DELETE\s+FROM|UPDATE\s+\w+\s+SET)\b/)
console.log('PASS: in-memory schedule constraints remain independent of persistence')

for (const name of javaFiles) {
  const relative = name.replaceAll('\\', '/')
  const source = readFileSync(new URL(relative, root), 'utf8')
  if (/^@Service\b/m.test(source)) assert.ok(relative.startsWith('service/'), relative + ': misplaced service')
  if (/^@Repository\b/m.test(source)) assert.ok(relative.startsWith('repository/'), relative + ': misplaced repository')
  if (/^@Configuration\b/m.test(source)) assert.ok(relative.startsWith('config/'), relative + ': misplaced configuration')
  if (/^(model|dto)\//.test(relative)) {
    assert.doesNotMatch(source, /@(Service|Repository|RestController|Component|Configuration)\b/, relative + ': data type is not a Spring component')
  }
}
const runtimeModeSource = readFileSync(new URL('config/RuntimeMode.java', root), 'utf8')
assert.doesNotMatch(runtimeModeSource, /\b(?:JdbcTemplate|SqlStore|SELECT|INSERT\s+INTO|LOCK\s+TABLE)\b/)
console.log('PASS: Spring stereotype placement and runtime initialization persistence boundary')

const setupSource = readFileSync(new URL('cli/SetupCommand.java', root), 'utf8')
assert.match(setupSource, /new RuntimeMode\(new RuntimeModeRepository\(/)
assert.doesNotMatch(setupSource, /pg_advisory_xact_lock/)
assert.match(setupSource, /new MutationRepository\(db\.jdbc\(\)\)\.lock\(\)/)
console.log('PASS: CLI shares runtime-mode protection and mutation lock')

const sqlAdapterSource = readFileSync(new URL('repository/SqlStore.java', root), 'utf8')
assert.doesNotMatch(sqlAdapterSource, /\bAuditRepository\b|\bvoid\s+audit\s*\(/)
console.log('PASS: SqlStore no longer owns audit dependencies')

assert.doesNotMatch(setupSource, /\b(?:SELECT\s|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM)\b/)
assert.ok(existsSync(new URL('repository/SetupRepository.java', root)))
console.log('PASS: local setup SQL belongs to SetupRepository')
