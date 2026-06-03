import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

export interface Note {
  id: string
  title: string
  content: string
  tags: string[]
  pinned: boolean
  createdAt: string
  updatedAt: string
  wordCount: number
  sourceMessage?: string
}

const STORAGE_KEY = 'voyage_notes'

function loadFromStorage(): Note[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function countWords(text: string): number {
  const clean = text.replace(/```[\s\S]*?```/g, '').replace(/[#*`_~>[\]()]/g, '')
  const cjk = (clean.match(/[\u4e00-\u9fff\u3400-\u4dbf\u20000-\u2a6df]/g) || []).length
  const latin = (clean.match(/\b[a-zA-Z0-9]+\b/g) || []).length
  return cjk + latin
}

export const useNotesStore = defineStore('notes', () => {
  const notes = ref<Note[]>(loadFromStorage())

  watch(notes, (val) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
  }, { deep: true })

  const pinnedNotes = computed(() =>
    notes.value.filter(n => n.pinned).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  )
  const unpinnedNotes = computed(() =>
    notes.value.filter(n => !n.pinned).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  )
  const sortedNotes = computed(() => [...pinnedNotes.value, ...unpinnedNotes.value])

  function createNote(initialContent = '', sourceMessage?: string): Note {
    const now = new Date().toISOString()
    const lines = initialContent.split('\n')
    const firstLine = lines[0]?.replace(/^#+\s*/, '').trim() || '新笔记'
    const note: Note = {
      id: crypto.randomUUID(),
      title: firstLine.length > 30 ? firstLine.slice(0, 30) + '…' : firstLine || '新笔记',
      content: initialContent,
      tags: [],
      pinned: false,
      createdAt: now,
      updatedAt: now,
      wordCount: countWords(initialContent),
      sourceMessage,
    }
    notes.value.unshift(note)
    return note
  }

  function updateNote(id: string, updates: Partial<Omit<Note, 'id' | 'createdAt'>>) {
    const idx = notes.value.findIndex(n => n.id === id)
    if (idx === -1) return
    notes.value[idx] = {
      ...notes.value[idx],
      ...updates,
      wordCount: countWords(updates.content ?? notes.value[idx].content),
      updatedAt: new Date().toISOString(),
    }
  }

  function deleteNote(id: string) {
    notes.value = notes.value.filter(n => n.id !== id)
  }

  function togglePin(id: string) {
    const note = notes.value.find(n => n.id === id)
    if (note) note.pinned = !note.pinned
  }

  return { notes, sortedNotes, pinnedNotes, unpinnedNotes, createNote, updateNote, deleteNote, togglePin, countWords }
})
