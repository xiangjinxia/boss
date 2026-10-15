#!/usr/bin/env python3
"""Optional report diagram generation; requires Graphviz `dot` installed."""
from pathlib import Path
import subprocess

OUT = Path(__file__).resolve().parent.parent/'docs/diagrams'
OUT.mkdir(parents=True, exist_ok=True)
STYLE = '''graph [bgcolor="white",pad="0.35",nodesep="0.45",ranksep="0.65",dpi=150,fontname="Noto Sans CJK SC"];
node [shape=box,style="rounded,filled",fillcolor="#edf4e7",color="#9ab28a",fontcolor="#315c38",fontname="Noto Sans CJK SC",fontsize=12,margin="0.18,0.12"];
edge [color="#8ba37b",fontcolor="#7c8b71",fontname="Noto Sans CJK SC",fontsize=10];'''
DIAGRAMS = {
 '01-architecture': '''rankdir=TB;
 browser[label="浏览器 / Vue 3\nVue Router · Element Plus · Axios · ECharts"];
 controller[label="Spring MVC / Controller\n请求校验 · 统一响应 · REST API"];
 security[label="Spring Security\n令牌认证 · 管理员角色"];
 service[label="Service 业务层\n所有权校验 · 商品审核 · 订单事务"];
 repository[label="Repository / JdbcTemplate\n参数化 SQL · 聚合查询"];
 db[shape=cylinder,label="MySQL / InnoDB\n外键 · 唯一索引 · 行锁"];
 files[label="本地图片目录\n解码校验 · UUID · PNG"];
 browser -> security [label="HTTP / Bearer"];
 security -> controller -> service -> repository -> db;
 controller -> files [label="图片上传"];
 ''',
 '02-er': '''rankdir=LR;
 user[label="app_user\nPK id · UQ username\npassword_hash · role · status"];
 category[label="category\nPK id · name · icon"];
 goods[label="goods\nPK id · seller_id · category_id\nprice · status · reject_reason"];
 image[label="goods_image\ngoods_id · image_url · sort_order"];
 favorite[label="favorite\nUQ(user_id,goods_id)"];
 message[label="message\ngoods_id · sender_id\nreply_to_id · content"];
 order[label="trade_order\nbuyer_id · seller_id · goods_id\namount · status · snapshot\nUQ active_goods_id"];
 report[label="report\nreporter_id · goods_id\nstatus · result · handled_by"];
 session[label="auth_session / upload_file\n令牌摘要与图片上传归属"];
 audit[label="audit_log\nadmin_id · target · action"];
 user -> goods [label="1:N 发布"];
 category -> goods [label="1:N 分类"];
 goods -> image [label="1:N 图片"];
 user -> favorite [label="1:N"];
 goods -> favorite [label="1:N"];
 goods -> message [label="1:N 留言"];
 user -> message [label="1:N 发送"];
 message -> message [label="回复"];
 goods -> order [label="1:N 历史交易"];
 user -> order [label="买家/卖家"];
 goods -> report [label="1:N 举报"];
 user -> report [label="举报/处理"];
 user -> session;
 user -> audit;
 ''',
 '03-goods-state': '''rankdir=TB;
 pending[label="待审核 PENDING"];
 listed[label="在售 LISTED"];
 rejected[label="已驳回 REJECTED"];
 reserved[label="已预订 RESERVED"];
 sold[label="已售出 SOLD",fillcolor="#d7e7c8"];
 offline[label="已下架 OFFLINE",fillcolor="#f5e9d8"];
 pending -> listed [label="管理员通过"];
 pending -> rejected [label="管理员驳回 + 原因"];
 rejected -> pending [label="编辑重提"];
 listed -> pending [label="卖家编辑"];
 listed -> reserved [label="卖家确认订单"];
 reserved -> listed [label="取消交易"];
 reserved -> sold [label="买家确认完成"];
 listed -> offline [label="下架"];
 reserved -> offline [label="管理员下架 + 取消订单"];
 offline -> pending [label="编辑重提"];
 ''',
 '04-order-state': '''rankdir=LR;
 pending[label="待卖家确认\nPENDING"];
 confirmed[label="待线下交易\nCONFIRMED"];
 completed[label="已完成\nCOMPLETED",fillcolor="#d7e7c8"];
 cancelled[label="已取消\nCANCELLED",fillcolor="#f5e9d8"];
 pending -> confirmed [label="卖家接受\n商品预订"];
 confirmed -> completed [label="买家确认\n商品售出"];
 pending -> cancelled [label="拒绝/取消/其他订单获确认"];
 confirmed -> cancelled [label="双方之一取消\n商品恢复上架"];
 '''
}
for name, body in DIAGRAMS.items():
    dot = OUT/f'{name}.dot'
    dot.write_text('digraph G {\n'+STYLE+'\n'+body+'\n}')
    subprocess.run(['dot', '-Tpng', str(dot), '-o', str(OUT/f'{name}.png')], check=True)
    subprocess.run(['dot', '-Tsvg', str(dot), '-o', str(OUT/f'{name}.svg')], check=True)
    print(name)
