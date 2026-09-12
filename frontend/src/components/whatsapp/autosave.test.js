import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createAutosave } from './autosave.js'

test('preserva campos e regras editados rapidamente e agrupa o mesmo campo', async () => {
  const gravados = []
  const estados = []
  const fila = createAutosave((s) => estados.push(s))
  fila.schedule('saudacao', async () => gravados.push('antiga'))
  fila.schedule('opcoes', async () => gravados.push('opcoes'))
  fila.schedule('saudacao', async () => gravados.push('nova'))
  fila.schedule('regras', async () => gravados.push('regras'))
  await fila.flush()
  assert.deepEqual(gravados, ['nova', 'opcoes', 'regras'])
  assert.equal(estados.at(-1), 'saved')
})

test('informa falha e permite tentar novamente sem perder a alteração', async () => {
  let falha = true
  let estado
  const fila = createAutosave((s) => { estado = s })
  fila.schedule('texto', async () => { if (falha) throw new Error('offline') })
  await fila.flush()
  assert.equal(estado, 'error')
  falha = false
  await fila.retry()
  assert.equal(estado, 'saved')
})

test('serializa edições durante uma gravação lenta', async () => {
  let liberar
  const gravados = []
  const fila = createAutosave(() => {})
  fila.schedule('texto', async () => {
    await new Promise((resolve) => { liberar = resolve })
    gravados.push('anterior')
  })
  const primeira = fila.flush()
  fila.schedule('texto', async () => gravados.push('atual'))
  const segunda = fila.flush()
  liberar()
  await Promise.all([primeira, segunda])
  assert.deepEqual(gravados, ['anterior', 'atual'])
})
