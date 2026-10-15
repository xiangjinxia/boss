<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, date } from '../services/api'
import Icon from './Icon.vue'
const props = defineProps({ kind: String, rows: Array }),
  emit = defineEmits(['refresh']),
  open = ref(false),
  busy = ref(false),
  id = ref(null)
const form = reactive({
  name: '',
  icon: 'box',
  sortOrder: 0,
  status: 'ACTIVE',
  title: '',
  content: '',
  pinned: false
})
const iconNames = {
  book: '书籍',
  laptop: '数码',
  coffee: '生活',
  bike: '运动',
  shirt: '服饰',
  box: '其他',
  camera: '相机',
  leaf: '叶子'
}
function edit(row) {
  id.value = row?.id || null
  Object.assign(form, {
    name: row?.name || '',
    icon: row?.icon || 'box',
    sortOrder: row?.sort_order || 0,
    status: row?.status || (props.kind === 'categories' ? 'ACTIVE' : 'DRAFT'),
    title: row?.title || '',
    content: row?.content || '',
    pinned: !!row?.pinned
  })
  open.value = true
}
async function save() {
  if (
    props.kind === 'categories' ? !form.name.trim() : !form.title.trim() || !form.content.trim()
  ) {
    ElMessage.warning('请填写完整内容')
    return
  }
  busy.value = true
  try {
    const payload =
      props.kind === 'categories'
        ? { name: form.name, icon: form.icon, sortOrder: form.sortOrder, status: form.status }
        : { title: form.title, content: form.content, status: form.status, pinned: form.pinned }
    if (id.value) await http.put(`/admin/${props.kind}/${id.value}`, payload)
    else await http.post(`/admin/${props.kind}`, payload)
    open.value = false
    ElMessage.success('配置已保存')
    emit('refresh')
  } catch {
  } finally {
    busy.value = false
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(
      props.kind === 'categories'
        ? '仅未关联商品的分类可以删除；有关联的分类请改为停用。'
        : '删除后公告将不再展示，操作会保留日志。',
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await http.delete(`/admin/${props.kind}/${row.id}`)
    ElMessage.success('已删除')
    emit('refresh')
  } catch {}
}
</script>
<template>
  <div class="config-manager">
    <div class="config-toolbar">
      <p class="chart-note">
        {{
          kind === 'categories'
            ? '停用分类会停止新发布时选择，已有商品和订单保留；可通过排序数字调整顺序。'
            : '已发布公告展示在集市首页，草稿仅管理员可见；置顶公告优先展示。'
        }}
      </p>
      <button class="button primary small" @click="edit(null)">
        <Icon name="plus" :size="15" />{{ kind === 'categories' ? '新增分类' : '新增公告' }}
      </button>
    </div>
    <div v-if="!rows.length" class="empty-state">
      <h3>暂时没有记录</h3>
      <p>点击右上方按钮添加。</p>
    </div>
    <div v-else class="table-scroll">
      <table v-if="kind === 'categories'" class="data-table">
        <thead>
          <tr>
            <th>分类</th>
            <th>排序</th>
            <th>关联商品</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td>
              <span class="inline-icon"><Icon :name="r.icon" :size="18" />{{ r.name }}</span>
            </td>
            <td>{{ r.sort_order }}</td>
            <td>{{ r.goods_count }}</td>
            <td>
              <span class="badge" :class="r.status === 'ACTIVE' ? 'active' : 'offline'">{{
                r.status === 'ACTIVE' ? '启用' : '停用'
              }}</span>
            </td>
            <td>
              <div class="table-actions">
                <button class="text-button" @click="edit(r)">编辑分类</button
                ><button class="text-button red" @click="remove(r)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>标题</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td>
              <span v-if="r.pinned" class="badge listed">置顶</span> {{ r.title }}
              <p class="announcement-preview">{{ r.content }}</p>
            </td>
            <td>{{ r.status === 'PUBLISHED' ? '已发布' : '草稿' }}</td>
            <td>{{ date(r.updated_at) }}</td>
            <td>
              <div class="table-actions">
                <button class="text-button" @click="edit(r)">编辑公告</button
                ><button class="text-button red" @click="remove(r)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <el-dialog
      v-model="open"
      :title="`${id ? '编辑' : '新增'}${kind === 'categories' ? '分类' : '公告'}`"
      width="560px"
      ><form @submit.prevent="save">
        <template v-if="kind === 'categories'"
          ><label class="form-field"
            ><span>分类名称</span
            ><el-input v-model="form.name" maxlength="32" placeholder="例如：毕业好物" /></label
          ><label class="form-field"
            ><span>图标</span
            ><el-select v-model="form.icon"
              ><el-option
                v-for="(name, key) in iconNames"
                :key="key"
                :label="name"
                :value="key" /></el-select></label
          ><label class="form-field"
            ><span>排序（越小越靠前）</span
            ><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></label
          ><label class="form-field"
            ><span>分类状态</span
            ><el-select v-model="form.status"
              ><el-option label="启用" value="ACTIVE" /><el-option
                label="停用"
                value="INACTIVE" /></el-select></label></template
        ><template v-else
          ><label class="form-field"
            ><span>公告标题</span
            ><el-input v-model="form.title" maxlength="80" placeholder="输入公告标题" /></label
          ><label class="form-field"
            ><span>公告正文</span
            ><el-input
              v-model="form.content"
              type="textarea"
              :rows="6"
              maxlength="2000"
              show-word-limit
              placeholder="输入公告内容，使用纯文本展示" /></label
          ><label class="form-field"
            ><span>发布状态</span
            ><el-select v-model="form.status"
              ><el-option label="草稿" value="DRAFT" /><el-option
                label="发布到首页"
                value="PUBLISHED" /></el-select></label
          ><label class="form-field"
            ><el-checkbox v-model="form.pinned">在首页置顶</el-checkbox></label
          ></template
        >
        <div class="dialog-buttons">
          <button class="button ghost" type="button" @click="open = false">取消</button
          ><button class="button primary" :disabled="busy" type="submit">保存配置</button>
        </div>
      </form></el-dialog
    >
  </div>
</template>
