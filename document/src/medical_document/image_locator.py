"""Versioned source-image locator; does not mutate published dataset v1."""
from dataclasses import dataclass
import hashlib
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator
import rfc8785


class ImageLocator(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    schema_version: Literal["1.0.0"]
    kind: Literal["IMAGE"]
    frame_index: int = Field(ge=0, le=999)
    original_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    width: int = Field(gt=0, le=100000)
    height: int = Field(gt=0, le=100000)
    x0: float = Field(ge=0, le=1)
    y0: float = Field(ge=0, le=1)
    x1: float = Field(ge=0, le=1)
    y1: float = Field(ge=0, le=1)
    coordinate_system: Literal["ORIGINAL_TOP_LEFT_NORMALIZED"]

    @model_validator(mode="after")
    def ordered(self):
        if self.x0 >= self.x1 or self.y0 >= self.y1:
            raise ValueError("IMAGE_LOCATION_INVALID")
        return self


@dataclass(frozen=True)
class ImagePayload:
    canonical_bytes: bytes

    def read(self):
        return ImageLocator.model_validate_json(self.canonical_bytes)


def bind_image(locator):
    return ImagePayload(rfc8785.dumps(ImageLocator.model_validate(locator).model_dump()))
