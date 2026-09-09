#!/usr/bin/env python3
"""Decrypt and safely extract pnLibrary .pndebug reports outside the server."""

import argparse
import base64
import gzip
import json
import pathlib
import zipfile
from io import BytesIO

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def b64(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def decrypt(document: bytes, private_key) -> tuple[bytes, str]:
    envelope = json.loads(document)
    if envelope.get("format") != "pnlibrary-diagnostics" or envelope.get("version") != 1:
        raise ValueError("Unsupported pnLibrary diagnostic envelope")
    key_id = envelope["keyId"]
    created = envelope["createdUtc"]
    aad = f"pnlibrary-diagnostics|1|{key_id}|{created}|RSA-OAEP-256|A256GCM|gzip".encode()
    content_key = private_key.decrypt(
        b64(envelope["wrappedKey"]),
        padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    compressed = AESGCM(content_key).decrypt(b64(envelope["nonce"]), b64(envelope["ciphertext"]), aad)
    return gzip.decompress(compressed), envelope.get("payloadFormat", "json")


def safe_extract(data: bytes, output: pathlib.Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    root = output.resolve()
    with zipfile.ZipFile(BytesIO(data)) as archive:
        for item in archive.infolist():
            target = (root / item.filename).resolve()
            if root not in target.parents and target != root:
                raise ValueError(f"Unsafe archive path: {item.filename}")
        archive.extractall(root)


def main() -> None:
    parser = argparse.ArgumentParser(description="Decrypt a pnLibrary diagnostic report")
    parser.add_argument("report", type=pathlib.Path)
    parser.add_argument("private_key", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    private_key = serialization.load_pem_private_key(args.private_key.read_bytes(), password=None)
    payload, payload_format = decrypt(args.report.read_bytes(), private_key)
    output = args.output or args.report.with_suffix("")
    if payload_format == "zip":
        safe_extract(payload, output)
        print(output.resolve())
    else:
        target = output.with_suffix(".json")
        target.write_bytes(payload)
        print(target.resolve())


if __name__ == "__main__":
    main()
