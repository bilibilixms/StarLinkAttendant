<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { registerMember } from '../api'
import { GENDER_MAP } from '@/common/constants'
import type { MemberRegisterRequest } from '../types'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const formData = reactive<MemberRegisterRequest>({
  phone: '',
  password: '',
  realName: '',
  gender: 0,
  idCard: '',
  birthday: '',
})

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度6-32位', trigger: 'blur' },
  ],
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await registerMember(formData)
      ElMessage.success('会员注册成功')
      router.push('/member/list')
    } catch { /* interceptor */ }
    finally { loading.value = false }
  })
}

const handleCancel = () => { router.push('/member/list') }
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">会员注册</h1>
      <p class="page-desc">新增会员并创建账户</p>
    </div>

    <div class="form-card">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" style="max-width: 600px;">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="formData.gender">
            <el-radio v-for="(label, val) in GENDER_MAP" :key="val" :value="Number(val)">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="身份证">
          <el-input v-model="formData.idCard" placeholder="请输入身份证号" />
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="formData.birthday" type="date" placeholder="选择生日" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">注册</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
.page-desc { font-size: 14px; color: #64748b; margin-top: 4px; }
.form-card { background: white; border-radius: 12px; padding: 32px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
</style>
