#!/usr/bin/env bash
# ============================================================================
# 跑一个压测臂：输出 QPS / 延迟分位 / 状态码，并附上 MySQL 与 Redis 的计数器增量。
#
# 为什么不能只看延迟：本机是 Windows + Docker Desktop，
#   - MySQL / Redis 都跑在容器里，走端口转发，单次往返比 Linux 原生慢一个量级；
#   - 布隆过滤器一次请求要 exists + 7×getbit = 8 次往返 → 吞吐被往返次数卡住。
# 所以「MySQL 查询次数」和「Redis 命令次数」才是能扛住质疑的硬证据，延迟只作参考。
#
# 用法：
#   bench/run-arm.sh <label> <codes-file> <concurrency> <mode> [duration] [warmup] [url]
# 例：
#   bench/run-arm.sh A1-bloom-on bench/invalid.txt 50 loop 15 5
#   bench/run-arm.sh B1-cold     bench/codes.txt   50 once
# ============================================================================
set -u
LABEL="${1:?label}"; CODES="${2:?codes file}"; CONC="${3:-50}"; MODE="${4:-loop}"
DUR="${5:-15}"; WARM="${6:-5}"; URL="${7:-http://localhost:8080/%s}"
cd "$(dirname "$0")/.." || exit 1

R=$(grep '^MYSQL_ROOT_PASSWORD' .env | cut -d= -f2 | tr -d '\r')
RW=$(grep '^REDIS_PASSWORD' .env | cut -d= -f2 | tr -d '\r')

my() { docker exec shortlink-mysql-1 mysql -uroot -p"$R" -N -e \
        "SHOW GLOBAL STATUS LIKE '$1';" 2>/dev/null | grep -v Warning | awk '{print $2}'; }
rc() { docker exec shortlink-redis-1 redis-cli -a "$RW" --no-auth-warning INFO commandstats 2>/dev/null \
        | tr -d '\r' | grep "^cmdstat_$1:" | sed 's/^[^:]*:calls=\([0-9]*\).*/\1/'; }

MYS="Com_select Com_insert Com_update"
RDIS="get getbit set exists evalsha eval rpush hset incr expire"

declare -A M0 R0
for k in $MYS; do M0[$k]=$(my "$k"); done
for k in $RDIS; do R0[$k]=$(rc "$k"); done
T0=$(date +%s.%N)

java bench/LoadGen.java --url "$URL" --codes "$CODES" --concurrency "$CONC" \
     --mode "$MODE" --duration "$DUR" --warmup "$WARM" --label "$LABEL"

T1=$(date +%s.%N)
echo "-------------------------------- 计数器增量 --------------------------------"
printf "MySQL  : "
for k in $MYS; do v=$(my "$k"); printf "%s=%s  " "$k" "$((v - ${M0[$k]:-0}))"; done
echo
printf "Redis  : "
for k in $RDIS; do v=$(rc "$k"); d=$(( ${v:-0} - ${R0[$k]:-0} )); [ "$d" -ne 0 ] && printf "%s=%s  " "$k" "$d"; done
echo
awk -v a="$T0" -v b="$T1" 'BEGIN{printf "墙钟   : %.2f s\n", b-a}'
echo "============================================================================"
