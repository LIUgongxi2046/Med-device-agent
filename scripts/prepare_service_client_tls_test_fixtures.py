"""Ephemeral unit-test material only; never imports trust or registers a service.

Caller must pass a fresh child named service-client-tls-test-fixtures under its
private test temp directory and remove the entire tree after the test.
"""
import argparse
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import ipaddress
import json
from pathlib import Path
from uuid import uuid4
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


def generate(output):
    if output.name != 'service-client-tls-test-fixtures' or output.exists() or not output.parent.is_dir():
        raise ValueError('FRESH_UNIT_TEST_DIRECTORY_REQUIRED')
    output.mkdir(mode=0o700)
    def save(name, body):
        path = output/name
        with path.open('xb') as stream: stream.write(body)
        path.chmod(0o600)
    def der(cert): return cert.public_bytes(serialization.Encoding.DER)
    def encoded(value): return base64.b64encode(value).decode('ascii')
    def sha(cert): return hashlib.sha256(der(cert)).hexdigest()
    def document(name, value): save(name, (json.dumps(value, sort_keys=True)+'\n').encode())
    now=datetime.now(timezone.utc); revision=str(uuid4())
    ca_key=ec.generate_private_key(ec.SECP256R1()); name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'EPHEMERAL TEST CA '+str(uuid4()))])
    ca=(x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(ca_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=2)).not_valid_after(now+timedelta(hours=1)).add_extension(x509.BasicConstraints(ca=True,path_length=0),True).add_extension(x509.KeyUsage(True,False,False,False,False,True,True,False,False),True).add_extension(x509.SubjectKeyIdentifier.from_public_key(ca_key.public_key()),False).sign(ca_key,hashes.SHA256()))
    save('ca.der',der(ca)); issued={}; keys={}
    for label,uri,server in [('client','urn:medical:test:host',False),('other-client','urn:medical:test:other-host',False),('server','urn:medical:test:supervisor',True),('other-server','urn:medical:test:other-supervisor',True),('wrong-eku','urn:medical:test:supervisor',False)]:
        key=ec.generate_private_key(ec.SECP256R1()); names=[x509.UniformResourceIdentifier(uri),x509.DNSName('localhost'),x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]
        cert=(x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'EPHEMERAL TEST '+label)])).issuer_name(ca.subject).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=2)).not_valid_after(now+timedelta(minutes=30)).add_extension(x509.BasicConstraints(ca=False,path_length=None),True).add_extension(x509.KeyUsage(True,False,False,False,False,False,False,False,False),True).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH if server else ExtendedKeyUsageOID.CLIENT_AUTH]),False).add_extension(x509.SubjectAlternativeName(names),False).add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()),False).sign(ca_key,hashes.SHA256()))
        issued[label]=(cert,uri); keys[label]=key; save(label+'.der',der(cert))
        save(label+'.p12',pkcs12.serialize_key_and_certificates(label.encode(),key,cert,[ca],serialization.BestAvailableEncryption(b'test-only')))
    crls={}
    for label,revoked,expired in [('current',False,False),('revoked',True,False),('expired',False,True)]:
        builder=x509.CertificateRevocationListBuilder().issuer_name(ca.subject).last_update(now-timedelta(minutes=10)).next_update(now-timedelta(minutes=1) if expired else now+timedelta(minutes=30)).add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()),False).add_extension(x509.CRLNumber(2 if revoked else 1),False)
        if revoked: builder=builder.add_revoked_certificate(x509.RevokedCertificateBuilder().serial_number(issued['server'][0].serial_number).revocation_date(now-timedelta(minutes=1)).build())
        crls[label]=builder.sign(ca_key,hashes.SHA256()).public_bytes(serialization.Encoding.DER); save(label+'.crl.der',crls[label])
    registrations={}
    for cert,uri in issued.values(): registrations.setdefault(uri,[]).append(sha(cert))
    trust={'schema_version':'1.0.0','revision':revision,'authorities':[encoded(der(ca))],'revocations':[encoded(crls['current'])],'registered_certificates':registrations,'protocols':['TLSv1.3'],'ciphers':['TLS_AES_128_GCM_SHA256','TLS_AES_256_GCM_SHA384','TLS_CHACHA20_POLY1305_SHA256']}
    document('trust.json',trust)
    for label in ('client','other-client'):
        cert,uri=issued[label]; value={'schema_version':'1.0.0','service_uri':uri,'certificate_sha256':sha(cert),'trust_revision':revision,'key_algorithm':'EC','private_key_pkcs8':encoded(keys[label].private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption())),'certificate_chain':[encoded(der(cert)),encoded(der(ca))]}
        document('client-tls.json' if label=='client' else 'other-client.json',value)
    document('manifest.json',{'purpose':'EPHEMERAL UNIT TEST ONLY','trust_revision':revision,'server_service_uri':issued['server'][1],'server_sha256':sha(issued['server'][0]),'wrong_eku_sha256':sha(issued['wrong-eku'][0])})

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);generate(parser.parse_args().output)
