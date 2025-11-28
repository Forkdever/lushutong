# -*- coding: utf-8 -*-
import os
import sys
import time
import json
import pickle
import requests
import numpy as np
from typing import List, Dict, Any, Tuple  # 新增 Tuple

# 尝试导入 FAISS（GPU 版本优先）
# 注意：faiss-gpu 与 faiss-cpu 二选一安装，且与本机 CUDA 对应
try:
    import faiss  # 若安装的是 faiss-gpu，则此导入即含 GPU 支持
    FAISS_AVAILABLE = True
except Exception as e:
    print(f"导入 FAISS 失败，请确认已安装 faiss 或 faiss-gpu: {e}")
    FAISS_AVAILABLE = False

# 尝试导入 PyTorch 以判断是否可在 GPU 上运行 Embedding（可选）
try:
    import torch
    TORCH_AVAILABLE = True
    TORCH_CUDA = torch.cuda.is_available()
except Exception:
    TORCH_AVAILABLE = False
    TORCH_CUDA = False

from sentence_transformers import SentenceTransformer

# =========================
# 1) 基本配置（路径与模型）
# =========================
DB_FAISS_PATH = "vector_db/faiss_index"        # FAISS 索引路径
DB_CHUNKS_PATH = "vector_db/text_chunks.pkl"   # 文本块（含 content/source）
EMBEDDING_MODEL_NAME = "BAAI/bge-m3"           # 嵌入模型（多语言，适合中文检索）

# RAG / 检索参数
DEFAULT_TOP_K = int(os.getenv("TOP_K", "5"))
MAX_CONTEXT_CHARS = int(os.getenv("MAX_CONTEXT_CHARS", "4000"))  # 拼接上下文长度上限（按字符）

# =========================
# 2) Zetatechs API 配置
# =========================
# 强烈建议使用环境变量存放密钥，避免明文写入代码
ZETA_API_KEY = os.getenv("ZETA_API_KEY", "sk-EcTPdoioWuMvbkmyvh8jPYjwPd5bQ5wSXj3EDI1nbnYw6XTb").strip()
ZETA_BASE_URL = os.getenv("ZETA_API_BASE_URL", "https://api.zetatechs.com/v1").rstrip("/")
ZETA_MODEL = os.getenv("ZETA_MODEL", "gpt-5-chat-latest")
REQUEST_TIMEOUT = int(os.getenv("REQUEST_TIMEOUT", "30"))

# 若您坚持直接硬编码密钥，可在此处设置，但不推荐：
# ZETA_API_KEY = "sk-xxxxxxxx" # 不推荐，安全风险高

# =========================
# 3) 设备选择（Embedding）
# =========================
def pick_device() -> str:
    """
    选择 sentence-transformers 的设备。
    若 torch 可用且支持 CUDA，则使用 'cuda'
    否则使用 'cpu'
    """
    if TORCH_AVAILABLE and TORCH_CUDA:
        return "cuda"
    return "cpu"

# =========================
# 4) 加载 DB 与 模型
# =========================
def load_index_and_chunks() -> Tuple[Any, List[Dict[str, Any]]]:
    """
    读取 FAISS 索引和文本块；若 GPU 可用则尝试迁移索引到 GPU。
    返回: (faiss_index, chunks)
    """
    if not FAISS_AVAILABLE:
        raise RuntimeError("未检测到 FAISS，请先安装 faiss 或 faiss-gpu。")

    print("正在加载向量数据库和文本数据...")
    try:
        index_cpu = faiss.read_index(DB_FAISS_PATH)
    except Exception as e:
        raise RuntimeError(f"读取 FAISS 索引失败: {e}")

    try:
        with open(DB_CHUNKS_PATH, "rb") as f:
            chunks = pickle.load(f)
            if not isinstance(chunks, list):
                raise ValueError("text_chunks.pkl 格式异常，期望为 List[dict]")
    except Exception as e:
        raise RuntimeError(f"读取文本块失败: {e}")

    # 若可用，尝试将索引迁移到 GPU（device 0）
    index = index_cpu
    if hasattr(faiss, "StandardGpuResources"):
        try:
            print("检测到 FAISS GPU 支持，尝试将索引迁移到 GPU...")
            res = faiss.StandardGpuResources()  # 可根据需要配置临时内存等
            index = faiss.index_cpu_to_gpu(res, 0, index_cpu)  # 迁移到 GPU 0
            print("索引已迁移到 GPU。")
        except Exception as e:
            print(f"索引迁移到 GPU 失败，将在 CPU 上检索。原因: {e}")

    return index, chunks

def load_embedding_model() -> SentenceTransformer:
    """
    加载 SentenceTransformer 嵌入模型（bge-m3）。
    若 GPU 可用则放置到 CUDA 上。
    """
    device = pick_device()
    print(f"正在加载 Embedding 模型: {EMBEDDING_MODEL_NAME} 到设备: {device} ...")
    try:
        model = SentenceTransformer(EMBEDDING_MODEL_NAME, device=device)
    except Exception as e:
        raise RuntimeError(f"加载 Embedding 模型失败: {e}")
    print("Embedding 模型加载完成。")
    return model

# =========================
# 5) 检索函数
# =========================
def search(index, chunks: List[Dict[str, Any]], model: SentenceTransformer, query: str, k: int = DEFAULT_TOP_K) -> List[Dict[str, Any]]:
    """
    在向量数据库中检索最相关的 k 个文本块。
    返回: List[dict]，包含 content, source, score
    """
    print(f"\n正在为您查询: '{query}'")

    # 1) 将查询问题编码为向量（归一化以便余弦一致）
    query_vec = model.encode([query], normalize_embeddings=True)
    query_vec = np.array(query_vec, dtype="float32")

    # 2) 在 FAISS 索引中搜索
    # 注意：若索引度量为 L2，faiss 返回的是“平方 L2 距离”
    distances, indices = index.search(query_vec, k)

    # 3) 映射到原始文本块并计算近似相似度
    results = []
    if len(indices) == 0:
        return results

    for i in range(len(indices[0])):
        idx = int(indices[0][i])
        if idx < 0 or idx >= len(chunks):
            continue
        d = float(distances[0][i])  # 若为 L2，则为“平方 L2 距离”

        # 对于“归一化向量 + L2 距离”，余弦近似：cos ≈ 1 - d/2
        approx_cos_sim = 1.0 - 0.5 * d
        results.append({
            "content": (chunks[idx].get("content") or "").strip(),
            "source": (chunks[idx].get("source") or "").strip(),
            "score": approx_cos_sim
        })
    return results

# =========================
# 6) 构造 RAG 上下文与提示词
# =========================
def build_context_snippets(retrieved: List[Dict[str, Any]], max_chars: int = MAX_CONTEXT_CHARS) -> str:
    """
    将检索到的文本块按分数从高到低拼接为上下文，并控制整体长度。
    """
    if not retrieved:
        return ""
    sorted_items = sorted(retrieved, key=lambda x: x.get("score", 0.0), reverse=True)
    pieces = []
    total = 0
    for i, item in enumerate(sorted_items, start=1):
        content = item.get("content", "").strip()
        source = item.get("source", "").strip()
        block = f"[段落{i}] 来源: {source}\n{content}\n"
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
    """
    构造与 Zetatechs /v1/chat/completions 兼容的 messages。
    """
    system_msg = (
        "你是一名中文旅游问答助手。请仅依据提供的资料回答用户问题；"
        "若资料不足或未涵盖，请明确说明“资料未涵盖该问题”。"
        "回答应简洁准确，并在答案末尾列出引用来源（如有）。"
    )
    user_msg = (
        f"用户问题：{user_query}\n\n"
        f"以下是检索到的资料（可能不完整）：\n"
        f"{context if context else '【未检索到相关资料】'}\n\n"
        "请依据这些资料进行回答。若资料未涵盖，请说明不足。"
    )
    return [
        {"role": "system", "content": system_msg},
        {"role": "user", "content": user_msg}
    ]

# =========================
# 7) 调用 Zetatechs API 生成答案
# =========================
def call_zeta_chat_completions(messages: List[Dict[str, str]], model: str = ZETA_MODEL, timeout: int = REQUEST_TIMEOUT) -> str:
    """
    使用 Zetatechs 的 /v1/chat/completions 生成答案。
    要求设置环境变量 ZETA_API_KEY。
    """
    if not ZETA_API_KEY:
        raise RuntimeError("未设置 ZETA_API_KEY 环境变量，请设置 API 密钥后重试。")

    url = f"{ZETA_BASE_URL}/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {ZETA_API_KEY}"
    }
    data = {
        "model": model,
        "messages": messages,
        # 常用参数（不同提供方支持集可能不同，尽量使用兼容字段）
        "temperature": 0.2,
        "top_p": 0.9,
        "max_tokens": 800
    }

    try:
        resp = requests.post(url, headers=headers, json=data, timeout=timeout)
        resp.raise_for_status()
    except requests.exceptions.RequestException as e:
        raise RuntimeError(f"请求 Zetatechs API 失败: {e}")

    try:
        payload = resp.json()
    except json.JSONDecodeError:
        raise RuntimeError(f"Zetatechs API 返回非 JSON：{resp.text[:200]}...")

    # 兼容 OpenAI 风格的响应解析
    try:
        choices = payload.get("choices") or []
        if not choices:
            raise KeyError("choices 为空")
        content = choices[0]["message"]["content"]
        return (content or "").strip()
    except Exception as e:
        # 打印部分返回，便于排障
        raise RuntimeError(f"解析 Zetatechs 响应失败: {e}，返回: {json.dumps(payload)[:400]}")

# =========================
# 8) 一次完整查询并输出
# =========================
def run_once(user_query: str, k: int = DEFAULT_TOP_K):
    # 加载资源
    index, chunks = load_index_and_chunks()
    model = load_embedding_model()

    # 检索
    retrieved = search(index, chunks, model, user_query, k=k)

    # 打印检索结果预览
    print("\n--- 检索到的相关信息 ---")
    if not retrieved:
        print("未检索到相关资料。")
    else:
        for i, r in enumerate(retrieved, start=1):
            print(f"\n【结果 {i} | 来源: {r.get('source','')} | 相似度(近似): {r.get('score',0.0):.4f}】")
            print(r.get("content",""))
        print("\n----------------------")

    # 构造上下文与消息
    context = build_context_snippets(retrieved, max_chars=MAX_CONTEXT_CHARS)
    messages = build_messages(context, user_query)

    # 调用 AI
    print("\n正在调用 Zetatechs API 生成答案，请稍候...")
    answer = call_zeta_chat_completions(messages)

    # 输出答案
    print("\n=== AI 回答 ===")
    print(answer)

    # 输出引用来源（去重）
    sources = []
    for r in retrieved:
        s = (r.get("source") or "").strip()
        if s:
            sources.append(s)
    if sources:
        seen = set()
        uniq = []
        for s in sources:
            if s not in seen:
                uniq.append(s)
                seen.add(s)
        print("\n=== 引用来源 ===")
        for i, s in enumerate(uniq, start=1):
            print(f"[{i}] {s}")
    else:
        print("\n未提供引用来源或资料为空。")

# =========================
# 9) CLI 入口
# =========================
def main():
    if not os.path.exists(DB_FAISS_PATH):
        print(f"未找到 FAISS 索引文件: {DB_FAISS_PATH}")
        sys.exit(1)
    if not os.path.exists(DB_CHUNKS_PATH):
        print(f"未找到文本块文件: {DB_CHUNKS_PATH}")
        sys.exit(1)

    try:
        user_query = input("请输入您的问题：").strip()
    except Exception:
        print("读取输入失败。")
        sys.exit(1)

    if not user_query:
        print("问题为空，已退出。")
        sys.exit(0)

    # 可通过命令行指定 Top-k：python rag_zetatechs.py 8
    top_k = DEFAULT_TOP_K
    if len(sys.argv) >= 2:
        try:
            top_k = int(sys.argv[1])
        except Exception:
            pass

    run_once(user_query, k=top_k)

if __name__ == "__main__":
    main()