import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path


def output(command: list[str]) -> str:
    return subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE).stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--channel", choices=["internal", "beta", "stable"], required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("apks", nargs="+")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    releases = []
    for raw_path in args.apks:
        apk = Path(raw_path)
        badging = output([args.aapt, "dump", "badging", str(apk)]).splitlines()[0]
        metadata = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
        if not metadata: raise SystemExit(f"cannot read metadata from {apk}")
        certificate_output = output([args.apksigner, "verify", "--print-certs", str(apk)])
        certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})", certificate_output)
        if not certificate: raise SystemExit(f"cannot read certificate from {apk}")
        app_id, version_code, version_name = metadata.groups()
        filename = f"{app_id}-{version_code}-{version_name}.apk".replace("/", "_")
        destination = args.output / filename
        shutil.copyfile(apk, destination)
        releases.append({
            "applicationId": app_id, "channel": args.channel, "versionCode": int(version_code),
            "versionName": version_name, "filename": filename,
            "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
            "certificateSha256": certificate.group(1).lower(),
        })
    (args.output / "fleet-releases.json").write_text(json.dumps({"releases": releases}, indent=2), encoding="utf-8")


if __name__ == "__main__": main()
