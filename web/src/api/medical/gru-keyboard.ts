/** One Enter gesture can never both finish IME input and submit a record. */
export function createGruComposerKeys() {
  let composing = false
  let compositionEnded = false
  return {
    start() { composing = true; compositionEnded = false },
    end() { composing = false; compositionEnded = true },
    keyup(event: Pick<KeyboardEvent, 'key'>) { if (event.key === 'Enter') compositionEnded = false },
    shouldSubmit(event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'ctrlKey' | 'metaKey' | 'altKey' | 'isComposing' | 'keyCode' | 'repeat'>) {
      if (event.key !== 'Enter') { compositionEnded = false; return false }
      if (composing || compositionEnded || event.isComposing || event.keyCode === 229 || event.repeat) return false
      return !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey
    },
    reset() { composing = false; compositionEnded = false }
  }
}

/** Registration stores callbacks only, never a document, session, draft or message. */
let registered: { open: () => void; close: () => void } | null = null
export function registerGruEntry(entry: { open: () => void; close: () => void }) {
  registered?.close()
  registered = entry
  return () => { if (registered === entry) { entry.close(); registered = null } }
}
export function openRegisteredGru(): boolean {
  if (!registered) return false
  registered.open()
  return true
}
