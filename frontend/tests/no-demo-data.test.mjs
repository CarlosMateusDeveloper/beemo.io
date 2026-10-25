import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import test from 'node:test'

test('build de produção não inclui pacientes de demonstração', () => {
  const assets = join(import.meta.dirname, '..', 'dist', 'assets')
  const bundle = readdirSync(assets)
    .filter(name => name.endsWith('.js'))
    .map(name => readFileSync(join(assets, name), 'utf8'))
    .join('\n')

  assert.ok(bundle.length > 0, 'execute npm run build antes deste teste')
  assert.equal(bundle.includes('Dados demonstrativos'), false)
  assert.equal(bundle.includes('Prévia do MVP com dados de exemplo'), false)
  assert.equal(bundle.includes('pat-001'), false)
})
