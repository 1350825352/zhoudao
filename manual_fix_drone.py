import requests
import json

BASE = "http://127.0.0.1:8080/api"

# 1. 查设备列表
print("=== [1] 设备列表 ===")
try:
    r = requests.get(f"{BASE}/device/list", timeout=10)
    data = r.json()
    if data.get("code") == 0 and data.get("data"):
        for d in data["data"]:
            print(f"  SN={d.get('sn')} type={d.get('deviceType')} project={d.get('projectUuid')} name={d.get('deviceName')} online={d.get('online')}")
except Exception as e:
    print(f"  设备列表API失败: {e}")

# 2. 查直播列表(重点找飞机1581F8HGX252F00A018X的)
print("\n=== [2] 飞机1581F8HGX252F00A018X的直播列表 ===")
try:
    r = requests.get(f"{BASE}/live-stream/list", timeout=10, params={"pageSize": 100, "pageNum": 1})
    data = r.json()
    target_live = None
    if data.get("code") == 0:
        records = data.get("data", {}).get("records", []) if isinstance(data.get("data"), dict) else data.get("data", [])
        for ls in records:
            ac = ls.get("aircraftSn") or ls.get("aircraft_sn")
            if ac and "1581F8HG" in ac:
                print(f"  liveId={ls.get('id')} ac={ac} gw={ls.get('gatewaySn')} cam={ls.get('cameraIndex')} status={ls.get('status')} zs={ls.get('zhimouStatus')} ztask={ls.get('zhimouTaskId')} push={ls.get('pushUrl')[:70] if ls.get('pushUrl') else 'None'} create={ls.get('createTime')}")
                if ls.get("status") in [0, 1] and target_live is None:
                    target_live = ls
    print(f"  -> 找到活跃直播: liveId={target_live.get('id') if target_live else 'None'}")
except Exception as e:
    print(f"  直播列表API失败: {e}")
    import traceback; traceback.print_exc()
    target_live = None

# 3. 如果没找到, 尝试直接用device接口查实时状态
if target_live is None:
    print("\n=== [2b] 查device-realtime接口 ===")
    try:
        r = requests.get(f"{BASE}/device-realtime/list", timeout=10, params={"pageSize": 50, "pageNum": 1})
        data = r.json()
        print(f"  接口返回code={data.get('code')} keys={list(data.keys())}")
    except Exception as e:
        print(f"  失败: {e}")

# 4. 手动创建知眸任务(P0救急)
if target_live is not None:
    live_id = target_live.get("id")
    print(f"\n=== [3] P0救急: 为liveId={live_id}手动创建知眸任务 ===")
    try:
        payload = {
            "liveId": live_id,
            "aircraftSn": target_live.get("aircraftSn") or target_live.get("aircraft_sn"),
            "gatewaySn": target_live.get("gatewaySn") or target_live.get("gateway_sn"),
        }
        print(f"  POST /zhimou/task body={json.dumps(payload, ensure_ascii=False)}")
        r = requests.post(f"{BASE}/zhimou/task", json=payload, timeout=30)
        print(f"  HTTP {r.status_code}: {r.text[:500]}")
    except Exception as e:
        print(f"  创建任务失败: {e}")
        import traceback; traceback.print_exc()
else:
    print("\n=== [3] ❌ 没有找到飞机1581F8HG的活跃直播, 无法直接创建知眸任务")
    print("  -> 先尝试手动开播: POST /api/live-stream/start-bypass")
    # 尝试手动开播
    try:
        payload = {
            "gatewaySn": "8UUXN2A00A00M9",
            "aircraftSn": "1581F8HGX252F00A018X",
            "cameraIndex": "99-0-0",
            "streamProtocol": "rtmp",
            "qualityType": "adaptive",
            "projectUuid": "5ac8e7fe-210e-4ad0-95db-c59c67bea827",
            "zhimouAppId": "3y14nhqxbd",
            "zhimouPkgId": "par579tq"
        }
        print(f"\n  [3b] 尝试手动开播: POST /live-stream/start-bypass\n  body={json.dumps(payload, indent=2, ensure_ascii=False)}")
        r = requests.post(f"{BASE}/live-stream/start-bypass", json=payload, timeout=60)
        print(f"  HTTP {r.status_code}: {r.text[:2000]}")
    except Exception as e:
        print(f"  手动开播失败: {e}")
        import traceback; traceback.print_exc()
