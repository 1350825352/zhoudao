<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户管理</span>
          <el-button type="primary" @click="openAddDialog">新增用户</el-button>
        </div>
      </template>

      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="username" label="登录账号" />
        <el-table-column prop="nickname" label="用户昵称" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="dept" label="所属部门" />
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="phone" label="电话" width="150" />
        <el-table-column prop="password" label="用户密码" width="100">
          <template #default="{ row }">
            <span>******</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="账号状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" />
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="用户昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="密码" :prop="isEdit ? '' : 'password'">
          <el-input v-model="form.password" type="password" :placeholder="isEdit ? '不修改请留空' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-input v-model="form.role" />
        </el-form-item>
        <el-form-item label="所属部门" prop="dept">
          <el-input v-model="form.dept" />
        </el-form-item>
        <el-form-item label="账号状态" prop="status">
          <el-select v-model="form.status">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/api/request'

interface User {
  id?: number
  username: string
  password: string
  nickname: string
  email: string
  phone: string
  role: string
  dept: string
  status: number
  lastLoginTime?: string
}

const tableData = ref<User[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const isEdit = ref(false)
const formRef = ref()

const form = reactive<User>({
  id: undefined,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  role: '',
  dept: '',
  status: 1
})

const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入用户昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }],
  role: [{ required: true, message: '请输入角色', trigger: 'blur' }],
  dept: [{ required: true, message: '请输入所属部门', trigger: 'blur' }],
  status: [{ required: true, message: '请选择账号状态', trigger: 'change' }]
}

const fetchData = async () => {
  try {
    const res = await request.get('/user/list', {
      params: { page: currentPage.value, size: pageSize.value }
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('获取用户列表失败:', error)
  }
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const openAddDialog = () => {
  dialogTitle.value = '新增用户'
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row: User) => {
  dialogTitle.value = '编辑用户'
  isEdit.value = true
  form.id = row.id
  form.username = row.username
  form.password = ''
  form.nickname = row.nickname
  form.email = row.email
  form.phone = row.phone
  form.role = row.role
  form.dept = row.dept
  form.status = row.status
  dialogVisible.value = true
}

const resetForm = () => {
  form.id = undefined
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.email = ''
  form.phone = ''
  form.role = ''
  form.dept = ''
  form.status = 1
}

const handleSubmit = async () => {
  if (!isEdit.value) {
    await formRef.value.validate().catch(() => {
      return
    })
  }

  try {
    if (isEdit.value) {
      if (form.password) {
        await request.put(`/user/${form.id}`, form)
      } else {
        const updateData = { ...form }
        delete updateData.password
        await request.put(`/user/${form.id}`, updateData)
      }
      ElMessage.success('更新成功')
    } else {
      await request.post('/user', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error('操作失败:', error)
  }
}

const handleResetPassword = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定要重置 ${row.nickname} 的密码吗？重置后密码为888888`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.post(`/user/${row.id}/reset-password`)
    ElMessage.success('密码已重置为888888')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('重置密码失败:', error)
    }
  }
}

const handleDelete = async (row: User) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/user/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="less" scoped>
.page-container {
  width: 100%;
}

.page-card {
  border-radius: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>