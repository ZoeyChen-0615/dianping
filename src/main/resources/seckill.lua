-- 1. Args
-- 1.1 voucher ID
local voucherId = ARGV[1]
-- 1.2 user ID
local userId = ARGV[2]

-- 2. Redis keys
-- 2.1 stock key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 order key
local orderKey = 'seckill:order:' .. voucherId

-- 3. Script logic
-- 3.1 check stock
if(tonumber(redis.call('get',stockKey)) <= 0)then
    -- 3.2 out of stock: return 1
    return 1
end
-- 3.2 check whether the user already ordered
if(redis.call('sismember',orderKey,userId) == 1) then
    -- 3.3 exists: duplicate order
    return 2
end
-- 3.4 decrement stock
redis.call('incrby',stockKey,-1)
-- 3.5 place the order and record the user
redis.call('sadd',orderKey,userId)
return 0