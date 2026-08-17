toolName: view_folder
            
status: success
          
H:\traeproject\ceshisikongapiAI\qianduan\admin-v3\src\views
          

        
            
toolName: view_folder
            
status: success
          
H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller
          

        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\qianduan\admin-v3\src\views\workorder\index.vue
          

        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          
## 工单系统设计方案

基于现有代码分析，目前工单系统仅在 `zhimou_detection_result` 表中记录了 `work_order_id`，需要设计完整的工单管理体系。以下是详细设计思路：

---

### 一、数据库设计

需要新建独立的工单表 `work_order` 和工单图片表 `work_order_image`：

#### 1. `work_order` 工单主表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| code | VARCHAR(32) | 工单编号（如 WO-20240110-001） |
| alarm_id | BIGINT | 关联告警ID |
| alarm_type | VARCHAR(100) | 告警类型 |
| aircraft_sn | VARCHAR(100) | 飞机SN |
| wayline_uuid | VARCHAR(64) | 航线UUID |
| alarm_image_path | VARCHAR(500) | 告警图片路径（复用已有路径） |
| handler_email | VARCHAR(100) | 处理人邮箱 |
| handler_name | VARCHAR(50) | 处理人姓名 |
| status | VARCHAR(20) | 状态：待处理/处理中/已提交/已完成/已驳回 |
| handle_comment | TEXT | 处理意见 |
| reject_reason | TEXT | 驳回原因 |
| cc_email | VARCHAR(100) | 抄送邮箱 |
| h5_token | VARCHAR(64) | H5访问Token |
| event_time | DATETIME | 告警时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 2. `work_order_image` 工单图片表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| work_order_id | BIGINT | 关联工单ID |
| image_path | VARCHAR(500) | 图片本地路径 |
| image_type | VARCHAR(20) | 图片类型：ALARM(原始) / HANDLER(处理) |
| create_time | DATETIME | 创建时间 |

---

### 二、后端接口设计

#### 1. 工单管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/workorder/create` | POST | 从告警创建工单 |
| `/workorder/list` | GET | 查询工单列表（分页） |
| `/workorder/{id}` | GET | 查询工单详情 |
| `/workorder/{id}/update` | PUT | 更新工单（驳回、完成） |
| `/workorder/{id}/reject` | POST | 驳回工单 |

#### 2. H5接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/workorder/h5/{token}` | GET | 通过Token获取工单信息 |
| `/workorder/h5/submit` | POST | 提交处理结果（含图片上传） |
| `/workorder/h5/upload` | POST | 上传处理图片 |

#### 3. 邮件通知接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/workorder/notify` | POST | 发送邮件通知 |

---

### 三、前端页面设计

#### 1. admin-v3 工单管理页面

**表格列：**
- 序号、工单编号、告警类型、飞机SN、告警图片、处理人、状态、创建时间、操作

**操作按钮：**
- 详情（弹窗查看完整信息）
- 完成（确认处理结果）
- 驳回（填写驳回原因，抄送管理人员）

**状态标签：**
- 待处理：黄色
- 处理中：蓝色
- 已提交：橙色
- 已完成：绿色
- 已驳回：红色

#### 2. H5移动端页面

**页面结构：**
```
┌─────────────────────────────┐
│        工单详情              │
├─────────────────────────────┤
│  工单编号：WO-20240110-001   │
│  告警类型：越界告警           │
│  飞机SN：SN-001             │
│  告警时间：2024-01-10 14:35  │
├─────────────────────────────┤
│      [原始告警图片]          │
├─────────────────────────────┤
│  处理区域：                  │
│  ┌───────────────────────┐  │
│  │  [拍照/上传图片按钮]   │  │
│  │    已上传：0/3        │  │
│  └───────────────────────┘  │
├─────────────────────────────┤
│  处理意见：                  │
│  ┌───────────────────────┐  │
│  │  [文本输入框]         │  │
│  └───────────────────────┘  │
├─────────────────────────────┤
│       [提交按钮]             │
│   （需有图片+处理意见）       │
└─────────────────────────────┘
```

---

### 四、业务流程

#### 1. 转工单流程
```
告警页面 → 点击转工单 → 选择处理人 → 创建工单记录 → 更新告警状态 → 发送邮件通知
```

#### 2. 现场处理流程
```
邮件链接 → H5页面 → 查看工单 → 拍照上传 → 填写处理意见 → 提交 → 更新工单状态
```

#### 3. 后台审核流程
```
工单列表 → 查看详情 → [完成/驳回] → 更新状态 → 若驳回则抄送通知
```

---

### 五、技术实现要点

#### 1. 图片存储
- **原始告警图片**：复用 `zhimou_detection_result` 表中的 `image_relative_path`
- **处理上传图片**：保存到本地目录 `/upload/workorder/`，路径记录到 `work_order_image` 表

#### 2. H5访问安全
- 使用 `h5_token` 作为访问凭证，一次性或有限时间有效
- Token绑定工单ID，防止越权访问

#### 3. 邮件发送
- 使用 Spring Boot 邮件模块
- 邮件模板包含H5链接、工单信息、处理时限

#### 4. 文件上传
- 使用 MultipartFile 接收图片
- 限制文件大小（如5MB）和格式（jpg/png）

---

### 六、项目结构扩展

```
backend/
├── controller/
│   └── WorkOrderController.java    # 工单管理接口
├── service/
│   └── WorkOrderService.java       # 工单业务逻辑
├── mapper/
│   └── WorkOrderMapper.java        # 数据库操作
├── entity/
│   ├── WorkOrder.java              # 工单实体
│   └── WorkOrderImage.java         # 工单图片实体
└── config/
    └── MailConfig.java             # 邮件配置

frontend/
├── admin-v3/
│   └── views/
│       └── workorder/index.vue     # 工单管理页面
└── h5/                              # 新建H5移动端项目
    ├── src/
    │   ├── views/
    │   │   └── WorkOrderDetail.vue # 工单详情页面
    │   └── api/
    │       └── workorder.ts        # H5接口调用
    └── index.html
```

---

### 七、实施步骤

1. **第一步**：创建数据库表（`work_order`、`work_order_image`）
2. **第二步**：实现后端工单CRUD接口
3. **第三步**：实现邮件发送功能
4. **第四步**：开发admin-v3工单管理页面
5. **第五步**：开发H5移动端页面
6. **第六步**：联调测试

---

以上是完整的工单系统设计方案，涵盖数据库、后端接口、前端页面和业务流程。如需开始实施，请告知从哪一步开始。




让我检查screen-v3的配置和邮件服务器配置：




        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\qianduan\screen-v3\vite.config.ts
          

        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\resources\application.yml
          
你说得对！我之前的信息有误：

## 1. screen-v3 地址更正

从配置文件可以看到，screen-v3 的端口是 **3202**，不是3200：

```typescript
// screen-v3/vite.config.ts
server: {
  port: 3202,  // 实际端口
}
```

正确地址：**http://localhost:3202**

让我确认screen-v3是否正在运行：





        
            
toolName: Shell
            
status: success
          
          
screen-v3运行正常，地址是 **http://localhost:3202**。

## 2. 邮件服务器

是的，邮件服务器还没有添加！根据工单系统设计，需要添加邮件发送功能来通知现场处理人员。让我现在添加：

### 添加邮件依赖和配置





        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\pom.xml
          
添加邮件依赖到pom.xml：





        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\pom.xml
          
changes: undefined
          
添加邮件配置到application.yml：






        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\resources\application.yml
          
changes: undefined
          
创建邮件发送服务：






        
            
toolName: Write
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\service\MailService.java
          
changes: undefined
          
现在在工单创建时发送邮件通知：







        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          

        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          
changes: undefined
          

        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          
changes: undefined
          

        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          

        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          
changes: undefined
          
现在在工单驳回时也发送邮件通知：







        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          

        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          

        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\java\com\dji\sikong\controller\WorkOrderController.java
          
changes: undefined
          
现在重新编译后端并重启服务：






        
            
toolName: StopCommand
            
status: success
          
          

        
            
toolName: Shell
            
status: success
          
          

        
            
toolName: Shell
            
status: success
          
          

        
            
toolName: Shell
            
status: success
          
          

        
            
toolName: Shell
            
status: success
          
          
YAML配置文件有重复的key错误。我之前添加了一个新的`spring:`节点，但原来已经有一个了。让我修复：





        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\resources\application.yml
          

        
            
toolName: view_files
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\resources\application.yml
          
找到了问题！YAML配置文件中有两个`spring:`节点。我需要将邮件配置合并到第一个`spring:`节点中：


        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\resources\application.yml
          
changes: undefined
          

        
            
toolName: edit_file_search_replace
            
status: success
          
            
filePath: H:\traeproject\ceshisikongapiAI\src\main\resources\application.yml
          
changes: undefined
          

        
            
toolName: Shell
            
status: success
          
          

        
            
toolName: Shell
            
status: success
          
          
后端启动成功！以下是更正和新增的内容：

## 1. screen-v3 地址更正

**之前错误**: http://localhost:3200  
**正确地址**: **http://localhost:3202**

从配置文件 [vite.config.ts](file:///H:/traeproject/ceshisikongapiAI/qianduan/screen-v3/vite.config.ts#L53) 可以看到：
```typescript
server: {
  port: 3202  // 实际端口
}
```

## 2. 邮件服务器已添加

### 新增文件
- [MailService.java](file:///H:/traeproject/ceshisikongapiAI/src/main/java/com/dji/sikong/service/MailService.java) - 邮件发送服务

### 修改的文件
- [pom.xml](file:///H:/traeproject/ceshisikongapiAI/pom.xml) - 添加 `spring-boot-starter-mail` 依赖
- [application.yml](file:///H:/traeproject/ceshisikongapiAI/src/main/resources/application.yml) - 添加邮件配置
- [WorkOrderController.java](file:///H:/traeproject/ceshisikongapiAI/src/main/java/com/dji/sikong/controller/WorkOrderController.java) - 集成邮件发送

### 邮件功能
- **创建工单时**：自动发送邮件通知处理人，包含H5链接
- **驳回工单时**：自动发送邮件通知处理人和抄送邮箱

### 配置说明

当前邮件配置使用的是示例值，需要您修改为真实邮箱：

```yaml
spring:
  mail:
    host: smtp.qq.com          # 邮件服务器地址（QQ邮箱）
    port: 587                  # 端口
    username: your_email@qq.com    # 您的邮箱
    password: your_email_password  # 邮箱授权码（不是登录密码）
```

**获取QQ邮箱授权码步骤**：
1. 登录QQ邮箱 → 设置 → 账户
2. 开启"POP3/SMTP服务"
3. 点击"生成授权码"
4. 使用生成的授权码作为密码

---

### 当前服务状态

| 服务 | 状态 | 地址 |
|------|------|------|
| 后端API | ✅ 运行中 | http://localhost:8080/api |
| admin-v3前端 | ✅ 运行中 | http://localhost:3201 |
| screen-v3前端 | ✅ 运行中 | http://localhost:3202 |

### 工单数据

系统中已有 **5条工单**，状态均为"待处理"。