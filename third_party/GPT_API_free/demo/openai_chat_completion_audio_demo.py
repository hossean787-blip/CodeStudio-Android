import base64
import requests
from openai import OpenAI

client = OpenAI(
    # 输入转发API Key
    api_key="sk-dohFcAaTBXsloNfKBnoNOaGM4ArEjOL1fPGOziLsONeXaH7P",
    base_url="https://api.chatanywhere.tech/v1",
)

# Fetch the audio file and convert it to a base64 encoded string
url = "https://cdn.openai.com/API/docs/audio/alloy.wav"
response = requests.get(url)
response.raise_for_status()
wav_data = response.content
encoded_string = base64.b64encode(wav_data).decode("utf-8")

completion = client.chat.completions.create(
    model="gemini-2.5-pro",
    modalities=["text", "audio"],
    audio={"voice": "alloy", "format": "wav"},
    messages=[
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "What is in this recording? 给他变成中文"},
                {
                    "type": "input_audio",
                    "input_audio": {"data": encoded_string, "format": "wav"},
                },
            ],
        },
    ],
)

print(completion.choices[0].message)
