import requests
import zipfile
import io

download_url = "https://file-storage.djicdn.com/83f6ba9b-cd2c-4fe0-a4a5-3783baec77f6/5ac8e7fe-210e-4ad0-95db-c59c67bea827/05cb30bd-63d2-4695-aa62-7dee71e2d02f/%E6%96%B0%E5%BB%BA%E5%B7%A1%E9%80%BB%E8%88%AA%E7%BA%BF-with-res.kmz?auth_key=1784189638-0-0-246ed2f2bc5b7762b956f4f4e66ae0bc&response-content-disposition=attachment;filename=\"新建巡逻航线.kmz\";filename*=utf-8''%25E6%2596%25B0%25E5%25BB%25BA%25B7%A1%25E9%2580%25BB%25E8%2588%25AA%25E7%25BA%25BF.kmz"

print("=== 下载并解析KMZ文件 ===")
try:
    resp = requests.get(download_url, timeout=30)
    print(f"下载状态: {resp.status_code}")
    print(f"文件大小: {len(resp.content)} bytes")
    
    kmz_file = io.BytesIO(resp.content)
    with zipfile.ZipFile(kmz_file, 'r') as zip_ref:
        print(f"\nKMZ内文件列表:")
        for name in zip_ref.namelist():
            info = zip_ref.getinfo(name)
            print(f"  - {name} ({info.file_size} bytes)")
        
        kml_content = None
        for name in zip_ref.namelist():
            if name.lower().endswith('.kml'):
                kml_content = zip_ref.read(name).decode('utf-8')
                break
        
        if kml_content:
            print(f"\nKML文件内容:")
            print(kml_content[:2000])
            
            import re
            coords = re.findall(r'<coordinates>(.*?)</coordinates>', kml_content, re.DOTALL)
            print(f"\n找到 {len(coords)} 个 coordinates 标签")
            for i, c in enumerate(coords[:3]):
                print(f"\n坐标点 {i+1}:")
                lines = c.strip().split()
                print(f"  行数: {len(lines)}")
                for j, line in enumerate(lines[:5]):
                    parts = line.split(',')
                    if len(parts) >= 2:
                        print(f"    {j+1}. 经度:{parts[0]}, 纬度:{parts[1]}{', 高度:' + parts[2] if len(parts) > 2 else ''}")
        else:
            print("\n未找到KML文件")
except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()