import json
import tempfile
import unittest
from pathlib import Path

from app.catalog import load_catalog, release_for, sha256


class CatalogTest(unittest.TestCase):
    def test_latest_release_wins(self):
        catalog = {"schemaVersion": 1, "releases": [
            {"app": "com.yukista.tutaua", "channel": "stable", "versionCode": 1},
            {"app": "com.yukista.tutaua", "channel": "stable", "versionCode": 3},
            {"app": "com.yukista.tutaua", "channel": "pilot", "versionCode": 9},
        ]}
        self.assertEqual(3, release_for("com.yukista.tutaua", "stable", catalog)["versionCode"])

    def test_catalog_schema_is_required(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "releases.json"
            path.write_text(json.dumps({"schemaVersion": 1, "releases": []}), encoding="utf-8")
            self.assertEqual([], load_catalog(path)["releases"])
            path.write_text("{}", encoding="utf-8")
            with self.assertRaises(RuntimeError): load_catalog(path)

    def test_sha256(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.apk"
            path.write_bytes(b"tutaua")
            self.assertEqual("a8844f6425200b7dda7016c995a044adf157b3f6e7d0e3db65f8dd490bbdcab5", sha256(path))


if __name__ == "__main__": unittest.main()
