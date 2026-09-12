// Uma fila por editor: agrupa alterações por campo e mantém a ordem das gravações.
export function createAutosave(onState, delay = 700) {
  const pending = new Map()
  const failed = new Map()
  let timer
  let running = null

  async function flush() {
    clearTimeout(timer)
    if (running) { await running; return flush() }
    if (!pending.size) return
    const batch = [...pending]
    pending.clear()
    running = (async () => {
      for (const [key, save] of batch) {
        try { await save(); failed.delete(key) }
        catch { if (!pending.has(key)) failed.set(key, save) }
      }
    })()
    await running
    running = null
    if (pending.size) return flush()
    onState(failed.size ? 'error' : 'saved')
  }

  return {
    schedule(key, save) {
      pending.set(key, save)
      failed.delete(key)
      onState('saving')
      clearTimeout(timer)
      timer = setTimeout(flush, delay)
    },
    retry() {
      for (const [key, save] of failed) if (!pending.has(key)) pending.set(key, save)
      failed.clear()
      onState('saving')
      return flush()
    },
    flush,
  }
}
