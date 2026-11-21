#!/usr/bin/env python3
"""为 horizon-prepare 准备一份"已安装 26.3" 的 .minecraft 目录（CI 用）。

上游 horizon-prepare 不会自己下载游戏，它只认启动器装好的目录结构；
ANDROID.md 也给了同样的做法：用 piston-meta 拼一份 ~/.minecraft 即可。

产出（默认 $HOME/.minecraft）：
  versions/26.3/26.3.json
  versions/26.3/26.3.jar
  libraries/<path>          （version json 里所有 artifact）
  assets/indexes/<id>.json
  assets/objects/<前2位>/<hash>
"""

import json
import os
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request, urlopen

VERSION = "26.3"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net"
WORKERS = 16


def get_json(url: str):
    req = Request(url, headers={"User-Agent": "verse-launcher-ci"})
    with urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 0:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = Request(url, headers={"User-Agent": "verse-launcher-ci"})
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urlopen(req, timeout=120) as resp, open(tmp, "wb") as out:
        while True:
            chunk = resp.read(1 << 16)
            if not chunk:
                break
            out.write(chunk)
    tmp.replace(dest)


def main() -> int:
    home = Path(sys.argv[1] if len(sys.argv) > 1 else Path.home() / ".minecraft")
    print(f"==> 目标目录: {home}")

    manifest = get_json(MANIFEST)
    entry = next(v for v in manifest["versions"] if v["id"] == VERSION)
    version = get_json(entry["url"])

    vdir = home / "versions" / VERSION
    vdir.mkdir(parents=True, exist_ok=True)
    (vdir / f"{VERSION}.json").write_text(json.dumps(version, ensure_ascii=False), encoding="utf-8")

    tasks = [(version["downloads"]["client"]["url"], vdir / f"{VERSION}.jar")]

    for lib in version.get("libraries", []):
        artifact = lib.get("downloads", {}).get("artifact")
        if artifact and artifact.get("url"):
            tasks.append((artifact["url"], home / "libraries" / artifact["path"]))

    assets_index = version.get("assetIndex")
    if assets_index:
        index_path = home / "assets" / "indexes" / f"{assets_index['id']}.json"
        index_path.parent.mkdir(parents=True, exist_ok=True)
        download(assets_index["url"], index_path)
        index = json.loads(index_path.read_text(encoding="utf-8"))
        for name, obj in index.get("objects", {}).items():
            h = obj["hash"]
            tasks.append((f"{RESOURCES}/{h[:2]}/{h}", home / "assets" / "objects" / h[:2] / h))

    print(f"==> 待下载 {len(tasks)} 个文件（库里 + 资源）")
    failures = []

    def run(task):
        url, dest = task
        try:
            download(url, dest)
        except Exception as exc:  # 单个失败不阻断，最后统一报告
            failures.append(f"{dest.name}: {exc}")

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        list(pool.map(run, tasks))

    if failures:
        print(f"!! 失败 {len(failures)} 个，示例：{failures[:5]}")
        return 1
    print("==> 完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
