import requests, pymysql, json, time, sys
BASE='http://localhost:8080/api'
SN='1581F8HGX252F00A018X'

# 0. 服务启动确认
r=requests.get(BASE+'/mqtt/status', timeout=15)
print('服务启动:', 'OK' if r.status_code==200 else 'FAIL', r.status_code)
sys.stdout.flush()

# 1. 把这架飞机之前的直播改成status=2停止,避免影响自动开播判断
conn = pymysql.connect(host='192.168.2.200',user='szy02',password='123456',database='sikong_cloud_api',charset='utf8mb4')
cur = conn.cursor()
cur.execute("""UPDATE live_stream SET status=2, deleted=1
               WHERE aircraft_sn=%s AND deleted=0 AND status IN (0,1,3)""", (SN,))
print(f'强制停止之前未结束的直播 {cur.rowcount} 条')
conn.commit()
sys.stdout.flush()

# 2. 用接口关一次，顺便触发内部重置（不管接口有没成功）
try:
    requests.post(BASE+'/live-stream/stop-by-aircraft', params={'aircraftSn':SN}, timeout=15)
except Exception as e:
    print('stop接口(忽略):', e)
sys.stdout.flush()

# 3. 查当前高度确认还在空
r=requests.get(BASE+'/device/realtime/list', timeout=20)
data = r.json().get('data') or []
for d in data:
    if d.get('deviceSn')==SN:
        print(f'当前 {SN} 高度={d.get("height")}m, gateway={d.get("gatewaySn")}')
        break
sys.stdout.flush()

print()
print('=== 等待80秒,观察自动开播(OSD到后端之后会自动触发) ===')
sys.stdout.flush()
last_id = 0
for i in range(16):
    time.sleep(5)
    cur.execute("""SELECT id, aircraft_sn, camera_index, status, status_message, push_url, pull_url
                   FROM live_stream WHERE aircraft_sn=%s ORDER BY id DESC LIMIT 1""", (SN,))
    row = cur.fetchone()
    if row:
        if row[0] != last_id or i%3==0:
            last_id = row[0]
            msg = str(row[4])[:60] if row[4] else ''
            print(f'[{(i+1)*5:>2}s] id={row[0]} camera={row[2]} status={row[3]} msg={msg}')
            sys.stdout.flush()
            if row[3]==1:
                print()
                print('====== ✅ 自动开播成功! 状态=推送中(status=1) ======')
                print('  推流 pushUrl:', row[5])
                print('  拉流 pullUrl(前端播放):', row[6])
                sys.stdout.flush()
                break
cur.close(); conn.close()
