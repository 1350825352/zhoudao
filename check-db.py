#!/usr/bin/env python3
"""检查 11:20 后的数据库记录 - 通过 API 查"""
import requests

# 用 171 的 API 查
base = "http://192.168.2.171/api"

# 1. 登录
r = requests.post(f"{base}/auth/login", json={"username": "admin", "password": "888888"}, timeout=5)
j = r.json()
token = j.get("data", {}).get("token", "")
print(f"登录: code={j.get('code')}, token={token[:20]}...")

# 2. 查 11:20 后的数据 - 不带时间过滤
headers = {"Authorization": f"Bearer {token}"}
r = requests.get(f"{base}/zhimou/image-results/page", params={"page": 1, "size": 50}, headers=headers, timeout=5)
j = r.json()
print(f"\n照片告警查询: code={j.get('code')}, total={j.get('data', {}).get('total')}")

records = j.get("data", {}).get("list", [])
if records:
    print(f"返回记录数: {len(records)}")
    print("\n前10条:")
    for rec in records[:10]:
        print(f"  id={rec.get('id')} time={rec.get('event_time')} app={rec.get('rule_name')} hits={rec.get('hit_count')} clean={rec.get('clean_status')} img={rec.get('local_image_name', '')[-30:]}")
    print("\n最后5条:")
    for rec in records[-5:]:
        print(f"  id={rec.get('id')} time={rec.get('event_time')} app={rec.get('rule_name')} hits={rec.get('hit_count')} clean={rec.get('clean_status')} img={rec.get('local_image_name', '')[-30:]}")
else:
    print("无记录!")

# 3. 查今天的所有记录
print("\n=== 查今天所有记录 ===")
r = requests.get(f"{base}/zhimou/image-results/page",
    params={"page": 1, "size": 100, "start_time": "2026-09-04 00:00:00", "end_time": "2026-09-04 23:59:59"},
    headers=headers, timeout=5)
j = r.json()
print(f"今天记录: code={j.get('code')}, total={j.get('data', {}).get('total')}")
recs = j.get("data", {}).get("list", [])
if recs:
    for rec in recs[:5]:
        print(f"  id={rec.get('id')} time={rec.get('event_time')} app={rec.get('rule_name')} hits={rec.get('hit_count')}")
    print(f"  ... 共 {len(recs)} 条")
