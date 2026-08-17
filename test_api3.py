import urllib.request
import json

for sn in ["1581F8HGX252F00A018X", "1581F6GKB235900300NW", "1581F6Q8D23BM00ALJFB"]:
    url = f"http://localhost:8080/api/uavs/{sn}/video/live"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            print(f"=== {sn} ===")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            print()
    except Exception as e:
        print(f"=== {sn} === Error: {e}")
        print()
