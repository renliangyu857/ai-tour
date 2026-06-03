import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ItineraryResponse } from '@/api/agent'

export interface PlanBookItem {
  id: string
  savedAt: string
  customTitle?: string
  cityName: string
  cityCode: string
  startDate: string
  endDate: string
  totalDays: number
  tripSummary: string
  itinerary: ItineraryResponse
}

const STORAGE_KEY = 'voyage_planbook'

function loadFromStorage(): PlanBookItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export const usePlanBookStore = defineStore('planBook', () => {
  const items = ref<PlanBookItem[]>(loadFromStorage())

  function save(itinerary: ItineraryResponse): boolean {
    if (items.value.some(i => i.id === itinerary.itineraryId)) return false
    items.value.unshift({
      id: itinerary.itineraryId,
      savedAt: new Date().toISOString(),
      cityName: itinerary.cityName,
      cityCode: itinerary.cityCode,
      startDate: itinerary.startDate,
      endDate: itinerary.endDate,
      totalDays: itinerary.totalDays,
      tripSummary: itinerary.tripSummary,
      itinerary,
    })
    persist()
    return true
  }

  function remove(id: string) {
    const idx = items.value.findIndex(i => i.id === id)
    if (idx >= 0) { items.value.splice(idx, 1); persist() }
  }

  function isSaved(id: string): boolean {
    return items.value.some(i => i.id === id)
  }

  function updateTitle(id: string, title: string) {
    const item = items.value.find(i => i.id === id)
    if (item) { item.customTitle = title; persist() }
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items.value))
  }

  return { items, save, remove, isSaved, updateTitle }
})
