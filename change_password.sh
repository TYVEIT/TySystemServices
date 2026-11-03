#!/usr/bin/env bash
#
# 计算 / 替换「开发调试」入口的口令哈希
#
# 用法：
#   ./tools/change_password.sh                交互输入密码，显示哈希，询问是否写回
#   ./tools/change_password.sh -p 'NewPass'   直接指定密码
#   ./tools/change_password.sh -p 'NewPass' -y  指定密码并直接写回源码
#   ./tools/change_password.sh -p 'NewPass' -n  只计算，不写回
#   ./tools/change_password.sh --check -p 'x'   只校验 x 是否是当前密码
#
# Windows 请在 Git Bash 里运行（自带 sha256sum）。
#
# 说明：
#  - 盐和当前哈希都从 SecretGate.kt 里读取，脚本和代码永远保持一致；
#  - 一律用 printf '%s'，绝不加换行（echo 会多一个 \n，导致哈希永远对不上）；
#  - 写回前自动备份 SecretGate.kt.bak，写回后需要重新编译才生效。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GATE_FILE="./app/src/main/java/com/android/ty/systemservices/compose/ui/util/SecretGate.kt"

DEFAULT_SALT="TySystemServices@TeYun"
PASSWORD=""
MODE="apply-ask"   # apply-ask | apply-yes | apply-no | check

usage() {
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--password) PASSWORD="${2:-}"; shift 2 ;;
        -y|--yes)      [[ "$MODE" == "check" ]] || MODE="apply-yes"; shift ;;
        -n|--no)       [[ "$MODE" == "check" ]] || MODE="apply-no";  shift ;;
        -c|--check)    MODE="check";     shift ;;
        -h|--help)     usage ;;
        *) echo "未知参数：$1（-h 查看用法）" >&2; exit 1 ;;
    esac
done

if [[ ! -f "$GATE_FILE" ]]; then
    echo "找不到 SecretGate.kt：$GATE_FILE" >&2
    exit 1
fi

# 从源码读盐与当前哈希
SALT="$(sed -n -E 's/.*private const val SALT = "([^"]*)".*/\1/p' "$GATE_FILE" | head -n1)"
OLD_HASH="$(sed -n -E 's/.*private const val DIGEST = "([^"]*)".*/\1/p' "$GATE_FILE" | head -n1)"
SALT="${SALT:-$DEFAULT_SALT}"

if [[ -z "$PASSWORD" ]]; then
    if [[ ! -t 0 ]]; then
        echo "非交互环境请用 -p '密码' 指定" >&2
        exit 1
    fi
    read -r -s -p "新密码: " PASSWORD; echo
    read -r -s -p "再输一次: " PASSWORD2; echo
    if [[ "$PASSWORD" != "$PASSWORD2" ]]; then
        echo "两次输入不一致" >&2
        exit 1
    fi
fi

if [[ -z "$PASSWORD" ]]; then
    echo "密码不能为空" >&2
    exit 1
fi

hash_text() {
    if command -v sha256sum >/dev/null 2>&1; then
        printf '%s' "$1" | sha256sum | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
    else
        echo "未找到 sha256sum / shasum，请先安装 coreutils" >&2
        return 1
    fi
}

NEW_HASH="$(hash_text "${SALT}${PASSWORD}")"
OLD_HASH_LOWER="$(printf '%s' "$OLD_HASH" | tr '[:upper:]' '[:lower:]')"

echo
echo "盐        : $SALT"
echo "新密码    : (已隐藏，长度 ${#PASSWORD})"
echo "新哈希    : $NEW_HASH"
echo "代码当前  : ${OLD_HASH:-<未找到>}"
if [[ "$NEW_HASH" == "$OLD_HASH_LOWER" ]]; then
    echo "比对结果  : ✅ 一致，这个密码现在就能进"
else
    echo "比对结果  : ❌ 不一致，需要写回源码并重新编译"
fi
echo

if [[ "$MODE" == "check" ]]; then
    [[ "$NEW_HASH" == "$OLD_HASH_LOWER" ]] && exit 0 || exit 2
fi

if [[ "$MODE" == "apply-ask" ]]; then
    if [[ -t 0 ]]; then
        read -r -p "是否写回 SecretGate.kt？[y/N] " answer
        case "$answer" in
            [Yy]*) MODE="apply-yes" ;;
            *)     MODE="apply-no" ;;
        esac
    else
        MODE="apply-no"
    fi
fi

if [[ "$MODE" == "apply-yes" ]]; then
    if [[ "$NEW_HASH" == "$OLD_HASH_LOWER" ]]; then
        echo "哈希没变化，跳过写入。"
        exit 0
    fi

    cp "$GATE_FILE" "$GATE_FILE.bak"
    tmp="$(mktemp)"
    sed -E "s|(private const val DIGEST = \")[^\"]*(\")|\1${NEW_HASH}\2|" "$GATE_FILE" > "$tmp"
    mv "$tmp" "$GATE_FILE"

    if grep -q "$NEW_HASH" "$GATE_FILE"; then
        echo "已写入：$GATE_FILE"
        echo "已备份：$GATE_FILE.bak"
        echo
        echo "记得重新编译，否则装上的包还是旧密码："
        echo "    ./gradlew assembleRelease"
    else
        echo "写入失败，已保留备份，请手动替换 DIGEST。" >&2
        exit 1
    fi
else
    echo "未写入。需要手动替换这一行："
    echo "    private const val DIGEST = \"$NEW_HASH\""
fi
