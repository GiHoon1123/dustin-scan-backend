#!/usr/bin/env python3
"""별도 로컬 체인에서만 실행: 테스트 지갑 생성과 네이티브 송금·DB 인덱싱 확인."""
import argparse
import json
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--api', default='http://localhost:4140')
parser.add_argument('--indexer', default='http://localhost:4141')
args = parser.parse_args()
if not args.api.startswith(('http://localhost:', 'http://127.0.0.1:')):
    raise SystemExit('로컬 테스트 API만 허용합니다.')

def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(args.api + path, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=75) as response:
        return json.load(response)

a = request('/accounts/create-wallet', {})['data']
b = request('/accounts/create-wallet', {})['data']
result = request('/accounts/transfer-native', {'privateKey': a['privateKey'], 'to': b['address'], 'amount': '1'})['data']
assert result['status'] == 'confirmed', result
h = result['hash']
for _ in range(30):
    page = request('/transactions')['data']['items']
    if any(tx['hash'] == h for tx in page):
        break
    time.sleep(1)
else:
    raise AssertionError('제출 거래가 DB 목록에 적재되지 않았습니다.')
tx = request('/transactions/' + h)['data']
assert tx['valueWei'] == '1000000000000000000', tx
assert tx['status'] == 1, tx
assert tx['blockNumber'] == result['blockNumber'], tx
assert len(tx['blockHash']) == 66, tx
block = request('/blocks/number/' + tx['blockNumber'])['data']
assert any(item['hash'] == h for item in block['transactions']), block
recipient = request('/accounts/' + b['address'])['data']
print(json.dumps({'hash': h, 'blockNumber': tx['blockNumber'], 'status': result['status'], 'recipientBalanceWei': recipient['balanceWei'], 'databaseIndexed': True}, ensure_ascii=False))
