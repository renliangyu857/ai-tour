<template>
  <div class="chat-window" ref="chatWindowRef">

    <!-- ─── Welcome screen ─────────────────────────────── -->
    <div v-if="!chatStore.currentSession" class="welcome">
      <div class="welcome-inner">
        <div class="welcome-mark">
          <span class="welcome-gem">◆</span>
        </div>
        <h2 class="welcome-title">你想去哪里？</h2>
        <p class="welcome-sub">在左侧选择城市，开始规划你的旅程</p>

        <div class="quick-section">
          <div class="quick-label">快速提问</div>
          <div class="quick-grid">
            <button
              v-for="q in quickQuestions"
              :key="q.text"
              class="quick-card"
              @click="handleQuickQuestion(q.text)"
              :disabled="cityStore.selectedCities.length === 0"
            >
              <span class="quick-icon">{{ q.icon }}</span>
              <span class="quick-text">{{ q.text }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ─── Messages ────────────────────────────────────── -->
    <div v-else class="messages" ref="messagesRef">

      <!-- Empty session -->
      <div v-if="chatStore.currentMessages.length === 0" class="empty-chat">
        <div class="empty-inner">
          <p class="empty-label">开始一段新的旅途对话</p>
          <div class="empty-quick">
            <button
              v-for="q in quickQuestions"
              :key="q.text"
              class="empty-quick-btn"
              @click="handleQuickQuestion(q.text)"
            >
              {{ q.text }}
            </button>
          </div>
        </div>
      </div>

      <!-- Message list -->
      <MessageBubble
        v-for="message in chatStore.currentMessages"
        :key="message.id"
        :message="message"
        @regenerate="emit('regenerate', $event)"
        @quick-ask="emit('quick-question', $event)"
      />

      <div ref="bottomAnchorRef" style="height: 1px;" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useCityStore } from '@/stores/city'
import MessageBubble from '@/components/MessageBubble.vue'

const emit = defineEmits<{
  'quick-question': [question: string]
  'regenerate': [id: string]
}>()

const chatStore = useChatStore()
const cityStore = useCityStore()
const bottomAnchorRef = ref<HTMLElement | null>(null)

const quickQuestions = [
  { icon: '🗺', text: '推荐3天亲子游行程' },
  { icon: '🍜', text: '当地必吃美食有哪些？' },
  { icon: '🚆', text: '交通怎么去最方便？' },
  { icon: '📷', text: '有哪些拍照打卡地？' },
  { icon: '💑', text: '推荐情侣浪漫路线' },
  { icon: '🧳', text: '适合背包客的玩法' },
]

watch(
  () => chatStore.currentMessages.length,
  async () => { await nextTick(); scrollToBottom() },
  { immediate: true }
)

watch(
  () => {
    const msgs = chatStore.currentMessages
    return msgs[msgs.length - 1]?.content
  },
  async () => { await nextTick(); scrollToBottom() }
)

function scrollToBottom() {
  bottomAnchorRef.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
}

function handleQuickQuestion(question: string) {
  if (cityStore.selectedCities.length === 0) return
  emit('quick-question', question)
}
</script>

<style scoped>
.chat-window {
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Welcome */
.welcome {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  padding: 40px 24px;
}

.welcome-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  max-width: 580px;
  width: 100%;
}

.welcome-mark {
  width: 48px;
  height: 48px;
  background: var(--forest);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.welcome-gem {
  color: var(--gold);
  font-size: 12px;
}

.welcome-title {
  font-family: 'Cormorant Garamond', 'Noto Serif SC', serif;
  font-size: 36px;
  font-weight: 500;
  color: var(--text);
  text-align: center;
}

.welcome-sub {
  font-size: 14px;
  color: var(--text-3);
  text-align: center;
}

.quick-section {
  width: 100%;
  margin-top: 16px;
}

.quick-label {
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-3);
  text-align: center;
  margin-bottom: 14px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 14px 16px;
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}

.quick-card:hover:not(:disabled) {
  border-color: var(--forest);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.quick-card:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.quick-icon {
  font-size: 20px;
}

.quick-text {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.4;
}

/* Messages */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0 16px;
  scroll-behavior: smooth;
}

/* Empty chat */
.empty-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.empty-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.empty-label {
  font-size: 14px;
  color: var(--text-3);
  font-style: italic;
  font-family: 'Cormorant Garamond', serif;
  font-size: 18px;
}

.empty-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 480px;
}

.empty-quick-btn {
  padding: 7px 14px;
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 20px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: border-color 0.2s, color 0.2s;
}

.empty-quick-btn:hover {
  border-color: var(--forest);
  color: var(--forest);
}
</style>
