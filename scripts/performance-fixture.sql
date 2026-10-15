-- OPTIONAL: run on an empty/demo database for the 500-goods performance check.
-- Idempotent. DemoData must already have created the seller account.
INSERT INTO goods(seller_id,category_id,title,price,condition_level,description,status,created_at)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n<500)
SELECT u.id,MOD(n-1,6)+1,CONCAT('PERF_SAMPLE_',LPAD(n,3,'0')),MOD(n,200)+1,'九成新','500 条分页查询性能测试样本','LISTED',DATE_SUB(NOW(),INTERVAL 90 DAY)
FROM seq CROSS JOIN app_user u WHERE u.username='seller'
AND NOT EXISTS(SELECT 1 FROM goods WHERE title=CONCAT('PERF_SAMPLE_',LPAD(n,3,'0')));
INSERT INTO goods_image(goods_id,image_url,sort_order)
SELECT g.id,'/samples/books.svg',0 FROM goods g WHERE g.title REGEXP '^PERF_SAMPLE_[0-9]{3}$'
AND NOT EXISTS(SELECT 1 FROM goods_image i WHERE i.goods_id=g.id);
