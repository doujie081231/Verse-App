#!/usr/bin/env python3
"""FclCore 漂移校验。

FclCore 是 FCL 启动器源码的 vendored 副本，已与上游永久分叉（上游快照未入库，
因此无法在 CI 里做 diff）。这里用「文件哈希基线」代替：任何对 FclCore 源码的
新增 / 修改 / 删除都会被检测出来，逼迫改动方同时更新账本。

用法：
    python tools/fclcore_drift.py --check      # 校验（默认，CI 用）
    python tools/fclcore_drift.py --update     # 有意改动后刷新基线

基线文件：tools/fclcore-baseline.json
账本：FclCore/VERSE-CHANGES.md

退出码：0 = 与基线一致；1 = 有漂移；2 = 基线缺失或路径不可用。
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "FclCore" / "src"
BASELINE = ROOT / "tools" / "fclcore-baseline.json"

# 构建产物与 IDE 缓存不属于源码，不参与校验
EXCLUDED_DIR_NAMES = {"build", ".gradle", ".idea", "out"}
TEXT_SUFFIXES = {".java", ".kt", ".kts", ".xml", ".json", ".properties", ".txt", ".md"}


def normalized_bytes(path: Path, data: bytes) -> bytes:
    """Ignore checkout line endings for text; never rewrite binary content."""
    return data.replace(b"\r\n", b"\n") if path.suffix.lower() in TEXT_SUFFIXES else data


def iter_source_files():
    if not TARGET.is_dir():
        raise SystemExit(f"找不到 FclCore 源码目录：{TARGET}")
    for path in sorted(TARGET.rglob("*")):
        if not path.is_file():
            continue
        if EXCLUDED_DIR_NAMES & set(path.parts):
            continue
        yield path


def sha256_of(path: Path) -> str:
    return hashlib.sha256(normalized_bytes(path, path.read_bytes())).hexdigest()


def snapshot() -> dict:
    result = {}
    for path in iter_source_files():
        key = path.relative_to(ROOT).as_posix()
        result[key] = sha256_of(path)
    return result


def load_baseline() -> dict:
    if not BASELINE.is_file():
        raise SystemExit(
            "缺少基线文件 tools/fclcore-baseline.json，"
            "请先运行：python tools/fclcore_drift.py --update"
        )
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    files = data.get("files")
    if not isinstance(files, dict):
        raise SystemExit("基线文件格式不正确：缺少 files 字典")
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description="FclCore 源码漂移校验")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--check", action="store_true", help="校验当前源码与基线是否一致（默认）")
    group.add_argument("--update", action="store_true", help="重算并写入基线")
    args = parser.parse_args()

    current = snapshot()

    if args.update:
        payload = {
            "schemaVersion": 2,
            "textNormalization": "LF",
            "说明": "FclCore 源码哈希基线。有意改动后请更新本文件，并在 FclCore/VERSE-CHANGES.md 第 2 节记账。",
            "生成命令": "python tools/fclcore_drift.py --update",
            "files": current,
        }
        BASELINE.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(f"基线已更新：{len(current)} 个文件 -> {BASELINE.relative_to(ROOT)}")
        return 0

    baseline = load_baseline()
    added = sorted(set(current) - set(baseline))
    removed = sorted(set(baseline) - set(current))
    modified = sorted(k for k in set(current) & set(baseline) if current[k] != baseline[k])

    if not (added or removed or modified):
        print(f"FclCore 未漂移：{len(current)} 个文件与基线一致")
        return 0

    print("FclCore 与基线不一致：")
    for key in added:
        print(f"  新增  {key}")
    for key in modified:
        print(f"  修改  {key}")
    for key in removed:
        print(f"  删除  {key}")
    print()
    print(f"合计：新增 {len(added)} / 修改 {len(modified)} / 删除 {len(removed)}")
    print()
    print("如果这些改动是有意的：")
    print("  1) 在 FclCore/VERSE-CHANGES.md 第 2 节记账（改了什么 / 为什么 / 影响面）")
    print("  2) 更新同文件第 3 节的漂移数字")
    print("  3) 运行 python tools/fclcore_drift.py --update 刷新基线，并与代码一起提交")
    print()
    print("如果是误改：直接 revert 这些文件即可。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
