import requests
import json
import time

BASE = "http://127.0.0.1:8080/api"

# P0救急:手动为liveId=1965和1855调用知眸创建任务
target_live_ids = [1965, 1855]
app_id = "3y14nhqxbd"
pkg_id = "par579tq"

for live_id in target_live_ids:
    print(f"\n=== 为liveId={live_id}创建知眸任务 ===")
    try:
        payload = {
            "live_stream_id": live_id,
            "app_id": app_id,
            "application_package_id": pkg_id
        }
        print(f"  POST /zhimou/task: {json.dumps(payload, ensure_ascii=False)}")
        r = requests.post(f"{BASE}/zhimou/task", json=payload, timeout=60)
        print(f"  HTTP {r.status_code}: {r.text[:2000]}")
    except Exception as e:
        print(f"  ❌失败: {e}")
        import traceback; traceback.print_exc()
    time.sleep(2)

# 再调用一下live-stream详情看看结果
print("\n=== 检查创建结果: live-stream详情 ===")
try:
    r = requests.get(f"{BASE}/live-stream/{1965}", timeout=10)
    print(f"  liveId=1965: HTTP {r.status_code}, {r.text[:1000]}")
except Exception as e:
    print(f"  查询失败: {e}")
