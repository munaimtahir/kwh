from fastapi import FastAPI
from pydantic import BaseModel
from transformers import pipeline
import redis

app = FastAPI()
cache = redis.Redis(host="localhost", port=6379, db=0)
generator = pipeline("text-generation", model="distilgpt2")

class GenerateRequest(BaseModel):
    prompt: str

class GenerateResponse(BaseModel):
    code: str

class ExecuteRequest(BaseModel):
    code: str
    tests: str

class ExecuteResponse(BaseModel):
    results: str

class CommitRequest(BaseModel):
    message: str
    branch: str

class CommitResponse(BaseModel):
    commit_hash: str

@app.post("/generate", response_model=GenerateResponse)
async def generate(request: GenerateRequest):
    cached_code = cache.get(request.prompt)
    if cached_code:
        return GenerateResponse(code=cached_code.decode("utf-8"))

    generated_text = generator(request.prompt, max_length=100, num_return_sequences=1)[0]["generated_text"]
    cache.set(request.prompt, generated_text)
    return GenerateResponse(code=generated_text)

import docker

@app.post("/execute", response_model=ExecuteResponse)
async def execute(request: ExecuteRequest):
    client = docker.from_env()
    container = client.containers.run(
        "python:3.10-slim",
        command=f"echo '{request.code}' > code.py && echo '{request.tests}' > tests.py && python -m pytest tests.py",
        mem_limit="512m",
        cpu_quota=100000,
        detach=True,
    )
    result = container.wait()
    logs = container.logs().decode("utf-8")
    container.remove()
    return ExecuteResponse(results=logs)

import git
import logging
from datetime import datetime

logging.basicConfig(filename='logs/git_ops.log', level=logging.INFO)

@app.post("/commit", response_model=CommitResponse)
async def commit(request: CommitRequest):
    repo = git.Repo(".")
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    branch_name = f"auto-ai/{timestamp}"
    new_branch = repo.create_head(branch_name)
    new_branch.checkout()
    repo.git.add(A=True)
    commit = repo.index.commit(request.message, gpg_sign=True)
    logging.info(f"Created branch {branch_name} and commit {commit.hexsha}")
    return CommitResponse(commit_hash=commit.hexsha)
