# 内部债务清算试算应用

这是一个“只试算/确认清算方案，不接真实银行”的全栈示例，用于处理甲欠乙、乙欠丙、丙又欠甲的内部债务抵销。

## 业务边界

1. **按协议与币种分组**：不同协议绝不混合；同协议下不同币种也绝不合并净额。
2. **严格排除不可抵销债权**：无协议、协议外法人、已质押、存在争议、非 `OPEN` 的原始债权不会进入清算。
3. **原始债权保留**：确认前只生成 `TRIAL` 批次；确认后原始债权状态从 `OPEN` 改为 `NETTED`，并在 `claim_allocation` 中保留到发票级的去向。
4. **确认方案不是银行付款**：后端没有银行客户端，结算表只表示模拟净付款指令。
5. **BigDecimal 计算**：金额使用 `NUMERIC(19,4)` 与 Java `BigDecimal`，四舍五入到 4 位小数；换币折算金额 10 位、尾差 12 位。
6. **法人净头寸不变**：每个“协议 + 币种 + 法人”的原始净头寸和方案后净头寸差额必须为 0，计算器发现非零差额会直接失败。
7. **跨币种仅用于展示**：输入模拟展示币种和人工汇率时，逐笔模拟净付款列出来源币、目标币、汇率、时点、尾差金额及尾差承担法人；不会改变原币净头寸。

## 核心验收场景

演示数据见 `backend/src/main/resources/db/migration/V2__demo_data.sql`：

- `INV-B-1001`、`INV-C-1002`、`INV-A-1003`：甲乙丙各 100 CNY 的环形债务，期望 3 笔缩减为 0 笔。
- `INV-B-1101`、`INV-C-1102`、`INV-A-1103`：不等额环形债务，期望 3 笔缩减为 2 笔，保留 39.50 CNY 的净付款。
- EUR 三笔：独立于 CNY 分组，不做跨币净额；输入展示币种时逐笔显示人工汇率与尾差。
- 质押、争议、丁法人不在协议内、无协议的发票全部保留为原债。
- `NA-BILAT` 下 50 USD 与 20 USD 双边抵销为 30 USD，不会和三方协议混合。

## 本地运行

### Docker Compose

```bash
docker compose up --build
```

- Angular: <http://localhost>
- Spring Boot: <http://localhost:8080/api/claims>
- PostgreSQL: `localhost:5432/netting`，用户/密码均为 `netting`

### 开发模式

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm start
```

打开 <http://localhost:4200>。前端开发服务器把 `/api` 代理到 `localhost:8080`。

## API

### 创建试算

```http
POST /api/trials
Content-Type: application/json

{
  "agreementCode": null,
  "currency": null,
  "targetCurrency": "CNY",
  "fxRateTime": "2026-09-30T09:30:00Z",
  "residualBearer": "PAYER",
  "fxRates": [
    {"fromCurrency": "EUR", "toCurrency": "CNY", "rate": 7.81234567895},
    {"fromCurrency": "USD", "toCurrency": "CNY", "rate": 7.12345678901},
    {"fromCurrency": "CNY", "toCurrency": "CNY", "rate": 1}
  ]
}
```

不需要跨币种展示时，将 `targetCurrency`、`fxRateTime`、`fxRates` 置空。

### 确认批次

```http
POST /api/batches/{batchId}/confirm
```

确认时重新计算试算输入签名；如果源发票金额、状态、质押/争议标记等发生变化，返回 `409`，要求重新试算。

## PostgreSQL 数据模型

- `legal_entity`：法人。
- `netting_agreement`、`agreement_party`：互抵协议及协议方边界。
- `claim`：原始债权/发票，不物理删除。
- `netting_batch`：试算与确认批次、输入 JSON、签名、汇总和版本号。
- `netting_group`：协议 + 币种分组结果。
- `settlement`：模拟净付款指令。
- `fx_quote`：逐笔模拟付款的人工汇率、时点、尾差与归属。
- `claim_allocation`：每张原始发票如何被互抵或进入模拟付款链。

## 前端操作

1. 选择协议/币种范围、模拟展示币种、人工汇率时点和尾差承担方。
2. 点击“生成确认前试算”。
3. 在债务图上点击债项边，右侧面板会显示原始发票、抵销类型、金额和模拟付款编号。
4. 查看“法人余额不变证明”，所有差额必须为 `0.0000`。
5. 检查排除债权是否按质押、争议或协议边界保留。
6. 确认方案后批次状态变为 `CONFIRMED`，源债权变为 `NETTED`；仍然没有真实银行划款。
