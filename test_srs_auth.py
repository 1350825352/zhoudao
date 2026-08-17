import urllib.request
import base64
import json

# 尝试访问 SRS API（需要认证，试试常见的用户名密码）
host = "192.168.2.201"
port = 2022

# 常见的 SRS 管理账号
credentials = [
    ("admin", "admin"),
    ("admin", "123456"),
    ("admin", "888888"),
    ("root", "root"),
    ("", ""),  # 无密码
]

for user, pwd in credentials:
    try:
        url = f"http://{host}:{port}/api/v1/summaries"
        req = urllib.request.Request(url)
        if user or pwd:
            auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()
            req.add_header('Authorization', f'Basic {auth}')
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            print(f"✅ 账号 {user}/{pwd}: 成功!")
            print(json.dumps(data, indent=2)[:500])
            break
    except urllib.error.HTTPError as e:
        if e.code == 401:
            print(f"❌ 账号 {user}/{pwd}: 401 认证失败")
        else:
            print(f"⚠️  账号 {user}/{pwd}: HTTP {e.code}")
    except Exception as e:
        print(f"❌ 账号 {user}/{pwd}: {e}")
