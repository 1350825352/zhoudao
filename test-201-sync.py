import requests, json
HOST = "http://192.168.2.201:8080"

# 测同步项目
print("=== POST /api/project/sync ===")
try:
    r = requests.post(f"{HOST}/api/project/sync", timeout=60)
    print(f"status={r.status_code}")
    print(r.text[:500])
except Exception as e:
    print(f"err: {e}")

# 测同步航线
print("\n=== POST /api/wayline/sync ===")
try:
    r = requests.post(f"{HOST}/api/wayline/sync", timeout=120)
    print(f"status={r.status_code}")
    print(r.text[:500])
except Exception as e:
    print(f"err: {e}")
