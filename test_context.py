import urllib.request

# 测试两个路径
for path in ["/api/uavs/1581F8HGX252F00A018X/video/live", "/uavs/1581F8HGX252F00A018X/video/live"]:
    url = f"http://localhost:8080{path}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as resp:
            print(f"✅ {path} -> {resp.status}")
    except urllib.error.HTTPError as e:
        print(f"❌ {path} -> HTTP {e.code}")
    except Exception as e:
        print(f"❌ {path} -> {e}")
