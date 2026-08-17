import urllib.request
import json

# 尝试访问 SRS 的各个管理端口和 API
host = "192.168.2.201"

# 常见的 SRS HTTP API 端口
ports_to_try = [
    (2022, "/rtc/v1/whep/"),  # 当前用的
    (2022, "/api/v1/summaries"),  # SRS API
    (2022, "/"),  # 首页
    (1985, "/api/v1/summaries"),  # SRS 默认 API 端口
    (1985, "/rtc/v1/whep/"),
    (8080, "/"),  # SRS WebRTC 端口
    (8080, "/rtc/v1/whep/"),
    (9090, "/"),  # ORYX 管理界面
    (3000, "/"),  # 另一个常见端口
]

for port, path in ports_to_try:
    url = f"http://{host}:{port}{path}"
    try:
        req = urllib.request.Request(url, timeout=3)
        with urllib.request.urlopen(req) as resp:
            body = resp.read()[:300].decode('utf-8', errors='ignore')
            print(f"✅ {port}{path}: {resp.status} - {body[:100]}")
    except urllib.error.HTTPError as e:
        print(f"⚠️  {port}{path}: HTTP {e.code}")
    except Exception as e:
        err = str(e)[:80]
        print(f"❌ {port}{path}: {err}")
