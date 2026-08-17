import urllib.request
import json

url = "http://localhost:8080/api/uavs/1581F8HGX252F00A018X/video/live"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=5) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        print("API 返回:")
        print(json.dumps(data, indent=2, ensure_ascii=False))
except Exception as e:
    print(f"Error: {e}")
