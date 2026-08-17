import urllib.request
import json

# 测试 WHEP URL
whep_url = "http://192.168.2.201:2022/rtc/v1/whep/?app=live&stream=1581F8HGX252F00A018X/99-0-0.flv&secret=f91c3c6060cd401b99ae649fc50a9bb6"

print("=== 测试 WHEP URL ===")
print(f"URL: {whep_url}")
print()

# 尝试不同的端口
for port in [2022, 1985, 8080, 9090]:
    test_url = whep_url.replace(":2022", f":{port}")
    try:
        req = urllib.request.Request(test_url, method='POST', data=b'')
        req.add_header('Content-Type', 'application/sdp')
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"端口 {port}: {resp.status} - {resp.read()[:200]}")
    except urllib.error.HTTPError as e:
        print(f"端口 {port}: HTTP {e.code}")
    except Exception as e:
        print(f"端口 {port}: Error - {e}")

print()
# 也试试不带端口（默认80）
try:
    test_url = whep_url.replace(":2022", "")
    req = urllib.request.Request(test_url, method='POST', data=b'')
    req.add_header('Content-Type', 'application/sdp')
    with urllib.request.urlopen(req, timeout=5) as resp:
        print(f"端口 80 (默认): {resp.status}")
except Exception as e:
    print(f"端口 80 (默认): Error - {e}")
