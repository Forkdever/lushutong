import os
import json  # 新增：用于修改config.json
import fitz  # PyMuPDF
import pickle
import faiss
import numpy as np
from sentence_transformers import SentenceTransformer
from langchain.text_splitter import RecursiveCharacterTextSplitter
from typing import List
from modelscope.hub.snapshot_download import snapshot_download

# --- 1. 配置参数 ---
PDF_FOLDER_PATH = "travel_guides_pdf"
DB_FAISS_PATH = "vector_db/faiss_index"
DB_CHUNKS_PATH = "vector_db/text_chunks.pkl"

# --- 模型加载核心逻辑（修复config.json + 本地加载） ---
MODEL_ID = "BAAI/bge-m3"
LOCAL_MODEL_PATH = "/root/autodl-tmp/models/bge-m3"
# 关键：指向模型文件实际所在的子目录（ModelScope下载后会多一层目录，需调整）
ACTUAL_MODEL_PATH = os.path.join(LOCAL_MODEL_PATH, MODEL_ID)

# 1. 从ModelScope下载模型（首次运行下载，后续跳过）
print(f"检查模型目录：{ACTUAL_MODEL_PATH}")
if not os.path.exists(os.path.join(ACTUAL_MODEL_PATH, "config.json")):
    print("本地无模型，开始从ModelScope下载...")
    snapshot_download(
        model_id=MODEL_ID,
        cache_dir=LOCAL_MODEL_PATH,  # 父目录
        revision="master",
        ignore_file_pattern=["*.bin.index"]  # 跳过无关小文件
    )
else:
    print("本地已存在模型，跳过下载...")

# 2. 自动修复config.json：添加"model_type": "bert"（关键步骤）
config_path = os.path.join(ACTUAL_MODEL_PATH, "config.json")
with open(config_path, "r", encoding="utf-8") as f:
    config = json.load(f)

# 若缺少model_type字段，手动添加（bge-m3属于bert类）
if "model_type" not in config:
    config["model_type"] = "bert"
    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)
    print("已为config.json添加model_type: bert")
else:
    print("config.json已包含model_type，无需修改...")

# 3. 加载修复后的本地模型（全局唯一实例，后续直接使用）
EMBEDDING_MODEL_NAME = SentenceTransformer(ACTUAL_MODEL_PATH)
print(f"模型 '{MODEL_ID}' 加载成功！\n")

# --- 确保保存路径存在 ---
os.makedirs("vector_db", exist_ok=True)

# --- 2. 实现各个功能函数 ---

def extract_text_from_pdfs(folder_path: str) -> List[dict]:
    """
    遍历文件夹中的所有PDF文件，提取文本内容和元数据。
    """
    documents = []
    print(f"开始从 '{folder_path}' 文件夹中提取PDF文本...")
    for filename in os.listdir(folder_path):
        if filename.lower().endswith(".pdf"):
            file_path = os.path.join(folder_path, filename)
            try:
                with fitz.open(file_path) as doc:
                    full_text = ""
                    for page in doc:
                        full_text += page.get_text()

                    documents.append({
                        "content": full_text,
                        "source": filename
                    })
                    print(f"  - 成功提取: {filename}")
            except Exception as e:
                print(f"  - [错误] 无法处理文件 {filename}: {e}")
    print("所有PDF文本提取完成。\n")
    return documents


def split_text_chunks(documents: List[dict]) -> List[dict]:
    """
    根据自然边界对提取的文本进行智能切分。
    """
    print("开始进行文本智能切分...")
    text_splitter = RecursiveCharacterTextSplitter(
        separators=["\n\n", "\n", "。", "！", "？", "，", "、", ""],
        chunk_size=1200,
        chunk_overlap=180,
        length_function=len,
        is_separator_regex=False,
    )

    all_chunks = []
    for doc in documents:
        chunks = text_splitter.split_text(doc['content'])
        for chunk in chunks:
            all_chunks.append({
                "content": chunk,
                "source": doc['source']
            })
    print(f"切分完成，共得到 {len(all_chunks)} 个文本块。\n")
    return all_chunks


def build_and_save_vector_db(chunks: List[dict]):
    """
    使用指定的Embedding模型为文本块生成向量，并构建FAISS索引库（优化版，优先使用GPU）。
    """
    # --- 1. 检查GPU并移动模型到指定设备（核心修改：直接使用全局模型，无需重复初始化） ---
    device = 'cuda' if faiss.get_num_gpus() > 0 else 'cpu'
    print(f"检测到可用设备: {device.upper()}")

    print(f"开始将Embedding模型 '{MODEL_ID}' 移动到 {device.upper()}...")
    # 直接使用全局已加载的模型，调用to()方法移动到目标设备
    model = EMBEDDING_MODEL_NAME.to(device)
    print(f"模型 '{MODEL_ID}' 已成功移动到 {device.upper()}。\n")

    # --- 2. 提取文本并在GPU上批量编码 ---
    contents = [chunk['content'] for chunk in chunks]

    print("开始在GPU上为文本块生成向量...")
    # 批量编码，效率更高。GPU可以处理更大的batch_size。
    embeddings = model.encode(
        contents,
        batch_size=64,  # 增加了batch_size以利用GPU性能
        show_progress_bar=True,
        normalize_embeddings=True
        # 无需重复传device：模型已在目标设备上，encode会自动使用模型所在设备
    )

    # 将生成的向量从GPU内存转移到CPU内存，并转换为FAISS要求的float32类型
    embeddings = np.array(embeddings, dtype='float32')

    print("\n向量生成完毕，开始构建FAISS索引...")

    # --- 3. 构建FAISS索引（使用GPU）---
    d = embeddings.shape[1]

    # 如果有GPU，我们可以创建一个使用GPU的索引
    if faiss.get_num_gpus() > 0:
        print("使用GPU构建FAISS索引...")
        # 创建一个在GPU上的Flat索引
        index = faiss.index_factory(d, "Flat", faiss.METRIC_L2)
        gpu_index = faiss.index_cpu_to_all_gpus(index)
        gpu_index.add(embeddings)
        print(f"FAISS GPU索引构建完成，共包含 {gpu_index.ntotal} 个向量。")

        # 在保存之前，需要将索引从GPU移回CPU
        index = faiss.index_gpu_to_cpu(gpu_index)
    else:
        # 如果没有GPU，则使用CPU索引
        print("未检测到GPU，使用CPU构建FAISS索引...")
        index = faiss.IndexFlatL2(d)
        index.add(embeddings)
        print(f"FAISS CPU索引构建完成，共包含 {index.ntotal} 个向量。")

    # --- 4. 保存索引和文本块 ---
    faiss.write_index(index, DB_FAISS_PATH)
    with open(DB_CHUNKS_PATH, "wb") as f:
        pickle.dump(chunks, f)

    print(f"向量数据库已成功保存至 '{DB_FAISS_PATH}' 和 '{DB_CHUNKS_PATH}'。")


# --- 3. 主执行流程 ---

def main_build():
    """主函数，执行数据处理和建库全流程"""
    # 第一步：读取PDF文件并进行文本提取
    documents = extract_text_from_pdfs(PDF_FOLDER_PATH)
    if not documents:
        print("指定文件夹下未找到PDF文件，程序退出。")
        return

    # 第二步：内容切分
    chunks = split_text_chunks(documents)

    # 第三步：生成向量，向量存储，生成可供AI检索的数据库
    build_and_save_vector_db(chunks)


if __name__ == '__main__':
    main_build()