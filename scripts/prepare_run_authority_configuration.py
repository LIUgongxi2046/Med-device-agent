import argparse
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import pwd
from uuid import uuid4

import cryptography
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

import local_object_storage as objects


ROOT = objects.ROOT


def save(path, content):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(content)
        output.flush()
        os.fsync(output.fileno())


def main():
    parser = argparse.ArgumentParser(description="配置本地C02的实际服务证书及RSA签发密钥")
    parser.add_argument("--directory", required=True, type=Path)
    parser.add_argument("--service-uri", required=True)
    parser.add_argument("--issuer", required=True)
    parser.add_argument("--audience", required=True)
    parser.add_argument("--certificate-lifetime-hours", required=True, type=int)
    parser.add_argument("--crl-lifetime-hours", required=True, type=int)
    parser.add_argument("--maximum-token-lifetime-seconds", required=True, type=int)
    parser.add_argument("--maximum-token-bytes", required=True, type=int)
    parser.add_argument("--maximum-configuration-bytes", required=True, type=int)
    args = parser.parse_args()
    directory = args.directory.resolve()
    directory.relative_to(ROOT / ".implementation-work/runtime")
    if cryptography.__version__ != "50.0.2" or not args.service_uri.startswith("urn:medical-local:service:") \
            or not args.issuer.startswith("urn:medical-local:") or not args.audience or any(value <= 0 for value in (
                args.certificate_lifetime_hours, args.crl_lifetime_hours, args.maximum_token_lifetime_seconds,
                args.maximum_token_bytes, args.maximum_configuration_bytes)) \
            or args.crl_lifetime_hours > args.certificate_lifetime_hours:
        raise ValueError("RUN_AUTHORITY_LOCAL_CONFIGURATION_REQUIRED")
    directory.mkdir(mode=0o700, parents=True, exist_ok=False)
    now = datetime.now(timezone.utc)
    identity = str(uuid4())
    ca_key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "medical-local-service-ca-" + identity)])
    ca = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(ca_key.public_key())
        .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=1))
        .not_valid_after(now + timedelta(hours=args.certificate_lifetime_hours))
        .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
        .add_extension(x509.KeyUsage(True, False, False, False, False, True, True, False, False), critical=True)
        .add_extension(x509.SubjectKeyIdentifier.from_public_key(ca_key.public_key()), critical=False).sign(ca_key, hashes.SHA256()))
    client_key = ec.generate_private_key(ec.SECP256R1())
    client = (x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "medical-local-workflow-worker")]))
        .issuer_name(ca.subject).public_key(client_key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=1)).not_valid_after(now + timedelta(hours=args.certificate_lifetime_hours))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.KeyUsage(True, False, False, False, False, False, False, False, False), critical=True)
        .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.CLIENT_AUTH]), critical=False)
        .add_extension(x509.SubjectAlternativeName([x509.UniformResourceIdentifier(args.service_uri)]), critical=False)
        .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()), critical=False).sign(ca_key, hashes.SHA256()))
    crl = (x509.CertificateRevocationListBuilder().issuer_name(ca.subject).last_update(now - timedelta(minutes=1))
        .next_update(now + timedelta(hours=args.crl_lifetime_hours))
        .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()), critical=False)
        .add_extension(x509.CRLNumber(1), critical=False).sign(ca_key, hashes.SHA256()))
    signing = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_der = signing.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    private_der = signing.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    policy_revision = str(uuid4())
    objects.save_private(directory / "run-token-policy.json", {"schema_version": "1.0.0", "revision": policy_revision,
        "issuer": args.issuer, "audience": args.audience, "public_key_der_base64": base64.b64encode(public_der).decode("ascii"),
        "maximum_lifetime_seconds": args.maximum_token_lifetime_seconds, "maximum_token_bytes": args.maximum_token_bytes})
    policy_sha = hashlib.sha256((directory / "run-token-policy.json").read_bytes()).hexdigest()
    objects.save_private(directory / "run-token-signing-key.json", {"schema_version": "1.0.0", "revision": str(uuid4()),
        "policy_revision": policy_revision, "policy_sha256": policy_sha, "private_key_der_base64": base64.b64encode(private_der).decode("ascii")})
    fingerprint = client.fingerprint(hashes.SHA256()).hex()
    objects.save_private(directory / "trust.json", {"schema_version": "1.0.0", "revision": str(uuid4()),
        "authorities": [base64.b64encode(ca.public_bytes(serialization.Encoding.DER)).decode("ascii")],
        "revocations": [base64.b64encode(crl.public_bytes(serialization.Encoding.DER)).decode("ascii")],
        "registered_certificates": {args.service_uri: [fingerprint]}, "protocols": ["TLSv1.3"],
        "ciphers": ["TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256"]})
    save(directory / "ca-key.pem", ca_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    save(directory / "ca.pem", ca.public_bytes(serialization.Encoding.PEM))
    save(directory / "worker-key.pem", client_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    save(directory / "worker.pem", client.public_bytes(serialization.Encoding.PEM))
    for name in ("trust.json", "run-token-policy.json", "run-token-signing-key.json"):
        if (directory / name).stat().st_size > args.maximum_configuration_bytes:
            raise ValueError("RUN_AUTHORITY_LOCAL_CONFIGURATION_CAPACITY_EXCEEDED")
    receipt = {"schema_version": "1.0.0", "scope": "LOCAL_RUN_AUTHORITY_CONFIGURATION", "identity": identity,
        "created_at": now.isoformat(), "configuration_directory": str(directory.relative_to(ROOT)),
        "configuration_owner": pwd.getpwuid(os.getuid()).pw_name, "service_uri": args.service_uri,
        "certificate_sha256": fingerprint, "public_key_sha256": hashlib.sha256(public_der).hexdigest(),
        "policy_revision": policy_revision, "policy_sha256": policy_sha,
        "maximum_configuration_bytes": args.maximum_configuration_bytes, "certificate_expires_at": client.not_valid_after_utc.isoformat(),
        "crl_expires_at": crl.next_update_utc.isoformat(), "private_directory_mode": oct(directory.stat().st_mode & 0o777),
        "files": {path.name: {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "mode": oct(path.stat().st_mode & 0o777)}
            for path in sorted(directory.iterdir()) if path.is_file()}, "customer_authorization_created": False,
        "run_created": False, "token_issued": False, "model_request_sent": False}
    target = ROOT / ".implementation-work/runtime" / ("run-authority-configuration-" + identity + ".json")
    objects.save_private(target, receipt)
    print(str(target.relative_to(ROOT)), flush=True)


if __name__ == "__main__":
    main()
