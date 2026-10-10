import os
from pathlib import Path
import stat

from pydantic import SecretStr


def _open_directory(path: Path) -> int:
    if not path.is_absolute() or path.parent.resolve(strict=True) != path.parent:
        raise PermissionError("秘密路径必须绝对定位并禁止链接目录")
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    information = os.fstat(descriptor)
    if information.st_uid != os.getuid() or stat.S_IMODE(information.st_mode) != 0o700:
        os.close(descriptor)
        raise PermissionError("秘密目录必须属于当前用户且权限为0700")
    return descriptor


def prepare_key_file(path: Path) -> None:
    directory = _open_directory(path)
    try:
        descriptor = os.open(path.name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                             0o600, dir_fd=directory)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        os.fsync(directory)
    finally:
        os.close(directory)


def read_key(path: Path) -> SecretStr:
    directory = _open_directory(path)
    try:
        descriptor = os.open(path.name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        with os.fdopen(descriptor, "rb") as source:
            information = os.fstat(source.fileno())
            if (not stat.S_ISREG(information.st_mode) or information.st_uid != os.getuid()
                    or stat.S_IMODE(information.st_mode) != 0o600 or information.st_nlink != 1):
                raise PermissionError("秘密文件必须属于当前用户、权限为0600且禁止共享链接")
            if information.st_size > 4096:
                raise ValueError("密钥文件超出允许长度")
            content = source.read(4097)
            if len(content) > 4096:
                raise ValueError("密钥文件超出允许长度")
            value = content.decode("ascii").removesuffix("\n").removesuffix("\r")
            if not value:
                raise ValueError("DeepSeek密钥尚未配置")
            if any(ord(character) <= 32 or ord(character) >= 127 for character in value):
                raise ValueError("密钥必须保存为单行ASCII文本")
            return SecretStr(value)
    finally:
        os.close(directory)
