flv_url = "http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6"

# 模拟后端的转换逻辑
from urllib.parse import urlparse, parse_qs, urlencode

parsed = urlparse(flv_url)
host = parsed.hostname
port = parsed.port or 80
path = parsed.path
query = parsed.query

parts = path.split("/")
print("path parts:", parts)

if len(parts) >= 4:
    app = parts[1] + "/" + parts[2]
    stream = parts[3]
    for i in range(4, len(parts)):
        stream += "/" + parts[i]
else:
    app = "live"
    stream = ""

print("app:", app)
print("stream:", stream)

whep = f"http://{host}:{port}/rtc/v1/whep/?app={app}&stream={stream}"
if query:
    whep += "&" + query

print("\n转换后的 WHEP URL:")
print(whep)

print("\n用户给的正确 URL:")
print("http://192.168.2.201:2022/rtc/v1/whep/?app=live/1581F8HGX252F00A018X&stream=99-0-0.flv")
