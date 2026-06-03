import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export interface FavoriteItem {
  id: string
  content: string
  cities: string[]
  question: string   // the user question that prompted this reply
  savedAt: Date
  sessionTitle: string
}

const STORAGE_KEY = 'voyage_favorites'

function loadFromStorage(): FavoriteItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    return JSON.parse(raw).map((item: any) => ({
      ...item,
      savedAt: new Date(item.savedAt),
    }))
  } catch {
    return []
  }
}

export const useFavoritesStore = defineStore('favorites', () => {
  const items = ref<FavoriteItem[]>(loadFromStorage())

  // Persist on every change
  watch(items, (val) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
  }, { deep: true })

  function add(item: Omit<FavoriteItem, 'id' | 'savedAt'>) {
    items.value.unshift({
      ...item,
      id: crypto.randomUUID(),
      savedAt: new Date(),
    })
  }

  function remove(id: string) {
    const idx = items.value.findIndex(i => i.id === id)
    if (idx !== -1) items.value.splice(idx, 1)
  }

  function isSaved(content: string): boolean {
    return items.value.some(i => i.content === content)
  }

  return { items, add, remove, isSaved }
})
