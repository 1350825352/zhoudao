import requests
import json
import sys

BASE_URL = "http://localhost:8080/api"
DEFAULT_PROJECT_UUID = "5ac8e7fe-210e-4ad0-95db-c59c67bea827"  # 改成你的项目UUID

def sync_wayline(project_uuid=None):
    puuid = project_uuid or DEFAULT_PROJECT_UUID
    params = {"projectUuid": puuid} if puuid else None
    url = f"{BASE_URL}/wayline/sync"
    print(f"▶ POST {url}")
    print(f"  query params: {json.dumps(params, ensure_ascii=False)}")
    resp = requests.post(url, params=params, timeout=30)
    print(f"◀ HTTP {resp.status_code}")
    try:
        data = resp.json()
        print(json.dumps(data, ensure_ascii=False, indent=2))
    except Exception:
        print(resp.text[:500])

if __name__ == "__main__":
    puuid = sys.argv[1] if len(sys.argv) > 1 else None
    sync_wayline(puuid)