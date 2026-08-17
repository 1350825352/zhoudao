#!/bin/bash
# ============================================================
# 部署配置文件 - 修改此文件适配你的环境
# ============================================================

# 公司/项目配置文件名（对应 deploy/configs/ 下的目录名）
COMPANY_PROFILE="company-a"

# 后端端口
BACKEND_PORT=8080

# ---- 目录结构 ----
# /opt/wurenji/
# ├── backend/          # 后端 jar
# ├── frontends/        # 前端静态文件
# │   ├── admin/
# │   ├── screen/
# │   └── h5/
# └── data/             # 数据
#     ├── zhimou-images/
#     └── logs/

# 项目根目录（部署后的代码目录）
APP_ROOT="/opt/wurenji"

# 后端 jar 部署目录
BACKEND_DEPLOY_DIR="$APP_ROOT/backend"

# 前端静态文件目录
FRONTEND_DEPLOY_DIR="$APP_ROOT/frontends"

# 数据目录
DATA_DIR="$APP_ROOT/data"

# 知眸图片保存目录
ZHIMOU_IMG_DIR="$DATA_DIR/zhimou-images"

# 是否安装 MySQL（如果数据库在本机就设 true，在其他机器就设 false）
INSTALL_MYSQL="true"

# 是否安装 Redis（如果 Redis 在本机就设 true，在其他机器就设 false）
INSTALL_REDIS="true"

# ---- 部署模式 ----
# 是否跳过构建（如果已经在 Windows 上构建好了，就设为 true
# 设为 true 时，需要提前把以下文件放到项目根目录下的 dist-packages/ 目录：
#   dist-packages/
#   ├── backend/
#   │   └── sikong-cloud-api-1.0.0.jar
#   └── frontends/
#       ├── admin/
#       ├── screen/
#       └── h5/
SKIP_BUILD="false"
