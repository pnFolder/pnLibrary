#!/usr/bin/env python3
"""Open an encrypted pnFolder PN Support Archive."""

import argparse
import gzip
import json
import pathlib
import struct
import zipfile
from io import BytesIO

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


MAGIC = b"PNSUPPORT\r\n"
HEADER = ">HBHHHII"
MULTILINE_FIELDS = {"fullError", "exception", "collectionError"}


def decrypt(document: bytes, private_key) -> tuple[bytes, str]:
    if not document.startswith(MAGIC):
        raise ValueError("This is not a PN Support Archive")
    header_size = struct.calcsize(HEADER)
    if len(document) < len(MAGIC) + header_size:
        raise ValueError("Truncated PN Support Archive")
    offset = len(MAGIC)
    version, format_len, key_len, created_len, nonce_len, wrapped_len, cipher_len = struct.unpack_from(
        HEADER, document, offset
    )
    if version != 1:
        raise ValueError(f"Unsupported PN Support Archive version: {version}")
    offset += header_size
    expected = offset + format_len + key_len + created_len + nonce_len + wrapped_len + cipher_len
    if expected != len(document) or nonce_len != 12 or wrapped_len > 16_384 or cipher_len > 128 * 1024 * 1024:
        raise ValueError("Invalid PN Support Archive lengths")

    def take(size: int) -> bytes:
        nonlocal offset
        value = document[offset:offset + size]
        offset += size
        return value

    payload_format = take(format_len).decode("utf-8")
    key_id = take(key_len).decode("utf-8")
    created = take(created_len).decode("utf-8")
    nonce = take(nonce_len)
    wrapped_key = take(wrapped_len)
    ciphertext = take(cipher_len)
    aad = f"pnlibrary-diagnostics|1|{key_id}|{created}|RSA-OAEP-256|A256GCM|gzip".encode()
    content_key = private_key.decrypt(
        wrapped_key,
        padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    compressed = AESGCM(content_key).decrypt(nonce, ciphertext, aad)
    return gzip.decompress(compressed), payload_format


def safe_extract(data: bytes, output: pathlib.Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    root = output.resolve()
    with zipfile.ZipFile(BytesIO(data)) as archive:
        for item in archive.infolist():
            target = (root / item.filename).resolve()
            if root not in target.parents and target != root:
                raise ValueError(f"Unsafe archive path: {item.filename}")
        archive.extractall(root)


def decrypt_history(output: pathlib.Path, private_key) -> None:
    for history in output.rglob("*.pndlog"):
        payload, payload_format = decrypt(history.read_bytes(), private_key)
        if payload_format != "incident-history":
            raise ValueError(f"Unexpected history payload: {history}")
        document = make_readable(json.loads(payload.decode("utf-8")))
        write_pretty_json(history.with_suffix(".json"), document)
        history.unlink()


def make_readable(value, field: str | None = None):
    if isinstance(value, dict):
        readable = {
            key: make_readable(item, key)
            for key, item in value.items()
            if key != "consoleBlock"
        }
        full_error = value.get("fullError")
        if isinstance(full_error, str):
            occurrences = value.get("occurrences")
            first_seen = occurrences.get("firstSeenUtc") if isinstance(occurrences, dict) else None
            timestamp = first_seen or value.get("firstSeenUtc", value.get("timeUtc", "unknown-time"))
            level = value.get("level", "ERROR")
            plugin = value.get("plugin", "unknown")
            message = value.get("message", "Unhandled error")
            header = f"[{timestamp} {level}] [{plugin}] {message}"
            readable["fullError"] = [header, *[line.lstrip("\t") for line in full_error.splitlines()]]
        return readable
    if isinstance(value, list):
        return [make_readable(item) for item in value]
    if isinstance(value, str) and field in MULTILINE_FIELDS:
        return [line.lstrip("\t") for line in value.splitlines()]
    return value


def write_pretty_json(target: pathlib.Path, document) -> None:
    target.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def format_report_json(output: pathlib.Path) -> None:
    for target in output.rglob("*.json"):
        if "configuration" in target.parts:
            continue
        document = json.loads(target.read_text(encoding="utf-8"))
        write_pretty_json(target, make_readable(document))


def main() -> None:
    parser = argparse.ArgumentParser(description="Open a pnFolder .pnsupport archive")
    parser.add_argument("report", type=pathlib.Path)
    parser.add_argument("private_key", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    private_key = serialization.load_pem_private_key(args.private_key.read_bytes(), password=None)
    payload, payload_format = decrypt(args.report.read_bytes(), private_key)
    output = args.output or args.report.with_suffix("")
    if payload_format == "zip":
        safe_extract(payload, output)
        decrypt_history(output, private_key)
        format_report_json(output)
        print(output.resolve())
    else:
        target = output.with_suffix(".json")
        document = make_readable(json.loads(payload.decode("utf-8")))
        write_pretty_json(target, document)
        print(target.resolve())


if __name__ == "__main__":
    main()
