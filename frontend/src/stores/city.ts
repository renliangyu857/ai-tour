import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { cityApi } from '@/api'
import type { City } from '@/types'
import { ElMessage } from 'element-plus'

export const useCityStore = defineStore('city', () => {
  const cities = ref<City[]>([])
  const selectedCities = ref<string[]>([])  // 已选城市编码列表（支持多城市联游）
  const loading = ref(false)

  const selectedCityObjects = computed(() =>
    cities.value.filter(c => selectedCities.value.includes(c.code))
  )

  const enabledCities = computed(() =>
    cities.value.filter(c => c.knowledgeIngested)
  )

  // Mock 数据：后端未启动时用于本地预览样式
  const MOCK_CITIES: City[] = [
    { code: 'qingdao', nameCn: '青岛', nameEn: 'Qingdao', province: '山东省', description: '红瓦绿树、碧海蓝天', knowledgeIngested: true },
    { code: 'beijing', nameCn: '北京', nameEn: 'Beijing', province: '北京市', description: '千年古都，历史文化名城', knowledgeIngested: false },
    { code: 'shanghai', nameCn: '上海', nameEn: 'Shanghai', province: '上海市', description: '国际大都市，东方明珠', knowledgeIngested: false },
  ]

  async function fetchCities() {
    loading.value = true
    try {
      cities.value = await cityApi.getEnabledCities()
      // 默认选中第一个城市（如有）
      if (cities.value.length > 0 && selectedCities.value.length === 0) {
        selectedCities.value = [cities.value[0].code]
      }
    } catch (e) {
      // 后端未启动时使用 Mock 数据（仅用于本地样式预览）
      console.warn('后端未启动，使用 Mock 城市数据')
      cities.value = MOCK_CITIES
      selectedCities.value = ['qingdao']
    } finally {
      loading.value = false
    }
  }

  function selectCity(code: string) {
    if (!selectedCities.value.includes(code)) {
      selectedCities.value = [code]  // 单选模式，切换城市时清空旧选择
    }
  }

  function toggleCity(code: string) {
    // 多选模式：用于联游场景
    const idx = selectedCities.value.indexOf(code)
    if (idx === -1) {
      selectedCities.value.push(code)
    } else if (selectedCities.value.length > 1) {
      selectedCities.value.splice(idx, 1)
    }
  }

  async function initDefaultAndIngest() {
    try {
      await cityApi.initDefaultCities()
      ElMessage.success('默认城市初始化成功，正在摄入知识库...')
      await fetchCities()
    } catch (e) {
      ElMessage.error('初始化失败')
    }
  }

  return {
    cities,
    selectedCities,
    selectedCityObjects,
    enabledCities,
    loading,
    fetchCities,
    selectCity,
    toggleCity,
    initDefaultAndIngest,
  }
})
