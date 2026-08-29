import argparse
import json
import subprocess
from pathlib import Path


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--project", default="/opt/docker/tutaua-fleet")
    parser.add_argument("--container", default="tutaua-fleet-control-1")
    args = parser.parse_args()
    catalog = json.loads((args.directory / "fleet-releases.json").read_text(encoding="utf-8"))
    for release in catalog["releases"]:
        source = args.directory / release["filename"]
        incoming = "/data/artifacts/incoming-" + release["filename"]
        run(["docker", "cp", str(source), f"{args.container}:{incoming}"])
        run(["docker", "compose", "-f", f"{args.project}/compose.yaml", "exec", "-T", "control",
             "python", "-m", "control.admin", "publish-release", release["applicationId"],
             release["channel"], str(release["versionCode"]), release["versionName"], incoming,
             release["certificateSha256"]])


if __name__ == "__main__": main()
