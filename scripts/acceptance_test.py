#!/usr/bin/env python3
"""Real HTTP + MySQL acceptance tests. No mocks. Creates isolated, prefixed demo records."""
import concurrent.futures
import json
import os
from pathlib import Path
import time
import unittest
import urllib.request
import urllib.error
import uuid

BASE = os.environ.get('API_URL', 'http://127.0.0.1:8080/api')
ROOT = Path(__file__).resolve().parent.parent


def request(method, path, token=None, data=None, expected=200, raw=None, content_type=None):
    headers = {}
    if token: headers['Authorization'] = 'Bearer ' + token
    body = raw
    if data is not None:
        body = json.dumps(data, ensure_ascii=False).encode()
        headers['Content-Type'] = 'application/json'
    if content_type: headers['Content-Type'] = content_type
    req = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=20) as r: status, result = r.status, json.load(r)
    except urllib.error.HTTPError as e:
        status, result = e.code, json.loads(e.read())
    assert status == expected, f'{method} {path}: expected {expected}, got {status}: {result}'
    return result['data']


def upload(token, content=None, filename='book.png', mime='image/png', expected=200):
    boundary = 'Campus' + uuid.uuid4().hex
    content = (ROOT / 'scripts/fixtures/book.png').read_bytes() if content is None else content
    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode() + content + f'\r\n--{boundary}--\r\n'.encode())
    return request('POST', '/uploads', token, expected=expected, raw=raw, content_type='multipart/form-data; boundary=' + boundary)


class Acceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.prefix = 'at' + str(time.time_ns())[-12:]
        cls.admin = request('POST', '/auth/login', data={'username': 'admin', 'password': 'Campus123!'})['token']
        cls.people = []
        for suffix in ['seller', 'buyer', 'other']:
            cls.people.append(request('POST', '/auth/register', data={'username': cls.prefix + suffix, 'password': 'Test12345!', 'nickname': '验收' + suffix, 'role': 'ADMIN'}))
        cls.seller, cls.buyer, cls.other = [p['token'] for p in cls.people]
        cls.image = upload(cls.seller)['url']

    def payload(self, suffix=''):
        return {'title': (self.prefix + ' 数据结构教材 ' + suffix).strip(), 'categoryId': 1, 'price': 25.50,
                'conditionLevel': '九成新', 'description': '真实接口验收：校园教材，线下面交。', 'images': [self.image]}

    def goods(self, approved=True, suffix=''):
        g = request('POST', '/goods', self.seller, self.payload(suffix))['id']
        if approved: request('POST', f'/admin/goods/{g}/review', self.admin, {'action': 'approve'})
        return g

    def order(self, g, token=None):
        return request('POST', '/orders', token or self.buyer, {'goodsId': g})['id']

    def act(self, o, token, action, expected=200):
        return request('POST', f'/orders/{o}/actions', token, {'action': action}, expected)

    def test_01_AT01_registration_authentication_and_roles(self):
        self.assertEqual(self.people[0]['user']['role'], 'STUDENT')
        request('GET', '/admin/dashboard', self.buyer, expected=403)
        request('POST', '/goods', data=self.payload(), expected=401)
        request('POST', '/auth/login', data={'username': self.prefix + 'seller', 'password': 'wrong'}, expected=401)

    def test_02_AT02_pending_goods_are_private(self):
        g = self.goods(False, '待审核')
        self.assertEqual(request('GET', f'/goods/{g}', self.seller)['status'], 'PENDING')
        request('GET', f'/goods/{g}', expected=404)
        request('GET', f'/goods/{g}', self.buyer, expected=404)
        data = request('GET', '/goods?q=' + self.prefix)
        self.assertNotIn(g, [x['id'] for x in data['items']])

    def test_03_AT03_review_search_filters_pagination(self):
        g = self.goods(suffix='search')
        data = request('GET', f'/goods?q={self.prefix}&category=1&min=25&max=26&days=1&sort=price_asc&size=48')
        self.assertIn(g, [x['id'] for x in data['items']])
        self.assertEqual(request('GET', f'/goods/{g}')['status'], 'LISTED')

    def test_04_AT04_favorites_idempotency_and_persistent_messages(self):
        g = self.goods()
        for _ in range(2): request('PUT', f'/goods/{g}/favorite', self.buyer)
        self.assertEqual(sum(x['id'] == g for x in request('GET', '/me/favorites', self.buyer)), 1)
        request('DELETE', f'/goods/{g}/favorite', self.buyer)
        self.assertFalse(request('GET', f'/goods/{g}', self.buyer)['is_favorite'])
        m = request('POST', f'/goods/{g}/messages', self.buyer, {'content': '<script>alert(1)</script> 图书还有吗？'})['id']
        request('POST', f'/goods/{g}/messages', self.other, {'content': '冒充回复', 'replyToId': m}, 403)
        request('POST', f'/goods/{g}/messages', self.seller, {'content': '还在，欢迎咨询。', 'replyToId': m})
        messages = request('GET', f'/goods/{g}/messages')
        self.assertEqual(len(messages), 2)
        self.assertEqual(messages[1]['reply_to_id'], m)
        self.assertTrue(any(x['goods_id'] == g for x in request('GET', '/me/messages', self.buyer)))

    def test_05_AT05_complete_trade_and_immutable_sold_goods(self):
        g = self.goods(); o = self.order(g)
        self.act(o, self.seller, 'confirm')
        self.assertEqual(request('GET', f'/goods/{g}')['status'], 'RESERVED')
        self.act(o, self.seller, 'complete', 403)
        self.act(o, self.buyer, 'complete')
        self.assertEqual(request('GET', f'/goods/{g}')['status'], 'SOLD')
        self.act(o, self.buyer, 'complete', 409)
        request('PUT', f'/goods/{g}', self.seller, self.payload(), 409)
        order = next(x for x in request('GET', '/me/orders', self.buyer) if x['id'] == o)
        self.assertEqual(order['status'], 'COMPLETED'); self.assertEqual(float(order['amount']), 25.50)

    def test_06_AT06_concurrent_confirmation_cannot_double_book(self):
        g = self.goods(); orders = [self.order(g), self.order(g, self.other)]
        def confirm(o):
            try: self.act(o, self.seller, 'confirm'); return 'ok'
            except AssertionError as e:
                if 'got 409' not in str(e): raise
                return 'conflict'
        with concurrent.futures.ThreadPoolExecutor(2) as pool: outcomes = list(pool.map(confirm, orders))
        self.assertEqual(sorted(outcomes), ['conflict', 'ok'])
        all_orders = request('GET', '/me/orders?side=selling', self.seller)
        matches = [x for x in all_orders if x['id'] in orders]
        self.assertEqual(sorted(x['status'] for x in matches), ['CANCELLED', 'CONFIRMED'])
        winning = next(x for x in matches if x['status'] == 'CONFIRMED')
        self.act(winning['id'], self.seller, 'cancel')
        self.assertEqual(request('GET', f'/goods/{g}')['status'], 'LISTED')

    def test_07_AT07_statistics_match_completed_order_rows(self):
        data = request('GET', '/admin/dashboard', self.admin)
        orders = request('GET', '/admin/orders?status=COMPLETED', self.admin)
        self.assertEqual(data['kpi']['completed'], len(orders))
        self.assertAlmostEqual(float(data['kpi']['amount']), sum(float(x['amount']) for x in orders))
        self.assertEqual(len(data['trend']), 7)

    def test_08_owner_check_and_buying_own_goods(self):
        g = self.goods()
        request('PUT', f'/goods/{g}', self.buyer, self.payload(), 403)
        request('POST', f'/goods/{g}/offline', self.buyer, expected=403)
        request('POST', '/orders', self.seller, {'goodsId': g}, 400)
        o = self.order(g)
        self.act(o, self.other, 'cancel', 403)
        self.act(o, self.admin, 'complete', 403)

    def test_09_cancel_reject_and_relist(self):
        g = self.goods(); o = self.order(g)
        self.act(o, self.seller, 'reject')
        self.assertEqual(request('GET', f'/goods/{g}')['status'], 'LISTED')
        o = self.order(g); self.act(o, self.seller, 'confirm'); self.act(o, self.buyer, 'cancel')
        self.assertEqual(request('GET', f'/goods/{g}')['status'], 'LISTED')

    def test_10_rejected_goods_can_be_edited_and_reviewed_again(self):
        g = self.goods(False)
        request('POST', f'/admin/goods/{g}/review', self.admin, {'action': 'reject', 'reason': '请补充描述'})
        self.assertEqual(request('GET', f'/goods/{g}', self.seller)['reject_reason'], '请补充描述')
        request('PUT', f'/goods/{g}', self.seller, self.payload('已补充'))
        self.assertEqual(request('GET', f'/goods/{g}', self.seller)['status'], 'PENDING')
        request('POST', f'/admin/goods/{g}/review', self.admin, {'action': 'approve'})
        self.assertEqual(request('GET', f'/goods/{g}')['reject_reason'], '')

    def test_11_reject_malformed_and_spoofed_uploads(self):
        upload(self.buyer, content=b'not an image', expected=400)
        upload(self.buyer, filename='payload.html', expected=400)
        upload(self.buyer, filename='image.jpg', mime='image/jpeg', expected=400)
        invalid = self.payload(); invalid['images'] = ['https://example.com/x.png']
        request('POST', '/goods', self.seller, invalid, 400)
        invalid['images'] = []; request('POST', '/goods', self.seller, invalid, 400)

    def test_12_report_handling_and_audit_trail(self):
        g = self.goods()
        r = request('POST', '/reports', self.buyer, {'goodsId': g, 'reason': '验收举报'})['id']
        request('POST', '/reports', self.buyer, {'goodsId': g, 'reason': '重复举报'}, 409)
        request('POST', f'/admin/reports/{r}/handle', self.admin, {'action': 'resolve', 'reason': '已核实并联系卖家'})
        mine = next(x for x in request('GET', '/me/reports', self.buyer) if x['id'] == r)
        self.assertEqual(mine['status'], 'RESOLVED'); self.assertTrue(mine['handled_at'])
        self.assertTrue(any(x['target_type'] == 'report' and x['target_id'] == r for x in request('GET', '/admin/audit', self.admin)))

    def test_13_disabled_user_loses_existing_session(self):
        user = request('POST', '/auth/register', data={'username': self.prefix+'disabled', 'password': 'Test12345!', 'nickname': '禁用测试'})
        request('POST', f"/admin/users/{user['user']['id']}/status", self.admin, {'action': 'disable'})
        request('GET', '/me', user['token'], expected=401)
        request('POST', '/orders', user['token'], {'goodsId': 1}, 401)
        request('POST', '/auth/login', data={'username': self.prefix+'disabled', 'password': 'Test12345!'}, expected=403)

    def test_14_offline_preserves_orders_and_cancels_pending(self):
        g = self.goods(); o = self.order(g)
        request('POST', f'/goods/{g}/offline', self.seller)
        request('GET', f'/goods/{g}', expected=404)
        order = next(x for x in request('GET', '/me/orders', self.buyer) if x['id'] == o)
        self.assertEqual(order['status'], 'CANCELLED'); self.assertEqual(order['goods_title'], self.payload()['title'])

    def test_15_admin_offline_cancels_reservation_atomically(self):
        g = self.goods(); o = self.order(g); self.act(o, self.seller, 'confirm')
        request('POST', f'/admin/goods/{g}/review', self.admin, {'action': 'offline', 'reason': '验收强制下架'})
        order = next(x for x in request('GET', '/me/orders', self.buyer) if x['id'] == o)
        self.assertEqual(order['status'], 'CANCELLED')
        self.act(o, self.buyer, 'complete', 409)

    def test_16_profile_and_query_parameter_validation(self):
        request('PUT', '/me', self.buyer, {'nickname': '验收买家', 'bio': '我的个人签名', 'avatar': ''})
        self.assertEqual(request('GET', '/me', self.buyer)['nickname'], '验收买家')
        request('GET', '/goods?page=0', expected=400)
        request('GET', '/goods?min=100&max=10', expected=400)
        request('GET', '/admin/orders?from=not-a-date', self.admin, expected=400)
        request('GET', '/goods?q=%27%20OR%201%3D1%20--', expected=200)

    def test_17_logout_revokes_token(self):
        user = request('POST', '/auth/login', data={'username': self.prefix+'buyer', 'password': 'Test12345!'})
        request('POST', '/auth/logout', user['token'])
        request('GET', '/me', user['token'], expected=401)


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(Acceptance)
    started = time.time()
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    output = {'tests': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors),
              'seconds': round(time.time()-started, 2), 'database': 'MySQL 8.4 (actual HTTP service)',
              'timestamp': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
              'details': [(str(t), e) for t, e in result.failures + result.errors]}
    (ROOT/'test-results').mkdir(exist_ok=True)
    (ROOT/'test-results/api-results.json').write_text(json.dumps(output, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result.wasSuccessful() else 1)
