import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from cryptography import x509
from cryptography.hazmat.primitives import hashes


ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description="复核服务证书检查的当前源码和原始签名证据")
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    receipt_path = args.receipt.resolve(strict=True)
    output = args.output.resolve()
    require(receipt_path.is_relative_to(ROOT / ".implementation-work") and output.is_relative_to(ROOT / ".implementation-work"),
        "SERVICE_CERTIFICATE_PROJECT_EVIDENCE_REQUIRED")
    receipt = json.loads(receipt_path.read_bytes())
    require(receipt["exit_code"] == 0 and receipt["inputs_unchanged"], "SERVICE_CERTIFICATE_SUCCESS_RECEIPT_REQUIRED")
    checked_inputs = []
    for item in receipt["inputs"]:
        path = Path(item["path"])
        if path.is_relative_to(ROOT / "docs"):
            continue
        require(digest(path) == item["sha256"], "SERVICE_CERTIFICATE_CURRENT_INPUT_BYTES_REQUIRED")
        checked_inputs.append(item)
    for item in receipt["artifacts"]:
        require(digest(Path(item["path"])) == item["sha256"], "SERVICE_CERTIFICATE_ORIGINAL_ARTIFACT_REQUIRED")
    directory = receipt_path.parent
    check = json.loads((directory / "check.json").read_bytes())
    require(check["totals"] == {"tests": 3, "failures": 0, "errors": 0, "skipped": 0}, "SERVICE_CERTIFICATE_NATIVE_SUITES_REQUIRED")
    require(digest(directory / "TEST-com.ruoyi.medical.identity.ServiceCertificateTrustStoreTest.xml") == check["trust_store_junit_sha256"],
        "SERVICE_CERTIFICATE_TRUST_STORE_JUNIT_REQUIRED")
    totals = {name: 0 for name in ("tests", "failures", "errors", "skipped")}
    for item in check["affected_junit"]:
        path = directory / item["path"]
        require(digest(path) == item["sha256"], "SERVICE_CERTIFICATE_ORIGINAL_JUNIT_REQUIRED")
        suite = ET.parse(path).getroot()
        for name in totals:
            totals[name] += int(suite.attrib[name])
    require(totals == check["affected_totals"] and totals["tests"] >= 81 and
        all(totals[name] == 0 for name in ("failures", "errors", "skipped")), "SERVICE_CERTIFICATE_AFFECTED_TESTS_REQUIRED")
    for item in check["public_certificate_artifacts"]:
        require(digest(directory / item["path"]) == item["sha256"], "SERVICE_CERTIFICATE_PUBLIC_BYTES_REQUIRED")
    started = datetime.fromisoformat(receipt["started_at"])
    ended = datetime.fromisoformat(receipt["ended_at"])
    for label, name in (("native", "service-certificate-result.json"), ("servlet", "service-certificate-servlet-result.json")):
        actual = json.loads((directory / name).read_bytes())
        expected = check["result"] if label == "native" else check["servlet_result"]
        require(actual == expected, "SERVICE_CERTIFICATE_ORIGINAL_RESULT_REQUIRED")
        if label == "servlet":
            require(actual["current_trust_store_verified"] and len(actual["checks"]) == 16 and
                {"same_connection_current_registration_denied", "expired_crl_http_denied", "current_valid_registration_reopened",
                    "current_file_permission_denied", "current_file_missing_unavailable", "current_file_restored"}.issubset(actual["checks"]),
                "SERVICE_CERTIFICATE_CURRENT_TRUST_SERVLET_REQUIRED")
        require(actual["private_credentials_removed"] and actual["server_closed"] and not actual["worker_identity_verified"]
            and not actual["business_success_verified"] and not actual["customer_success_verified"]
            and not actual["professional_approval_created"], "SERVICE_CERTIFICATE_SCOPE_AND_CLEANUP_REQUIRED")
        public = directory / "certificates" / label
        ca = x509.load_pem_x509_certificate((public / "ca.pem").read_bytes())
        ca.verify_directly_issued_by(ca)
        require(ca.not_valid_before_utc <= ended < ca.not_valid_after_utc, "SERVICE_CERTIFICATE_CA_VALIDITY_REQUIRED")
        manifest = actual["certificate_manifest"]
        for certificate_name in ("server", "approved", "unpinned", "revoked", "ambiguous", "unregistered", "expired"):
            certificate = x509.load_pem_x509_certificate((public / (certificate_name + ".pem")).read_bytes())
            certificate.verify_directly_issued_by(ca)
            require(certificate.fingerprint(hashes.SHA256()).hex() == manifest["certificates"][certificate_name],
                "SERVICE_CERTIFICATE_LEAF_IDENTITY_REQUIRED")
            if certificate_name == "expired":
                require(certificate.not_valid_after_utc < started, "SERVICE_CERTIFICATE_ACTUAL_EXPIRY_REQUIRED")
            else:
                require(certificate.not_valid_before_utc <= ended < certificate.not_valid_after_utc,
                    "SERVICE_CERTIFICATE_LEAF_VALIDITY_REQUIRED")
        revoked = x509.load_pem_x509_certificate((public / "revoked.pem").read_bytes())
        for crl_name in ("current", "expired"):
            crl = x509.load_pem_x509_crl((public / (crl_name + ".crl.pem")).read_bytes())
            require(crl.is_signature_valid(ca.public_key()), "SERVICE_CERTIFICATE_SIGNED_CRL_REQUIRED")
            require(crl.get_revoked_certificate_by_serial_number(revoked.serial_number) is not None, "SERVICE_CERTIFICATE_REAL_REVOCATION_REQUIRED")
            require(crl.last_update_utc <= ended, "SERVICE_CERTIFICATE_CRL_DATE_REQUIRED")
            require(crl.next_update_utc > ended if crl_name == "current" else crl.next_update_utc < started,
                "SERVICE_CERTIFICATE_CRL_EXPIRY_REQUIRED")
    result = {"scope": "CURRENT_SERVICE_CERTIFICATE_SOURCE_TEST_DEPENDENCIES_AND_SIGNED_PUBLIC_ARTIFACTS",
        "status": "PASS", "receipt": str(receipt_path), "receipt_sha256": digest(receipt_path),
        "verifier_sha256": digest(Path(__file__)), "current_inputs_checked": len(checked_inputs), "totals": totals,
        "public_artifacts_checked": len(check["public_certificate_artifacts"]),
        "documentation_verified": False, "deployment_verified": False, "worker_identity_verified": False,
        "customer_success_verified": False}
    with output.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
