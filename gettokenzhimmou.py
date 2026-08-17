import requests
import json

url = "http://192.168.2.202:10042/api/token"

payload = json.dumps({
   "appkey": "75eda2b9aa530c05090ce6c3f48af94b",
   "appsecret": "IimhnRbevTgyxxMK7vJJVJVZlOywaHa1",
})
headers = {
  'Authorization': 'Bearer ',
   'Content-Type': 'application/json'
}

response = requests.request("POST", url, headers=headers, data=payload)

print(response.text)