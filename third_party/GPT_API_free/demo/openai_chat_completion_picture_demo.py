# -*- coding: utf-8 -*-
"""
说明：
这个示例文件包含两种方法调用 GPT-4o 识别图片：
1）网络图片 URL 版本
2）本地图片 Base64 版本

修改下面的 `API_KEY` 变量后直接运行即可。
"""

import base64
import mimetypes
from openai import OpenAI

# ====== 配置区 ======
API_KEY = "sk-xxxxx"  # 你的 API Key
BASE_URL = "https://api.chatanywhere.tech/v1"
# ====================

# 创建OpenAI客户端
client = OpenAI(
    api_key=API_KEY,
    base_url=BASE_URL
)


def describe_image_from_url(image_url: str):
    """
    方法1：发送网络图片 URL
    """
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "介绍一下这张图"},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": image_url,
                            "detail": "high"  # high 更精细；low 更快
                        }
                    },
                ],
            }
        ],
        stream=False
    )
    print("\n=== 网络图片识别结果 ===")
    print(response.choices[0].message)


def describe_image_from_local(local_image_path: str):
    """
    方法2：读取本地图片 -> 转Base64 -> 发送
    """
    # 自动识别 MIME 类型
    mime_type, _ = mimetypes.guess_type(local_image_path)
    if mime_type is None:
        mime_type = "image/png"

    # 转 Base64
    with open(local_image_path, "rb") as f:
        image_base64 = base64.b64encode(f.read()).decode("utf-8")

    image_data_url = f"data:{mime_type};base64,{image_base64}"

    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "介绍一下这张本地图片"},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": image_data_url,
                            "detail": "high"
                        }
                    },
                ],
            }
        ],
        stream=False
    )
    print("\n=== 本地图片识别结果 ===")
    print(response.choices[0].message)


if __name__ == "__main__":
    # 示例1：网络图片
    describe_image_from_url(
        "https://lmg.jj20.com/up/allimg/1114/0G020114924/200G0114924-11-1200.jpg"
    )

    # 示例2：本地图片
    # describe_image_from_local(
    #     "/Users/xxxx/Downxxloads/test.png"
    # )
