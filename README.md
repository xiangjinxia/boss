# 拾光 · Campus Exchange

校园二手交易平台课程项目。Vue 3 + Spring Boot + MySQL，学生端和管理员端使用真实 REST 接口，业务数据持久化到 MySQL。本项目不接入支付、学籍系统、实时聊天。

![校园集市](docs/screenshots/01-market-desktop.png)

## 先看这里

- 使用 IDEA 开发、已有本机 MySQL：按下文“IDEA + 本机 MySQL”操作，无需 Docker。
- 想用容器快速准备数据库：使用根目录 `compose.yaml`，只运行数据库。
- 文档在 `docs/`，数据库结构在 `backend/src/main/resources/db/migration/V1__schema.sql`。
- 需求可以继续调整。业务状态集中在后端 Service，页面、接口调用和数据库迁移分别管理。

## 目录

```text
backend/                 IDEA 可导入的 Maven / Spring Boot 工程
  src/main/java/cn/campus/exchange/
    api/                 Controller、请求校验、统一错误响应
    service/             账号、商品、订单、管理业务与事务
    repository/          参数化 JDBC 数据访问
    config/              鉴权、上传资源映射、演示数据
  src/main/resources/db/migration/  Flyway 数据库初始化
frontend/                Vue 3 / Vue Router / Element Plus / Axios / ECharts
  src/pages/             集市、详情、发布、个人中心、留言、后台
  src/components/        商品卡片、图标、状态标签、图表
  tests/                 Playwright 真实浏览器验收
scripts/                 建库 SQL、接口验收、性能样本与启动辅助
docs/                    设计报告、接口说明、测试报告、截图、答辩资料
```

## 软件版本

JDK **21**、Maven **3.9+**、Node.js **22.12+ 或 24 LTS**、MySQL **8.0+**。IDEA Community 也可运行，不依赖 IDEA Ultimate 的 Spring 功能。源码当前使用 Spring Boot 3.4.5、Vue 3、Vite 7；前端精确依赖版本由 `package-lock.json` 固定。

## IDEA + 本机 MySQL（推荐）

1. 在 MySQL Workbench / Navicat / IDEA 数据库控制台执行 `scripts/create-database.sql`，只创建空库。**不用手动重复导入表结构**：后端启动时 Flyway 自动建表，首次运行自动生成演示账号、分类、商品和历史订单。
2. 用 IDEA 打开 `backend/pom.xml`，选择“作为项目打开”，项目 SDK 设为 JDK 21，等待 Maven 加载。
3. 找到 `cn.campus.exchange.CampusApplication`，编辑运行配置，设置环境变量（按你自己的 MySQL 用户和密码）：

   ```text
   DB_HOST=127.0.0.1
   DB_PORT=3306
   DB_NAME=campus_exchange
   DB_USER=root
   DB_PASSWORD=你自己的本机密码
   ```

   IDEA 的环境变量编辑框可逐项添加。后端工作目录设为 `backend`。不要把真实密码写进源码或截图。

4. 运行 `CampusApplication.main()`。启动日志包含 Tomcat 8080 与 Flyway 成功记录。请求 `/api/health` 应返回 `database: up`。
5. 在另一个终端进入 `frontend`，运行：

   ```bash
   npm ci
   npm run dev
   ```

6. 用浏览器打开 Vite 输出的本机 5173 地址。开发代理已把 `/api`、`/uploads` 和 `/samples` 转发到后端 8080。

注意：不启动后端时，页面无法加载商品；不是静态演示。上传文件存放在后端工作目录的 `uploads/`，重启后保留。账号登录令牌保存在当前标签页的 sessionStorage，使用不同浏览器或无痕窗口可同时登录多个角色。

## Docker 准备 MySQL（可选）

```bash
# Windows PowerShell 用 Copy-Item .env.example .env
cp .env.example .env
docker compose up -d mysql
```

数据库默认映射到本机 **3307**，应用账号 `campus`，密码见本地 `.env`。`application.yml` 的本地默认配置与此对应，适用于课程演示。使用本机 MySQL 时通常是 3306，要在 IDEA 中覆盖。`.env` 不会被 Java 自动读取；IDEA 配置以它的运行环境变量为准。容器数据保存在命名卷中，普通重启不会清空。

## 演示账号

| 角色 | 账号 | 密码 | 建议演示内容 |
| --- | --- | --- | --- |
| 管理员 | admin | Campus123! | 审核、禁用、举报、订单与统计 |
| 卖家 | seller | Campus123! | 发布商品、回复留言、确认订单 |
| 买家 | buyer | Campus123! | 收藏、留言、下单、确认完成 |

另有 3 个样例学生。首次空库包含 24 件在售好物、3 件待审核商品、13 笔历史成交、收藏和留言。插画为项目内本地 SVG，不依赖外部图片站点。演示初始化只在用户表为空时执行，不会因重启覆盖你的操作。共享部署前关闭 `DEMO_DATA` 并更换演示凭证。

## 已实现的业务

注册登录、退出及角色/所有权校验；昵称/头像/签名；分类、关键词、价格、时间筛选与分页；1–5 张实物图片；商品发布/编辑/下架及重新审核；收藏去重；持久化留言和卖家回复；买卖订单、确认/拒绝/取消/完成；个人记录；商品举报及处理；管理审核、用户状态、订单过滤、审计日志和 ECharts 看板。

卖家确认订单时，事务先锁商品再锁订单，预订一件商品后自动取消其他待确认订单。MySQL 生成列唯一索引额外限制每件商品最多一笔已确认订单。完成只允许买家执行。价格、标题和图片在下单时保存快照；管理员不能代替买家确认。

## 构建和验证

```bash
cd backend
mvn clean package
cd ../frontend
npm ci
npm run build
```

这是前后端分离项目：后端 JAR 只提供 API 与图片资源；`frontend/dist` 部署时需要由静态服务器托管，并将 API、图片转发到后端，Vue 路由使用 history 回退。课程开发直接使用 Vite 即可。

启动 MySQL、后端和前端后，在项目根目录执行：

```bash
python scripts/acceptance_test.py
cd frontend
npx playwright install chromium
npx playwright test
```

接口脚本只使用 Python 标准库，通过真实 HTTP 服务操作 MySQL，覆盖 17 项验收；浏览器脚本覆盖桌面/375px 手机浏览及完整交易，录屏保存到 `test-results/browser/`。脚本创建带唯一前缀的测试账号/商品，不要针对生产数据执行。仅 `mvn package` 的成功不能代表业务测试通过。

## 后续修改入口

- 分类：`category` 表；页面自动读取。
- 商品字段与约束：`Requests.Goods`、`GoodsService`、`Publish.vue`。
- 订单规则：`OrderService`；数据库变更新增 `V2__*.sql`，不要修改已经运行过的 V1。
- 页面风格：`frontend/src/style.css`。
- 演示样例：`DemoData.java`；修改后仅新空库自动初始化。
- 账号、端口、图片位置：环境变量，见 `docs/运行与答辩指南.md`。

报告和答辩提纲已提供。提交前按学校要求补充姓名、学号、班级，并在本机重新跑一遍流程。
