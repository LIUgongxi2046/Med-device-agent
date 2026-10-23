import json
import re
import subprocess
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent.parent
    runtime = root / ".implementation-work/runtime"
    deployment = json.loads((runtime / "application-deployment.json").read_text())
    current = (root / deployment["artifact"]).resolve(strict=True)
    processes = subprocess.run(["/bin/ps", "-axo", "command="], capture_output=True, text=True, check=True, timeout=20).stdout
    artifacts = (
        "3d0a1fe71f21928b61671350211b26282505693d1ec6e3bb551703c5d1b6cec0",
        "fae8cd0869dae019b09db881d2cb4d53019be745f92f7b52c5ba05f6d818a8cf",
        "e12ca4b0770e1759724d7316a135d91218d9e3ec57741235fbc9b8b941bb964e",
    )
    candidates = []
    for digest in artifacts:
        if not re.fullmatch(r"[a-f0-9]{64}", digest):
            raise RuntimeError("BUILD_ARTIFACT_ID_INVALID")
        directory = runtime / "applications" / digest
        artifact = directory / "ruoyi-admin.jar"
        if directory.is_symlink() or artifact.is_symlink():
            raise RuntimeError("BUILD_ARTIFACT_SYMLINK_REJECTED")
        if not artifact.exists():
            continue
        if artifact.resolve(strict=True) == current or digest in processes:
            raise RuntimeError("BUILD_ARTIFACT_STILL_ACTIVE")
        if {item.name for item in directory.iterdir()} != {"ruoyi-admin.jar"}:
            raise RuntimeError("BUILD_ARTIFACT_DIRECTORY_NOT_EXCLUSIVE")
        candidates.append(artifact)
    released = 0
    for artifact in candidates:
        released += artifact.stat().st_size
        artifact.unlink()
        artifact.parent.rmdir()
    print(json.dumps({"removed_superseded_builds": len(candidates), "released_bytes": released, "current_deployment_retained": True}))


if __name__ == "__main__":
    main()
