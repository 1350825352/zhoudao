import urllib.request
import json

# 测试 SRS 的 /rtc/v1/play/ 接口
url = "http://192.168.2.201:2022/rtc/v1/play/"

# SRS 的 WebRTC 播放接口需要的参数
data = {
    "api": "/rtc/v1/play/",
    "clientip": None,
    "sdp": "v=0\r\no=- 0 0 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\n",
    "streamurl": "room/livestream",
}

# 也试试不同的 stream 格式
stream_formats = [
    "live/1581F8HGX252F00A018X/99-0-0.flv",
    "live/1581F8HGX252F00A018X/99-0-0",
    "1581F8HGX252F00A018X/99-0-0",
]

for stream in stream_formats:
    test_data = data.copy()
    test_data["streamurl"] = stream
    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(test_data).encode('utf-8'),
            method='POST'
        )
        req.add_header('Content-Type', 'application/json')
        with urllib.request.urlopen(req, timeout=5) as resp:
            result = json.loads(resp.read().decode())
            print(f"stream={stream}: code={result.get('code')}, sdp_len={len(result.get('sdp',''))}")
            if result.get('sdp'):
                print(f"  SDP preview: {result['sdp'][:100]}")
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='ignore')
        print(f"stream={stream}: HTTP {e.code} - {body[:100]}")
    except Exception as e:
        print(f"stream={stream}: Error - {e}")
