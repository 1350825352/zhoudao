import requests
url = "http://58.213.86.2:2022/live/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6"
try:
    r = requests.get(url, timeout=10, stream=True)
    print(f"status={r.status_code}")
    print(f"Content-Type={r.headers.get('Content-Type')}")
    # 读前512字节看看是不是FLV
    chunk = r.raw.read(512)
    print(f"前16字节: {chunk[:16].hex()}")
    print(f"以FLV开头: {chunk[:3] == b'FLV'}")
except Exception as e:
    print(f"错误: {e}")
