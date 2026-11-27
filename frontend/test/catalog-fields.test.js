import test from 'node:test'
import assert from 'node:assert/strict'
import { catalogPayload } from '../src/features/catalog/catalogFields.js'

const form = { campusId:'2', code:'P01', name:'Phòng 01', capacity:'40', type:'LECTURE',
  status:'ACTIVE', periodNo:'3', startTime:'07:00', endTime:'07:50', shift:'MORNING',
  effectiveFrom:'2026-09-01', startDate:'2026-09-02', endDate:'2026-09-03' }

test('room payload only includes room fields and numeric IDs', () => {
  assert.deepEqual(catalogPayload('rooms', form),
    { campusId:2, code:'P01', name:'Phòng 01', capacity:40, type:'LECTURE', status:'ACTIVE' })
})
test('period payload retains effective date and converts period number', () => {
  assert.deepEqual(catalogPayload('periods', form),
    { campusId:2, periodNo:3, startTime:'07:00', endTime:'07:50', shift:'MORNING', effectiveFrom:'2026-09-01' })
})
test('school-wide holidays retain null campus and exclude other fields', () => {
  assert.deepEqual(catalogPayload('holidays', {...form, campusId:''}),
    { campusId:null, name:'Phòng 01', startDate:'2026-09-02', endDate:'2026-09-03' })
})
