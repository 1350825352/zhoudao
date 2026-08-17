import urllib.request
import socket

host = "192.168.2.201"
socket.setdefaulttimeout(3)

ports_to_try = [
    (2022, "/rtc/v1/whep/"),
    (2022, "/api/v1/summaries"),
    (2022, "/"),
    (1985, "/api/v1/summaries"),
    (1985, "/rtc/v1/whep/"),
    (8080, "/"),
    (8080, "/rtc/v1/whep/"),
    (9090, "/"),
    (3000, "/"),
]

for port, path in ports_to_try:
    url = f"http://{host}:{port}{path}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=3) as resp:
            body = resp.read()[:300].decode('utf-8', errors='ignore')
            print(f"✅ {port}{path}: {resp.status} - {body[:100]}")
    except urllib.error.HTTPError as e:
        print(f"⚠️  {port}{path}: HTTP {e.code}")
    except Exception as e:
        err = str(e)[:80]
        print(f"❌ {port}{path}: {err}")
