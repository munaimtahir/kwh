import httpx
import click

@click.group()
def cli():
    pass

@cli.command()
@click.argument("prompt")
def generate(prompt):
    """Generate code from a prompt."""
    response = httpx.post("http://127.0.0.1:8000/generate", json={"prompt": prompt})
    click.echo(response.json()["code"])

@cli.command()
@click.argument("code")
@click.argument("tests")
def execute(code, tests):
    """Execute code or tests."""
    response = httpx.post("http://127.0.0.1:8000/execute", json={"code": code, "tests": tests})
    click.echo(response.json()["results"])

@cli.command()
@click.argument("message")
@click.argument("branch")
def commit(message, branch):
    """Commit code to a Git repository."""
    response = httpx.post("http://127.0.0.1:8000/commit", json={"message": message, "branch": branch})
    click.echo(response.json()["commit_hash"])

if __name__ == "__main__":
    cli()
