import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('voyage_token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// ──────────────────────────── Types ────────────────────────────

export interface ItineraryRequest {
  cityCode: string
  startDate: string
  endDate: string
  preferences?: string[]
  budget?: 'low' | 'medium' | 'high'
  transportMode?: 'walking' | 'driving' | 'transit'
  adults?: number
  children?: number
}

export interface WeatherInfo {
  date: string
  condition: string          // sunny / cloudy / rainy etc.
  conditionText: string      // 晴 / 多云 / 小雨
  tempHigh: number
  tempLow: number
  windDir: string
  windScale: string
  humidity: number
  uvIndex: string
  outdoorFriendly: boolean
  dataSource: string
}

export interface PoiInfo {
  id: string
  name: string
  category: string
  address: string
  lat: number
  lng: number
  rating: number
  openingHours: string
  ticketPrice: string
  visitDurationMinutes: number
  indoorVenue: boolean
  tags: string[]
  description: string
  dataSource: string
}

export interface RouteLeg {
  fromName: string
  toName: string
  distanceKm: number
  durationMinutes: number
  transportSuggestion: string
  instruction: string
}

export interface RouteInfo {
  optimizedPois: PoiInfo[]
  legs: RouteLeg[]
  totalDistanceKm: number
  totalDurationMinutes: number
  optimizationMethod: string
  dataSource: string
}

export interface TimeSlotActivity {
  timeSlot: string
  activity: string
  type: string
  poi?: PoiInfo
  durationMinutes: number
  transportFromPrev: string
  transportMinutes: number
  estimatedCost: number
  notes: string
}

export interface FoodRecommendation {
  name: string
  category: string
  rating: number
  priceRange: string
  distanceKm: number
  address: string
  businessStatus: string
  openingHours: string
  phone?: string
  recommendReason: string
  mealType: string
  lat: number
  lng: number
  dataSource: string
}

export interface DayPlan {
  date: string
  dayNumber: number
  dayOfWeek: string
  weather: WeatherInfo
  mainPlanTitle: string
  mainActivities: TimeSlotActivity[]
  alternatePlanTitle: string
  alternateActivities: TimeSlotActivity[]
  route: RouteInfo
  foods: FoodRecommendation[]
  tips: string[]
  narrative: string
  budget: Record<string, string>
}

export interface ToolCallLog {
  toolName: string
  provider: string
  startTime: string
  durationMs: number
  success: boolean
  usedFallback: boolean
  errorMessage?: string
}

export interface ItineraryResponse {
  itineraryId: string
  requestId: string
  cityCode: string
  cityName: string
  startDate: string
  endDate: string
  totalDays: number
  preferences: string[]
  budget: string
  transportMode: string
  tripSummary: string
  days: DayPlan[]
  totalBudget: Record<string, string>
  toolCallLogs: ToolCallLog[]
  generatedAt: string
  hasRealWeatherData: boolean
  hasRealPoiData: boolean
  hasRealFoodData: boolean
}

// ──────────────────────────── API ────────────────────────────

export const agentApi = {
  generateItinerary(req: ItineraryRequest): Promise<ItineraryResponse> {
    return api.post('/agent/itinerary', req).then(r => r.data)
  },

  getItinerary(id: string): Promise<ItineraryResponse> {
    return api.get(`/agent/itinerary/${id}`).then(r => r.data)
  },

  getStatus(): Promise<unknown> {
    return api.get('/agent/status').then(r => r.data)
  },
}
