package org.example

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*

// MCPサーバーを実行するメイン関数
fun `run mcp server`() {
    // HTTP クライアントの作成
    // 米国気象庁APIへの接続設定を行う
    val httpClient = HttpClient {
        // デフォルトのリクエスト設定
        defaultRequest {
            // 米国気象庁APIのベースURL
            url("https://api.weather.gov")
            // ヘッダー設定
            headers {
                // GeoJSONフォーマットでデータを受け取るための設定
                append("Accept", "application/geo+json")
                // ユーザーエージェントの設定（APIアクセス時の識別子）
                append("User-Agent", "WeatherApiClient/1.0")
            }
            // コンテンツタイプをJSONに設定
            contentType(ContentType.Application.Json)
        }
        // JSONデータの解析のためのプラグインをインストール
        // 未知のキーがあっても無視する設定
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // MCPサーバーのインスタンスを作成
    val server = Server(
        // 実装名とバージョンを指定
        Implementation(
            name = "kotlin-mcp-quickstart", // ツール名は「kotlin-mcp-quickstart」
            version = "1.0.0" // 実装のバージョン
        ),
        // サーバーオプションを設定
        ServerOptions(
            // ツールの変更通知機能を有効化
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    )

    // 州別の気象警報を取得するツールを登録
    server.addTool(
        // ツール名
        name = "get_alerts",
        // ツールの説明
        description = """
        米国の州ごとの気象警報を取得します。入力は2文字の州コード（例：CA, NY）です。
    """.trimIndent(),
        // 入力スキーマの定義
        inputSchema = Tool.Input(
            // パラメータの定義
            properties = buildJsonObject {
                putJsonObject("state") {
                    put("type", "string")
                    put("description", "2文字の米国州コード（例：CA, NY）")
                }
            },
            // 必須パラメータの指定
            required = listOf("state")
        )
    ) { request ->
        // リクエストから州コードを取得
        val state = request.arguments["state"]?.jsonPrimitive?.content
        // 州コードが提供されていない場合はエラーを返す
        if (state == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("'state'パラメータが必要です。"))
            )
        }

        // 気象警報を取得する処理（実装は別途必要）
        val alerts = httpClient.getAlerts(state)

        // 結果を返す
        CallToolResult(content = alerts.map { TextContent(it) })
    }

    // 緯度経度による天気予報取得ツールを登録
    server.addTool(
        // ツール名
        name = "get_forecast",
        // ツールの説明
        description =
            """
            特定の緯度/経度の天気予報を取得します。
            """.trimIndent(),
        // 入力スキーマの定義
        inputSchema = Tool.Input(
            // パラメータの定義
            properties = buildJsonObject {
                putJsonObject("latitude") { put("type", "number") }
                putJsonObject("longitude") { put("type", "number") }
            },
            // 必須パラメータの指定
            required = listOf("latitude", "longitude")
        )
    ) { request ->
        // リクエストから緯度経度を取得
        val latitude = request.arguments["latitude"]?.jsonPrimitive?.doubleOrNull
        val longitude = request.arguments["longitude"]?.jsonPrimitive?.doubleOrNull
        // 緯度経度が提供されていない場合はエラーを返す
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("'latitude'と'longitude'パラメータが必要です。"))
            )
        }

        // 天気予報を取得する処理
        val forecast = httpClient.getForecast(latitude, longitude)

        // 結果を返す
        CallToolResult(content = forecast.map { TextContent(it) })
    }

    /**
     * 標準入出力を使用したサーバー通信用のトランスポートを作成
     * AIモデルとのやり取りは標準入出力を介して行われる
     */
    val transport = StdioServerTransport(
        System.`in`.asInput(), System.out.asSink().buffered()
    )

    // コルーチンスコープ内でサーバーを実行
    runBlocking {
        // トランスポートを使用してサーバーを接続
        server.connect(transport)
        // サーバーが閉じるまで待機するためのジョブを作成
        val done = Job()
        // サーバーが閉じた時の処理を設定
        server.onClose {
            done.complete()
        }
        // ジョブが完了するまで待機（サーバーが閉じるまで実行を継続）
        done.join()
    }
}
