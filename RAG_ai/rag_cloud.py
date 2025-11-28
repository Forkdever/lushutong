# -*- coding: utf-8 -*-
import os
import sys

# =========================
# 【关键修复】设置 Hugging Face 国内镜像
# 必须在导入 sentence_transformers 之前设置
# =========================
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

import time
import json
import pickle
import requests
import numpy as np
from datetime import datetime
from typing import List, Dict, Any, Tuple
from flask import Flask, request, jsonify
from flask_cors import CORS

# =========================
# 依赖库导入检查
# =========================
try:
    from sentence_transformers import SentenceTransformer
except ImportError:
    print("错误: 未找到 sentence_transformers 库。请运行 pip install sentence-transformers")
    sys.exit(1)

try:
    import faiss
    FAISS_AVAILABLE = True
except ImportError:
    print("警告: 未找到 faiss 库，将无法加载向量索引。请运行 pip install faiss-cpu 或 faiss-gpu")
    FAISS_AVAILABLE = False

try:
    import torch
    TORCH_AVAILABLE = True
    TORCH_CUDA = torch.cuda.is_available()
except ImportError:
    TORCH_AVAILABLE = False
    TORCH_CUDA = False


# =========================
# 1) 基本配置（路径与模型）
# =========================
DB_FAISS_PATH = "vector_db/faiss_index"
DB_CHUNKS_PATH = "vector_db/text_chunks.pkl"
EMBEDDING_MODEL_NAME = "BAAI/bge-m3"

DEFAULT_TOP_K = int(os.getenv("TOP_K", "5"))
MAX_CONTEXT_CHARS = int(os.getenv("MAX_CONTEXT_CHARS", "4000"))

# =========================
# 2) Zetatechs API 配置
# =========================
ZETA_API_KEY = os.getenv("ZETA_API_KEY", "sk-EcTPdoioWuMvbkmyvh8jPYjwPd5bQ5wSXj3EDI1nbnYw6XTb").strip()
ZETA_BASE_URL = os.getenv("ZETA_API_BASE_URL", "https://api.zetatechs.com/v1").rstrip("/")
ZETA_MODEL = os.getenv("ZETA_MODEL", "gemini-3-pro-preview-flatfee")
REQUEST_TIMEOUT = int(os.getenv("REQUEST_TIMEOUT", "600"))

# =========================
# 3) 全局变量 
# =========================
index = None
chunks = None
embedding_model = None

# =========================
# 4) 设备选择与资源加载
# =========================
def pick_device() -> str:
    if TORCH_AVAILABLE and TORCH_CUDA:
        return "cuda"
    return "cpu"

def load_index_and_chunks() -> Tuple[Any, List[Dict[str, Any]]]:
    if not FAISS_AVAILABLE:
        raise RuntimeError("FAISS 库未安装，无法加载索引。")

    if not os.path.exists(DB_FAISS_PATH) or not os.path.exists(DB_CHUNKS_PATH):
        raise FileNotFoundError(f"向量数据库文件未找到，请确保 '{DB_FAISS_PATH}' 和 '{DB_CHUNKS_PATH}' 存在。")

    print("正在加载向量数据库和文本数据...")
    try:
        index_cpu = faiss.read_index(DB_FAISS_PATH)
    except Exception as e:
        raise RuntimeError(f"读取 FAISS 索引失败: {e}")

    try:
        with open(DB_CHUNKS_PATH, "rb") as f:
            chunks = pickle.load(f)
    except Exception as e:
        raise RuntimeError(f"读取文本块失败: {e}")

    index = index_cpu
    if hasattr(faiss, "StandardGpuResources"):
        try:
            res = faiss.StandardGpuResources()
            index = faiss.index_cpu_to_gpu(res, 0, index_cpu)
        except Exception:
            pass

    return index, chunks

def load_embedding_model(device: str) -> Any:
    print(f"正在加载 Embedding 模型: {EMBEDDING_MODEL_NAME} 到设备: {device} ...")
    print("提示: 如果下载速度慢，请确保已配置 HF_ENDPOINT 环境变量。")
    try:
        # 这里会自动使用 HF_ENDPOINT 指定的镜像站下载
        model = SentenceTransformer(EMBEDDING_MODEL_NAME, device=device)
    except Exception as e:
        raise RuntimeError(f"加载 Embedding 模型失败: {e}\n请检查网络连接或尝试手动下载模型。")
    return model

# =========================
# 5) 检索与生成核心函数
# =========================
def search(query: str, k: int = DEFAULT_TOP_K) -> List[Dict[str, Any]]:
    print(f"\n正在为您查询: '{query}'")
    query_vec = embedding_model.encode([query], normalize_embeddings=True)
    query_vec = np.array(query_vec, dtype="float32")

    distances, indices = index.search(query_vec, k)

    results = []
    if len(indices) == 0:
        return results

    for i in range(len(indices[0])):
        idx = int(indices[0][i])
        if idx < 0 or idx >= len(chunks):
            continue
        d = float(distances[0][i])
        approx_cos_sim = 1.0 - 0.5 * d
        results.append({
            "content": (chunks[idx].get("content") or "").strip(),
            "source": (chunks[idx].get("source") or "").strip(),
            "score": approx_cos_sim
        })
    return results

def build_context_snippets(retrieved: List[Dict[str, Any]], max_chars: int = MAX_CONTEXT_CHARS) -> str:
    if not retrieved:
        return ""
    sorted_items = sorted(retrieved, key=lambda x: x.get("score", 0.0), reverse=True)
    pieces = []
    total = 0
    for i, item in enumerate(sorted_items, start=1):
        content = item.get("content", "").strip()
        source = item.get("source", "").strip()
        block = f"[资料{i}] 来源: {source}\n{content}\n"
        if total + len(block) > max_chars:
            remain = max_chars - total
            if remain <= 0:
                break
            block = block[:max(0, remain)]
        pieces.append(block)
        total += len(block)
        if total >= max_chars:
            break
    return "\n---\n".join(pieces)

def build_messages(context: str, user_query: str) -> List[Dict[str, str]]:
    system_msg = (
        "你是一位专业的中文旅行规划助手。你的任务是根据用户的需求和提供的参考资料，生成一个详细的旅行计划。"
        "请直接使用 **Markdown** 格式输出，使其排版美观易读。"
        "要求："
        "1. 使用一级标题 (#) 作为旅行计划的总标题，标题中最好包含天数和目的地。"
        "2. 使用二级标题 (##) 区分每一天的行程（例如：## 第一天：抵达与探索）。"
        "3. 具体活动请使用列表或加粗字体，注明时间、地点和活动内容。"
        "4. 考虑到用户的具体出行日期，如果资料中有相关季节信息（如赏花、滑雪等），请优先推荐。"
    )
    user_msg = (
        f"用户需求：{user_query}\n\n"
        f"参考资料：\n{context if context else '【未检索到相关资料】'}\n\n"
        "请根据以上信息，为我生成一个详细的旅行计划。"
    )
    return [
        {"role": "system", "content": system_msg},
        {"role": "user", "content": user_msg}
    ]

def call_zeta_chat_completions(messages: List[Dict[str, str]], model: str = ZETA_MODEL, timeout: int = REQUEST_TIMEOUT) -> str:
    if not ZETA_API_KEY:
        raise RuntimeError("ZETA_API_KEY 未设置。")

    url = f"{ZETA_BASE_URL}/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {ZETA_API_KEY}"
    }
    data = {
        "model": model,
        "messages": messages,
        "temperature": 0.7,
        "top_p": 0.9,
        "max_tokens": 2000
    }

    try:
        resp = requests.post(url, headers=headers, json=data, timeout=timeout)
        resp.raise_for_status()
    except requests.exceptions.RequestException as e:
        raise RuntimeError(f"请求 Zetatechs API 失败: {e}")

    try:
        payload = resp.json()
        content = payload["choices"][0]["message"]["content"].strip()
        return content
    except Exception as e:
        raise RuntimeError(f"解析 Zetatechs 响应失败: {e}")

# =========================
# 6) Flask 服务与API接口
# =========================
app = Flask(__name__)
CORS(app)

def initialize_resources():
    global index, chunks, embedding_model
    
    device = pick_device()
    index, chunks = load_index_and_chunks()
    embedding_model = load_embedding_model(device)
    print("所有资源加载完毕，服务已准备就绪。")

def get_ai_trip_recommendation(departure: str, destination: str, start_date: str, end_date: str, preferences: List[str]) -> str:
    # 1. 计算天数
    try:
        d1 = datetime.strptime(start_date, "%Y-%m-%d")
        d2 = datetime.strptime(end_date, "%Y-%m-%d")
        delta = d2 - d1
        duration_days = delta.days + 1
        if duration_days <= 0:
            return "错误：结束日期必须晚于或等于开始日期。"
    except ValueError:
        return "错误：日期格式不正确，请使用 YYYY-MM-DD 格式。"

    # 2. 构建查询
    prefs_str = "、".join(preferences) if preferences else "无特殊偏好"
    user_query = (
        f"我想规划一次旅行。出发地：{departure}，目的地：{destination}。"
        f"时间是 {start_date} 到 {end_date}，共 {duration_days} 天。"
        f"我的旅行偏好是：{prefs_str}。"
        f"请为我生成一个详细的每日行程计划。"
    )
    
    # 3. 检索与生成
    retrieved_docs = search(user_query, k=DEFAULT_TOP_K)
    context = build_context_snippets(retrieved_docs)
    messages = build_messages(context, user_query)
    
    ai_response_text = call_zeta_chat_completions(messages)
    return ai_response_text

@app.route('/generate_trip_plan', methods=['POST'])
def generate_trip_plan_route():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "请求体为空或非JSON格式"}), 400

        departure = data.get('departure')
        destination = data.get('destination')
        start_date = data.get('startDate')
        end_date = data.get('endDate')
        preferences = data.get('preferences', [])

        if not all([departure, destination, start_date, end_date]):
            return jsonify({"error": "缺少必填参数: departure, destination, startDate, endDate"}), 400
        
        markdown_content = get_ai_trip_recommendation(departure, destination, start_date, end_date, preferences)

        return jsonify({
            "markdown": markdown_content
        })

    except FileNotFoundError as e:
        return jsonify({"error": str(e)}), 503
    except RuntimeError as e:
        return jsonify({"error": str(e)}), 500
    except Exception as e:
        return jsonify({"error": f"服务器内部错误: {str(e)}"}), 500

# =========================
# 7) 服务启动入口
# =========================
if __name__ == '__main__':
    try:
        initialize_resources()
    except Exception as e:
        print(f"资源初始化失败，服务无法启动: {e}")
        sys.exit(1)
    
    app.run(debug=True, host='0.0.0.0', port=5000)