<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http, upload, task } from '../services/api'
import Icon from '../components/Icon.vue'
const route = useRoute(),
  router = useRouter(),
  categories = ref([]),
  busy = ref(false),
  uploading = ref(false),
  ready = ref(!route.params.id),
  error = ref('')
const form = reactive({
  title: '',
  categoryId: undefined,
  price: undefined,
  conditionLevel: '九成新',
  description: '',
  images: []
})
onMounted(async () => {
  await task(async () => {
    categories.value = await http.get('/categories')
    if (route.params.id) {
      const g = await http.get(`/goods/${route.params.id}`)
      Object.assign(form, {
        title: g.title,
        categoryId: g.category_id,
        price: Number(g.price),
        conditionLevel: g.condition_level,
        description: g.description,
        images: g.images
      })
      ready.value = true
    }
  })
})
async function addImages(e) {
  const files = [...e.target.files]
  e.target.value = ''
  if (form.images.length + files.length > 5) {
    ElMessage.warning('每件商品最多 5 张图片')
    return
  }
  uploading.value = true
  for (const file of files) {
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      ElMessage.warning('请选择不超过 5MB 的 PNG 或 JPG 图片')
      continue
    }
    await task(async () => form.images.push(await upload(file)))
  }
  uploading.value = false
}
async function submit() {
  error.value = ''
  if (
    !form.title.trim() ||
    !form.categoryId ||
    !form.price ||
    !form.description.trim() ||
    !form.images.length
  ) {
    error.value = '请填写完整信息，并上传至少一张商品图片。'
    return
  }
  busy.value = true
  await task(async () => {
    const data = route.params.id
      ? await http.put(`/goods/${route.params.id}`, form)
      : await http.post('/goods', form)
    ElMessage.success('提交成功，审核通过后会出现在校园集市')
    router.push(`/goods/${data.id}`)
  })
  busy.value = false
}
</script>
<template>
  <div class="container page-container">
    <div class="breadcrumb">
      <RouterLink to="/">校园集市</RouterLink><Icon name="chevron" :size="13" /><span>{{
        route.params.id ? '编辑商品' : '发布闲置'
      }}</span>
    </div>
    <div class="page-header">
      <div>
        <h1 class="page-title">
          {{ route.params.id ? '给好物更新一点故事' : '让好物，开启下一段旅程' }}
        </h1>
        <p class="page-subtitle">认真介绍你的闲置，帮它找到一位珍惜它的新主人。</p>
      </div>
    </div>
    <div class="publish-layout">
      <form v-if="ready" class="panel publish-form" @submit.prevent="submit">
        <div class="hint-box">
          <Icon
            name="shield"
            :size="14"
            style="vertical-align: middle; margin-right: 5px"
          />发布或修改后进入审核，通过后公开展示。已预订、已售出商品不能编辑。
        </div>
        <label class="form-field"
          ><span>好物标题 *</span
          ><el-input
            v-model="form.title"
            placeholder="品牌、名称、特点，让同学一眼找到它"
            maxlength="80"
            show-word-limit
            required
        /></label>
        <div class="form-row">
          <label class="form-field"
            ><span>选择分类 *</span
            ><el-select v-model="form.categoryId" placeholder="它属于哪一类？"
              ><el-option
                v-for="c in categories"
                :key="c.id"
                :label="c.name"
                :value="c.id" /></el-select></label
          ><label class="form-field"
            ><span>新旧程度 *</span
            ><el-select v-model="form.conditionLevel"
              ><el-option
                v-for="s in ['全新', '几乎全新', '九成新', '八成新', '有使用痕迹']"
                :key="s"
                :label="s"
                :value="s" /></el-select
          ></label>
        </div>
        <label class="form-field"
          ><span>转让价格（元）*</span
          ><el-input-number
            v-model="form.price"
            :min="0.01"
            :max="999999.99"
            :precision="2"
            :step="0.01"
            :controls="false"
            placeholder="给它一个合理的价格"
        /></label>
        <div class="form-field">
          <span
            >实物图片 * <small class="muted">{{ form.images.length }} / 5</small></span
          >
          <div class="upload-grid">
            <div class="upload-item" v-for="(img, i) in form.images" :key="img">
              <img :src="img" :alt="`图片 ${i + 1}`" /><button
                type="button"
                @click="form.images.splice(i, 1)"
                :aria-label="`删除图片 ${i + 1}`"
              >
                <Icon name="close" :size="13" />
              </button>
            </div>
            <label v-if="form.images.length < 5" class="upload-trigger"
              ><input
                type="file"
                accept="image/png,image/jpeg"
                multiple
                :disabled="uploading"
                @change="addImages"
              /><Icon name="image" :size="25" /><span>{{
                uploading ? '上传中…' : '添加实物图片'
              }}</span></label
            >
          </div>
          <p class="form-help">支持 JPG / PNG，每张不超过 5MB，最多 1200 万像素，上传 1–5 张。</p>
        </div>
        <label class="form-field"
          ><span>描述一下它 *</span
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="6"
            placeholder="介绍一下购买时间、使用情况、配件，以及需要说明的小瑕疵吧。请勿填写学号、手机号或宿舍门牌。"
            maxlength="3000"
            show-word-limit
            required
        /></label>
        <div v-if="error" class="form-error" role="alert">{{ error }}</div>
        <div style="display: flex; gap: 12px">
          <button class="button primary" type="submit" :disabled="busy || uploading">
            {{ busy ? '提交中…' : '提交审核' }}<Icon name="right" :size="16" /></button
          ><RouterLink to="/me" class="button ghost">取消</RouterLink>
        </div>
      </form>
      <div v-else class="panel">
        <el-skeleton animated :rows="10" />
        <p class="muted">正在加载商品信息；如无权访问，请返回个人中心。</p>
        <RouterLink to="/me" class="button ghost small" style="margin-top: 20px"
          >返回个人中心</RouterLink
        >
      </div>
      <aside class="publish-notes">
        <h3><Icon name="leaf" />发布小贴士</h3>
        <ul>
          <li>实物拍照更让人心动，清晰的图片可以加速交易。</li>
          <li>如实写明使用痕迹，好物的新主人会感谢你的坦诚。</li>
          <li>价格友好一点，闲置就能更快出发。</li>
          <li>不售卖违禁、侵权、虚假商品。管理员会审核每一份发布。</li>
        </ul>
        <p class="muted">我们相信，好物值得被继续喜欢。<br />让每一份闲置，再次闪光。</p>
      </aside>
    </div>
  </div>
</template>
