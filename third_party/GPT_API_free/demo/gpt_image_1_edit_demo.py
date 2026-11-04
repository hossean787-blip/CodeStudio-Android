import requests

url = "https://api.chatanywhere.tech/v1/images/edits"
api_key = "sk-xxxxxx"  # 这里填写你的 API KEY

# 文件路径,此文件存在和本py文件相同目录, 其他目录请自行询问gpt 如何更改
image_path = "imagetest.png"

# 构建请求
with open(image_path, "rb") as image_file:
    files = {
        "image": image_file,
    }
    data = {
        "prompt": "把人物换成特朗普",
        "model": "gpt-image-1",
        "size": "1024x1024",
    }
    headers = {
        "Authorization": f"Bearer {api_key}"
    }

    response = requests.post(url, headers=headers, files=files, data=data)

# 打印返回内容
print(response.status_code)
print(response.json())
