#!/usr/bin/env python3
"""171 ARM 机器完整部署脚本"""
import paramiko
import os
import stat
import time

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"
PORT = 22
DEPLOY_DIR = "/opt/sikong-cloud"
LOCAL_BASE = "H:\\traeproject\\zhoudao"

def ssh_connect():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASS, timeout=15)
    return client

def sudo_exec(client, cmd):
    """用 sudo 执行命令"""
    full_cmd = f"echo '{PASS}' | sudo -S bash -c '{cmd}' 2>&1"
    stdin, stdout, stderr = client.exec_command(full_cmd, timeout=120)
    out = stdout.read().decode('utf-8', errors='replace').strip()
    return out

def normal_exec(client, cmd, timeout=60):
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    return out if out else err

def sftp_upload_dir(client, local_dir, remote_dir):
    """上传整个目录"""
    sftp = client.open_sftp()
    try:
        # 递归创建远程目录
        try:
            sftp.stat(remote_dir)
        except FileNotFoundError:
            parts = remote_dir.strip('/').split('/')
            path = ''
            for p in parts:
                path += '/' + p
                try:
                    sftp.stat(path)
                except FileNotFoundError:
                    sftp.mkdir(path)

        for root, dirs, files in os.walk(local_dir):
            rel = os.path.relpath(root, local_dir).replace('\\', '/')
            if rel == '.':
                remote_path = remote_dir
            else:
                remote_path = remote_dir + '/' + rel
            try:
                sftp.stat(remote_path)
            except FileNotFoundError:
                sftp.mkdir(remote_path)

            for f in files:
                local_file = os.path.join(root, f)
                remote_file = remote_path + '/' + f
                sftp.put(local_file, remote_file)
    finally:
        sftp.close()

def sftp_upload_file(client, local_file, remote_file):
    """上传单个文件"""
    sftp = client.open_sftp()
    try:
        # 确保远程目录存在
        remote_dir = os.path.dirname(remote_file).replace('\\', '/')
        parts = remote_dir.strip('/').split('/')
        path = ''
        for p in parts:
            path += '/' + p
            try:
                sftp.stat(path)
            except FileNotFoundError:
                sftp.mkdir(path)
        sftp.put(local_file, remote_file)
    finally:
        sftp.close()

def write_remote_file(client, remote_path, content, mode=0o644):
    """写远程文件"""
    sftp = client.open_sftp()
    try:
        with sftp.file(remote_path, 'w') as f:
            f.write(content)
        sftp.chmod(remote_path, mode)
    finally:
        sftp.close()

def step1_install_deps(client):
    """安装 JDK 17 + Nginx"""
    print("\n[步骤1] 安装 JDK 17 + Nginx...")
    
    # 检查是否已装
    java_check = normal_exec(client, "java -version 2>&1 || echo 'NOT_INSTALLED'")
    if "NOT_INSTALLED" in java_check:
        print("  安装 JDK 17...")
        result = sudo_exec(client, "apt update -qq && apt install -y openjdk-17-jdk-headless")
        print(f"  JDK 安装完成: {result[-100:] if result else 'OK'}")
    else:
        print(f"  JDK 已安装: {java_check[:60]}")
    
    nginx_check = normal_exec(client, "nginx -v 2>&1 || echo 'NOT_INSTALLED'")
    if "NOT_INSTALLED" in nginx_check:
        print("  安装 Nginx...")
        result = sudo_exec(client, "apt install -y nginx")
        print(f"  Nginx 安装完成: {result[-100:] if result else 'OK'}")
    else:
        print(f"  Nginx 已安装: {nginx_check[:60]}")
    
    # 验证
    java_ver = normal_exec(client, "java -version 2>&1 | head -1")
    nginx_ver = normal_exec(client, "nginx -v 2>&1")
    print(f"  验证: Java={java_ver}, Nginx={nginx_ver}")

def step2_create_dirs(client):
    """创建部署目录"""
    print("\n[步骤2] 创建部署目录...")
    sudo_exec(client, f"mkdir -p {DEPLOY_DIR}/logs")
    sudo_exec(client, f"chown -R {USER}:{USER} {DEPLOY_DIR}")
    print(f"  部署目录: {DEPLOY_DIR}")

def step3_upload_files(client):
    """上传部署文件"""
    print("\n[步骤3] 上传部署文件...")
    
    # 3.1 上传 jar
    jar_local = os.path.join(LOCAL_BASE, "target", "sikong-cloud-api-1.0.0.jar")
    jar_remote = f"{DEPLOY_DIR}/sikong-cloud-api-1.0.0.jar"
    if os.path.exists(jar_local):
        size_mb = os.path.getsize(jar_local) / 1024 / 1024
        print(f"  上传后端 jar ({size_mb:.1f}MB)...")
        sftp_upload_file(client, jar_local, jar_remote)
        print(f"  jar 上传完成")
    else:
        print(f"  ERROR: jar 不存在 {jar_local}")
        return False
    
    # 3.2 上传前端 dist
    for name, dist_dir in [
        ("admin", "admin-v3"),
        ("screen", "screen-v3"),
        ("h5", "h5-v3"),
    ]:
        local_dist = os.path.join(LOCAL_BASE, "qianduan", dist_dir, "dist")
        remote_dist = f"{DEPLOY_DIR}/frontend/{name}"
        if os.path.exists(local_dist):
            print(f"  上传 {name} 前端...")
            sftp_upload_dir(client, local_dist, remote_dist)
            print(f"  {name} 前端上传完成")
        else:
            print(f"  WARNING: {name} dist 不存在")
    
    # 3.3 上传配置文件
    config_local = os.path.join(LOCAL_BASE, "src", "main", "resources", "application-base.yml")
    config_remote = f"{DEPLOY_DIR}/config/application-base.yml"
    if os.path.exists(config_local):
        sftp_upload_file(client, config_local, config_remote)
    
    # company-a 配置 (需要修改 h5.host)
    company_config = os.path.join(LOCAL_BASE, "src", "main", "resources", "application-company-a.yml")
    if os.path.exists(company_config):
        with open(company_config, 'r', encoding='utf-8') as f:
            content = f.read()
        # h5.host 改为 171
        content = content.replace('192.168.2.130', '192.168.2.171')
        sftp = client.open_sftp()
        try:
            remote_company = f"{DEPLOY_DIR}/config/application-company-a.yml"
            with sftp.file(remote_company, 'w') as f:
                f.write(content)
        finally:
            sftp.close()
        print(f"  配置文件上传完成 (h5.host→192.168.2.171)")
    
    return True

def step4_nginx_config(client):
    """配置 Nginx"""
    print("\n[步骤4] 配置 Nginx...")
    
    nginx_conf = '''server {
    listen 80;
    server_name _;
    
    # 前端静态资源
    location /admin/ {
        alias /opt/sikong-cloud/frontend/admin/;
        try_files $uri $uri/ /admin/index.html;
    }
    
    location /screen/ {
        alias /opt/sikong-cloud/frontend/screen/;
        try_files $uri $uri/ /screen/index.html;
    }
    
    location /h5/ {
        alias /opt/sikong-cloud/frontend/h5/;
        try_files $uri $uri/ /h5/index.html;
    }
    
    # 默认指向 screen
    location = / {
        return 302 /screen/;
    }
    
    # 后端 API 反代
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_connect_timeout 10s;
    }
    
    # 知眸图片路径
    location /zhimou-img/ {
        alias /opt/sikong-cloud/zhimou-img/;
    }
    
    # 标注图路径
    location /image-detect-img/ {
        alias /opt/sikong-cloud/image-detect-img/;
    }
}
'''
    
    # 写 Nginx 配置
    write_remote_file(client, "/tmp/sikong-cloud.conf", nginx_conf)
    sudo_exec(client, "cp /tmp/sikong-cloud.conf /etc/nginx/sites-available/sikong-cloud.conf")
    sudo_exec(client, "ln -sf /etc/nginx/sites-available/sikong-cloud.conf /etc/nginx/sites-enabled/")
    sudo_exec(client, "rm -f /etc/nginx/sites-enabled/default")
    
    # 测试 Nginx 配置
    result = sudo_exec(client, "nginx -t 2>&1")
    print(f"  Nginx 配置测试: {result}")
    
    # 创建图片目录
    sudo_exec(client, f"mkdir -p {DEPLOY_DIR}/zhimou-img {DEPLOY_DIR}/image-detect-img")
    sudo_exec(client, f"chown -R {USER}:{USER} {DEPLOY_DIR}")
    
    # 重启 Nginx
    sudo_exec(client, "systemctl restart nginx")
    sudo_exec(client, "systemctl enable nginx")
    print("  Nginx 启动并设置开机自启")

def step5_systemd_service(client):
    """配置 systemd 开机自启"""
    print("\n[步骤5] 配置 systemd 开机自启...")
    
    service_content = f'''[Unit]
Description=Sikong Cloud API
After=network.target mysql.service redis.service

[Service]
Type=simple
User={USER}
WorkingDirectory={DEPLOY_DIR}
ExecStart=/usr/bin/java -Dspring.profiles.active=base,company-a -jar {DEPLOY_DIR}/sikong-cloud-api-1.0.0.jar --spring.config.additional-location=file:{DEPLOY_DIR}/config/
Restart=always
RestartSec=10
StandardOutput=append:{DEPLOY_DIR}/logs/app.log
StandardError=append:{DEPLOY_DIR}/logs/app-error.log

[Install]
WantedBy=multi-user.target
'''
    
    write_remote_file(client, "/tmp/sikong-cloud.service", service_content)
    sudo_exec(client, "cp /tmp/sikong-cloud.service /etc/systemd/system/sikong-cloud.service")
    sudo_exec(client, "systemctl daemon-reload")
    sudo_exec(client, "systemctl enable sikong-cloud")
    print("  systemd 服务配置完成 (开机自启)")

def step6_start_and_verify(client):
    """启动并验证"""
    print("\n[步骤6] 启动后端并验证...")
    
    # 启动服务
    sudo_exec(client, "systemctl start sikong-cloud")
    print("  后端启动中，等待 15 秒...")
    time.sleep(15)
    
    # 检查状态
    status = sudo_exec(client, "systemctl is-active sikong-cloud")
    print(f"  服务状态: {status}")
    
    # 检查日志
    log_tail = normal_exec(client, f"tail -20 {DEPLOY_DIR}/logs/app.log 2>/dev/null || echo '日志文件还未生成'")
    print(f"  启动日志:\n{log_tail}")
    
    # 检查端口
    port_check = normal_exec(client, "ss -tlnp | grep 8080 || echo '8080 端口未监听'")
    print(f"  端口检查: {port_check}")
    
    # 检查 Nginx
    nginx_check = normal_exec(client, "ss -tlnp | grep ':80 ' || echo '80 端口未监听'")
    print(f"  Nginx 端口: {nginx_check}")

def main():
    print("="*60)
    print(f"开始部署到 {HOST} ({USER})")
    print("="*60)
    
    client = ssh_connect()
    try:
        step1_install_deps(client)
        step2_create_dirs(client)
        
        success = step3_upload_files(client)
        if not success:
            print("上传失败，终止部署")
            return
        
        step4_nginx_config(client)
        step5_systemd_service(client)
        step6_start_and_verify(client)
        
        print("\n" + "="*60)
        print("部署完成!")
        print(f"  前端: http://{HOST}/screen/")
        print(f"  admin: http://{HOST}/admin/")
        print(f"  h5: http://{HOST}/h5/")
        print(f"  API: http://{HOST}/api/")
        print("="*60)
    finally:
        client.close()

if __name__ == "__main__":
    main()
