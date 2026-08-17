import requests
import json

url = "http://192.168.2.202:10042/api/task//ailive"

payload = json.dumps({
   "rtmp": "string",
   "bitrate": 0,
   "app_ids": [
      "3y14nhqxbd"
   ]
})
headers = {
   'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3ODM1ODAzMzh9.qpVDucKWIkvZ-Gwbx00fYljmoVqZdGuPRKFPVo54EZQ',
   'Content-Type': 'application/json'
}

response = requests.request("POST", url, headers=headers, data=payload)

print(response.text)