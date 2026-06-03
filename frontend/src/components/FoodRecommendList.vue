<template>
  <div class="food-list">
    <div v-if="foods.length === 0" class="empty-tip">暂无餐厅推荐</div>
    <div v-for="food in foods" :key="food.name" class="food-card">
      <div class="food-header">
        <span class="food-name">{{ food.name }}</span>
        <span class="food-category">{{ food.category }}</span>
        <span class="food-badge" :class="sourceClass(food.dataSource)">
          {{ food.dataSource === 'gaode_api' ? '高德' : 'Mock' }}
        </span>
      </div>
      <div class="food-meta">
        <span class="rating">⭐ {{ food.rating.toFixed(1) }}</span>
        <span class="price">💰 {{ food.priceRange }}</span>
        <span class="distance">📍 {{ food.distanceKm }}km</span>
        <span class="status" :class="food.businessStatus === '营业中' ? 'open' : 'closed'">
          {{ food.businessStatus }}
        </span>
      </div>
      <div class="food-address">{{ food.address }}</div>
      <div v-if="food.openingHours" class="food-hours">🕐 {{ food.openingHours }}</div>
      <div class="food-reason">{{ food.recommendReason }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { FoodRecommendation } from '@/api/agent'

defineProps<{ foods: FoodRecommendation[] }>()

function sourceClass(source: string) {
  return source === 'gaode_api' ? 'badge-real' : 'badge-mock'
}
</script>

<style scoped>
.food-list { display: flex; flex-direction: column; gap: 10px; }
.empty-tip { color: #999; text-align: center; padding: 16px; }
.food-card {
  background: #fff;
  border: 1px solid #e8ecf0;
  border-radius: 10px;
  padding: 12px 14px;
  transition: box-shadow .2s;
}
.food-card:hover { box-shadow: 0 2px 10px rgba(0,0,0,.08); }
.food-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.food-name { font-weight: 600; font-size: 15px; color: #1a1a2e; }
.food-category {
  font-size: 11px; background: #f0f4ff; color: #4a7cf0;
  padding: 2px 7px; border-radius: 4px;
}
.food-badge { font-size: 10px; padding: 1px 6px; border-radius: 10px; margin-left: auto; }
.badge-real { background: #e8f5e9; color: #2e7d32; }
.badge-mock { background: #fff3e0; color: #e65100; }
.food-meta { display: flex; gap: 12px; font-size: 13px; color: #555; margin-bottom: 4px; flex-wrap: wrap; }
.status.open  { color: #2e7d32; font-weight: 500; }
.status.closed{ color: #c62828; }
.food-address { font-size: 12px; color: #888; margin-bottom: 3px; }
.food-hours   { font-size: 12px; color: #888; margin-bottom: 4px; }
.food-reason  { font-size: 12px; color: #666; font-style: italic; background: #f9f9f9; padding: 4px 8px; border-radius: 4px; }
</style>
