<template>
  <div class="city-selector">
    <div class="city-list" v-if="cityStore.cities.length > 0">
      <button
        v-for="city in cityStore.cities"
        :key="city.code"
        class="city-row"
        :class="{
          'selected': cityStore.selectedCities.includes(city.code),
          'not-ingested': !city.knowledgeIngested
        }"
        @click="handleToggle(city.code)"
      >
        <span class="city-dot" :class="{ ingested: city.knowledgeIngested }"></span>
        <span class="city-name">{{ city.nameCn }}</span>
        <span class="city-prov">{{ city.province }}</span>
        <span class="city-check" v-if="cityStore.selectedCities.includes(city.code)">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
            <path d="M1.5 5l2.5 2.5L8.5 2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
      </button>
    </div>

    <div v-else-if="cityStore.loading" class="city-skeleton">
      <div class="sk-row" v-for="i in 3" :key="i"></div>
    </div>

    <div v-else class="city-empty">
      <span>暂无城市数据</span>
      <button class="init-btn" @click="handleInit">初始化青岛</button>
    </div>

    <Transition name="badge-fade">
      <div v-if="cityStore.selectedCities.length > 1" class="multi-badge">
        <span class="badge-pulse"></span>
        <span>多城市联游</span>
        <span class="badge-count">{{ cityStore.selectedCities.length }}城</span>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { useCityStore } from '@/stores/city'
import { ingestApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const cityStore = useCityStore()

function handleToggle(code: string) {
  const idx = cityStore.selectedCities.indexOf(code)
  if (idx === -1) {
    if (cityStore.selectedCities.length >= 3) {
      ElMessage({ message: '最多联游 3 个城市', type: 'warning', duration: 2000 })
      return
    }
    cityStore.selectedCities = [...cityStore.selectedCities, code]
  } else {
    if (cityStore.selectedCities.length === 1) return // keep at least one
    cityStore.selectedCities = cityStore.selectedCities.filter(c => c !== code)
  }
}

async function handleInit() {
  try {
    await ElMessageBox.confirm(
      '将初始化青岛为默认城市并开始摄入知识库（约需 1-2 分钟）',
      '初始化确认',
      { confirmButtonText: '开始', cancelButtonText: '取消', type: 'info' }
    )
    await cityStore.initDefaultAndIngest()
    await ingestApi.ingestQingdao()
    ElMessage.success('青岛知识库摄入已启动，请稍候刷新')
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.city-selector {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* ── City list ── */
.city-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.city-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 7px;
  cursor: pointer;
  border: 1px solid transparent;
  background: transparent;
  transition: background 0.15s, border-color 0.15s;
  width: 100%;
  text-align: left;
}

.city-row:hover {
  background: rgba(255,255,255,0.07);
}

.city-row.selected {
  background: rgba(184, 146, 58, 0.13);
  border-color: rgba(184, 146, 58, 0.25);
}

.city-row.not-ingested {
  opacity: 0.55;
}

/* Ingestion dot */
.city-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255,255,255,0.2);
  flex-shrink: 0;
  transition: background 0.2s;
}

.city-dot.ingested {
  background: #4ade80;
  box-shadow: 0 0 5px rgba(74,222,128,0.4);
}

/* City name */
.city-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: rgba(255,255,255,0.85);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  letter-spacing: 0.01em;
}

.city-row.selected .city-name {
  color: #fff;
}

/* Province tag */
.city-prov {
  font-size: 10px;
  color: rgba(255,255,255,0.28);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

/* Check icon */
.city-check {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(184,146,58,0.8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* ── Skeleton ── */
.city-skeleton {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sk-row {
  height: 32px;
  border-radius: 7px;
  background: linear-gradient(90deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.09) 50%, rgba(255,255,255,0.05) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Empty state ── */
.city-empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}

.city-empty span {
  font-size: 12px;
  color: rgba(255,255,255,0.3);
}

.init-btn {
  align-self: flex-start;
  background: rgba(184,146,58,0.2);
  color: rgba(184,146,58,0.9);
  border: 1px solid rgba(184,146,58,0.3);
  border-radius: 5px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.2s;
}

.init-btn:hover {
  background: rgba(184,146,58,0.3);
  border-color: rgba(184,146,58,0.5);
}

/* ── Multi badge ── */
.multi-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 6px;
  background: rgba(184,146,58,0.1);
  border: 1px solid rgba(184,146,58,0.2);
  font-size: 11px;
  color: rgba(184,146,58,0.9);
  letter-spacing: 0.04em;
}

.badge-pulse {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #B8923A;
  animation: pulse 2s ease-in-out infinite;
}

.badge-count {
  margin-left: auto;
  font-size: 10px;
  opacity: 0.7;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

/* ── Transitions ── */
.badge-fade-enter-active,
.badge-fade-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}

.badge-fade-enter-from,
.badge-fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
