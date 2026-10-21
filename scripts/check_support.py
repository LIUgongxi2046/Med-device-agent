"""Portable locations shared by evidence-producing check entrypoints."""
import os
from pathlib import Path


def capture_check_path():
    configured = os.environ.get('CAPTURE_CHECK_PATH')
    path = Path(configured).expanduser() if configured else Path(__file__).with_name('capture_check.py')
    path = path.resolve(strict=True)
    if not path.is_file():
        raise ValueError('CAPTURE_CHECK_FILE_REQUIRED')
    return path
