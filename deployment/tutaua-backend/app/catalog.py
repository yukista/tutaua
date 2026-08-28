import hashlib
import json
from pathlib import Path
from typing import Any


def load_catalog(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError("release catalog unavailable") from error
    if data.get("schemaVersion") != 1 or not isinstance(data.get("releases"), list):
        raise RuntimeError("invalid release catalog")
    return data


def release_for(app_id: str, channel: str, catalog: dict[str, Any]) -> dict[str, Any] | None:
    matches = [item for item in catalog["releases"]
               if item.get("app") == app_id and item.get("channel") == channel]
    return max(matches, key=lambda item: int(item.get("versionCode", 0)), default=None)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
