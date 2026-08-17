# 同端口 HTTP / HTTPS

在 Minecraft 玩家端口（默认 `25565`）上同时提供 HTTP/1.1 与 HTTPS，不另开端口。

MC 已经用 Netty 独占该 TCP 监听套接字，不能再 `bind` 一次。做法是在原版 pipeline 最前面按首包分流：HTTP / TLS 换成自己的 handler，其余仍走 MC 协议。

关闭：`-Dae2isallyouneed.http=false`

---

## 1. 依赖

MC 1.20.1 / Forge 47.3.0 自带 Netty **4.1.82**，有 `SslHandler`，**没有** `netty-codec-http`。

本模组额外引入 `io.netty:netty-codec-http:4.1.82.Final`，版本必须与游戏对齐，不能升 4.2+。Forge `jarJar` / Fabric `include` 只嵌入这一只 jar（`isTransitive = false`），不重复打包 MC 已有的 `netty-common` 等模块。

不用 Ktor：Ktor 引擎要自己 `bind`，接不到 MC 已经 `accept` 的 Channel。

---

## 2. 分流

挂钩：`ServerConnectionListener$1.initChannel` 末尾 `pipeline.addFirst`。

```text
[allyouneed_proto]          ProtocolDetector
 timeout
 legacy_query
 splitter / decoder / prepender / encoder
 packet_handler             Connection
```

`ProtocolClassifier` 只看首包、不消费字节：

| 首包 | 判定 |
| --- | --- |
| `GET `/`POST `/`HEAD `/`PUT `/`PATCH `/`DELETE `/`OPTIONS `/`CONNECT `/`TRACE `/`PRI * HTTP` | HTTP |
| `16 03 00`–`16 03 04` | TLS → HTTPS |
| `FE` 或其余 | Minecraft（含旧版 ping） |

`G`/`P`/`16` 也可能是合法 MC VarInt 长度，必须等后续字节（空格、`HTTP/`、TLS version）再定。不够则 `NEED_MORE`。

命中 HTTP/TLS 后：

1. 从 `ServerConnectionListener.getConnections()` 去掉已登记的 `Connection`（**不要** `disconnect()`，那会关 channel）
2. 拆除 `legacy_query` … `packet_handler`（保留 `timeout`）
3. TLS 则加上 `SslHandler`（`SelfSignedCertificate`，不声明 ALPN `h2`，浏览器走 HTTP/1.1）
4. `HttpServerCodec` + `HttpObjectAggregator(64KiB)` + `HttpRouter`

命中 MC 则只移除检测器，原 pipeline 不变。

LAN 开放（`publishServer`）走同一条 bind，也会带上 HTTP。

---

## 3. 路由与线程

`HttpModule.register()` 在 `Main.init()` 里注册默认项。分发顺序：**API 先于静态页**，都没有则 404。中间件（鉴权 / CORS / 串行）尚未接入。

`HttpApi` 按 `METHOD + 规范化 path` 匹配，支持 `{param}` 段；字面量段更多的路由优先。同 method+path 后注册覆盖。

`HttpPages` 三种注册：

| 方法 | 作用 |
| --- | --- |
| `mount(urlPrefix, classpathRoot)` | 目录挂载，`/` 与目录 URL 回落 `index.html` |
| `page(path, classpathFile)` | 单文件 |
| `bytes(path, contentType, data)` | 内存内容 |

精确 `page` / `bytes` 先于 `mount`。`../` 会被拒绝。默认挂载 `/` → `assets/ae2isallyouneed/web`。

| 路径 | 说明 |
| --- | --- |
| `GET /` | 静态 `index.html`（拉 `/api/stats`） |
| `GET /api` | 已注册 API / 页面目录 |
| `GET /api/stats` | 游戏内统计，见第 4 节 |

`HttpCall` 跑在 **Netty 事件循环**，不能直接读世界 / 玩家 / AE2。

- 静态或纯计算：`call.json` / `call.text` / `call.bytes`
- 游戏状态：`call.onServer { server -> ... }`，块在服务端线程执行，结果再回到 event loop 写出
- 服未就绪：`503 { "error": "server unavailable" }`

构造时已拷下 `method` / `path` / `query` / `params` / `body`。`onServer` 不要再碰 `FullHttpRequest`。

---

## 4. `GET /api/stats`

服务端线程采集，JSON 大致为：

```json
{
  "players": { "online": 1, "max": 20, "names": ["Steve"] },
  "tick": { "mspt": 12.34, "tps": 20.0, "count": 12345 },
  "server": { "motd": "...", "dedicated": true, "version": "1.20.1", "port": 25565 },
  "worlds": [{ "id": "minecraft:overworld", "players": 1, "dayTime": 1000 }]
}
```

`hide-online-players` 开启时 `names` 为空。不返回 IP / UUID。

后续数据挂进同一份 JSON：

```kotlin
GameStats.register("ae2") { server ->
    mapOf("networks" to 0)
}
```

同 key 后注册覆盖。采集器同样只在服务端线程调用。

---

## 5. 接入新 API / 网页

传输层不用改。

```kotlin
HttpApi.get("/api/foo") { call ->
    call.onServer { server ->
        mapOf("ok" to true)
    }
}
HttpApi.get("/api/grids/{mac}") { call ->
    val mac = call.params.getValue("mac")
    call.onServer { /* ... */ }
}
HttpPages.page("/about", "assets/ae2isallyouneed/web/about.html")
HttpPages.mount("/docs", "assets/ae2isallyouneed/web/docs")
```

| 类型 | 做法 |
| --- | --- |
| REST | `HttpApi.get` / `post` / `put` / `delete` / `add`，回 `call.json` |
| 只读游戏数据 | 优先 `GameStats.register`，避免再开路径 |
| 静态页 | `HttpPages.mount` / `page` / `bytes` |
| WebSocket | 分流之后再加 `WebSocketServerProtocolHandler`（尚未做） |

不要把 Ktor / 独立 `HttpServer` 绑到同一端口。不要在 handler 里直接碰 Level。

---

## 6. 验证

```bash
curl http://127.0.0.1:25565/
curl http://127.0.0.1:25565/api
curl http://127.0.0.1:25565/api/stats
curl -k https://127.0.0.1:25565/api/stats
```

HTTPS 为运行时自签证书，浏览器会告警。客户端进服与服务器列表 ping 应不受影响。

---

## 7. 关键文件

| 路径 | 作用 |
| --- | --- |
| `common/.../net/http/ProtocolClassifier.kt` | 首包分类 |
| `common/.../net/http/ProtocolDetector.kt` | 换 pipeline |
| `common/.../net/http/HttpApi.kt` | API 注册 / `{param}` 匹配 |
| `common/.../net/http/HttpPages.kt` | 静态页 mount / page / bytes |
| `common/.../net/http/HttpRouter.kt` | API → 静态 → 404 |
| `common/.../net/http/HttpCall.kt` | 响应 / `onServer` / body |
| `common/.../net/http/HttpModule.kt` | 默认 `/api`、`/api/stats`、`/` 挂载 |
| `common/.../net/http/GameStats.kt` | 统计 + 扩展采集器 |
| `common/resources/assets/ae2isallyouneed/web/` | 默认静态根 |
| `common/.../mixin/minecraft/ServerConnectionListenerInitMixin.java` | 挂钩 `initChannel` |
| `common/.../mixin/minecraft/LegacyQueryHandlerAccessor.java` | 取 `ServerConnectionListener` |

---

## 8. 已知限制

- 自签证书不能当正式 HTTPS；未做用户证书 / ACME
- 无鉴权，统计接口对外网等于公开 MOTD / 在线名
- 只实现 HTTP/1.1，无 HTTP/2
- `$1` 匿名 `ChannelInitializer` 绑死 1.20.1；大版本升级可能要改 mixin 目标
