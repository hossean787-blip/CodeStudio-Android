import requests

url = 'https://api.chatanywhere.tech/v1/images/generations'
headers = {
    'Content-Type': 'application/json',
    # 转发秘钥
    'Authorization': 'Bearer sk-xxxx'
}
data = {
    "model": "dall-e-3",
    "prompt": "从前有座山 山里有个庙 庙里有个老和尚讲故事",
    "n": 1,
    "size": "1024x1024"
}

response = requests.post(url, headers=headers, json=data)

print(response.json())