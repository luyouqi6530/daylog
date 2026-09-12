<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isLoginMode = ref(true)
const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码 6-32 位', trigger: 'blur' }
  ]
}

const registerRules = {
  ...loginRules,
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,30}$/, message: '4-30 位字母/数字/下划线', trigger: 'blur' }
  ],
  nickname: [{ max: 30, message: '昵称最长 30 字', trigger: 'blur' }]
}

async function handleSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    if (isLoginMode.value) {
      const res = await login({ username: form.username, password: form.password })
      userStore.setToken(res.data.token)
      userStore.setUserInfo(res.data.userInfo)
      ElMessage.success(`欢迎回来，${res.data.userInfo.nickname}`)
      router.push(route.query.redirect || '/dashboard')
    } else {
      await register(form)
      ElMessage.success('注册成功，请登录')
      isLoginMode.value = true
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">Daylog<span class="logo-dot" /></div>
      <div class="login-slogan">每日记录，看见情绪的变化</div>

      <el-form ref="formRef" :model="form" :rules="isLoginMode ? loginRules : registerRules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="null" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item v-if="!isLoginMode" prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（不填默认同用户名）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleSubmit">
            {{ isLoginMode ? '登 录' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-switch">
        {{ isLoginMode ? '还没有账号？' : '已有账号？' }}
        <el-link type="primary" @click="isLoginMode = !isLoginMode">
          {{ isLoginMode ? '去注册' : '去登录' }}
        </el-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}

.login-card {
  width: 380px;
  padding: 44px 36px 28px;
  background: var(--bg-card);
  border: 1px solid var(--border-hairline);
  border-radius: 14px;
}

.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--text-main);
  margin-bottom: 8px;
}

.logo-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-main);
}

.login-slogan {
  text-align: center;
  font-size: 13px;
  color: var(--text-faint);
  letter-spacing: 1px;
  margin-bottom: 30px;
}

.login-btn {
  width: 100%;
}

.login-switch {
  text-align: center;
  font-size: 13px;
  color: var(--text-faint);
}
</style>
