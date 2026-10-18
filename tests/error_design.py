from pathlib import Path
import re

from markdown_it import MarkdownIt


def published_selection_error_statuses(root: Path) -> dict[str, int]:
    """Read the two already-published Selection errors without changing frozen schemas."""
    source = root / "backend-design/接口补充设计.md"
    paragraphs = [token.content for token in MarkdownIt("commonmark").parse(source.read_text(encoding="utf-8"))
                  if token.type == "inline" and token.content.startswith("Selection 包含固定")]
    if len(paragraphs) != 1:
        raise ValueError("Selection 错误设计段落必须唯一")
    pairs = re.findall(r"\b(409|422)\s+(SELECTION_[A-Z_]+)\b", paragraphs[0])
    expected = {("409", "SELECTION_STALE"), ("422", "SELECTION_INVALID")}
    if len(pairs) != 2 or set(pairs) != expected:
        raise ValueError("Selection 错误代码与 HTTP 状态定义不一致")
    return {code: int(status) for status, code in pairs}


def published_error_codes(root: Path) -> list[str]:
    tokens = MarkdownIt("commonmark").enable("table").parse((root / "LLD-后端.md").read_text(encoding="utf-8"))
    starts = [index for index, token in enumerate(tokens) if token.type == "heading_open" and tokens[index + 1].content.startswith("4.10 ")]
    if len(starts) != 1:
        raise ValueError("错误设计章节必须唯一")
    start = starts[0]
    end = next(index for index in range(start + 1, len(tokens)) if tokens[index].type == "heading_open" and tokens[index].tag == tokens[start].tag)
    codes = []
    column = 0
    rows = 0
    for token in tokens[start:end]:
        if token.type == "tr_open":
            column = 0
        elif token.type == "td_open":
            column += 1
            if column == 1:
                rows += 1
        elif token.type == "inline" and column == 1:
            for child in token.children:
                if child.type == "code_inline":
                    codes.extend(child.content.split("/"))
        elif token.type == "tr_close":
            column = 0
    if rows != 25 or len(codes) != len(set(codes)):
        raise ValueError("错误设计表格范围或代码唯一性不一致")
    selection = published_selection_error_statuses(root)
    if set(codes).intersection(selection):
        raise ValueError("Selection 错误代码不能重复登记")
    codes.extend(selection)
    return sorted(codes)
