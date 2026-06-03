<template>
  <div class="auth-page">

    <!-- ─── Left visual panel ──────────────────────────────── -->
    <div class="visual-panel">
      <div class="vp-bg">
        <div class="vp-dots"></div>
        <div class="vp-glow"></div>
        <svg class="vp-terrain" viewBox="0 0 800 200" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0,180 L80,130 L160,155 L260,90 L340,120 L440,60 L520,100 L620,70 L700,110 L800,80 L800,200 L0,200 Z"
            fill="rgba(255,255,255,0.04)" />
          <path d="M0,200 L100,160 L200,175 L320,120 L400,145 L500,100 L580,130 L680,90 L800,120 L800,200 Z"
            fill="rgba(255,255,255,0.06)" />
        </svg>
      </div>

      <div class="vp-content">
        <div class="vp-brand">
          <span class="vp-gem">◆</span>
          <span class="vp-appname">旅途 · Voyage</span>
        </div>

        <h1 class="vp-headline">探索中国，<br>从一场对话<br>开始旅程</h1>

        <p class="vp-desc">
          AI 驱动的深度旅游规划助手<br>
          深度知识，个性推荐，让每次出行<br>
          都成为难忘的故事。
        </p>

        <div class="vp-cities">
          <span class="vp-cities-label">覆盖城市</span>
          <div class="vp-cities-row">
            <span>青岛</span><span class="sep">·</span>
            <span>北京</span><span class="sep">·</span>
            <span>上海</span><span class="sep">·</span>
            <span>成都</span><span class="sep">·</span>
            <span class="more">更多</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ─── Right form panel ───────────────────────────────── -->
    <div class="form-panel">
      <div class="form-inner">

        <div class="form-nav">
          <router-link to="/register" class="form-nav-link">还没账号？注册</router-link>
        </div>

        <div class="form-body">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-sub">登录你的旅途账号，继续规划旅程</p>

          <form @submit.prevent="handleLogin" class="f-form">
            <div class="f-field">
              <label class="f-label">邮箱地址</label>
              <input
                v-model="email"
                class="f-input"
                type="email"
                placeholder="name@example.com"
                autocomplete="email"
              />
            </div>

            <div class="f-field">
              <label class="f-label">
                密码
                <a href="#" class="f-hint-link" @click.prevent>忘记密码？</a>
              </label>
              <input
                v-model="password"
                class="f-input"
                type="password"
                placeholder="输入密码"
                autocomplete="current-password"
              />
            </div>

            <p v-if="errorMsg" class="f-error">{{ errorMsg }}</p>

            <button type="submit" class="f-btn-primary" :disabled="loading">
              <span v-if="loading" class="btn-loading">登录中...</span>
              <template v-else>登录 <span class="btn-arrow">→</span></template>
            </button>
          </form>

          <p class="form-register-link">
            还没有账号？
            <router-link to="/register">立即注册</router-link>
          </p>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'
import type { User } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (!email.value || !password.value) {
    errorMsg.value = '请填写邮箱和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await authApi.login({ email: email.value, password: password.value })
    const user: User = { userId: res.userId, username: res.username, email: res.email }
    authStore.setAuth(res.token, user)
    router.push('/app')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败，请检查邮箱和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ── Layout ─────────────────────────────────────────── */
.auth-page {
  display: flex;
  min-height: 100vh;
}

/* ── Visual Panel ───────────────────────────────────── */
.visual-panel {
  position: relative;
  flex: 0 0 50%;
  background: var(--forest);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

@media (max-width: 768px) {
  .visual-panel { display: none; }
  .form-panel { flex: 1; }
}

.vp-bg {
  position: absolute;
  inset: 0;
}

.vp-dots {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(255,255,255,0.12) 1px, transparent 1px);
  background-size: 28px 28px;
}

.vp-glow {
  position: absolute;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(61,112,96,0.4) 0%, transparent 70%);
  top: -100px;
  right: -100px;
}

.vp-terrain {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  width: 100%;
}

.vp-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  padding: 64px 56px;
  gap: 32px;
}

.vp-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: rgba(255,255,255,0.7);
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.vp-gem {
  color: var(--gold);
  font-size: 10px;
}

.vp-headline {
  font-family: 'Cormorant Garamond', 'Noto Serif SC', serif;
  font-size: clamp(40px, 4.5vw, 56px);
  font-weight: 500;
  line-height: 1.15;
  color: #fff;
  letter-spacing: -0.01em;
}

.vp-desc {
  font-size: 15px;
  line-height: 1.75;
  color: rgba(255,255,255,0.55);
  font-weight: 300;
}

.vp-cities {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vp-cities-label {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.35);
}

.vp-cities-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255,255,255,0.6);
  font-size: 15px;
  font-family: 'Cormorant Garamond', serif;
  font-size: 17px;
}

.vp-cities-row .sep {
  color: rgba(255,255,255,0.25);
}

.vp-cities-row .more {
  font-size: 13px;
  color: rgba(255,255,255,0.35);
  font-family: 'DM Sans', sans-serif;
  font-style: italic;
}

/* ── Form Panel ─────────────────────────────────────── */
.form-panel {
  flex: 0 0 50%;
  background: var(--cream);
  display: flex;
  flex-direction: column;
}

.form-inner {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 32px 48px 48px;
}

.form-nav {
  display: flex;
  justify-content: flex-end;
  margin-bottom: auto;
  padding-bottom: 40px;
}

.form-nav-link {
  font-size: 14px;
  color: var(--text-2);
  text-decoration: none;
  border-bottom: 1px solid var(--cream-300);
  padding-bottom: 1px;
  transition: color 0.2s, border-color 0.2s;
}

.form-nav-link:hover {
  color: var(--forest);
  border-color: var(--forest);
}

.form-body {
  max-width: 380px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  gap: 8px;
}

.form-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 38px;
  font-weight: 500;
  color: var(--text);
  line-height: 1.1;
  margin-bottom: 6px;
}

.form-sub {
  font-size: 14px;
  color: var(--text-3);
  margin-bottom: 24px;
}

.f-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.f-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.f-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-2);
}

.f-hint-link {
  font-size: 12px;
  text-transform: none;
  letter-spacing: 0;
  color: var(--text-3);
  text-decoration: none;
  transition: color 0.2s;
}

.f-hint-link:hover { color: var(--earth); }

.f-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border: 1.5px solid var(--cream-300);
  border-radius: var(--radius);
  background: var(--white);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 15px;
  color: var(--text);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  -webkit-appearance: none;
}

.f-input::placeholder { color: var(--text-3); }

.f-input:focus {
  border-color: var(--forest);
  box-shadow: 0 0 0 3px rgba(28,56,41,0.08);
}

.f-btn-primary {
  width: 100%;
  height: 48px;
  background: var(--forest);
  color: #fff;
  border: none;
  border-radius: var(--radius);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background 0.2s, transform 0.1s;
  margin-top: 4px;
}

.f-btn-primary:hover { background: var(--forest-600); }
.f-btn-primary:active { transform: scale(0.99); }

.btn-arrow {
  font-size: 18px;
  transition: transform 0.2s;
}

.f-btn-primary:hover .btn-arrow { transform: translateX(3px); }

.f-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-3);
  font-size: 12px;
}

.f-divider::before,
.f-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--cream-300);
}

.f-btn-ghost {
  width: 100%;
  height: 46px;
  background: transparent;
  color: var(--text-2);
  border: 1.5px solid var(--cream-300);
  border-radius: var(--radius);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.f-btn-ghost:hover {
  border-color: var(--forest);
  color: var(--forest);
}

.form-register-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-3);
  margin-top: 8px;
}

.form-register-link a {
  color: var(--forest);
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s;
}

.form-register-link a:hover { border-color: var(--forest); }

.f-error {
  font-size: 13px;
  color: #e53e3e;
  background: #fff5f5;
  border: 1px solid #fed7d7;
  border-radius: var(--radius);
  padding: 10px 14px;
  margin: 0;
}

.f-btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-loading {
  font-size: 14px;
}
</style>
