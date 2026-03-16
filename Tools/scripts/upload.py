import contextlib
import json
import mimetypes
import os
import time
import uuid
from pathlib import Path
from sys import argv
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BOT_TOKEN = os.environ.get("TG_BOT_TOKEN") or "7350436755:AAEpoGCZXJg4TJP_VqJrnXD06qjLCLZfOTM"
DEFAULT_CHAT_ID = os.environ.get("TG_UPLOAD_CHAT_ID") or "-1003616714912"

artifacts_path = Path("artifacts")
test_version = argv[3] == "test" if len(argv) > 3 else None
metadata_chat_id = argv[4] if len(argv) > 4 else None


def find_apk(abi: str) -> Path | None:
    for artifact_dir in artifacts_path.glob("*"):
        if not artifact_dir.is_dir():
            continue
        for apk in artifact_dir.glob("*.apk"):
            if abi in apk.name:
                return apk
    return None


def get_commit_info() -> tuple[str, str, str]:
    commit_id_raw = os.environ.get("COMMIT_ID") or "unknown"
    commit_id = commit_id_raw[:7]
    commit_url = os.environ.get("COMMIT_URL") or "https://github.com/alexandeer1/Alexgram/commits"
    commit_message = os.environ.get("COMMIT_MESSAGE") or "unknown"
    return commit_id, commit_url, commit_message


def normalize_message(text: str) -> str:
    return (text or "").replace("\\n", "\n")


def get_ai_summary() -> str:
    ai_summary = os.environ.get("AI_SUMMARY", "")
    if ai_summary:
        return "\n\n<blockquote expandable>" + normalize_message(ai_summary) + "</blockquote>"
    return ""


def get_caption() -> str:
    commit_id, commit_url, commit_message = get_commit_info()
    preface = "Test version." if test_version else "Release version."
    caption = (
        f"{preface}\n\n"
        f"Commit Message:\n<blockquote expandable>{commit_message}</blockquote>\n\n"
        f"See commit details [{commit_id}]({commit_url})"
    )
    ai_summary = get_ai_summary()
    full_caption = caption + ai_summary
    if len(full_caption) <= 1024:
        return full_caption
    if len(caption) > 1024:
        caption = caption[:1020] + "..."
    if len(caption + ai_summary) > 1024:
        return caption
    return caption + ai_summary


def get_metadata() -> str:
    commit_id = "<code>" + (os.environ.get("COMMIT_ID") or "unknown")[:7] + "</code>"
    commit_message = "<code>" + (os.environ.get("COMMIT_MESSAGE") or "unknown") + "</code>"
    build_timestamp = "<code>" + (os.environ.get("BUILD_TIMESTAMP") or "-1") + "</code>"
    return build_timestamp + " " + commit_id + "\n" + commit_message


def get_documents() -> list[dict[str, str | Path]]:
    documents: list[dict[str, str | Path]] = []
    for abi in ["arm64-v8a"]:
        apk = find_apk(abi)
        if apk is not None:
            documents.append({"path": apk, "caption": ""})
    if not documents:
        documents.append({
            "path": Path("TMessagesProj/src/main/ic_launcher_nagram_block_round-playstore.png"),
            "caption": "",
        })
    documents[-1]["caption"] = get_caption()
    return documents


def encode_multipart(fields: dict[str, str], files: dict[str, tuple[str, bytes, str]]) -> tuple[bytes, str]:
    boundary = "----NagramXUpload" + uuid.uuid4().hex
    body = bytearray()

    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        body.extend(str(value).encode("utf-8"))
        body.extend(b"\r\n")

    for name, (filename, content, content_type) in files.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode())
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode())
        body.extend(content)
        body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode())
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def telegram_request(method: str, data: dict[str, str], files: dict[str, tuple[str, bytes, str]] | None = None) -> dict:
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/{method}"
    last_error: Exception | None = None

    for attempt in range(5):
        try:
            if files:
                body, content_type = encode_multipart(data, files)
                request = Request(url, data=body, method="POST")
                request.add_header("Content-Type", content_type)
            else:
                body = json.dumps(data).encode("utf-8")
                request = Request(url, data=body, method="POST")
                request.add_header("Content-Type", "application/json")

            with urlopen(request, timeout=300) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except HTTPError as error:
            error_payload = error.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(error_payload)
            except json.JSONDecodeError:
                raise RuntimeError(f"Telegram API HTTP {error.code}: {error_payload}") from error
        except URLError as error:
            last_error = error
            time.sleep(min(2 ** attempt, 10))
            continue

        if payload.get("ok"):
            return payload

        retry_after = payload.get("parameters", {}).get("retry_after")
        if retry_after is not None:
            wait_seconds = int(retry_after) + 1
            print(f"Telegram rate limited {method}, retrying in {wait_seconds}s")
            time.sleep(wait_seconds)
            continue

        description = payload.get("description", "Unknown Telegram API error")
        raise RuntimeError(f"Telegram API {method} failed: {description}")

    if last_error is not None:
        raise RuntimeError(f"Telegram API {method} failed after retries: {last_error}") from last_error
    raise RuntimeError(f"Telegram API {method} failed after retries")


def send_document(chat_id: str, file_path: Path, caption: str = "") -> None:
    mime_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
    fields = {
        "chat_id": str(chat_id),
        "parse_mode": "HTML",
        "disable_notification": "true",
    }
    if caption:
        fields["caption"] = caption
    files = {
        "document": (file_path.name, file_path.read_bytes(), mime_type),
    }
    telegram_request("sendDocument", fields, files)


def send_message(chat_id: str, text: str) -> None:
    telegram_request("sendMessage", {
        "chat_id": str(chat_id),
        "text": text,
        "parse_mode": "HTML",
        "disable_notification": "true",
    })


def send_to_channel(chat_id: str) -> None:
    documents = get_documents()
    for index, document in enumerate(documents):
        send_document(chat_id, document["path"], str(document["caption"] or ""))
        if index != len(documents) - 1:
            time.sleep(1)


def send_metadata(chat_id: str) -> None:
    send_message(chat_id, get_metadata())


def main() -> None:
    send_to_channel(DEFAULT_CHAT_ID)

    with contextlib.suppress(Exception):
        if metadata_chat_id:
            send_metadata(metadata_chat_id)


if __name__ == "__main__":
    main()
