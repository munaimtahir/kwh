from fastapi.testclient import TestClient
from orchestrator.main import app

client = TestClient(app)

def test_generate(mocker):
    mocker.patch("orchestrator.main.generator", return_value=[{"generated_text": "test code"}])
    mocker.patch("redis.Redis.get", return_value=None)
    mocker.patch("redis.Redis.set", return_value=None)
    response = client.post("/generate", json={"prompt": "test prompt"})
    assert response.status_code == 200
    assert response.json() == {"code": "test code"}

def test_execute(mocker):
    mock_container = mocker.Mock()
    mock_container.wait.return_value = {"StatusCode": 0}
    mock_container.logs.return_value = b"test results"
    mock_from_env = mocker.patch("docker.from_env")
    mock_from_env.return_value.containers.run.return_value = mock_container
    response = client.post("/execute", json={"code": "test code", "tests": "test tests"})
    assert response.status_code == 200
    assert response.json() == {"results": "test results"}

def test_commit(mocker):
    mock_repo = mocker.Mock()
    mock_repo.index.commit.return_value.hexsha = "test hash"
    mocker.patch("git.Repo", return_value=mock_repo)
    response = client.post("/commit", json={"message": "test message", "branch": "test-branch"})
    assert response.status_code == 200
    assert response.json() == {"commit_hash": "test hash"}
