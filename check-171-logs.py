#!/usr/bin/env python3
"""查 171 上 11:20 后的日志"""
import paramiko

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASS, timeout=10)

    # 1. MQTT 降落消息
    print("=== 1. MQTT 降落消息 (11:00-12:00) ===")
    stdin, stdout, stderr = c.exec_command(
        "grep -E 'mode_code|land|降落|LAND|flight' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep '11:[0-5][0-9]' | tail -20"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无降落日志")

    # 2. MinIO 批处理
    print("\n=== 2. MinIO 批处理 (11:00-12:00) ===")
    stdin, stdout, stderr = c.exec_command(
        "grep -i 'minio' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep '11:[0-5][0-9]' | tail -20"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无 MinIO 日志")

    # 3. 知眸回调
    print("\n=== 3. 知眸回调 (11:00-12:00) ===")
    stdin, stdout, stderr = c.exec_command(
        "grep -E 'callback|回调|reportImage|上报' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep '11:[0-5][0-9]' | tail -20"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无回调日志")

    # 4. 图片任务创建
    print("\n=== 4. 图片任务创建 (11:00-12:00) ===")
    stdin, stdout, stderr = c.exec_command(
        "grep -E 'createTask|task_id|图片任务|image.*task' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep '11:[0-5][0-9]' | tail -20"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无任务创建日志")

    # 5. 所有 11:20-11:30 的日志
    print("\n=== 5. 11:20-11:30 所有日志 ===")
    stdin, stdout, stderr = c.exec_command(
        "grep '11:2[0-9]' /opt/sikong-cloud/logs/app.log 2>/dev/null | head -30"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "11:20-11:30 无任何日志")

    # 6. 最新日志
    print("\n=== 6. 最新 20 行日志 ===")
    stdin, stdout, stderr = c.exec_command(
        "tail -20 /opt/sikong-cloud/logs/app.log 2>/dev/null"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "无日志文件")

    # 7. 日志文件大小
    print("\n=== 7. 日志文件信息 ===")
    stdin, stdout, stderr = c.exec_command(
        "ls -la /opt/sikong-cloud/logs/ 2>/dev/null"
    )
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out)

    c.close()

if __name__ == "__main__":
    main()
