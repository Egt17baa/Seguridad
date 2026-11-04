#!/usr/bin/env python3
"""Test-fixture generator for Clavero 1.4.0 (Task 10): encrypted exports of Bitwarden, Aegis and 2FAS.

This is NOT part of the app. It was run once to write the files under app/src/test/resources/import/ (each encrypted
file next to its plain twin); the tests read those committed bytes and never run this script. Running it again gives
new random salts, IVs and keys, so the committed files would change: only do it on purpose, and commit the output.

It is an independent implementation, written only from public format documentation (no code was read or copied from
Bitwarden, Aegis, 2FAS, KeePassXC or any other importer), so that the Kotlin readers are checked against bytes they did
not write themselves. Sources:

- Bitwarden, password-protected JSON export («.json (Encrypted)» with «Protegida con contraseña»):
  - https://bitwarden.com/help/encrypted-export/ (password + salt -> KDF -> HKDF stretch -> encryption key and MAC);
  - https://community.bitwarden.com/t/new-backup-options-for-bitwarden-academic-questions/45338 (Bitwarden staff: the
    fields encrypted, passwordProtected, salt, kdfType, kdfIterations, encKeyValidation_DO_NOT_EDIT, data; the salt is
    random per export and stored in the file; the key is made with PBKDF2 and stretched with HKDF-Expand into an
    encryption part and a MAC part; encKeyValidation is a short string encrypted with the same key);
  - Bitwarden's help page «crypto.html» (https://github.com/bitwarden/help, crypto.html; read in the fork
    https://github.com/Jipje/help/blob/master/crypto.html, prose and labels only): PBKDF2-SHA256 over the UTF-8
    password with the salt string in UTF-8, 256-bit output; HKDF-Expand (HMAC-SHA256) with the infos «enc» and «mac»,
    32 bytes each; the cipher string type 2 is «2.iv|ct|mac», each part Base64, a random 16-byte IV, AES-256-CBC, and
    HMAC-SHA256 with the MAC key over IV || ciphertext, checked before decrypting;
  - https://bitwarden.com/help/kdf-algorithms/ (PBKDF2-SHA256 or Argon2id; Argon2id memory in MiB);
  - Argon2id salt: the Bitwarden developer who added Argon2id said on the community forum (thread «Argon2 KDF Support»,
    https://community.bitwarden.com/t/argon2-kdf-support/48825, page 2, no longer online when this was written; seen
    through a search result) that the salt is hashed with SHA-256 first, giving a 32-byte salt. FROM DOCUMENTATION,
    NOT YET CONFIRMED AGAINST A REAL EXPORT: here the Argon2id salt is SHA-256(UTF-8 of the «salt» string).
- Aegis, encrypted vault export: https://github.com/beemdevelopment/Aegis/blob/master/docs/vault.md (header.slots, the
  password slot type 1 with scrypt n, r, p and a hex salt; the master key wrapped with AES-256-GCM, key/nonce/tag in hex;
  db is Base64 with padding, opened with the master key and header.params nonce and tag; content version 3).
- 2FAS, encrypted backup: no format document from 2FAS was found. Used: Clavero's design (spec «2FAS»:
  servicesEncrypted = Base64(ciphertext and tag):Base64(salt):Base64(IV), PBKDF2-HMAC-SHA256, AES-256-GCM); the README of
  https://github.com/AlexanderQueen/2FAS-GUI-Backup-Decryption-Tool (PBKDF2-HMAC-SHA256, 10 000 iterations, 256-bit key,
  AES-GCM); and the README of https://github.com/elliotwutingfeng/2fas-backup-decryptor (the plaintext is the JSON array
  of services). FROM DOCUMENTATION, NOT YET CONFIRMED AGAINST A REAL EXPORT: the salt length (here 256 bytes), the IV
  length (here 12), that groups stay in clear, and the absence of an iteration count in the file.

All the data is invented. Usage (from the repository root): python3 -I scripts/import-fixtures/gen_encrypted.py
"""

import base64
import hashlib
import hmac
import json
import os
import sys

from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.argon2 import Argon2id
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

PASSWORD = "prueba-clavero-1.4"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "test", "resources", "import")


def b64(b: bytes) -> str:
    return base64.b64encode(b).decode("ascii")


def write(rel: str, text: str) -> None:
    path = os.path.normpath(os.path.join(OUT, rel))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("wrote", os.path.relpath(path))


def random_base32(n_bytes: int = 20) -> str:
    return base64.b32encode(os.urandom(n_bytes)).decode("ascii").rstrip("=")


# ---------------------------------------------------------------- Bitwarden


def hkdf_expand_sha256(prk: bytes, info: bytes, length: int) -> bytes:
    """RFC 5869 HKDF-Expand with HMAC-SHA256 (no Extract step)."""
    out = b""
    block = b""
    counter = 1
    while len(out) < length:
        block = hmac.new(prk, block + info + bytes([counter]), hashlib.sha256).digest()
        out += block
        counter += 1
    return out[:length]


def bitwarden_enc_string(enc_key: bytes, mac_key: bytes, plaintext: bytes) -> str:
    """Cipher string type 2: «2.» Base64(IV) | Base64(AES-256-CBC, PKCS#7) | Base64(HMAC-SHA256(mac, IV || ct))."""
    iv = os.urandom(16)
    padder = padding.PKCS7(128).padder()
    padded = padder.update(plaintext) + padder.finalize()
    enc = Cipher(algorithms.AES(enc_key), modes.CBC(iv)).encryptor()
    ct = enc.update(padded) + enc.finalize()
    mac = hmac.new(mac_key, iv + ct, hashlib.sha256).digest()
    return "2." + b64(iv) + "|" + b64(ct) + "|" + b64(mac)


def bitwarden_key(password: str, salt: str, kdf: dict) -> bytes:
    pw = password.encode("utf-8")
    if kdf["kdfType"] == 0:
        return hashlib.pbkdf2_hmac("sha256", pw, salt.encode("utf-8"), kdf["kdfIterations"], 32)
    salt_hash = hashlib.sha256(salt.encode("utf-8")).digest()  # From documentation only (see the header).
    return Argon2id(
        salt=salt_hash,
        length=32,
        iterations=kdf["kdfIterations"],
        lanes=kdf["kdfParallelism"],
        memory_cost=kdf["kdfMemory"] * 1024,  # kdfMemory is in MiB; the library takes KiB.
    ).derive(pw)


def bitwarden_protected(plain_text: str, kdf: dict) -> str:
    salt = b64(os.urandom(16))
    key = bitwarden_key(PASSWORD, salt, kdf)
    enc_key = hkdf_expand_sha256(key, b"enc", 32)
    mac_key = hkdf_expand_sha256(key, b"mac", 32)
    validation = bitwarden_enc_string(enc_key, mac_key, str(uuid4()).encode("ascii"))
    data = bitwarden_enc_string(enc_key, mac_key, plain_text.encode("utf-8"))
    doc = {"encrypted": True, "passwordProtected": True, "salt": salt}
    doc.update(kdf)
    doc["encKeyValidation_DO_NOT_EDIT"] = validation
    doc["data"] = data
    return json.dumps(doc, indent=2, ensure_ascii=False) + "\n"


def uuid4() -> str:
    b = bytearray(os.urandom(16))
    b[6] = (b[6] & 0x0F) | 0x40
    b[8] = (b[8] & 0x3F) | 0x80
    h = b.hex()
    return f"{h[0:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


def bitwarden_plain() -> str:
    work = uuid4()
    home = uuid4()
    doc = {
        "encrypted": False,
        "folders": [{"id": work, "name": "Trabajo"}, {"id": home, "name": "Casa ñandú"}],
        "items": [
            {
                "id": uuid4(), "organizationId": None, "folderId": work, "type": 1, "reprompt": 0,
                "name": "Correo de prueba", "notes": "Nota con acentos: áéíóú", "favorite": True,
                "fields": [{"name": "PIN", "value": "4821", "type": 1, "linkedId": None}],
                "login": {
                    "uris": [{"match": None, "uri": "https://correo.example.com/"}, {"match": None, "uri": "https://otro.example.com/"}],
                    "username": "ana." + os.urandom(3).hex() + "@example.com",
                    "password": "Pw-" + os.urandom(9).hex(),
                    "totp": "otpauth://totp/Correo:ana?secret=" + random_base32() + "&issuer=Correo&digits=6&period=30",
                },
                "collectionIds": None,
            },
            {
                "id": uuid4(), "organizationId": None, "folderId": home, "type": 1, "reprompt": 0,
                "name": "Tienda 🛒", "notes": None, "favorite": False,
                "login": {"uris": [{"match": None, "uri": "https://tienda.example.org/entrar"}], "username": "luis",
                          "password": "Clave-" + os.urandom(6).hex(), "totp": random_base32()},
                "collectionIds": None,
            },
            {
                "id": uuid4(), "organizationId": None, "folderId": None, "type": 2, "reprompt": 0,
                "name": "Nota segura", "notes": "Línea 1\nLínea 2", "favorite": False, "secureNote": {"type": 0},
                "collectionIds": None,
            },
            {
                "id": uuid4(), "organizationId": None, "folderId": None, "type": 3, "reprompt": 0,
                "name": "Tarjeta inventada", "notes": None, "favorite": False,
                "card": {"cardholderName": "Ana Prueba", "brand": "Visa", "number": "4" + str(int.from_bytes(os.urandom(6), "big")).zfill(15)[:15],
                         "expMonth": "7", "expYear": "2031", "code": "123"},
                "collectionIds": None,
            },
        ],
    }
    return json.dumps(doc, indent=2, ensure_ascii=False) + "\n"


# ---------------------------------------------------------------- Aegis


def aegis_content() -> dict:
    g1 = uuid4()
    g2 = uuid4()

    def entry(kind, name, issuer, secret, algo="SHA1", digits=6, period=30, note="", groups=(), favorite=False):
        info = {"secret": secret, "algo": algo, "digits": digits}
        if kind == "hotp":
            info["counter"] = 3
        else:
            info["period"] = period
        return {"type": kind, "uuid": uuid4(), "name": name, "issuer": issuer, "note": note, "favorite": favorite,
                "icon": None, "icon_mime": None, "icon_hash": None, "info": info, "groups": list(groups)}

    return {
        "version": 3,
        "entries": [
            entry("totp", "ana@example.com", "Banco inventado", random_base32(), note="Nota de Aegis", groups=[g1], favorite=True),
            entry("totp", "luis", "Foro ñ", random_base32(32), algo="SHA256", digits=8, period=60, groups=[g1, g2]),
            entry("totp", "", "Sin cuenta", random_base32(), algo="SHA512"),
            entry("hotp", "contador", "HOTP de prueba", random_base32()),
            entry("steam", "jugador", "Juego", random_base32(), digits=5),
        ],
        "groups": [{"uuid": g1, "name": "Personal"}, {"uuid": g2, "name": "Viajes ✈"}],
    }


def aegis_files() -> tuple:
    content = aegis_content()
    plain = {"version": 1, "header": {"slots": None, "params": None}, "db": content}
    master = os.urandom(32)
    salt = os.urandom(32)
    n, r, p = 1 << 15, 8, 1
    derived = hashlib.scrypt(PASSWORD.encode("utf-8"), salt=salt, n=n, r=r, p=p, maxmem=128 * 1024 * 1024, dklen=32)
    slot_nonce = os.urandom(12)
    wrapped = AESGCM(derived).encrypt(slot_nonce, master, None)
    db_nonce = os.urandom(12)
    sealed = AESGCM(master).encrypt(db_nonce, json.dumps(content, ensure_ascii=False).encode("utf-8"), None)
    enc = {
        "version": 1,
        "header": {
            "slots": [
                {"type": 1, "uuid": uuid4(), "key": wrapped[:-16].hex(),
                 "key_params": {"nonce": slot_nonce.hex(), "tag": wrapped[-16:].hex()},
                 "n": n, "r": r, "p": p, "salt": salt.hex(), "repaired": True, "is_backup": False},
            ],
            "params": {"nonce": db_nonce.hex(), "tag": sealed[-16:].hex()},
        },
        "db": b64(sealed[:-16]),
    }
    return (json.dumps(plain, indent=2, ensure_ascii=False) + "\n", json.dumps(enc, indent=2, ensure_ascii=False) + "\n")


# ---------------------------------------------------------------- 2FAS


def twofas_files() -> tuple:
    group = "g-" + uuid4()

    def service(name, issuer, account, algo="SHA1", digits=6, period=30, kind="TOTP", group_id=None):
        s = {"name": name, "secret": random_base32(), "updatedAt": 1762300000000,
             "otp": {"label": issuer + ":" + account, "account": account, "issuer": issuer, "digits": digits,
                     "period": period, "algorithm": algo, "tokenType": kind, "source": "Manual"},
             "order": {"position": 0}, "icon": {"selected": "Label", "label": {"text": name[:2].upper(), "backgroundColor": "Default"}}}
        if group_id:
            s["groupId"] = group_id
        return s

    services = [
        service("Nube inventada", "Nube inventada", "ana@example.com", group_id=group),
        service("Código ñ", "Código ñ", "luis", algo="SHA256", digits=8, period=60),
        service("Juego", "Juego", "jugador", digits=5, kind="STEAM"),
    ]
    groups = [{"id": group, "name": "Trabajo", "isExpanded": True}]
    plain = {"services": services, "groups": groups, "updatedAt": 1762300000000, "schemaVersion": 4, "appVersionCode": 5000000,
             "appVersionName": "5.0.0", "appOrigin": "android"}
    salt = os.urandom(256)
    iv = os.urandom(12)
    key = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=10_000).derive(PASSWORD.encode("utf-8"))
    sealed = AESGCM(key).encrypt(iv, json.dumps(services, ensure_ascii=False).encode("utf-8"), None)
    enc = {"services": [], "groups": groups, "updatedAt": 1762300000000, "schemaVersion": 4, "appVersionCode": 5000000,
           "appVersionName": "5.0.0", "appOrigin": "android",
           "servicesEncrypted": b64(sealed) + ":" + b64(salt) + ":" + b64(iv)}
    return (json.dumps(plain, indent=2, ensure_ascii=False) + "\n", json.dumps(enc, indent=2, ensure_ascii=False) + "\n")


def main() -> int:
    plain = bitwarden_plain()
    write("bitwarden/bitwarden-protegido-claro.json", plain)
    write("bitwarden/bitwarden-protegido-pbkdf2.json", bitwarden_protected(plain, {"kdfType": 0, "kdfIterations": 100_000}))
    write("bitwarden/bitwarden-protegido-argon2id.json",
          bitwarden_protected(plain, {"kdfType": 1, "kdfIterations": 3, "kdfMemory": 16, "kdfParallelism": 4}))
    a_plain, a_enc = aegis_files()
    write("aegis/aegis-cifrado-claro.json", a_plain)
    write("aegis/aegis-cifrado.json", a_enc)
    t_plain, t_enc = twofas_files()
    write("2fas/2fas-cifrado-claro.2fas", t_plain)
    write("2fas/2fas-cifrado.2fas", t_enc)
    return 0


if __name__ == "__main__":
    sys.exit(main())
