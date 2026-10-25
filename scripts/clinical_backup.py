"""Encrypted PostgreSQL logical backup for the clinical encryption rollout.

Requires the Python cryptography package and PostgreSQL client binaries. The
backup key is supplied through BACKUP_ENCRYPTION_KEY (32 random Base64 bytes).
Database connection settings use libpq's PGHOST, PGPORT, PGUSER, PGDATABASE and
PGPASSWORD or PGPASSFILE environment variables. No key is written to the dump.
"""

import argparse
import base64
import os
from pathlib import Path
import secrets
import subprocess
import sys

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.exceptions import InvalidTag


HEADER = b"CLINISISBACKUP1"
AAD = b"clinisis-db-backup-v1"
NONCE_SIZE = 12
TAG_SIZE = 16
CHUNK_SIZE = 1024 * 1024


def backup_key() -> bytes:
    encoded = os.environ.get("BACKUP_ENCRYPTION_KEY", "")
    try:
        key = base64.b64decode(encoded, validate=True)
    except ValueError as exc:
        raise ValueError("BACKUP_ENCRYPTION_KEY must be Base64") from exc
    if len(key) != 32:
        raise ValueError("BACKUP_ENCRYPTION_KEY must contain 32 random bytes")
    if encoded == os.environ.get("DATA_ENCRYPTION_KEY"):
        raise ValueError("Backup and clinical data keys must differ")
    return key


def create_backup(output: Path, pg_dump: str, key: bytes) -> None:
    if output.exists():
        raise FileExistsError("Refusing to overwrite an existing backup")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".partial")
    if temporary.exists():
        raise FileExistsError("A partial backup already exists")

    nonce = secrets.token_bytes(NONCE_SIZE)
    encryptor = Cipher(algorithms.AES(key), modes.GCM(nonce)).encryptor()
    encryptor.authenticate_additional_data(AAD)
    process = subprocess.Popen(
        [pg_dump, "-w", "-Fc"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL
    )
    try:
        with temporary.open("xb") as target:
            target.write(HEADER + nonce)
            while chunk := process.stdout.read(CHUNK_SIZE):
                target.write(encryptor.update(chunk))
            if process.wait() != 0:
                raise RuntimeError("pg_dump failed; encrypted partial backup removed")
            target.write(encryptor.finalize())
            target.write(encryptor.tag)
        os.replace(temporary, output)
    except BaseException:
        if process.poll() is None:
            process.kill()
            process.wait()
        temporary.unlink(missing_ok=True)
        raise


def verify_backup(source: Path, pg_restore: str, key: bytes) -> None:
    size = source.stat().st_size
    if size < len(HEADER) + NONCE_SIZE + TAG_SIZE + 5:
        raise ValueError("Backup file is incomplete")
    with source.open("rb") as encrypted:
        if encrypted.read(len(HEADER)) != HEADER:
            raise ValueError("Unknown backup format")
        nonce = encrypted.read(NONCE_SIZE)
        encrypted.seek(-TAG_SIZE, os.SEEK_END)
        tag = encrypted.read(TAG_SIZE)
        encrypted.seek(len(HEADER) + NONCE_SIZE)
        remaining = size - len(HEADER) - NONCE_SIZE - TAG_SIZE
        decryptor = Cipher(algorithms.AES(key), modes.GCM(nonce, tag)).decryptor()
        decryptor.authenticate_additional_data(AAD)
        restore = subprocess.Popen(
            [pg_restore, "-l"], stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        try:
            pipe_open = True
            while remaining:
                chunk = encrypted.read(min(CHUNK_SIZE, remaining))
                if not chunk:
                    raise ValueError("Backup file is truncated")
                plain = decryptor.update(chunk)
                if pipe_open:
                    try:
                        restore.stdin.write(plain)
                    except BrokenPipeError:
                        # pg_restore -l may exit after reading only the archive catalog.
                        pipe_open = False
                remaining -= len(chunk)
            final = decryptor.finalize()
            if pipe_open:
                try:
                    restore.stdin.write(final)
                    restore.stdin.close()
                except BrokenPipeError:
                    pass
            if restore.wait() != 0:
                raise RuntimeError("pg_restore could not read the backup archive")
        except BaseException:
            if restore.poll() is None:
                restore.kill()
                restore.wait()
            raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="action", required=True)
    backup = subcommands.add_parser("backup")
    backup.add_argument("output", type=Path)
    backup.add_argument("--pg-dump", default="pg_dump")
    verify = subcommands.add_parser("verify")
    verify.add_argument("source", type=Path)
    verify.add_argument("--pg-restore", default="pg_restore")
    args = parser.parse_args()
    try:
        key = backup_key()
        if args.action == "backup":
            create_backup(args.output, args.pg_dump, key)
            print(f"Encrypted backup created: {args.output}")
        else:
            verify_backup(args.source, args.pg_restore, key)
            print("Backup authentication and archive structure verified")
        return 0
    except (OSError, ValueError, RuntimeError, InvalidTag) as exc:
        print(f"Backup operation failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
