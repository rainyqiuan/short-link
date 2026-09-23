#!/usr/bin/env bash
# M2 手工验收脚本：应用启动后逐个调用，肉眼看返回体。
# 用法：bash docs/smoke-m2.sh   （Windows Git Bash 可直接跑）
BASE=http://localhost:8080

echo "===== 用例1 正常成功（期望 code=0）====="
curl -s -i "$BASE/api/demo/ok?name=qiuan" | head -20
echo -e "\n"

echo "===== 用例2 业务异常（期望 code=40005，HTTP 200）====="
curl -s -i "$BASE/api/demo/business" | head -20
echo -e "\n"

echo "===== 用例2b 业务异常+自定义message（期望 code=40006）====="
curl -s -i "$BASE/api/demo/business-custom" | head -20
echo -e "\n"

echo "===== 用例2c 服务端业务码（期望 code=50001）====="
curl -s -i "$BASE/api/demo/business-server" | head -20
echo -e "\n"

echo "===== 用例3 body校验失败（期望 code=40001，message=url 不能为空）====="
curl -s -i -X POST "$BASE/api/demo/validate-body" \
     -H "Content-Type: application/json" \
     -d '{"url":""}' | head -20
echo -e "\n"

echo "===== 用例4 参数校验失败（期望 code=40001，message=短码只能是 4-16 位字母数字）====="
curl -s -i "$BASE/api/demo/validate-param?code=ab" | head -20
echo -e "\n"

echo "===== 用例5 兜底异常（期望 code=50000，body 无 stackTrace）====="
curl -s -i "$BASE/api/demo/boom" | head -20
echo -e "\n"
