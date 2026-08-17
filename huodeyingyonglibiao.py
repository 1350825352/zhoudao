import requests

url = "http://192.168.2.202:10042/api/zapk"

payload = {}
headers = {
   'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3ODM1NzU1OTB9.bdxYuo9gfxdLe73OjfL4ry3rfOwOmrhBkSUMZAPOWbY'
}

response = requests.request("GET", url, headers=headers, data=payload)

print(response.text)