from openai import OpenAI

# ====== 配置区 ======
# 请替换为你实际的 API Key
API_KEY = "sk-xxxxxx"
# 请替换为你实际的服务商 Base URL
BASE_URL = "https://api.chatanywhere.tech/v1"
# ====================

# 初始化 OpenAI 客户端
# 注意：这里只需要初始化一次，后面所有请求都可以复用这个 client
client = OpenAI(
    api_key=API_KEY,
    base_url=BASE_URL
)

# 定义使用的模型名称
MODEL_NAME = "gpt-4.1"

def print_separator(title):
    """打印分隔线，方便查看输出结果"""
    print(f"\n{'='*20} {title} {'='*20}")

def test_basic_chat():
    """测试 1: 基础文本对话"""
    print_separator("测试 1: 基础文本对话")
    try:
        response = client.responses.create(
            model=MODEL_NAME,
            input="你好"
        )
        print("【请求成功】")
        print(response)
        # 如果返回结构支持，通常这里可以打印 response.output 之类的内容
    except Exception as e:
        print(f"【请求失败】: {e}")

def test_image_analysis():
    """测试 2: 图像识别 (Multimodal Image)"""
    print_separator("测试 2: 图像识别")
    try:
        response = client.responses.create(
            model=MODEL_NAME,
            input=[
                {
                    "role": "user",
                    "content": [
                        { "type": "input_text", "text": "这张图片里有什么？请用中文描述。" },
                        {
                            "type": "input_image",
                            "image_url": "https://lmg.jj20.com/up/allimg/1114/0G020114924/200G0114924-11-1200.jpg"
                        }
                    ]
                }
            ]
        )
        print("【请求成功】")
        print(response)
    except Exception as e:
        print(f"【请求失败】: {e}")

def test_file_analysis():
    """测试 3: 文件/PDF 分析 (Multimodal File)"""
    print_separator("测试 3: 文件分析")
    try:
        response = client.responses.create(
            model=MODEL_NAME,
            input=[
                {
                    "role": "user",
                    "content": [
                        { "type": "input_text", "text": "这个文件的主要内容是什么？" },
                        {
                            "type": "input_file",
                            "file_url": "https://www.berkshirehathaway.com/letters/2024ltr.pdf"
                        }
                    ]
                }
            ]
        )
        print("【请求成功】")
        print(response)
    except Exception as e:
        print(f"【请求失败】: {e}")

def test_web_search():
    """测试 4: 联网搜索 (Web Search Tool)"""
    print_separator("测试 4: 联网搜索")
    try:
        response = client.responses.create(
            model=MODEL_NAME,
            # 启用联网搜索工具
            tools=[{ "type": "web_search_preview" }],
            input="今天有什么正面的新闻报道吗？",
        )
        print("【请求成功】")
        print(response)
    except Exception as e:
        print(f"【请求失败】: {e}")

def test_function_calling():
    """测试 5: 函数调用 (Function Calling)"""
    print_separator("测试 5: 函数调用")

    # 定义工具列表
    tools = [
        {
            "type": "function",
            "name": "get_current_weather",
            "description": "获取指定地点的当前天气",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {
                        "type": "string",
                        "description": "城市和州，例如 San Francisco, CA",
                    },
                    "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]},
                },
                "required": ["location", "unit"],
            }
        }
    ]

    try:
        response = client.responses.create(
            model=MODEL_NAME,
            tools=tools,
            input="波士顿（Boston）现在的天气怎么样？",
            tool_choice="auto"
        )
        print("【请求成功】")
        print(response)
        # 注意：在真实的 Function Calling 场景中，
        # 你通常需要解析 response 中的 tool_calls，在本地执行函数，
        # 然后将结果传回给 AI。这里仅演示模型识别意图并返回函数调用请求。
    except Exception as e:
        print(f"【请求失败】: {e}")

if __name__ == "__main__":
    # 按顺序运行所有测试
    print("开始运行综合测试脚本...")

    test_basic_chat()
    test_image_analysis()
    test_file_analysis()
    test_web_search()
    test_function_calling()

    print_separator("测试结束")
