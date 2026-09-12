import { createApp } from 'vue'
import { createPinia } from 'pinia'
// v-loading 指令不被 unplugin 处理，需要全局注册
import { ElLoading } from 'element-plus'
// 函数式组件（ElMessage / ElMessageBox）在代码里显式 import，样式需手动注入
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'

import App from './App.vue'
import router from './router'
import '@/assets/styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElLoading)

app.mount('#app')
