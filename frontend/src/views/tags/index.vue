<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTags, createTag, updateTag, deleteTag } from '@/api/tag'

const loading = ref(false)
const tags = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)

const form = reactive({
  name: '',
  color: '#409EFF'
})

const PRESET_COLORS = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C', '#E67E22']

async function loadTags() {
  loading.value = true
  try {
    const res = await listTags()
    tags.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.color = '#409EFF'
  dialogVisible.value = true
}

function openEdit(tag) {
  editingId.value = tag.id
  form.name = tag.name
  form.color = tag.color
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('标签名不能为空')
    return
  }
  if (editingId.value) {
    await updateTag(editingId.value, form)
    ElMessage.success('标签已更新')
  } else {
    await createTag(form)
    ElMessage.success('标签已创建')
  }
  dialogVisible.value = false
  loadTags()
}

async function handleDelete(tag) {
  await ElMessageBox.confirm(
    `删除标签「${tag.name}」后，已打该标签的日记将自动解除关联，日记本身不受影响。`,
    '删除标签',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await deleteTag(tag.id)
  ElMessage.success('删除成功')
  loadTags()
}

onMounted(loadTags)
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>标签管理</span>
          <el-button type="primary" @click="openCreate">新建标签</el-button>
        </div>
      </template>

      <div v-loading="loading" class="tag-grid">
        <el-empty v-if="!loading && tags.length === 0" description="还没有标签" />
        <div v-for="tag in tags" :key="tag.id" class="tag-card">
          <el-tag :color="tag.color" effect="dark" size="large" class="tag-name">{{ tag.name }}</el-tag>
          <span class="tag-count">{{ tag.count }} 篇日记</span>
          <div class="tag-actions">
            <el-button link type="primary" size="small" @click="openEdit(tag)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(tag)">删除</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑标签' : '新建标签'" width="400px">
      <el-form label-width="70px">
        <el-form-item label="标签名">
          <el-input v-model="form.name" maxlength="20" show-word-limit placeholder="如：编程 / 运动 / 旅行" />
        </el-form-item>
        <el-form-item label="颜色">
          <div class="color-picker">
            <span
              v-for="color in PRESET_COLORS"
              :key="color"
              class="color-dot"
              :class="{ active: form.color === color }"
              :style="{ background: color }"
              @click="form.color = color"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tag-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.tag-card {
  width: 200px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  text-align: center;
}

.tag-name {
  border: none;
  font-size: 14px;
}

.tag-count {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.tag-actions {
  margin-top: 8px;
}

.color-picker {
  display: flex;
  gap: 10px;
}

.color-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  cursor: pointer;
  border: 3px solid transparent;
}

.color-dot.active {
  border-color: #303133;
}
</style>
