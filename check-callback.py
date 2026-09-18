#!/usr/bin/env python3
"""查知眸回调是否到达 171"""
import paramiko

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASS, timeout=10)

    # 1. 知眸回调日志
    print("=== 1. 知眸回调 (callback) 日志 ===")
    stdin, stdout, stderr = c.exec_command(
        "grep -i 'callback\\|/api/zhimou/callback\\|知眸.*回调' /opt/sikong-cloud/logs/app.log 2>/dev/null | tail -10"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无知眸回调日志")

    # 2. 知眸回调 URL 配置
    print("\n=== 2. 知眸回调 URL 配置 ===")
    stdin, stdout, stderr = c.exec_command(
        "grep -i 'callback\\|zhimou.*url\\|notify' /opt/sikong-cloud/config/application-company-a.yml 2>/dev/null"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无回调配置")

    # 3. 11:28 后所有与知眸相关的日志
    print("\n=== 3. 11:28-11:35 知眸相关日志 ===")
    stdin, stdout, stderr = c.exec_command(
        "grep '11:2[89]\\|11:3[0-5]' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep -i 'zhimou\\|知眸\\|callback\\|clean\\|清洗\\|push\\|推送' | head -20"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "11:28-11:35 无知眸相关日志")

    # 4. MinIO 轮询校验细节
    print("\n=== 4. MinIO 轮询校验细节 ===")
    stdin, stdout, stderr = c.exec_command(
        "grep 'MinIO' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep -v '扫描到' | tail -20"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无其他 MinIO 日志(只有扫描)")

    # 5. 检查知眸回调接口是否可访问
    print("\n=== 5. 测试知眸回调接口 ===")
    stdin, stdout, stderr = c.exec_command(
        "curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/zhimou/callback -H 'Content-Type: application/json' -d '{}' 2>/dev/null"
    )
    code = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"知眸回调接口: HTTP {code}")

    # 6. 查 130 上是否有回调
    print("\n=== 6. 对比 130 是否收到了回调 ===")
    import requests
    try:
        r = requests.get("http://192.168.2.130:8080/api/zhimou/image-results/page",
            params={"page": 1, "size": 3}, timeout=5)
        j = r.json()
        print(f"130 照片告警 total={j.get('data', {}).get('total')}")
    except Exception as e:
        print(f"130 不可达: {e}")

    c.close()

if __name__ == "__main__":
    main()
