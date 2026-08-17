#!/bin/bash
set -e

# ============================================================
# 无人机监控平台 - 一键部署脚本 (Ubuntu 22.04)
# 用法：
#   1. 把整个项目传到 /opt/ceshisikongapiAI
#   2. 修改 deploy/config.sh 中的配置
#   3. 执行：sudo bash deploy/deploy.sh
# ============================================================

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 加载配置
CONFIG_FILE="$SCRIPT_DIR/config.sh"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ 配置文件不存在：$CONFIG_FILE"
    echo "   请先复制 deploy/config.example.sh 为 deploy/config.sh 并修改配置"
    exit 1
fi
source "$CONFIG_FILE"

echo "=========================================="
echo "  无人机监控平台 - 一键部署脚本"
echo "  项目目录：$PROJECT_ROOT"
echo "=========================================="

# ============================================================
# 颜色输出
# ============================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================================
# 检测函数：已安装返回 0，未安装返回 1
# ============================================================
check_installed() {
    if command -v "$1" &> /dev/null; then
        return 0
    else
        return 1
    fi
}

check_service_active() {
    if systemctl is-active --quiet "$1" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# ============================================================
# 步骤 1：系统更新
# ============================================================
step_system_update() {
    log_info "步骤 1/7：更新系统软件包..."
    apt update -qq
    apt upgrade -y -qq
    log_info "系统更新完成"
}

# ============================================================
# 步骤 2：安装 Java 8
# ============================================================
step_install_java() {
    log_info "步骤 2/7：检测并安装 Java 8..."
    
    if check_installed java; then
        JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP 'version "\K[0-9.]+' | cut -d. -f1-2)
        if [[ "$JAVA_VER" == "1.8" ]]; then
            log_info "Java 8 已安装 ($(java -version 2>&1 | head -1))，跳过"
            return
        else
            log_warn "已安装 Java $JAVA_VER，但项目需要 Java 8"
            log_warn "尝试安装 Java 8..."
        fi
    fi
    
    apt install -y openjdk-8-jdk
    log_info "Java 8 安装完成：$(java -version 2>&1 | head -1)"
}

# ============================================================
# 步骤 3：安装 Maven
# ============================================================
step_install_maven() {
    log_info "步骤 3/7：检测并安装 Maven..."
    
    if check_installed mvn; then
        log_info "Maven 已安装 ($(mvn -version 2>&1 | head -1))，跳过"
        return
    fi
    
    apt install -y maven
    log_info "Maven 安装完成：$(mvn -version 2>&1 | head -1)"
}

# ============================================================
# 步骤 4：安装 Node.js 18
# ============================================================
step_install_nodejs() {
    log_info "步骤 4/7：检测并安装 Node.js 18+..."
    
    if check_installed node; then
        NODE_VER=$(node -v | cut -d. -f1 | sed 's/v//')
        if [ "$NODE_VER" -ge 18 ]; then
            log_info "Node.js 已安装 ($(node -v))，跳过"
            return
        else
            log_warn "已安装 Node.js $(node -v)，需要 >= 18，尝试升级..."
        fi
    fi
    
    # 使用 NodeSource 安装
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt install -y nodejs
    log_info "Node.js 安装完成：$(node -v)"
}

# ============================================================
# 步骤 5：安装 MySQL (可选)
# ============================================================
step_install_mysql() {
    log_info "步骤 5/7：检测 MySQL..."
    
    if check_installed mysql; then
        log_info "MySQL 已安装，跳过"
        return
    fi
    
    if [ "$INSTALL_MYSQL" = "true" ]; then
        log_info "安装 MySQL..."
        apt install -y mysql-server
        systemctl enable mysql
        systemctl start mysql
        log_info "MySQL 安装并启动完成"
    else
        log_warn "跳过 MySQL 安装（INSTALL_MYSQL=false），请确保配置中的 MySQL 可访问"
    fi
}

# ============================================================
# 步骤 6：安装 Redis (可选)
# ============================================================
step_install_redis() {
    log_info "步骤 6/7：检测 Redis..."
    
    if check_installed redis-server; then
        log_info "Redis 已安装，跳过"
        return
    fi
    
    if [ "$INSTALL_REDIS" = "true" ]; then
        log_info "安装 Redis..."
        apt install -y redis-server
        systemctl enable redis-server
        systemctl start redis-server
        log_info "Redis 安装并启动完成"
    else
        log_warn "跳过 Redis 安装（INSTALL_REDIS=false），请确保配置中的 Redis 可访问"
    fi
}

# ============================================================
# 步骤 7：安装 Nginx
# ============================================================
step_install_nginx() {
    log_info "步骤 7/7：检测并安装 Nginx..."
    
    if check_installed nginx; then
        log_info "Nginx 已安装 ($(nginx -v 2>&1))，跳过"
        return
    fi
    
    apt install -y nginx
    systemctl enable nginx
    systemctl start nginx
    log_info "Nginx 安装完成"
}

# ============================================================
# 配置后端
# ============================================================
config_backend() {
    log_info "配置后端..."
    
    BACKEND_CONFIG_SRC="$PROJECT_ROOT/deploy/configs/$COMPANY_PROFILE/backend.yml"
    BACKEND_CONFIG_DST="$PROJECT_ROOT/src/main/resources/application-$COMPANY_PROFILE.yml"
    
    if [ ! -f "$BACKEND_CONFIG_SRC" ]; then
        log_error "后端配置文件不存在：$BACKEND_CONFIG_SRC"
        exit 1
    fi
    
    cp "$BACKEND_CONFIG_SRC" "$BACKEND_CONFIG_DST"
    log_info "后端配置已复制：$BACKEND_CONFIG_DST"
    
    # 修改 application.yml 的 active profile
    APP_YML="$PROJECT_ROOT/src/main/resources/application.yml"
    sed -i "s/active: .*/active: base,$COMPANY_PROFILE/" "$APP_YML"
    log_info "application.yml 已设置 profile: base,$COMPANY_PROFILE"
}

# ============================================================
# 配置前端
# ============================================================
config_frontend() {
    log_info "配置前端..."
    
    FRONTEND_CONFIG_SRC="$PROJECT_ROOT/deploy/configs/$COMPANY_PROFILE/frontend.env"
    
    if [ ! -f "$FRONTEND_CONFIG_SRC" ]; then
        log_error "前端配置文件不存在：$FRONTEND_CONFIG_SRC"
        exit 1
    fi
    
    for FRONTEND in admin-v3 screen-v3 h5-v3; do
        FRONTEND_DIR="$PROJECT_ROOT/qianduan/$FRONTEND"
        if [ -d "$FRONTEND_DIR" ]; then
            cp "$FRONTEND_CONFIG_SRC" "$FRONTEND_DIR/.env.production"
            log_info "已配置：$FRONTEND/.env.production"
        fi
    done
}

# ============================================================
# 构建后端
# ============================================================
build_backend() {
    log_info "构建后端 (跳过测试)..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -q
    
    JAR_FILE="$PROJECT_ROOT/target/sikong-cloud-api-1.0.0.jar"
    if [ -f "$JAR_FILE" ]; then
        log_info "后端构建成功：$JAR_FILE"
    else
        log_error "后端构建失败，未找到 jar 文件"
        exit 1
    fi
    
    # 复制到部署目录
    log_info "复制 jar 到部署目录：$BACKEND_DEPLOY_DIR"
    mkdir -p "$BACKEND_DEPLOY_DIR"
    cp "$JAR_FILE" "$BACKEND_DEPLOY_DIR/"
    log_info "jar 已复制到：$BACKEND_DEPLOY_DIR/sikong-cloud-api-1.0.0.jar"
}

# ============================================================
# 从预构建包部署后端
# ============================================================
deploy_backend_from_package() {
    log_info "从预构建包部署后端..."
    PKG_JAR="$PROJECT_ROOT/dist-packages/backend/sikong-cloud-api-1.0.0.jar"
    
    if [ ! -f "$PKG_JAR" ]; then
        log_error "预构建 jar 不存在：$PKG_JAR"
        exit 1
    fi
    
    mkdir -p "$BACKEND_DEPLOY_DIR"
    cp "$PKG_JAR" "$BACKEND_DEPLOY_DIR/"
    log_info "jar 已复制到：$BACKEND_DEPLOY_DIR/sikong-cloud-api-1.0.0.jar"
}

# ============================================================
# 构建前端
# ============================================================
build_frontend() {
    log_info "构建前端..."
    
    # admin-v3
    if [ -d "$PROJECT_ROOT/qianduan/admin-v3" ]; then
        log_info "  构建 admin-v3..."
        cd "$PROJECT_ROOT/qianduan/admin-v3"
        [ ! -d "node_modules" ] && npm install --silent
        npm run build --silent
        rm -rf "$FRONTEND_DEPLOY_DIR/admin"
        cp -r dist "$FRONTEND_DEPLOY_DIR/admin"
        log_info "  admin-v3 构建完成"
    fi
    
    # screen-v3
    if [ -d "$PROJECT_ROOT/qianduan/screen-v3" ]; then
        log_info "  构建 screen-v3..."
        cd "$PROJECT_ROOT/qianduan/screen-v3"
        [ ! -d "node_modules" ] && npm install --silent
        npm run build:prod --silent
        rm -rf "$FRONTEND_DEPLOY_DIR/screen"
        cp -r dist "$FRONTEND_DEPLOY_DIR/screen"
        log_info "  screen-v3 构建完成"
    fi
    
    # h5-v3
    if [ -d "$PROJECT_ROOT/qianduan/h5-v3" ]; then
        log_info "  构建 h5-v3..."
        cd "$PROJECT_ROOT/qianduan/h5-v3"
        [ ! -d "node_modules" ] && npm install --silent
        npm run build --silent
        rm -rf "$FRONTEND_DEPLOY_DIR/h5"
        cp -r dist "$FRONTEND_DEPLOY_DIR/h5"
        log_info "  h5-v3 构建完成"
    fi
    
    log_info "所有前端构建完成"
}

# ============================================================
# 从预构建包部署前端
# ============================================================
deploy_frontend_from_package() {
    log_info "从预构建包部署前端..."
    PKG_FRONTENDS="$PROJECT_ROOT/dist-packages/frontends"
    
    for NAME in admin screen h5; do
        SRC_DIR="$PKG_FRONTENDS/$NAME"
        if [ -d "$SRC_DIR" ]; then
            rm -rf "$FRONTEND_DEPLOY_DIR/$NAME"
            cp -r "$SRC_DIR" "$FRONTEND_DEPLOY_DIR/$NAME"
            log_info "  已部署：$NAME"
        else
            log_warn "  跳过：$PKG_FRONTENDS/$NAME 不存在"
        fi
    done
    
    log_info "所有前端部署完成"
}

# ============================================================
# 配置 Nginx
# ============================================================
config_nginx() {
    log_info "配置 Nginx..."
    
    NGINX_CONF="/etc/nginx/sites-available/sikong"
    
    cat > "$NGINX_CONF" << 'NGINX_EOF'
server {
    listen 80;
    server_name _;

    # 知眸图片 (放前面避免被 /api 拦截)
    location /zhimou-img/ {
        alias __ZHIMOU_IMG_DIR__/;
    }

    # admin-v3 后台管理
    location /admin/ {
        alias __ADMIN_DIST__/;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }

    # screen-v3 监控大屏
    location /screen/ {
        alias __SCREEN_DIST__/;
        index index.html;
        try_files $uri $uri/ /screen/index.html;
    }

    # h5-v3 工单
    location /h5/ {
        alias __H5_DIST__/;
        index index.html;
        try_files $uri $uri/ /h5/index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:__BACKEND_PORT__;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
NGINX_EOF

    # 替换占位符
    sed -i "s|__BACKEND_PORT__|$BACKEND_PORT|g" "$NGINX_CONF"
    sed -i "s|__ADMIN_DIST__|$FRONTEND_DEPLOY_DIR/admin|g" "$NGINX_CONF"
    sed -i "s|__SCREEN_DIST__|$FRONTEND_DEPLOY_DIR/screen|g" "$NGINX_CONF"
    sed -i "s|__H5_DIST__|$FRONTEND_DEPLOY_DIR/h5|g" "$NGINX_CONF"
    sed -i "s|__ZHIMOU_IMG_DIR__|$ZHIMOU_IMG_DIR|g" "$NGINX_CONF"
    
    # 启用配置
    ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/sikong
    rm -f /etc/nginx/sites-enabled/default
    
    # 测试
    nginx -t
    systemctl reload nginx
    
    log_info "Nginx 配置完成"
}

# ============================================================
# 配置后端 systemd 服务
# ============================================================
config_backend_service() {
    log_info "配置后端 systemd 服务..."
    
    SERVICE_FILE="/etc/systemd/system/sikong-backend.service"
    
    cat > "$SERVICE_FILE" << SERVICE_EOF
[Unit]
Description=Sikong Cloud API Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$BACKEND_DEPLOY_DIR
ExecStart=/usr/bin/java -jar sikong-cloud-api-1.0.0.jar --spring.profiles.active=base,$COMPANY_PROFILE
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE_EOF

    systemctl daemon-reload
    systemctl enable sikong-backend
    systemctl restart sikong-backend
    
    sleep 3
    if check_service_active sikong-backend; then
        log_info "后端服务启动成功"
    else
        log_error "后端服务启动失败，请查看日志：journalctl -u sikong-backend -n 50"
        exit 1
    fi
}

# ============================================================
# 主流程
# ============================================================
main() {
    echo ""
    log_info "开始部署..."
    
    # 安装基础软件
    step_system_update
    step_install_java
    if [ "$SKIP_BUILD" != "true" ]; then
        step_install_maven
        step_install_nodejs
    else
        log_info "跳过构建模式，不安装 Maven 和 Node.js"
    fi
    step_install_mysql
    step_install_redis
    step_install_nginx
    
    # 创建部署目录
    log_info "创建部署目录..."
    mkdir -p "$BACKEND_DEPLOY_DIR"
    mkdir -p "$FRONTEND_DEPLOY_DIR"
    mkdir -p "$ZHIMOU_IMG_DIR"
    mkdir -p "$DATA_DIR/logs"
    log_info "部署目录创建完成：
      后端：   $BACKEND_DEPLOY_DIR
      前端：   $FRONTEND_DEPLOY_DIR
      图片：   $ZHIMOU_IMG_DIR
      日志：   $DATA_DIR/logs"
    
    # 配置后端（复制 application-xxx.yml）
    config_backend
    
    if [ "$SKIP_BUILD" != "true" ]; then
        # 完整模式：配置前端环境变量 + 构建
        config_frontend
        build_backend
        build_frontend
    else
        # 跳过构建模式：直接从预构建包部署
        log_info "======================================="
        log_info "  跳过构建模式，使用预构建产物"
        log_info "======================================="
        deploy_backend_from_package
        deploy_frontend_from_package
    fi
    
    # 部署
    config_nginx
    config_backend_service
    
    echo ""
    echo "=========================================="
    echo -e "  ${GREEN}部署完成！${NC}"
    echo "=========================================="
    echo "  访问地址："
    echo "    监控大屏：  http://$(hostname -I | awk '{print $1}')/screen/"
    echo "    后台管理：  http://$(hostname -I | awk '{print $1}')/admin/"
    echo "    工单H5：    http://$(hostname -I | awk '{print $1}')/h5/"
    echo "    后端API：   http://$(hostname -I | awk '{print $1}')/api/"
    echo ""
    echo "  常用命令："
    echo "    查看后端日志：journalctl -u sikong-backend -f"
    echo "    重启后端：    systemctl restart sikong-backend"
    echo "    重启Nginx：   systemctl reload nginx"
    echo "=========================================="
}

main
