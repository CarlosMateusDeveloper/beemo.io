import test from 'node:test'
import assert from 'node:assert/strict'
import { authenticatedRequest, sessaoAtualizada, aoReceberNaoAutenticado } from './apiClient.js'
const response = (status, body = {}) => ({ status, ok: status >= 200 && status < 300, json: async () => body })
test('cookies enviados sem Authorization e 401 encerra contexto', async () => {
  sessaoAtualizada()
  let ended = 0
  aoReceberNaoAutenticado(() => ended++)
  globalThis.fetch = async (_, opts) => { assert.equal(opts.credentials, 'include'); assert.equal(opts.headers.has('Authorization'), false); return response(401) }
  await assert.rejects(authenticatedRequest('/private'), /Sessão expirada/)
  assert.equal(ended, 1)
})
test('403 de permissão e erro de login não encerram a sessão', async () => {
  sessaoAtualizada()
  let ended = 0
  aoReceberNaoAutenticado(() => ended++)
  globalThis.fetch = async () => response(403)
  await assert.rejects(authenticatedRequest('/private'), /permissão/)
  globalThis.fetch = async () => response(401, { message: 'Senha inválida' })
  await assert.rejects(authenticatedRequest('/login', { authenticated: false }), /Senha inválida/)
  assert.equal(ended, 0)
})
test('401 atrasado não encerra sessão mais nova', async () => {
  sessaoAtualizada()
  let resolve
  let ended = 0
  aoReceberNaoAutenticado(() => ended++)
  globalThis.fetch = () => new Promise(r => { resolve = r })
  const request = authenticatedRequest('/private')
  sessaoAtualizada(); resolve(response(401))
  await assert.rejects(request)
  assert.equal(ended, 0)
})
test('POST obtém CSRF; renova somente quando o servidor rejeita CSRF', async () => {
  sessaoAtualizada()
  let csrf = 0, posts = 0
  globalThis.fetch = async (url, options) => {
    assert.equal(options.credentials, 'include')
    if (url.endsWith('/csrf')) return response(200, { token: 'csrf-' + (++csrf) })
    posts++
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf-' + csrf)
    return posts === 1 ? response(403, { code: 'csrf_invalid' }) : response(200, { ok: true })
  }
  assert.deepEqual(await authenticatedRequest('/post', { method: 'POST' }), { ok: true })
  assert.equal(csrf, 2); assert.equal(posts, 2)
})
test('CSRF compartilhado entre solicitações simultâneas', async () => {
  sessaoAtualizada()
  let csrf = 0
  globalThis.fetch = async url => url.endsWith('/csrf') ? (csrf++, response(200, { token: 'shared' })) : response(204)
  await Promise.all([authenticatedRequest('/a', { method: 'POST' }), authenticatedRequest('/b', { method: 'DELETE' })])
  assert.equal(csrf, 1)
})
