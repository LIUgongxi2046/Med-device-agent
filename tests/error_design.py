from pathlib import Path

from markdown_it import MarkdownIt


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
    return sorted(codes)
