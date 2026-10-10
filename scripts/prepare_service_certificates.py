import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import ipaddress
import json
from pathlib import Path
import secrets
from uuid import uuid4

import cryptography
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


SERVICE = "urn:medical:service:source-processing"


def save(path, content, private=False):
    with path.open("xb") as stream:
        stream.write(content)
    path.chmod(0o600 if private else 0o644)


def authority(name, now):
    key = ec.generate_private_key(ec.SECP256R1())
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, name)])
    certificate = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
        .public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=1)).not_valid_after(now + timedelta(days=1))
        .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
        .add_extension(x509.KeyUsage(True, False, False, False, False, True, True, False, False), critical=True)
        .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()), critical=False)
        .sign(key, hashes.SHA256()))
    return key, certificate


def issue(name, ca, now, uris, expired=False, server=False):
    key = ec.generate_private_key(ec.SECP256R1())
    names = ([x509.IPAddress(ipaddress.ip_address("127.0.0.1")), x509.DNSName("localhost")]
        if server else [x509.UniformResourceIdentifier(uri) for uri in uris])
    certificate = (x509.CertificateBuilder()
        .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, name)]))
        .issuer_name(ca[1].subject).public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(hours=2))
        .not_valid_after(now - timedelta(hours=1) if expired else now + timedelta(hours=12))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.KeyUsage(True, False, False, False, False, False, False, False, False), critical=True)
        .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH if server else ExtendedKeyUsageOID.CLIENT_AUTH]), critical=False)
        .add_extension(x509.SubjectAlternativeName(names), critical=False)
        .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca[0].public_key()), critical=False)
        .sign(ca[0], hashes.SHA256()))
    return key, certificate


def main():
    parser = argparse.ArgumentParser(description="生成原生TLS检查的实际证书和签名CRL")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if not output.is_relative_to(root / ".implementation-work"):
        raise ValueError("SERVICE_CERTIFICATE_PROJECT_DIRECTORY_REQUIRED")
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    private = output / "private"
    private.mkdir(mode=0o700)
    if cryptography.__version__ != "50.0.2":
        raise ValueError("SERVICE_CERTIFICATE_CRYPTOGRAPHY_VERSION_REQUIRED")
    now = datetime.now(timezone.utc)
    ca = authority("medical-service-native-" + str(uuid4()), now)
    unrelated = authority("medical-untrusted-native-" + str(uuid4()), now)
    password = secrets.token_urlsafe(48).encode("ascii")
    encryption = serialization.BestAvailableEncryption(password)
    save(private / "password", password, True)
    save(output / "ca.pem", ca[1].public_bytes(serialization.Encoding.PEM))
    save(private / "trust.p12", pkcs12.serialize_java_truststore(
        [pkcs12.PKCS12Certificate(ca[1], b"medical-service-ca")], encryption), True)
    manifest = {"scope": "ACTUAL_NATIVE_TLS_CERTIFICATES", "cryptography_version": cryptography.__version__,
        "trust_revision": str(uuid4()), "service_uri": SERVICE, "certificates": {}}
    issued = {}
    for name in ("server", "approved", "unpinned", "revoked", "ambiguous", "unregistered", "untrusted", "expired"):
        signer = unrelated if name == "untrusted" else ca
        uris = [SERVICE, "urn:medical:service:other"] if name == "ambiguous" else [
            "urn:medical:service:unregistered" if name == "unregistered" else SERVICE]
        key, certificate = issue(name, signer, now, uris, name == "expired", name == "server")
        issued[name] = certificate
        save(output / (name + ".pem"), certificate.public_bytes(serialization.Encoding.PEM))
        save(private / (name + ".p12"), pkcs12.serialize_key_and_certificates(
            name.encode("ascii"), key, certificate, [signer[1]], encryption), True)
        manifest["certificates"][name] = certificate.fingerprint(hashes.SHA256()).hex()
    revoked = (x509.RevokedCertificateBuilder().serial_number(issued["revoked"].serial_number)
        .revocation_date(now - timedelta(seconds=30)).build())
    for name in ("current", "expired"):
        crl = (x509.CertificateRevocationListBuilder().issuer_name(ca[1].subject)
            .last_update(now - timedelta(hours=2))
            .next_update(now - timedelta(hours=1) if name == "expired" else now + timedelta(hours=12))
            .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca[0].public_key()), critical=False)
            .add_extension(x509.CRLNumber(1), critical=False).add_revoked_certificate(revoked)
            .sign(ca[0], hashes.SHA256()))
        save(output / (name + ".crl.pem"), crl.public_bytes(serialization.Encoding.PEM))
    manifest["public_artifacts"] = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(output.iterdir()) if path.is_file()}
    save(output / "manifest.json", (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    print(json.dumps({"scope": manifest["scope"], "output": str(output), "private_directory_mode": oct(private.stat().st_mode & 0o777)}))


if __name__ == "__main__":
    main()
