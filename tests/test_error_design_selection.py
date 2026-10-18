from pathlib import Path
import tempfile
import unittest

from error_design import published_error_codes, published_selection_error_statuses


ROOT = Path(__file__).resolve().parents[1]


class SelectionPublishedErrorDesignTests(unittest.TestCase):
    def test_original_published_selection_statuses_are_covered(self):
        self.assertEqual(published_selection_error_statuses(ROOT), {
            "SELECTION_STALE": 409, "SELECTION_INVALID": 422})
        codes = published_error_codes(ROOT)
        self.assertEqual(codes, sorted(set(codes)))
        self.assertIn("SELECTION_STALE", codes)
        self.assertIn("SELECTION_INVALID", codes)
        self.assertIn("SCHEMA_INVALID", codes)
        self.assertIn("ACCESS_DENIED", codes)

    def test_missing_conflicting_or_duplicate_published_mapping_is_not_silently_accepted(self):
        original = (ROOT / "backend-design/接口补充设计.md").read_text()
        paragraph = next(line for line in original.splitlines() if line.startswith("Selection 包含固定"))
        cases = [original.replace("409 SELECTION_STALE", "422 SELECTION_STALE"),
                 original.replace("422 SELECTION_INVALID", "422 OTHER_INVALID"),
                 original + "\n\n" + paragraph + "\n"]
        for content in cases:
            with self.subTest(content_length=len(content)), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                (root / "backend-design").mkdir()
                (root / "backend-design/接口补充设计.md").write_text(content)
                with self.assertRaises(ValueError):
                    published_selection_error_statuses(root)


if __name__ == "__main__":
    unittest.main()
