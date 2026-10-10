import collections
import hashlib
import json
from pathlib import Path
import re

from markdown_it import MarkdownIt


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / ".implementation-work/full-functional-20261003"


def rows(filename):
    document = ROOT / filename
    tokens = MarkdownIt("commonmark").enable("table").parse(document.read_text(encoding="utf-8"))
    result = []
    row = None
    for token in tokens:
        if token.type == "tr_open":
            row = {"source_ref": filename, "line": token.map[0] + 1, "cells": []}
        elif token.type == "inline" and row is not None:
            row["cells"].append(token.content)
        elif token.type == "tr_close":
            result.append(row)
            row = None
    return result


def index(values, expression, count):
    selected = {}
    for row in values:
        match = re.match(expression, row["cells"][0])
        if match is None:
            continue
        identity = match.group(0)
        if identity in selected:
            raise ValueError("需求或用例标识重复：" + identity)
        selected[identity] = row
    if len(selected) != count:
        raise ValueError("完整范围数量不一致：" + expression)
    return selected


def main():
    plan = rows("实施任务计划.md")
    design = rows("测试与评测方案.md")
    security = rows("安全检查方案.md")
    tasks = index(plan, r"IMP-\d{2}\b", 42)
    acceptances = index(plan, r"AC-\d{2}\b", 57)
    nonfunctional = index(plan, r"NFR-\d{2}\b", 17)
    tests = index(design, r"TEST-\d{2}\b", 57)
    ntests = index(design, r"NTEST-\d{2}\b", 17)
    edges = index(design, r"EDGE-\d{2}\b", 18)
    evals = index(design, r"EVAL-\d{2}\b", 12)
    security_tests = index(security, r"SEC-\d{2}\b", 30)
    matrix = []
    for identity, requirement in {**acceptances, **nonfunctional}.items():
        cells = requirement["cells"]
        implementation_column = 3 if identity.startswith("AC-") else 2
        owners = cells[implementation_column].split(",")
        if not set(owners) <= tasks.keys():
            raise ValueError("实施责任包含未知任务：" + identity)
        test_id = cells[-2]
        test = (tests if identity.startswith("AC-") else ntests)[test_id]
        if identity not in test["cells"][0]:
            raise ValueError("验收与独立用例未对应：" + identity)
        status = cells[-1]
        if status not in {"NOT_RUN", "BLOCKED", "PASS", "FAIL", "STALE"}:
            raise ValueError("验收状态无效：" + identity)
        matrix.append({"acceptance_id": identity, "requirement": requirement, "test_case": test,
                       "implementation_tasks": owners, "status": status,
                       "business_pass_evidence": [], "verification_scope": "REQUIREMENT_INVENTORY"})
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    sources = []
    for item in dataset["sources"]:
        source = (ROOT / item["source_ref"]).resolve(strict=True)
        if not source.is_relative_to(ROOT):
            raise ValueError("资料来源必须位于项目目录")
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != item["sha256"] or len(data) != item["size_bytes"]:
            raise ValueError("资料字节与登记版本不一致")
        sources.append(item)
    inputs = ["PRD.md", "实施任务计划.md", "测试与评测方案.md", "安全检查方案.md", "docs/implementation-inputs.md",
              "data-design/sample_dataset.json", "data-design/dataset.schema.json", "scripts/check_full_function_inventory.py"]
    result = {"schema_version": "1.0.0", "scope": "FULL_APPROVED_REQUIREMENT_INVENTORY",
              "business_execution_result": "INSUFFICIENT_EVIDENCE", "matrix": matrix,
              "edge_cases": edges, "eval_cases": evals, "security_cases": security_tests, "tasks": tasks,
              "dataset": {"dataset_version": dataset["dataset_version"], "schema_ref": "data-design/dataset.schema.json",
                          "source_ref": "data-design/sample_dataset.json", "sources": sources,
                          "records": dataset["records"], "golden_evals_status": "BLOCKED",
                          "golden_evals_reason": "缺少批准模型发布、真实业务项目、独立专家标注和批准阈值"},
              "counts": {"business_acceptances": len(acceptances), "nonfunctional_acceptances": len(nonfunctional),
                         "edge_cases": len(edges), "eval_cases": len(evals), "security_cases": len(security_tests),
                         "tasks": len(tasks), "acceptance_status": dict(collections.Counter(row["status"] for row in matrix)),
                         "task_status": dict(collections.Counter(row["cells"][-1] for row in tasks.values()))},
              "inputs": [{"path": name, "sha256": hashlib.sha256((ROOT / name).read_bytes()).hexdigest()} for name in inputs]}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "inventory.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
