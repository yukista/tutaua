import os
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse

from .catalog import load_catalog as read_catalog, release_for

DATA_DIR = Path(os.environ.get("TUTAUA_DATA_DIR", "/data"))
CATALOG = DATA_DIR / "releases.json"
ARTIFACTS = DATA_DIR / "artifacts"

app = FastAPI(title="Tutaua Backend", version="0.1.0", docs_url=None, redoc_url=None)


def load_catalog() -> dict[str, Any]:
    return read_catalog(CATALOG)


@app.get("/health")
def health() -> dict[str, Any]:
    catalog = load_catalog()
    return {"status": "ok", "schemaVersion": catalog["schemaVersion"]}


@app.get("/v1/releases")
def releases() -> dict[str, Any]:
    return load_catalog()


@app.get("/v1/releases/{app_id}/{channel}")
def latest_release(app_id: str, channel: str) -> dict[str, Any]:
    release = release_for(app_id, channel, load_catalog())
    if release is None:
        raise HTTPException(status_code=404, detail="release not found")
    return release


@app.get("/artifacts/{filename}")
def artifact(filename: str) -> FileResponse:
    if Path(filename).name != filename or not filename.endswith(".apk"):
        raise HTTPException(status_code=404, detail="artifact not found")
    path = ARTIFACTS / filename
    if not path.is_file():
        raise HTTPException(status_code=404, detail="artifact not found")
    return FileResponse(path, media_type="application/vnd.android.package-archive", filename=filename)
