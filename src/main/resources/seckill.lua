---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by zhou.
--- DateTime: 2023/6/29 19:13
---

--- 1.参数列表
--- 1.1 优惠券id
local voucherId = ARGV[1]
--- 1.2 用户id
local userId = ARGV[2]

--- 2.数据key
--- 2.1 库存key
local stockKey = 'seckill:stock:' .. voucherId
--- 2.2 订单key
local orderKey = 'seckill:order:' .. voucherId

--- 3.脚本业务
--- 3.1 判断库存是否充足
if (tonumber(redis.call("get", stockKey)) <= 0) then
    --- 3.2 库存不足
    return 1
end
--- 3.3 库存充足

--- 4.判断用户是否下过单
--- sismember用于判断一个元素是否存在于set中
if (redis.call('sismember', orderKey, userId) == 1) then
    --- 4.1 用户下过单
    return 2
end
--- 4.2 用户未下单

--- 5.库存减一
redis.call("incrby", stockKey, -1)

--- 6.下单，保存用户到orderKey
--- set集合在添加第一个元素的时候会自动创建
redis.call("sadd", orderKey, userId)

return 0