import requests
import json

# API 接口地址
url = "https://api.zetatechs.com/v1/chat/completions"

# 请求头（包含认证信息和数据格式）
headers = {
    "Content-Type": "application/json",
    "Authorization": "Bearer sk-EcTPdoioWuMvbkmyvh8jPYjwPd5bQ5wSXj3EDI1nbnYw6XTb"  # 直接使用提供的 API 密钥
}

# 提示用户输入问题
user_question = input("请输入你的问题: ")

# 请求体数据（对话内容和模型参数）
data = {
    "model": "gpt-5-chat-latest",
    "messages": [
        {"role": "system", "content": "你是一个有帮助的助手。"},
        {"role": "user", "content": "你好吗？"} # 将用户输入的内容放入请求体
    ]
}

try:
    # 发送 POST 请求
    response = requests.post(url, json=data, headers=headers)
    response.raise_for_status() # 如果请求失败 (例如状态码为 4xx 或 5xx)，则会抛出异常

    # 解析 JSON 响应
    response_data = response.json()

    # 提取 AI 的纯文本回答
    ai_answer = response_data['choices'][0]['message']['content']

    # 打印纯文本回答
    print("\nAI 的回答：")
    print(ai_answer)

except requests.exceptions.RequestException as e:
    print(f"请求出错：{e}")
    # 如果服务器返回了错误信息，我们也打印出来
    if response is not None:
        print(f"服务器响应: {response.text}")
except (KeyError, IndexError) as e:
    print(f"解析响应失败，请检查 API 返回格式: {e}")
    print(f"原始响应内容: {response.text}")