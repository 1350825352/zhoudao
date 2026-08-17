import urllib.request
import json

url = "http://192.168.2.201:2022/rtc/v1/play/"

# 试试最简单的请求
for test_name, data in [
    ("empty json", {}),
    ("with streamurl", {"streamurl": "live/1581F8HGX252F00A018X/99-0-0"}),
    ("with api and streamurl", {"api": "/rtc/v1/play/", "streamurl": "live/1581F8HGX252F00A018X/99-0-0"}),
    ("null sdp", {"api": "/rtc/v1/play/", "streamurl": "live/1581F8HGX252F00A018X/99-0-0", "sdp": None}),
    ("empty sdp", {"api": "/rtc/v1/play/", "streamurl": "live/1581F8HGX252F00A018X/99-0-0", "sdp": ""}),
]:
    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode('utf-8'),
            method='POST'
        )
        req.add_header('Content-Type', 'application/json')
        with urllib.request.urlopen(req, timeout=5) as resp:
            result = json.loads(resp.read().decode())
            print(f"✅ {test_name}: code={result.get('code')}")
            print(f"   full: {json.dumps(result, ensure_ascii=False)[:200]}")
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='ignore')
        print(f"⚠️  {test_name}: HTTP {e.code} - {body[:200]}")
    except Exception as e:
        print(f"❌ {test_name}: {e}")
