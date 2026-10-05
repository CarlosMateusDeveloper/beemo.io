import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseData, inputData, dataHora } from './posVendaData.js'

test('interpreta offsets PostgreSQL e preserva o instante da próxima ação', () => {
  assert.equal(parseData('2026-09-17 09:30:00-03').toISOString(), '2026-09-17T12:30:00.000Z')
  assert.equal(parseData('2026-09-17 12:30:00+00').toISOString(), '2026-09-17T12:30:00.000Z')
  assert.equal(parseData('2026-09-17T09:30:00-03:00').toISOString(), '2026-09-17T12:30:00.000Z')
})

test('entrada local de data não muda o instante ao ser salva', () => {
  const local = inputData('2026-09-17 12:30:00+00')
  assert.equal(new Date(local).toISOString(), '2026-09-17T12:30:00.000Z')
  assert.equal(dataHora(null), 'A definir')
  assert.equal(inputData(null), '')
})
