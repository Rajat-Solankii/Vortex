"""Publish Vortex full-release APKs to a Telegram channel using Pyrofork (MTProto).

Standard Telegram Bot HTTP API caps uploads at 50 MB, whereas MTProto with
api_id/api_hash supports bot uploads up to 2 GB per file.
"""

import asyncio
import html
import os
import subprocess
import sys
from pathlib import Path

APK_TARGETS: list[tuple[str, str, str, str]] = [
    (
        "androidApp/build/outputs/apk/full/release/androidApp-full-arm64-v8a-release.apk",
        "Vortex-arm64-v8a-release.apk",
        "arm64-v8a",
        "<b>arm64-v8a:</b> Modern phones (recommended)",
    ),
    (
        "androidApp/build/outputs/apk/full/release/androidApp-full-universal-release.apk",
        "Vortex-universal-release.apk",
        "universal",
        "<b>universal:</b> Works on all phones (larger size)",
    ),
    (
        "androidApp/build/outputs/apk/full/release/androidApp-full-armeabi-v7a-release.apk",
        "Vortex-armeabi-v7a-release.apk",
        "armeabi-v7a",
        "<b>armeabi-v7a:</b> Older / budget 32-bit phones",
    ),
    (
        "androidApp/build/outputs/apk/full/release/androidApp-full-x86_64-release.apk",
        "Vortex-x86_64-release.apk",
        "x86_64",
        "<b>x86_64:</b> Emulators &amp; Chromebooks",
    ),
    (
        "androidApp/build/outputs/apk/full/release/androidApp-full-x86-release.apk",
        "Vortex-x86-release.apk",
        "x86",
        "<b>x86:</b> Legacy 32-bit emulators",
    ),
]

MAX_MESSAGE_CHARS = 4096


def parse_chat_id(raw_chat_id: str) -> int | str:
    """Convert numeric chat IDs (-100...) to int and normalize @usernames."""
    cleaned = raw_chat_id.strip()
    lowered = cleaned.lower()
    if "t.me/+" in lowered or "joinchat/" in lowered:
        raise ValueError(
            "Private channels must use the numeric ID (e.g. -1001234567890) in TELEGRAM_CHAT_ID, "
            "not an invite link (t.me/+...)."
        )
    if "t.me/" in lowered:
        handle = cleaned.split("t.me/", 1)[1].strip().strip("/")
        return handle if handle.startswith("@") else f"@{handle}"
    if cleaned.lstrip("-").isdigit():
        return int(cleaned)
    return cleaned


def collect_apks(root: Path = Path(".")) -> list[tuple[Path, str, str, str]]:
    """Return existing release APK tuples (path, display_name, abi, install_hint)."""
    found: list[tuple[Path, str, str, str]] = []
    for rel_path, display_name, abi, hint in APK_TARGETS:
        candidate = root / rel_path
        if candidate.is_file():
            found.append((candidate, display_name, abi, hint))
    return found


def format_changelog_line(line: str) -> str:
    """Format one commit subject line as a safe HTML bullet with keyword emoji."""
    cleaned = line.strip()
    if not cleaned:
        return ""
    if cleaned.startswith(("•", "-", "*")):
        cleaned = cleaned.lstrip("•-*").strip()

    lowered = cleaned.lower()
    if any(k in lowered for k in ("perf", "speed", "fast", "optimis", "optimiz", "resource")):
        emoji = "⚡"
    elif any(k in lowered for k in ("fix", "bug", "crash", "error", "resolve", "unreachable", "backup")):
        emoji = "🐛"
    elif any(k in lowered for k in ("feat", "add", "new", "implement", "support")):
        emoji = "✨"
    elif any(k in lowered for k in ("ui", "ux", "layout", "portrait", "theme", "design", "poster")):
        emoji = "🎨"
    elif any(k in lowered for k in ("ci", "workflow", "gradle", "build", "release", "bot", "telegram")):
        emoji = "🔧"
    else:
        emoji = "•"

    if ":" in cleaned:
        prefix, suffix = cleaned.split(":", 1)
        prefix = prefix.strip()
        suffix = suffix.strip()
        if suffix:
            suffix = suffix[0].upper() + suffix[1:]
        return f"• {emoji} <b>{html.escape(prefix)}:</b> {html.escape(suffix)}"

    cleaned = cleaned[0].upper() + cleaned[1:]
    return f"• {emoji} {html.escape(cleaned)}"


def get_commit_info() -> tuple[str, str]:
    """Read author name and commit message for HEAD."""
    try:
        author = subprocess.check_output(
            ["git", "log", "-1", "--pretty=format:%an"]
        ).decode("utf-8", errors="replace").strip()
        message = subprocess.check_output(
            ["git", "log", "-1", "--pretty=format:%B"]
        ).decode("utf-8", errors="replace").strip()
        message = "\n".join(line for line in message.splitlines() if line.strip())
    except Exception:
        author = os.environ.get("GITHUB_ACTOR", "Unknown")
        message = "New release build"
    return author or "Unknown", message or "New release build"


def _truncate_text(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    return text[: max(0, limit - 3)].rstrip() + "..."


def build_message_html(
    *,
    version: str,
    version_code: str,
    commit_sha: str,
    commit_author: str,
    commit_message: str,
    apks: list[tuple[Path, str, str, str]],
    changelog_text: str = "",
    is_release: bool = False,
) -> str:
    """Build the Telegram HTML summary post matching the PixelMusic layout."""
    abis_str = ", ".join(abi for _, _, abi, _ in apks)
    hints_block = "\n".join(f"• {hint}" for _, _, _, hint in apks)
    version_label = f"v{html.escape(version)}" if version else "latest"
    build_label = f"{version_label} ({html.escape(version_code)})" if version_code else version_label
    short_hash = html.escape(commit_sha[:7] or "unknown")

    if is_release:
        raw_lines = [line for line in changelog_text.splitlines() if line.strip()] or [commit_message]
        formatted_lines = [format_changelog_line(line) for line in raw_lines[:15]]
        changelog_block = "\n".join(line for line in formatted_lines if line)
        template_prefix = (
            f"🎬 <b>Vortex {version_label} Release</b> 🎬\n\n"
            f"Commit by: {html.escape(commit_author)}\n"
            f"Commit hash: #{short_hash}\n"
            f"Build: {build_label}\n"
            f"Device: mobile\n"
            f"ABI: {html.escape(abis_str)}\n"
            f"Files: {len(apks)}\n"
            f"Version: Android &gt;= 7.0\n\n"
            f"🚀 <b>What's New:</b>\n"
            f"<blockquote>"
        )
        template_suffix = (
            f"</blockquote>\n\n"
            f"💡 <b>Which APK to install?</b>\n"
            f"<blockquote>{hints_block}</blockquote>"
        )
        budget = max(200, MAX_MESSAGE_CHARS - len(template_prefix) - len(template_suffix))
        safe_changelog = _truncate_lines_within_budget(formatted_lines, budget)
        return f"{template_prefix}{safe_changelog}{template_suffix}"

    extra_lines = [line.strip() for line in changelog_text.splitlines() if line.strip()]
    extra_commits_section = ""
    if len(extra_lines) > 1:
        safe_bullets = [f"• {html.escape(_truncate_text(line, 180))}" for line in extra_lines[:8]]
        bullets = "\n".join(safe_bullets)
        extra_commits_section = f"\n📝 <b>Changes in push:</b>\n<blockquote>{bullets}</blockquote>\n"

    prefix = (
        f"🎬 <b>Vortex Release Build</b> 🎬\n\n"
        f"Commit by: {html.escape(commit_author)}\n"
        f"Commit message:\n<blockquote>"
    )
    suffix = (
        f"</blockquote>\n"
        f"Commit hash: #{short_hash}\n"
        f"Build: {build_label}\n"
        f"Device: mobile\n"
        f"ABI: {html.escape(abis_str)}\n"
        f"Files: {len(apks)}\n"
        f"Version: Android &gt;= 7.0\n"
        f"{extra_commits_section}\n"
        f"💡 <b>Which APK to install?</b>\n"
        f"<blockquote>{hints_block}</blockquote>"
    )
    budget = max(120, MAX_MESSAGE_CHARS - len(prefix) - len(suffix))
    safe_commit_message = html.escape(_truncate_text(commit_message, min(1200, budget // 2 if "<" in commit_message or "&" in commit_message else budget)))
    if len(safe_commit_message) > budget:
        safe_commit_message = safe_commit_message[: max(0, budget - 3)].rsplit("&", 1)[0] + "..."
    return f"{prefix}{safe_commit_message}{suffix}"


def _truncate_lines_within_budget(html_lines: list[str], budget: int) -> str:
    kept: list[str] = []
    used = 0
    for line in html_lines:
        if not line:
            continue
        cost = len(line) + (1 if kept else 0)
        if used + cost > budget:
            break
        kept.append(line)
        used += cost
    return "\n".join(kept) if kept else "• New release build"


async def publish() -> None:
    from pyrogram import Client
    from pyrogram.enums import ParseMode

    api_id = int(os.environ["TELEGRAM_API_ID"])
    api_hash = os.environ["TELEGRAM_API_HASH"]
    bot_token = os.environ["TELEGRAM_BOT_TOKEN"]
    chat_id = parse_chat_id(os.environ["TELEGRAM_CHAT_ID"])
    raw_thread_id = os.environ.get("TELEGRAM_THREAD_ID", "").strip()
    reply_to = int(raw_thread_id) if raw_thread_id.isdigit() else None

    version = os.environ.get("VERSION_NAME", "").strip()
    version_code = os.environ.get("VERSION_CODE", "").strip()
    commit_sha = os.environ.get("COMMIT_SHA", "").strip()
    changelog_text = os.environ.get("CHANGELOG_TEXT", "").strip()
    is_release = os.environ.get("IS_RELEASE", "false").strip().lower() == "true"

    apks = collect_apks()
    if not apks:
        print("ERROR: No release APKs found under androidApp/build/outputs/apk/full/release/", file=sys.stderr)
        sys.exit(1)

    for apk_path, display_name, _, _ in apks:
        size_mb = apk_path.stat().st_size / (1024 * 1024)
        print(f"Found APK: {apk_path} -> {display_name} ({size_mb:.1f} MB)", flush=True)

    commit_author, commit_message = get_commit_info()
    message_html = build_message_html(
        version=version,
        version_code=version_code,
        commit_sha=commit_sha,
        commit_author=commit_author,
        commit_message=commit_message,
        apks=apks,
        changelog_text=changelog_text,
        is_release=is_release,
    )

    async with Client(
        name="vortex_release_publisher",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
        in_memory=True,
        workers=8,
        max_concurrent_transmissions=4,
        sleep_threshold=60,
    ) as app:
        try:
            resolved_chat = await app.get_chat(chat_id)
            target_chat_id: int | str = resolved_chat.id
            print(f"Resolved channel: {resolved_chat.title} ({target_chat_id})", flush=True)
        except Exception as exc:
            print(
                f"ERROR: Could not resolve Telegram channel {chat_id!r}. "
                "Verify the bot is added as an Admin with Post Messages permission, or use a public @username if private peer lookup fails.",
                file=sys.stderr,
            )
            raise exc

        print(f"Sending build summary to {target_chat_id}...", flush=True)
        await app.send_message(
            chat_id=target_chat_id,
            text=message_html,
            parse_mode=ParseMode.HTML,
            reply_to_message_id=reply_to,
            disable_web_page_preview=True,
        )

        for apk_path, display_name, _, _ in apks:
            size_mb = apk_path.stat().st_size / (1024 * 1024)
            print(f"Uploading {display_name} ({size_mb:.1f} MB)...", flush=True)
            for attempt in range(1, 4):
                try:
                    await app.send_document(
                        chat_id=target_chat_id,
                        document=str(apk_path),
                        file_name=display_name,
                        reply_to_message_id=reply_to,
                        force_document=True,
                    )
                    print(f"Uploaded {display_name}", flush=True)
                    break
                except Exception as exc:
                    print(f"[Attempt {attempt}/3] Failed uploading {display_name}: {exc}", flush=True)
                    if attempt == 3:
                        raise
                    await asyncio.sleep(5 * attempt)

    print("All release APKs published to Telegram successfully.", flush=True)


if __name__ == "__main__":
    asyncio.run(publish())
