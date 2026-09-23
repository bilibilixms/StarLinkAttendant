# -*- coding: utf-8 -*-
"""
RAG 精排（rerank）本地服务
==========================
对 (query, 候选文档) 逐对打分，按分数降序返回，用于检索后的精排阶段。
与 emb_server.py 同风格：FastAPI + 本地模型绝对路径（不访问 huggingface）。

模型：D:/模型下载目录/embedding/bge-reranker-v2-m3
（若目录为空，先运行 D:/模型下载目录/_download_rerank.py 下载）

接口：
  POST /rerank
  body: { "query": "上机管理", "documents": ["片段1", "片段2", ...] }
  resp: { "results": [ {"index":0, "score":0.98, "text":"..."}, ... ] }  按 score 降序

依赖（与 emb_server.py 同一环境）：
  pip install fastapi uvicorn FlagEmbedding

启动：
  python rerank_server.py        # 0.0.0.0:8002，与 Java 端 rag.rerank-url 默认值一致
"""

import os

MODEL_PATH = r"D:\模型下载目录\embedding\bge-reranker-v2-m3"

if not os.path.exists(os.path.join(MODEL_PATH, "config.json")):
    raise SystemExit(
        "未找到本地 rerank 模型: %s\n"
        "请先执行 D:\\模型下载目录\\_download_rerank.py 下载模型后再启动。" % MODEL_PATH
    )

from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

try:
    from FlagEmbedding import FlagReranker
except ImportError as e:
    raise SystemExit("缺少依赖 FlagEmbedding，请先执行: pip install FlagEmbedding，原始错误: %s" % e)

app = FastAPI(title="bge-reranker-v2-m3 rerank service")

# 使用本地模型绝对路径，不再访问 huggingface（与 emb_server.py 一致）
# use_fp16=True 加速（需 GPU 或较新 CPU）；显存不足时改成 use_fp16=False
reranker = FlagReranker(MODEL_PATH, use_fp16=True)


class RerankRequest(BaseModel):
    query: str
    documents: List[str]


@app.post("/rerank")
def rerank(req: RerankRequest):
    if not req.query or not req.documents:
        return {"error": "query and documents(list) are required"}
    pairs = [[req.query, doc] for doc in req.documents]
    # normalize=True → 分数归一到 0~1，便于和 RRF 分数比较/日志观察
    scores = reranker.compute_score(pairs, normalize=True)
    if isinstance(scores, float):  # 单条文档时 FlagEmbedding 可能返回 float
        scores = [scores]
    results = sorted(
        [{"index": i, "score": round(float(s), 4), "text": req.documents[i]} for i, s in enumerate(scores)],
        key=lambda x: x["score"],
        reverse=True,
    )
    return {"results": results}


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    # host=0.0.0.0 方便同局域网其他机器也来调；仅本机用可改 127.0.0.1
    uvicorn.run(app, host="0.0.0.0", port=8002)
