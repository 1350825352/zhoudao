<template>
  <div class="header-box">
    <div class="useParty">
      <el-dropdown>
        <img src="./img/userIcon.png" alt="" class="userIcon" />
        <template #dropdown>
          <el-dropdown-menu style="background-color: #0d1931">
            <el-dropdown-item :icon="Setting" @click="goAdmin">
              后台管理
            </el-dropdown-item>
            <el-dropdown-item :icon="Key" @click="editPwd">
              修改密码
            </el-dropdown-item>
          </el-dropdown-menu>
          <el-dropdown-menu style="background-color: #0d1931">
            <el-dropdown-item :icon="SwitchButton" @click="userOut">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <span class="user-name">{{ userName }}</span>
    </div>
    <div class="right">
      <div>{{ currentDate }}</div>
      <div>{{ currentTime }}</div>
      <div>{{ currentWeek }}</div>
    </div>
    <div class="header-title">{{ screenTitle }}</div>

    <div class="loginContain" v-if="isShow">
      <div class="userTitle">修改密码</div>
      <el-form
        :model="initform"
        :rules="pwdRules"
        ref="initFormRef"
        class="formBox"
      >
        <el-form-item prop="oldPassword">
          <el-input
            placeholder="请输入原密码"
            v-model="initform.oldPassword"
            class="inputClass"
            :prefix-icon="Lock"
          >
          </el-input>
        </el-form-item>
        <el-form-item prop="newPassword">
          <el-input
            placeholder="请输入新密码"
            v-model="initform.newPassword"
            class="inputClass"
            :prefix-icon="Lock"
          >
          </el-input>
        </el-form-item>
        <el-form-item prop="newPassword1">
          <el-input
            placeholder="请重新输入新密码"
            v-model="initform.newPassword1"
            class="inputClass"
            :prefix-icon="Lock"
          >
          </el-input>
        </el-form-item>
        <el-form-item>
          <div class="btns">
            <el-button class="cancel-btn" @click="handleCancel">
              取消
            </el-button>
            <el-button class="sub-btn" @click="handleOk(initFormRef)">
              确定
            </el-button>
          </div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, reactive, computed } from 'vue'
import type { FormRules, FormInstance } from 'element-plus'
import { Key, SwitchButton, Lock, Setting } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/store'

const router = useRouter()
const store = useAppStore()

const currentDate = ref('')
const currentTime = ref('')
const currentWeek = ref('')
let intervalId: any = null
const initFormRef = ref()
const initform = reactive({
  oldPassword: '',
  newPassword: '',
  newPassword1: '',
})
const pwdRules = reactive({
  oldPassword: [{ required: true, trigger: 'blur' }],
  newPassword: [{ required: true, trigger: 'blur' }],
  newPassword1: [{ required: true, trigger: 'blur' }],
})

const screenTitle = ref(`${import.meta.env.VITE_COMPANY_NAME || ''}${import.meta.env.VITE_APP_TITLE || '无人机监控大屏'}`)

const userName = computed(() => {
  if (store.user) {
    return store.user.name || store.user.username
  }
  const userStr = sessionStorage.getItem('screen-user')
  if (userStr) {
    try {
      const userInfo = JSON.parse(userStr)
      return userInfo.name || userInfo.username || '用户名'
    } catch {
      return '用户名'
    }
  }
  return '用户名'
})

onMounted(() => {
  const userStr = sessionStorage.getItem('screen-user')
  if (userStr) {
    try {
      const userInfo = JSON.parse(userStr)
      store.setUser(userInfo)
    } catch {}
  }

  const updateDateTime = () => {
    const now = new Date()
    const year = now.getFullYear()
    const month = (now.getMonth() + 1).toString().padStart(2, '0')
    const day = now.getDate().toString().padStart(2, '0')
    const hours = now.getHours().toString().padStart(2, '0')
    const minutes = now.getMinutes().toString().padStart(2, '0')
    const seconds = now.getSeconds().toString().padStart(2, '0')
    const dayOfWeek = now.getDay()
    const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
    currentDate.value = `${year}-${month}-${day}`
    currentTime.value = `${hours}:${minutes}:${seconds}`
    currentWeek.value = weekdays[dayOfWeek]
  }

  updateDateTime()
  intervalId = setInterval(updateDateTime, 1000)
})

const userOut = () => {
  sessionStorage.clear()
  store.clearUser()
  window.location.href = '/#/login'
  window.location.reload()
}

const isShow = ref(false)

function goAdmin() {
  const token = sessionStorage.getItem('screen-token')
  // 开发模式直接跳转 admin-v3 端口(3201), 生产模式用 /admin 路径
  const adminBase = import.meta.env.DEV ? 'http://localhost:3201' : '/admin'
  if (token) {
    // 使用 hash 参数传递 token，刷新后仍能从 URL 中读取
    window.open(`${adminBase}/#/?token=${token}`, '_blank')
  } else {
    window.open(`${adminBase}/`, '_blank')
  }
}

function editPwd() {
  isShow.value = true
}

async function handleOk(formEl: FormInstance | undefined) {
  if (typeof formEl == 'undefined') return
  await formEl.validate((valid) => {
    if (valid) {
      isShow.value = false
    }
  })
}

function handleCancel() {
  if (initFormRef.value) initFormRef.value.resetFields()
  isShow.value = false
}

onUnmounted(() => {
  if (intervalId) clearInterval(intervalId)
})
</script>

<style>
.el-dropdown__popper.el-popper {
  background-color: #0d1931;
  border: 1px solid #0d1931;
}
.el-popper.is-light .el-popper__arrow:before {
  background-color: #0d1931;
  border: 1px solid #0d1931;
  right: 0;
}
.el-dropdown-menu__item {
  color: #fff;
}
</style>

<style lang="less" scoped>
.header-box {
  position: relative;
  text-align: center;
  width: 100%;
  height: 100%;
  background-image: url('./img/header-top.png');
  background-size: 100% 100%;
  background-repeat: no-repeat;
  padding: 0;

  .useParty {
    display: flex;
    align-items: center;
    position: absolute;
    right: 50px;
    top: 34px;

    .userIcon {
      width: 26px;
      height: 26px;
    }

    .user-name {
      font-family: SourceHanSansCN, SourceHanSansCN;
      font-weight: 400;
      font-size: 14px;
      color: #ffffff;
      margin-left: 8px;
    }
  }

  .header-title {
    font-family: AlimamaShuHeiTi, AlimamaShuHeiTi;
    font-weight: bold;
    font-size: 42px;
    color: #ffffff;
    text-align: center;
    font-style: normal;
    padding-top: 10px;
    letter-spacing: 4px;
  }

  .right {
    width: 348px;
    position: absolute;
    right: 184px;
    top: 52px;
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    font-weight: 400;
    font-size: 24px;
    color: #19beef;
  }

  .loginContain {
    background-image: url('./img/loginBox.png');
    background-size: 100% 100%;
    margin-top: 100px;
    width: 590px;
    height: 360px;
    padding-bottom: 60px;
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    z-index: 9;

    .userTitle {
      color: #63bbf8;
      font-size: 26px;
      font-weight: bold;
      font-family: Helvetica;
      letter-spacing: 8px;
      position: absolute;
      left: 50%;
      transform: translateX(-50%);
      top: 30px;
    }

    .formBox {
      position: absolute;
      top: 100px;
      left: 60px;
      right: 60px;
    }

    .inputClass {
      font-size: 16px;
      height: 44px;
      border: 3px solid #1a63c7;
      border-radius: 0;
      background-color: transparent;

      :deep(.el-input__wrapper) {
        background: transparent !important;
        box-shadow: none !important;
      }

      :deep(.el-input__inner) {
        background: rgba(0, 0, 0, 0) !important;
        color: #fff;
      }
    }

    .btns {
      margin-top: -20px;
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      .cancel-btn {
        width: 40%;
        text-align: center;
        background: #fff;
        color: #1f608e;
        border: none;
        height: 44px;
        line-height: 44px;
        font-weight: bold;
        font-size: 22px;
        letter-spacing: 8px;
        margin-top: 26px;
      }

      .sub-btn {
        width: 40%;
        text-align: center;
        background: #63bbf8;
        color: #1f608e;
        border: none;
        height: 44px;
        line-height: 44px;
        font-weight: bold;
        font-size: 22px;
        letter-spacing: 8px;
        margin-top: 26px;
      }
    }
  }
}
</style>
