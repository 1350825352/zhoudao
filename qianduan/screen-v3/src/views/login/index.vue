<template>
  <el-scrollbar height="100vh">
    <el-container class="page-body">
      <div class="contain">
        <div class="title">{{ screenTitle }}</div>
        <div class="loginContain">
          <div class="userTitle">用户登录</div>
          <el-form :model="loginForm" :rules="rules" ref="loginFormRef" class="formBox">
            <el-form-item prop="username">
              <el-input
                placeholder="请输入用户名"
                v-model="loginForm.username"
                class="inputClass"
              >
                <template #prefix>
                  <img
                    src="./img/user.png"
                    width="26"
                    height="26"
                    alt=""
                  />
                </template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                placeholder="请输入登录密码"
                v-model="loginForm.password"
                class="inputClass"
                type="password"
                show-password
              >
                <template #prefix>
                  <img
                    src="./img/pwd.png"
                    width="26"
                    height="26"
                    alt=""
                  />
                </template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button class="sub-btn" @click="handleLogin" :loading="loading">
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </el-container>
  </el-scrollbar>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/store'
import request from '@/utils/request'

const router = useRouter()
const store = useAppStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const screenTitle = ref(`${import.meta.env.VITE_COMPANY_NAME || '乐金'}无人机监控大屏`)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      const response = await request.post('/auth/login', {
        username: loginForm.username,
        password: loginForm.password
      })
      
      const userInfo = response.data
      sessionStorage.setItem('screen-token', userInfo.token)
      sessionStorage.setItem('screen-user', JSON.stringify(userInfo))
      store.setUser(userInfo)
      router.push('/')
    } catch (error) {
      console.error('登录失败', error)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="less" scoped>
.page-body {
  background-image: url("./img/bg.png");
  background-size: cover;
  background-position: center;
  position: relative;
  height: 100vh;

  .contain {
    background-image: url("./img/bgFigure.png");
    background-size: 100% 100%;
    height: 100%;
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    .title {
      font-family: Helvetica;
      font-size: 50px;
      color: #ffffff;
      background: linear-gradient(to bottom, #eeeeee 0%, #a6e1fe 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .loginContain {
      background-image: url("./img/loginBox.png");
      background-size: 100% 100%;
      margin-top: 60px;
      width: 590px;
      height: 340px;
      padding-bottom: 60px;
      position: relative;

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

        :deep(.el-input__inner):-webkit-autofill,
        :deep(.el-input__inner):-webkit-autofill:hover,
        :deep(.el-input__inner):-webkit-autofill:focus,
        :deep(.el-input__inner):-webkit-autofill:active {
          -webkit-box-shadow: 0 0 0 1000px rgba(0, 0, 0, 0) inset !important;
          -webkit-text-fill-color: #fff !important;
          background-color: transparent !important;
          transition: background-color 5000s ease-in-out 0s;
        }

        :deep(.el-input__icon) {
          color: #63bbf8;
          cursor: pointer;
        }
      }

      .sub-btn {
        width: 100%;
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
