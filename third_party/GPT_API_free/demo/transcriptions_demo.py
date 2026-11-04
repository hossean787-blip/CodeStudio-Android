import requests

url = 'https://api.chatanywhere.tech/v1/audio/transcriptions'
api_key = 'sk-xxxxxxxxxxxx'  # 你的 API 密钥

# 本地音频文件路径 此处替换成自己的本机的地址非编辑器里面文件的地址, 如需变成编辑器里面的文件问一下gpt
file_path = '/xxxxx/xxxx/xxxx.mp3'

headers = {
    'Authorization': f'Bearer {api_key}',
    # 注意不要设置 'Content-Type', Requests会自动根据 form-data 设置
}

data = {
    'model': 'whisper-1',
    'prompt': '',
    'response_format': 'json',
    # 'temperature': '0',
    # 'language': '',
}

files = {
    'file': open(file_path, 'rb'),
}

response = requests.post(url, headers=headers, data=data, files=files)
print(response.status_code)
print(response.text)
