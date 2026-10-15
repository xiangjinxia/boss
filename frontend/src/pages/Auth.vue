<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http, saveSession } from '../services/api'
import Icon from '../components/Icon.vue'
const route = useRoute(),
  router = useRouter(),
  registering = computed(() => route.path === '/register')
const form = reactive({ username: '', password: '', nickname: '' }),
  busy = ref(false),
  error = ref('')
function demo(username) {
  form.username = username
  form.password = 'Campus123!'
  error.value = ''
}
async function submit() {
  error.value = ''
  if (
    registering.value &&
    (!/^[A-Za-z0-9_]{4,24}$/.test(form.username) ||
      form.password.length < 8 ||
      !form.nickname.trim())
  ) {
    error.value = '请填写昵称，账号为 4–24 位字母、数字或下划线，密码至少 8 位。'
    return
  }
  busy.value = true
  try {
    const data = await http.post(registering.value ? '/auth/register' : '/auth/login', form)
    saveSession(data)
    ElMessage.success(registering.value ? '欢迎加入拾光！' : '登录成功，欢迎回来')
    const next =
      typeof route.query.redirect === 'string' &&
      route.query.redirect.startsWith('/') &&
      !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : data.user.role === 'ADMIN'
          ? '/admin'
          : '/'
    router.replace(next)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="container auth-page">
    <section class="auth-story">
      <span class="eyebrow">A LITTLE LESS, A LITTLE MORE.</span>
      <h1>少一点闲置，<br />多一点<span>校园美好。</span></h1>
      <p>一份被珍惜过的好物，<br />正在等待与你相遇。</p>
      <img :src="'/samples/books.svg'" alt="等待传递的书本" />
    </section>
    <section class="panel auth-panel">
      <h2>{{ registering ? '开启你的拾光旅程' : '很高兴，又见到你' }}</h2>
      <p>
        {{ registering ? '让每一份闲置，都能找到新的主人。' : '登录拾光，继续发现身边的好物。' }}
      </p>
      <form @submit.prevent="submit">
        <label v-if="registering" class="form-field"
          ><span>你的昵称</span
          ><el-input
            v-model="form.nickname"
            placeholder="同学们怎么称呼你？"
            maxlength="24"
            required /></label
        ><label class="form-field"
          ><span>账号</span
          ><el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="请输入账号"
            maxlength="24"
            required /></label
        ><label class="form-field"
          ><span>密码</span
          ><el-input
            v-model="form.password"
            type="password"
            show-password
            :autocomplete="registering ? 'new-password' : 'current-password'"
            :placeholder="registering ? '设置至少 8 位的密码' : '请输入密码'"
            maxlength="64"
            required
        /></label>
        <div v-if="error" class="form-error" role="alert">{{ error }}</div>
        <button class="button primary full-width" type="submit" :disabled="busy">
          {{ busy ? '请稍候…' : registering ? '注册并加入拾光' : '登录'
          }}<Icon name="right" :size="17" />
        </button>
      </form>
      <div class="auth-switch">
        {{ registering ? '已经有账号？' : '还没有账号？' }}
        <RouterLink :to="registering ? '/login' : '/register'"
          >{{ registering ? '去登录' : '注册一个' }}<span> →</span></RouterLink
        >
      </div>
      <div v-if="!registering" class="demo-accounts">
        <p>课程演示 · 点击填入账号</p>
        <div>
          <button @click="demo('buyer')">买家体验</button
          ><button @click="demo('seller')">卖家体验</button
          ><button @click="demo('admin')">管理后台</button>
        </div>
        <small>本地演示账号密码：Campus123!</small>
      </div>
    </section>
  </div>
</template>
