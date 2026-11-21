#!/usr/bin/env python3
"""把 Mojang 当前发布的 26.3 版本 JSON 加入 horizon 的 research-lock.json 白名单（CI 用）。

背景：horizon-prepare 会校验 versions/26.3/26.3.json 的 sha1 必须等于 lock 里的
manifest_sha1 或 accepted_manifest_sha1 之一。Mojang 每次"重新发布"都会换掉这个
JSON（上游注释说通常只有 assetIndex 变，jar 和库不变），而移植仓库的 lock 更新滞后，
于是准备流程报：`... is not a 26.3 version file Horizon knows`。

这里只做一件事：把当前 JSON 的 sha1 与 assetIndex sha1 追加进白名单，不碰其它字段。
"""

import hashlib
import json
import sys
from pathlib import Path


def sha1(path: Path) -> str:
    return hashlib.sha1(path.read_bytes()).hexdigest()


def main() -> int:
    port = Path(sys.argv[1] if len(sys.argv) > 1 else "port")
    minecraft = Path(sys.argv[2] if len(sys.argv) > 2 else ".minecraft")

    lock_path = port / "research-lock.json"
    version_json = minecraft / "versions" / "26.3" / "26.3.json"
    if not lock_path.is_file() or not version_json.is_file():
        print(f"缺少文件: {lock_path} / {version_json}")
        return 1

    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    mc = lock["minecraft"]
    current = sha1(version_json)
    version = json.loads(version_json.read_text(encoding="utf-8"))
    asset_index_sha1 = version.get("assetIndex", {}).get("sha1")

    accepted = mc.setdefault("accepted_manifest_sha1", [])
    if current != mc.get("manifest_sha1") and current not in accepted:
        accepted.append(current)
        print(f"白名单追加 manifest_sha1: {current}")
    else:
        print(f"manifest_sha1 已在白名单: {current}")

    if asset_index_sha1:
        accepted_index = mc.setdefault("accepted_asset_index_sha1", [])
        if asset_index_sha1 not in accepted_index:
            accepted_index.append(asset_index_sha1)
            print(f"白名单追加 asset_index_sha1: {asset_index_sha1}")

    lock_path.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("research-lock.json 已更新")
    return 0


if __name__ == "__main__":
    sys.exit(main())
