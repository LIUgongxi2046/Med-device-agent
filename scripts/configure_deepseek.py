import argparse
import asyncio
import datetime
import json
from pathlib import Path
import uuid

from medical_model_gateway.credentials import prepare_key_file, read_key
from medical_model_gateway.provider import DeepSeekProvider


ROOT = Path(__file__).resolve().parents[1]
KEY_PATH = ROOT / ".implementation-work/runtime/deepseek-api-key"


async def verify(model_id: str | None):
    provider = DeepSeekProvider(KEY_PATH)
    models = await provider.list_models()
    if model_id is not None and model_id not in {item["id"] for item in models}:
        raise ValueError("指定模型没有出现在实际供应商模型清单")
    result = {"verification_id": str(uuid.uuid4()), "verified_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "endpoint": provider.BASE_URL, "scope": "PROVIDER_CONNECTION_ONLY", "models": models}
    output = ROOT / ".implementation-work" / ("deepseek-verification-" + result["verification_id"] + ".json")
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "PASS", "scope": result["scope"], "model_ids": [item["id"] for item in models],
                      "receipt": str(output.relative_to(ROOT))}, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description="准备DeepSeek秘密文件并核验真实连接")
    parser.add_argument("command", choices=("prepare", "check", "verify"))
    parser.add_argument("--model")
    args = parser.parse_args()
    if args.model is not None and args.command != "verify":
        raise ValueError("model参数只能用于真实连接验证")
    if args.command == "prepare":
        prepare_key_file(KEY_PATH)
        print(json.dumps({"key_file": str(KEY_PATH), "permissions": "0600", "configured": False}, ensure_ascii=False))
    elif args.command == "check":
        read_key(KEY_PATH)
        print(json.dumps({"configured": True, "key_file": str(KEY_PATH)}, ensure_ascii=False))
    else:
        asyncio.run(verify(args.model))


if __name__ == "__main__":
    main()
