import graphlib
import hashlib
from importlib.metadata import version
import json
from pathlib import Path
import platform
import sys
import unittest

from jsonschema import Draft202012Validator, FormatChecker
from markdown_it import MarkdownIt


ROOT = Path(__file__).resolve().parents[1]


class ImplementationInputChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dataset = json.loads(
            (ROOT / "data-design/sample_dataset.json").read_text(encoding="utf-8")
        )
        cls.schema = json.loads(
            (ROOT / "data-design/dataset.schema.json").read_text(encoding="utf-8")
        )
        print(json.dumps({
            "python_executable": sys.executable,
            "python_version": platform.python_version(),
            "platform": platform.platform(),
            "libraries": {
                name: version(name) for name in ("jsonschema", "markdown-it-py")
            },
            "dataset_version": cls.dataset["dataset_version"],
            "sources": len(cls.dataset["sources"]),
            "records": len(cls.dataset["records"]),
        }, ensure_ascii=False), flush=True)

    def test_existing_dataset_matches_its_schema(self):
        Draft202012Validator.check_schema(self.schema)
        Draft202012Validator(
            self.schema, format_checker=FormatChecker()
        ).validate(self.dataset)

    def test_registered_original_bytes_match_version_and_size(self):
        for source in self.dataset["sources"]:
            with self.subTest(source_id=source["source_id"]):
                path = (ROOT / source["source_ref"]).resolve(strict=True)
                self.assertTrue(path.is_relative_to(ROOT))
                data = path.read_bytes()
                self.assertEqual(hashlib.sha256(data).hexdigest(), source["sha256"])
                self.assertEqual(len(data), source["size_bytes"])

    def test_source_relationships_and_quote_versions_remain_consistent(self):
        sources = {item["source_id"]: item for item in self.dataset["sources"]}
        self.assertEqual(len(sources), len(self.dataset["sources"]))
        graph = {}
        for source_id, source in sources.items():
            parent = source["parent_source_id"]
            if parent is not None:
                self.assertIn(parent, sources)
                self.assertNotEqual(parent, source_id)
            graph[source_id] = [parent] if parent else []
        list(graphlib.TopologicalSorter(graph).static_order())
        records = self.dataset["records"]
        self.assertEqual(len({item["record_id"] for item in records}), len(records))
        for record in records:
            with self.subTest(record_id=record["record_id"]):
                source = sources[record["source_id"]]
                self.assertEqual(record["source_version"], source["sha256"])
                self.assertEqual(
                    hashlib.sha256(record["text"].encode("utf-8")).hexdigest(),
                    record["quote_hash"],
                )

    def test_local_review_authorization_has_not_become_production_authorization(self):
        for source in self.dataset["sources"]:
            with self.subTest(source_id=source["source_id"]):
                self.assertEqual(source["production_status"], "PENDING_APPROVAL")
                self.assertTrue(source["license"])
                self.assertTrue(source["retention"])
                self.assertTrue(source["allowed_use"])
                self.assertLessEqual(
                    set(source["allowed_use"]),
                    {"LOCAL_DESIGN_REVIEW", "LOCAL_PARSER_CHECK"},
                )
        for record in self.dataset["records"]:
            self.assertIs(record["formal_evidence_eligible"], False)

    def test_started_tasks_have_unique_resolvable_development_record_links(self):
        # 索引需要指向当前记录，保证后续开发能够读取相同任务依据。
        expected = []
        cells = None
        for token in MarkdownIt("commonmark").enable("table").parse((ROOT / "实施任务计划.md").read_text(encoding="utf-8")):
            if token.type == "tr_open":
                cells = []
            elif token.type == "inline" and cells is not None:
                cells.append(token.content)
            elif token.type == "tr_close":
                if len(cells) == 7 and cells[0].startswith("IMP-") and cells[-1] in {"IN_PROGRESS", "IMPLEMENTED", "ACCEPTED"}:
                    expected.append(cells[0] + ".md")
                cells = None
        self.assertTrue(expected)
        index = ROOT / "docs/development/index.md"
        targets = []
        indexed_tasks = []
        cells = None
        links = None
        for token in MarkdownIt("commonmark").enable("table").parse(index.read_text(encoding="utf-8")):
            if token.type == "tr_open":
                cells = []
                links = []
            elif token.type == "inline" and cells is not None:
                cells.append(token.content)
                links.append([child.attrGet("href") for child in token.children or [] if child.type == "link_open"])
            elif token.type == "tr_close":
                if cells and cells[0].startswith("IMP-"):
                    self.assertEqual(len(cells), 5)
                    task = cells[0]
                    self.assertNotIn(task, indexed_tasks)
                    indexed_tasks.append(task)
                    if task + ".md" in expected:
                        self.assertEqual(links[-1], [task + ".md"])
                        self.assertTrue((index.parent / links[-1][0]).is_file())
                        targets.append(links[-1][0])
                cells = None
                links = None
            for child in token.children or []:
                if child.type == "link_open":
                    href = child.attrGet("href")
                    if href in expected:
                        self.assertTrue((index.parent / href).is_file())
        self.assertCountEqual(targets, expected)


if __name__ == "__main__":
    unittest.main(verbosity=2)
