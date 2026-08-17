import urllib.request

url = "http://192.168.2.201:2022/"
with urllib.request.urlopen(url, timeout=5) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    # 查找 WebRTC 相关的内容
    import re
    webrtc_refs = re.findall(r'(webrtc|whip|whep|rtc|WebRTC|WHEP|WHIP)[^<]{0,100}', html, re.IGNORECASE)
    print("=== 页面中 WebRTC 相关内容 ===")
    for ref in webrtc_refs[:20]:
        print(f"  {ref[:120]}")
    
    # 也看看所有链接
    links = re.findall(r'href=["\']([^"\']+)["\']', html)
    print("\n=== 页面链接 ===")
    for link in links[:30]:
        print(f"  {link}")
