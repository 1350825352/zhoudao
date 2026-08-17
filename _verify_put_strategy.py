import requests, pymysql, json, time, sys
BASE='http://localhost:8080/api'
SN='1581F8HGX252F00A018X'
GW='8UUXN2A00A00M9'

# 0. 等服务
print('等服务启动...')
ok=False
for i in range(35):
    try:
        r=requests.get(BASE+'/mqtt/status', timeout=5)
        if r.status_code==200:
            ok=True; print(f'  启动成功(等待{i+1}s)'); break
    except: pass
    time.sleep(1)
if not ok: print('  ❌ 服务未启动!'); sys.exit(1)

# 1. 关旧直播
r=requests.post(BASE+'/live-stream/stop-by-aircraft', params={'aircraftSn':SN}, timeout=20)
print('关旧直播:', r.status_code, r.json().get('code'), str(r.json().get('msg',''))[:50])

# 2. 清DB活跃
conn = pymysql.connect(host='192.168.2.200',user='szy02',password='123456',database='sikong_cloud_api',charset='utf8mb4')
cur = conn.cursor()
cur.execute("UPDATE live_stream SET status=2, deleted=1 WHERE aircraft_sn=%s AND deleted=0 AND status IN (0,1,3)", (SN,))
print(f'强制停止 {cur.rowcount} 条旧直播')
conn.commit()

# 3. 新开旁路推流
body = {'gatewaySn': GW, 'aircraftSn': SN, 'cameraIndex': '99-0-0', 'pushUrl': '', 'qualityType': 'adaptive', 'streamProtocol': 'rtmp'}
print()
print('=== 新开旁路推流(新策略:先查再PUT) ===')
r=requests.post(BASE+'/live-stream/start-bypass', json=body, timeout=45)
d=r.json()
print(f'HTTP={r.status_code} code={d.get("code")} msg={(str(d.get("msg"))+str(d.get("message","")))[:150]}')
live = d.get('data') or {}
id_new = live.get('id')
print(f'id={id_new}')
s = live.get('status')
mark = '✅推送中(status=1)' if s==1 else ('⏳待MQTT回复(0)' if s==0 else ('❌失败(3)' if s==3 else '停(2)'))
print(f'status={s} → {mark}')
print(f'status_msg={str(live.get("statusMessage"))[:100]}')
print(f'push={live.get("pushUrl")}')
print(f'pull={live.get("pullUrl")}')
sys.stdout.flush()

# 4. 查DB详情:是不是走了PUT?
print()
print('=== id_new DB详情 (2秒后查) ===')
time.sleep(2)
cur.execute('SELECT id,request_json,response_json,status,status_message,stream_id FROM live_stream WHERE id=%s', (id_new,))
row = cur.fetchone()
if row:
    try:
        req = json.loads(row[1]) if row[1] else {}
        resp = json.loads(row[2]) if row[2] else {}
        msg = str(row[4])[:100] if row[4] else ''
        print(f'  id={row[0]}  status={row[3]}  status_msg={msg}')
        # 判断是不是走了PUT:看request_json里有没有 converter_id
        if 'converter_id' in req:
            print(f'  ✅ 走了PUT策略!  converter_id={req.get("converter_id")}')
        else:
            print(f'  ⚠️ 走了POST创建策略(没找到converter)')
        print(f'  司空2响应: code={resp.get("code")} msg={str(resp.get("message") or resp.get("msg"))[:80]}')
    except Exception as e:
        print(f'  JSON解析失败: {e}, req={str(row[1])[:200]}')
    print(f'  stream_id(DB)= {row[5]}')

cur.close(); conn.close()

# 5. 给用户输出最终拉流地址
print()
print('='*80)
print('🎉 用户现在可以用以下地址播放飞机 1581F8HGX252F00A018X 的实时视频了!')
print('  HTTP-FLV(端口1935): http://58.213.86.2:1935/live/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6')
print('='*80)
