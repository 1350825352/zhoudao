import urllib.request

# 测试不同的 WHEP URL 和请求方式
base = "http://192.168.2.201:2022/rtc/v1/whep/"

urls = [
    "?app=live/1581F8HGX252F00A018X&stream=99-0-0.flv",
    "?app=live/1581F8HGX252F00A018X&stream=99-0-0.flv&secret=f91c3c6060cd401b99ae649fc50a9bb6",
]

for qs in urls:
    url = base + qs
    print(f"\n=== URL: {qs[:60]}... ===")
    
    # 测试 1: POST + application/sdp (前端用的方式)
    try:
        req = urllib.request.Request(url, method='POST', data=b'')
        req.add_header('Content-Type', 'application/sdp')
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"  POST+SDP: {resp.status}, body len={len(resp.read())}")
    except urllib.error.HTTPError as e:
        body = e.read()[:200].decode('utf-8', errors='ignore')
        print(f"  POST+SDP: HTTP {e.code}, body={body}")
    except Exception as e:
        print(f"  POST+SDP: Error - {e}")
    
    # 测试 2: POST + application/json
    try:
        req = urllib.request.Request(url, method='POST', data=b'{}')
        req.add_header('Content-Type', 'application/json')
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"  POST+JSON: {resp.status}, body len={len(resp.read())}")
    except urllib.error.HTTPError as e:
        body = e.read()[:200].decode('utf-8', errors='ignore')
        print(f"  POST+JSON: HTTP {e.code}, body={body}")
    except Exception as e:
        print(f"  POST+JSON: Error - {e}")
    
    # 测试 3: GET
    try:
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"  GET: {resp.status}, body len={len(resp.read())}")
    except urllib.error.HTTPError as e:
        body = e.read()[:200].decode('utf-8', errors='ignore')
        print(f"  GET: HTTP {e.code}, body={body}")
    except Exception as e:
        print(f"  GET: Error - {e}")
