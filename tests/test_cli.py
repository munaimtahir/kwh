from click.testing import CliRunner
from orchestrator.cli import cli

def test_generate(mocker):
    mocker.patch("httpx.post", return_value=mocker.Mock(json=lambda: {"code": "test code"}))
    runner = CliRunner()
    result = runner.invoke(cli, ["generate", "test prompt"])
    assert result.exit_code == 0
    assert "test code" in result.output

def test_execute(mocker):
    mocker.patch("httpx.post", return_value=mocker.Mock(json=lambda: {"results": "test results"}))
    runner = CliRunner()
    result = runner.invoke(cli, ["execute", "test code", "test tests"])
    assert result.exit_code == 0
    assert "test results" in result.output

def test_commit(mocker):
    mocker.patch("httpx.post", return_value=mocker.Mock(json=lambda: {"commit_hash": "test hash"}))
    runner = CliRunner()
    result = runner.invoke(cli, ["commit", "test message", "test-branch"])
    assert result.exit_code == 0
    assert "test hash" in result.output
