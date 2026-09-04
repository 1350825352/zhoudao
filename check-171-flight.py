#!/usr/bin/env python3
"""检查 171 机器 11:20 左右的飞行数据"""
import paramiko
import requests

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"
DEPLOY_DIR = "/opt/sikong-cloud"

def main():
    print("=== 1. 171 后端 API 查询 11:20 左右数据 ===")
    try:
        r = requests.get(
            "http://192.168.2.171/api/zhimou/image-results/page",
            params={"current": 1, "size": 50},
            timeout=5
        )
        j = r.json()
        if j.get("code") == 200 and j.get("data", {}).get("list"):
            records = j["data"]["list"]
            print(f"总记录数: {len(records)}")
            for rec in records[:10]:
                tid = rec.get("zhimouTaskId", "")
                sn = rec.get("aircraftSn", rec.get("deviceId", ""))
                app = rec.get("appName", "")
                hits = rec.get("hitCount", "")
                cs = rec.get("cleanStatus", "")
                ct = rec.get("createTime", "")
                img = rec.get("localImageName", "")
                print(f"  id={rec.get('id')} time={ct} task={tid[:20]} app={app} hits={hits} clean={cs} img={img[-30:]}")
        else:
            print(f"查询失败: {j}")
    except Exception as e:
        print(f"API 请求失败: {e}")

    print("\n=== 2. 171 日志检查 11:20 左右活动 ===")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASS, timeout=15)

    # 检查 11:20 左右的日志
    stdin, stdout, stderr = client.exec_command(
        f"grep '11:2[0-9]' {DEPLOY_DIR}/logs/app.log 2>/dev/null | tail -50"
    )
    logs = stdout.read().decode('utf-8', errors='replace').strip()
    if logs:
        print("11:20-11:29 日志:")
        for line in logs.split('\n')[:30]:
            if line.strip():
                print(f"  {line[:200]}")
    else:
        print("11:20-11:29 无日志")

    # 检查 MQTT 连接状态
    print("\n=== 3. MQTT 连接状态 ===")
    stdin, stdout, stderr = client.exec_command(
        f"grep -i 'MQTT.*连接\\|MQTT.*connect\\|DjiMqtt' {DEPLOY_DIR}/logs/app.log 2>/dev/null | tail -5"
    )
    mqtt_log = stdout.read().decode('utf-8', errors='replace').strip()
    print(mqtt_log if mqtt_log else "无 MQTT 日志")

    # 检查 OSD 消息（设备状态）
    print("\n=== 4. OSD 消息（飞机状态） ===")
    stdin, stdout, stderr = client.exec_command(
        f"grep 'OSD' {DEPLOY_DIR}/logs/app.log 2>/dev/null | tail -10"
    )
    osd_log = stdout.read().decode('utf-8', errors='replace').strip()
    if osd_log:
        for line in osd_log.split('\n'):
            if line.strip():
                print(f"  {line[:200]}")
    else:
        print("无 OSD 消息")

    # 检查 MinIO 批处理
    print("\n=== 5. MinIO 批处理 ===")
    stdin, stdout, stderr = client.exec_command(
        f"grep -i 'MinIO\\|minio' {DEPLOY_DIR}/logs/app.log 2>/dev/null | tail -10"
    )
    minio_log = stdout.read().decode('utf-8', errors='replace').strip()
    if minio_log:
        for line in minio_log.split('\n'):
            if line.strip():
                print(f"  {line[:200]}")
    else:
        print("无 MinIO 日志")

    # 检查知眸回调
    print("\n=== 6. 知眸回调 ===")
    stdin, stdout, stderr = client.exec_command(
        f"grep '知眸.*回调\\|知眸.*上报' {DEPLOY_DIR}/logs/app.log 2>/dev/null | tail -10"
    )
    callback_log = stdout.read().decode('utf-8', errors='replace').strip()
    if callback_log:
        for line in callback_log.split('\n'):
            if line.strip():
                print(f"  {line[:200]}")
    else:
        print("无知眸回调日志")

    client.close()

if __name__ == "__main__":
    main()
