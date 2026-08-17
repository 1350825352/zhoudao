import urllib.request
import socket

host = "192.168.2.201"
port = 2022
socket.setdefaulttimeout(3)

# SRS WebRTC 常见路径
paths = [
    "/rtc/v1/whep/",
    "/rtc/v1/whip/",
    "/rtc/v1/play/",
    "/rtc/v1/publish/",
    "/whep/",
    "/whip/",
    "/webrtc/play/",
    "/api/v1/rtc/play/",
    "/live/",
    "/test/",
]

for path in paths:
    url = f"http://{host}:{port}{path}"
    try:
        req = urllib.request.Request(url, method='POST', data=b'v=0\r\n')
        req.add_header('Content-Type', 'application/sdp')
        with urllib.request.urlopen(req, timeout=3) as resp:
            body = resp.read()[:200].decode('utf-8', errors='ignore')
            print(f"✅ POST {path}: {resp.status} - {body[:100]}")
    except urllib.error.HTTPError as e:
        body = e.read()[:200].decode('utf-8', errors='ignore') if hasattr(e, 'read') else ''
        print(f"⚠️  POST {path}: HTTP {e.code} - {body[:80]}")
    except Exception as e:
        err = str(e)[:80]
        print(f"❌ POST {path}: {err}")

print()
print("=== 也试试 GET 请求 ===")
for path in paths[:5]:
    url = f"http://{host}:{port}{path}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=3) as resp:
            body = resp.read()[:200].decode('utf-8', errors='ignore')
            print(f"✅ GET {path}: {resp.status} - {body[:100]}")
    except urllib.error.HTTPError as e:
        print(f"⚠️  GET {path}: HTTP {e.code}")
    except Exception as e:
        err = str(e)[:80]
        print(f"❌ GET {path}: {err}")
