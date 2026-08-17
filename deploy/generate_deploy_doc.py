#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""生成部署步骤 Word 文档"""

from docx import Document
from docx.shared import Pt, Inches, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT

doc = Document()

# 设置默认字体
style = doc.styles['Normal']
font = style.font
font.name = '微软雅黑'
font.size = Pt(11)

# ============================================================
# 标题
# ============================================================
title = doc.add_heading('无人机监控平台 - 部署步骤', level=0)
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

doc.add_paragraph('部署目标服务器：192.168.2.201（Ubuntu 22.04）')
doc.add_paragraph('生成日期：2026-08-05')
doc.add_paragraph('')

# ============================================================
# 1. 网络架构
# ============================================================
doc.add_heading('一、网络架构', level=1)

doc.add_paragraph('部署目标服务器 192.168.2.201 有两个网卡：')
doc.add_paragraph('  • 内网网卡：192.168.2.201', style='List Bullet')
doc.add_paragraph('  • 外网网卡：192.168.6.201（外网 58.213.86.2 的端口映射到此网卡）', style='List Bullet')
doc.add_paragraph('')

doc.add_paragraph('网络拓扑：')

# 用表格画网络拓扑
arch_text = """
外网 58.213.86.2
       │
       │ 端口映射
       ▼
┌──────────────────┐
│   路由器/防火墙    │
└──────────────────┘
       │
       ▼
192.168.6.201 (外网卡)
       │
192.168.2.201 (内网卡)          192.168.2.200
┌──────────────────┐            ┌──────────────────┐
│  Ubuntu 22.04    │            │  MySQL 数据库    │
│  - SRS 流媒体    │◄──────────►│  (端口 3306)     │
│  - 后端 jar      │            └──────────────────┘
│  - Nginx 前端    │
│  - Redis (本机)  │            192.168.6.35
│  - MySQL 客户端  │            ┌──────────────────┐
└──────────────────┘            │  知眸 AI 服务器   │
       │                       │  (端口 10042)    │
       │                       └──────────────────┘
       ▼
  外网映射端口
  63080 → 80   (前端页面 + API)
  2022  → 2022 (SRS 视频流)
  11883 → 11883 (DJI MQTT)
"""
p = doc.add_paragraph(arch_text)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(9)

# ============================================================
# 2. 服务清单
# ============================================================
doc.add_heading('二、服务清单', level=1)

table = doc.add_table(rows=8, cols=4)
table.style = 'Table Grid'
table.alignment = WD_TABLE_ALIGNMENT.CENTER

headers = ['#', '服务', '地址', '说明']
for i, h in enumerate(headers):
    cell = table.rows[0].cells[i]
    cell.text = h
    for p in cell.paragraphs:
        for run in p.runs:
            run.bold = True

data = [
    ['1', '后端 (Spring Boot)', '192.168.2.201:8080', 'Java 17，systemd 管理'],
    ['2', 'Nginx 前端代理', '192.168.2.201:80', 'admin/screen/h5 三个前端'],
    ['3', 'Redis', '127.0.0.1:6379', '本机安装'],
    ['4', 'MySQL', '192.168.2.200:3306', '使用 200 服务器，需新建数据库'],
    ['5', 'SRS 流媒体', '192.168.2.201:2022', '已在 201 上运行'],
    ['6', '知眸 AI 服务', '192.168.6.35:10042', '知眸识别服务'],
    ['7', 'MQTT 服务', '58.213.86.2:11883', 'DJI MQTT 服务'],
]

for i, row_data in enumerate(data):
    row = table.rows[i + 1]
    for j, val in enumerate(row_data):
        row.cells[j].text = val

doc.add_paragraph('')

# ============================================================
# 3. 本机打包
# ============================================================
doc.add_heading('三、本机打包（Windows）', level=1)

doc.add_heading('3.1 打包后端（JDK 17）', level=2)
doc.add_paragraph('打开 PowerShell，执行：')

code1 = """# 设置 JDK 17 环境变量（根据你的实际路径修改）
$env:JAVA_HOME="C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.x-hotspot"
$env:Path="$env:JAVA_HOME\\bin;$env:Path"

# 验证 Java 版本（必须显示 17）
java -version

# 打包
cd h:\\traeproject\\ceshisikongapiAI
mvn clean package -DskipTests
"""
p = doc.add_paragraph(code1)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_paragraph('打包成功后，jar 文件在：h:\\traeproject\\ceshisikongapiAI\\target\\sikong-cloud-api-1.0.0.jar')

doc.add_heading('3.2 打包前端（3 个前端分别构建）', level=2)
doc.add_paragraph('在 PowerShell 中执行：')

code2 = """# 1. 构建 admin-v3（后台管理）
cd h:\\traeproject\\ceshisikongapiAI\\qianduan\\admin-v3
npm run build

# 2. 构建 screen-v3（监控大屏）
cd h:\\traeproject\\ceshisikongapiAI\\qianduan\\screen-v3
npm run build:prod

# 3. 构建 h5-v3（工单 H5）
cd h:\\traeproject\\ceshisikongapiAI\\qianduan\\h5-v3
npm run build
"""
p = doc.add_paragraph(code2)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('3.3 整理预构建包', level=2)
doc.add_paragraph('在 PowerShell 中执行：')

code3 = """cd h:\\traeproject\\ceshisikongapiAI

# 创建 dist-packages 目录结构
New-Item -ItemType Directory -Force -Path dist-packages\\backend
New-Item -ItemType Directory -Force -Path dist-packages\\frontends\\admin
New-Item -ItemType Directory -Force -Path dist-packages\\frontends\\screen
New-Item -ItemType Directory -Force -Path dist-packages\\frontends\\h5

# 复制后端 jar
Copy-Item target\\sikong-cloud-api-1.0.0.jar dist-packages\\backend\\

# 复制前端构建产物
Copy-Item -Recurse -Force qianduan\\admin-v3\\dist\\* dist-packages\\frontends\\admin\\
Copy-Item -Recurse -Force qianduan\\screen-v3\\dist\\* dist-packages\\frontends\\screen\\
Copy-Item -Recurse -Force qianduan\\h5-v3\\dist\\* dist-packages\\frontends\\h5\\
"""
p = doc.add_paragraph(code3)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('3.4 打包项目传输包', level=2)
doc.add_paragraph('把项目（不含 node_modules 和 target）打包成 zip：')

code4 = """# 用 7-Zip 打包（排除大目录）
7z a -xr!node_modules -xr!target -xr!.git deploy-package.zip h:\\traeproject\\ceshisikongapiAI
"""
p = doc.add_paragraph(code4)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

# ============================================================
# 4. 传文件到服务器
# ============================================================
doc.add_heading('四、传文件到服务器', level=1)

doc.add_paragraph('用 WinSCP 或 scp 把项目传到 201 服务器：')

code5 = """# 方法 1：scp 传输
scp -r h:\\traeproject\\ceshisikongapiAI szylx@192.168.2.201:/opt/

# 方法 2：用 WinSCP 图形界面传输
#   主机名：192.168.2.201
#   用户名：szylx
#   密码：szylx666
#   上传到：/opt/ceshisikongapiAI
"""
p = doc.add_paragraph(code5)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_paragraph('注意：如果 /opt/ceshisikongapiAI 已存在，先删除旧目录或备份。')

# ============================================================
# 5. 服务器配置
# ============================================================
doc.add_heading('五、服务器配置（192.168.2.201）', level=1)

doc.add_heading('5.1 SSH 登录服务器', level=2)
code6 = """ssh szylx@192.168.2.201
# 密码：szylx666
"""
p = doc.add_paragraph(code6)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('5.2 在 MySQL（192.168.2.200）上新建数据库', level=2)
doc.add_paragraph('在 201 服务器上连接 200 的 MySQL，创建新数据库：')

code7 = """# 连接 200 的 MySQL
mysql -h 192.168.2.200 -u szy02 -p123456

# 在 MySQL 中执行：
CREATE DATABASE sikong_cloud_api_201 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;

# 导入表结构（从 200 导出再导入 201 专用库）
# 在 200 上导出：
mysqldump -h 192.168.2.200 -u szy02 -p123456 sikong_cloud_api > /tmp/sikong_schema.sql

# 导入到新库：
mysql -h 192.168.2.200 -u szy02 -p123456 sikong_cloud_api_201 < /tmp/sikong_schema.sql
"""
p = doc.add_paragraph(code7)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_paragraph('注意：新数据库名为 sikong_cloud_api_201，需要在 backend.yml 中同步修改。')

doc.add_heading('5.3 修改后端配置文件中的数据库名', level=2)
doc.add_paragraph('在服务器上修改配置：')

code8 = """cd /opt/ceshisikongapiAI/deploy/configs/company-a/
nano backend.yml

# 修改数据库连接 URL，把数据库名改为 sikong_cloud_api_201：
#   url: jdbc:mysql://192.168.2.200:3306/sikong_cloud_api_201?...
#                          ↑ 改为 _201
"""
p = doc.add_paragraph(code8)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('5.4 修改部署配置', level=2)
doc.add_paragraph('创建并修改 config.sh：')

code9 = """cd /opt/ceshisikongapiAI
cp deploy/config.example.sh deploy/config.sh

# 修改配置
nano deploy/config.sh

# 需要修改的配置项：
#   COMPANY_PROFILE="company-a"      # 不改
#   BACKEND_PORT=8080                 # 不改
#   INSTALL_MYSQL="false"             # 数据库在 200，不安装
#   INSTALL_REDIS="true"              # Redis 在本机安装
#   SKIP_BUILD="true"                 # 使用预构建包
"""
p = doc.add_paragraph(code9)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

# 配置文件说明表
doc.add_paragraph('')
doc.add_paragraph('config.sh 关键配置项说明：')

table2 = doc.add_table(rows=7, cols=3)
table2.style = 'Table Grid'

headers2 = ['配置项', '值', '说明']
for i, h in enumerate(headers2):
    cell = table2.rows[0].cells[i]
    cell.text = h
    for p in cell.paragraphs:
        for run in p.runs:
            run.bold = True

config_data = [
    ['COMPANY_PROFILE', 'company-a', '配置文件目录名'],
    ['BACKEND_PORT', '8080', '后端服务端口'],
    ['INSTALL_MYSQL', 'false', 'MySQL 在 200 服务器，不安装'],
    ['INSTALL_REDIS', 'true', 'Redis 在本机安装'],
    ['SKIP_BUILD', 'true', '使用预构建包，不在服务器编译'],
    ['APP_ROOT', '/opt/wurenji', '部署目录'],
]

for i, row_data in enumerate(config_data):
    row = table2.rows[i + 1]
    for j, val in enumerate(row_data):
        row.cells[j].text = val

doc.add_paragraph('')

# ============================================================
# 6. 执行部署
# ============================================================
doc.add_heading('六、执行部署', level=1)

doc.add_paragraph('在服务器上执行一键部署脚本：')

code10 = """cd /opt/ceshisikongapiAI
sudo bash deploy/deploy.sh
"""
p = doc.add_paragraph(code10)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_paragraph('部署脚本会自动完成：')
doc.add_paragraph('1. 安装 Java 17', style='List Number')
doc.add_paragraph('2. 安装 Redis（本机）', style='List Number')
doc.add_paragraph('3. 安装 Nginx', style='List Number')
doc.add_paragraph('4. 复制后端 jar 和配置文件到 /opt/wurenji/backend/', style='List Number')
doc.add_paragraph('5. 复制前端到 /opt/wurenji/frontends/', style='List Number')
doc.add_paragraph('6. 配置 Nginx 反向代理', style='List Number')
doc.add_paragraph('7. 创建 systemd 服务并启动后端', style='List Number')

# ============================================================
# 7. 验证
# ============================================================
doc.add_heading('七、验证部署', level=1)

doc.add_heading('7.1 检查服务状态', level=2)
code11 = """# 检查后端
sudo systemctl status sikong-backend

# 检查 Nginx
sudo systemctl status nginx

# 检查 Redis
sudo systemctl status redis-server

# 检查端口
sudo netstat -tlnp | grep -E "8080|80|6379"
"""
p = doc.add_paragraph(code11)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('7.2 查看后端日志', level=2)
code12 = """# 实时查看后端日志
sudo journalctl -u sikong-backend -f

# 查看最近 50 行日志
sudo journalctl -u sikong-backend -n 50
"""
p = doc.add_paragraph(code12)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('7.3 验证内网访问', level=2)
code13 = """# 在 201 服务器上测试
curl -I http://localhost/screen/
curl -I http://localhost/admin/
curl -I http://localhost/h5/
curl -I http://localhost/api/actuator/health

# 在内网其他机器上测试
curl -I http://192.168.2.201/screen/
curl -I http://192.168.2.201/admin/
"""
p = doc.add_paragraph(code13)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

doc.add_heading('7.4 验证外网访问', level=2)
code14 = """# 外网访问地址
#   监控大屏：http://58.213.86.2:63080/screen/
#   后台管理：http://58.213.86.2:63080/admin/
#   工单 H5：http://58.213.86.2:63080/h5/
#   后端 API：http://58.213.86.2:63080/api/
"""
p = doc.add_paragraph(code14)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

# ============================================================
# 8. 外网端口映射
# ============================================================
doc.add_heading('八、外网端口映射确认', level=1)

doc.add_paragraph('确保路由器/防火墙上以下端口映射到位：')

table3 = doc.add_table(rows=5, cols=4)
table3.style = 'Table Grid'

headers3 = ['外网端口', '映射到', '协议', '用途']
for i, h in enumerate(headers3):
    cell = table3.rows[0].cells[i]
    cell.text = h
    for p in cell.paragraphs:
        for run in p.runs:
            run.bold = True

port_data = [
    ['63080', '192.168.6.201:80', 'TCP', '前端页面 + 后端 API'],
    ['2022', '192.168.6.201:2022', 'TCP', 'SRS HTTP-FLV + WebRTC SDP'],
    ['8000-10000', '192.168.6.201:8000-10000', 'UDP', 'WebRTC 视频数据'],
    ['11883', '192.168.6.201:11883', 'TCP', 'DJI MQTT'],
]

for i, row_data in enumerate(port_data):
    row = table3.rows[i + 1]
    for j, val in enumerate(row_data):
        row.cells[j].text = val

doc.add_paragraph('')

# ============================================================
# 9. 后端配置文件说明
# ============================================================
doc.add_heading('九、后端配置文件说明', level=1)

doc.add_paragraph('配置文件路径：/opt/wurenji/backend/application-company-a.yml')
doc.add_paragraph('')

table4 = doc.add_table(rows=9, cols=3)
table4.style = 'Table Grid'

headers4 = ['配置项', '值', '说明']
for i, h in enumerate(headers4):
    cell = table4.rows[0].cells[i]
    cell.text = h
    for p in cell.paragraphs:
        for run in p.runs:
            run.bold = True

config_yml_data = [
    ['MySQL 地址', '192.168.2.200:3306', '使用 200 服务器的 MySQL'],
    ['MySQL 数据库', 'sikong_cloud_api_201', '需提前新建'],
    ['Redis 地址', '127.0.0.1:6379', '本机 Redis'],
    ['知眸服务地址', '192.168.6.35:10042', '知眸 AI 识别服务'],
    ['知眸回调地址', '192.168.2.201:8080', '知眸回调到本机后端'],
    ['流媒体内网地址', '192.168.2.201:2022', 'SRS 在本机'],
    ['流媒体外网地址', '58.213.86.2:2022', '外网访问视频流'],
    ['H5 邮件地址', '58.213.86.2:63080', '工单邮件中的链接'],
]

for i, row_data in enumerate(config_yml_data):
    row = table4.rows[i + 1]
    for j, val in enumerate(row_data):
        row.cells[j].text = val

doc.add_paragraph('')

# ============================================================
# 10. 常用运维命令
# ============================================================
doc.add_heading('十、常用运维命令', level=1)

code15 = """# 后端
sudo systemctl restart sikong-backend    # 重启后端
sudo systemctl status sikong-backend     # 查看状态
sudo journalctl -u sikong-backend -f     # 实时日志

# Nginx
sudo systemctl reload nginx              # 重载配置
sudo nginx -t                            # 测试配置

# Redis
sudo systemctl restart redis-server      # 重启 Redis
redis-cli                                # 连接 Redis

# MySQL（连接 200）
mysql -h 192.168.2.200 -u szy02 -p123456

# 查看端口占用
sudo netstat -tlnp | grep -E "8080|80|6379|2022"
"""
p = doc.add_paragraph(code15)
p.style.font.name = 'Consolas'
p.style.font.size = Pt(10)

# ============================================================
# 11. 注意事项
# ============================================================
doc.add_heading('十一、注意事项', level=1)

doc.add_paragraph('1. 数据库：需要在 192.168.2.200 上提前新建数据库 sikong_cloud_api_201', style='List Number')
doc.add_paragraph('2. Redis：部署脚本会自动在本机安装 Redis', style='List Number')
doc.add_paragraph('3. 图片目录：需要把 200 服务器上的知眸图片复制到 201 的 /opt/wurenji/data/zhimou-images/', style='List Number')
doc.add_paragraph('4. SRS 流媒体：确保 SRS 服务已正常运行（端口 2022）', style='List Number')
doc.add_paragraph('5. 防火墙：确保 201 服务器的 8080、80、6379、2022 端口对内网开放', style='List Number')
doc.add_paragraph('6. 外网映射：确保 58.213.86.2 的端口映射到 192.168.6.201 对应端口', style='List Number')
doc.add_paragraph('7. MQTT：确保 58.213.86.2:11883 映射到 192.168.6.201:11883', style='List Number')
doc.add_paragraph('8. WebRTC UDP：SRS 的 WebRTC 需要 UDP 端口范围（看 SRS 配置）', style='List Number')

# ============================================================
# 保存
# ============================================================
output_path = r'h:\traeproject\ceshisikongapiAI\deploy\部署步骤_192.168.2.201.docx'
doc.save(output_path)
print(f'Word 文档已生成：{output_path}')
