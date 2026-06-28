#!/usr/bin/env python3
"""Sign AXS module.yml with Ed25519 (payload: id:version:mainClass).

Public key for config.yml must be X509 SubjectPublicKeyInfo DER, Base64-encoded
(same format as Java PublicKey.getEncoded() / ModuleSignatureVerifier).
"""
from __future__ import annotations

import argparse
import base64
import re
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives import serialization
except ImportError:
    print("Install: pip install cryptography", file=sys.stderr)
    sys.exit(1)


def load_private_key(path: Path) -> Ed25519PrivateKey:
    data = path.read_bytes()
    return serialization.load_pem_private_key(data, password=None)


def load_public_key(path: Path):
    data = path.read_bytes()
    return serialization.load_pem_public_key(data)


def parse_module_yml(text: str) -> tuple[str, str, str]:
    def field(name: str) -> str:
        m = re.search(rf"^{name}:\s*(.+?)\s*$", text, re.MULTILINE)
        if not m:
            raise SystemExit(f"module.yml missing field: {name}")
        return m.group(1).strip().strip('"').strip("'")

    return field("id"), field("version"), field("main")


def build_payload(module_id: str, version: str, main_class: str) -> bytes:
    return f"{module_id}:{version}:{main_class}".encode("utf-8")


def cmd_keygen(out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    priv = Ed25519PrivateKey.generate()
    priv_pem = priv.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    pub_pem = priv.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    priv_path = out_dir / "ed25519-private.pem"
    pub_path = out_dir / "ed25519-public.pem"
    priv_path.write_bytes(priv_pem)
    pub_path.write_bytes(pub_pem)
    pub_b64 = base64.b64encode(
        priv.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    ).decode("ascii")
    print(f"Private key: {priv_path}")
    print(f"Public key:  {pub_path}")
    print(f"Public key (Base64 for config.yml):\n{pub_b64}")


def cmd_sign(module_yml: Path, private_key: Path) -> None:
    text = module_yml.read_text(encoding="utf-8")
    module_id, version, main_class = parse_module_yml(text)
    payload = build_payload(module_id, version, main_class)
    priv = load_private_key(private_key)
    signature = priv.sign(payload)
    sig_b64 = base64.b64encode(signature).decode("ascii")

    if re.search(r"^signature:\s*", text, re.MULTILINE):
        text = re.sub(
            r'^signature:\s*.*$',
            f'signature: "{sig_b64}"',
            text,
            count=1,
            flags=re.MULTILINE,
        )
    else:
        if not text.endswith("\n"):
            text += "\n"
        text += f'signature: "{sig_b64}"\n'

    module_yml.write_text(text, encoding="utf-8")
    print(f"Signed payload: {module_id}:{version}:{main_class}")
    print(f"Updated: {module_yml}")


def cmd_pubkey(public_key: Path) -> None:
    pub = load_public_key(public_key)
    der = pub.public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    print(base64.b64encode(der).decode("ascii"))


def cmd_verify(module_yml: Path, public_key: Path) -> None:
    text = module_yml.read_text(encoding="utf-8")
    module_id, version, main_class = parse_module_yml(text)
    m = re.search(r'^signature:\s*"?([^"\n]+)"?\s*$', text, re.MULTILINE)
    if not m:
        raise SystemExit("module.yml has no signature field")
    sig = base64.b64decode(m.group(1).strip())
    pub = load_public_key(public_key)
    pub.verify(sig, build_payload(module_id, version, main_class))
    print("OK: signature valid")


def main() -> None:
    p = argparse.ArgumentParser(description="AXS module.yml Ed25519 signing tool")
    sub = p.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("keygen", help="Generate Ed25519 key pair")
    g.add_argument("--out-dir", type=Path, default=Path("module-signing-keys"))

    s = sub.add_parser("sign", help="Sign module.yml in place")
    s.add_argument("--module-yml", type=Path, required=True)
    s.add_argument("--private-key", type=Path, required=True)

    u = sub.add_parser("pubkey", help="Print Base64 public key for config.yml")
    u.add_argument("--public-key", type=Path, required=True)

    v = sub.add_parser("verify", help="Verify module.yml signature")
    v.add_argument("--module-yml", type=Path, required=True)
    v.add_argument("--public-key", type=Path, required=True)

    args = p.parse_args()
    if args.cmd == "keygen":
        cmd_keygen(args.out_dir)
    elif args.cmd == "sign":
        cmd_sign(args.module_yml, args.private_key)
    elif args.cmd == "pubkey":
        cmd_pubkey(args.public_key)
    elif args.cmd == "verify":
        cmd_verify(args.module_yml, args.public_key)


if __name__ == "__main__":
    main()
