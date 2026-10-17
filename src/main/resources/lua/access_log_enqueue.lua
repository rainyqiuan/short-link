-- KEYS[1]: 队列 key (RedisKeyConstant.ACCESS_QUEUE)
-- ARGV[1]: 日志 JSON 字符串
-- ARGV[2]: 队列封顶容量 cap (必须是字符串)
-- 返回值: 整数，三态
--   0  → 正常入队，未触发裁剪
--   >0 → 本次因封顶被裁掉的条数（每 push 一次最多裁 1 条；只有队列被外部灌过才会 >1）
--   -1 → cap 参数非法，本次既没入队也没裁剪（Java 侧据此打 error）
--
-- 为什么返回"条数"而不是 true/false：Java 侧要把它累加进计数器（droppedByCapTotal），
-- 在 flush 周期里打"本轮新增丢弃 N 条"。只返回布尔的话，就只能知道"发生过"，不知道"丢了多少"。
-- 为什么 cap 非法用 -1 而不是 0：0 已经是"正常入队"的语义，两者必须能区分开。

local queueKey = KEYS[1]
local logJson = ARGV[1]
local cap = tonumber(ARGV[2])  -- 必须先用 tonumber 转换，否则无法比较大小

-- 1. cap 校验：非法（非数字 / <= 0）就不入队、不裁剪，返回 -1 让 Java 侧看见
--    为什么不用"给个默认上限"兜底：那会把"其实没封顶"伪装成"封顶了"，
--    而且这个默认值会和 application.yml 的 access-queue-max-size 形成第二个真源（改一处漏一处）。
--    ⚠️ 也绝不能只跳过判断就直接执行 LTRIM：cap <= 0 时 -cap 是正数，
--       队列长度比它短 → LTRIM 的 start > stop → 整个队列被清空（实测 LLEN 3 → 0）。
if cap == nil or cap <= 0 then
    return -1
end

-- 2. 推入队列尾部（RPUSH）
redis.call('RPUSH', queueKey, logJson)

-- 3. 获取当前队列长度
local currentLen = redis.call('LLEN', queueKey)

-- 4. 超过上限才裁剪 —— 绝大多数次 push 走到这里都是不裁的（LLEN <= cap），
--    这也是把 RPUSH/LTRIM 合进一段脚本的收益之一：LTRIM 不再每次都空转。
if currentLen > cap then
    -- 计算需要丢弃多少条（一般是超出上限的部分）
    local dropped = currentLen - cap
    
    -- LTRIM -cap -1：只保留从倒数第 cap 个到最后一个元素（负下标从尾部数，-1 = 最后一个）
    -- 语义核对：currentLen = cap+1 时，起始下标是 (cap+1)-cap = 1 → 保留 index 1..cap，
    -- 正好只删掉最旧的 1 条。所以常态下 dropped 恒为 1；只有队列被外部一次灌进多条时才会 >1（见文件头）。
    redis.call('LTRIM', queueKey, -cap, -1)
    
    -- 返回本次丢弃的条数
    return dropped
end

-- 没超过上限，返回 0
return 0