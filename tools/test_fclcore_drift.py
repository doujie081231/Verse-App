"""Regression checks for cross-platform drift hashing."""
import hashlib
from pathlib import Path
import tempfile
import unittest

import fclcore_drift


class DriftHashTest(unittest.TestCase):
    def test_text_line_endings_are_equivalent(self):
        path = Path("Example.java")
        self.assertEqual(fclcore_drift.normalized_bytes(path, b"class X {\r\n}\r\n"),
                         fclcore_drift.normalized_bytes(path, b"class X {\n}\n"))

    def test_binary_bytes_are_not_normalized(self):
        data = b"\x00\xff\r\n\x01"
        self.assertEqual(data, fclcore_drift.normalized_bytes(Path("icon.webp"), data))

    def test_content_changes_still_change_the_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Example.kt"
            path.write_bytes(b"val version = 1\r\n")
            before = fclcore_drift.sha256_of(path)
            path.write_bytes(b"val version = 2\n")
            self.assertNotEqual(before, fclcore_drift.sha256_of(path))
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), fclcore_drift.sha256_of(path))


if __name__ == "__main__":
    unittest.main()
