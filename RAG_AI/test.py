import torch

# 1. 检查 PyTorch 版本
print("PyTorch 版本:", torch.__version__)

# 2. 检查 PyTorch 对应的 CUDA 版本（编译时的 CUDA 版本）
print("PyTorch 编译时 CUDA 版本:", torch.version.cuda)

# 3. 检查是否能调用 CUDA（关键！返回 True 则可用）
print("CUDA 是否可用:", torch.cuda.is_available())

# 4. 检查 GPU 数量和型号（验证硬件识别）
if torch.cuda.is_available():
    print("GPU 数量:", torch.cuda.device_count())
    print("GPU 型号:", torch.cuda.get_device_name(0))  # 0 表示第一个 GPU
    print("当前使用的 GPU:", torch.cuda.current_device())
else:
    print("未识别到 GPU，CUDA 不可用")