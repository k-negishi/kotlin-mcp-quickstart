package org.example

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// 指定された緯度と経度の天気予報を取得する拡張関数
suspend fun HttpClient.getForecast(latitude: Double, longitude: Double): List<String> {
    // まず、指定された座標に関する情報を取得
    // これにより、実際の予報URLを含むPointsオブジェクトが得られる
    val points = this.get("/points/$latitude,$longitude").body<Points>()

    // points.properties.forecastに含まれるURLから実際の予報データを取得
    val forecast = this.get(points.properties.forecast).body<Forecast>()

    // 予報期間ごとのデータを整形して文字列のリストとして返す
    return forecast.properties.periods.map { period ->
        """
            ${period.name}:
            Temperature: ${period.temperature} ${period.temperatureUnit}
            Wind: ${period.windSpeed} ${period.windDirection}
            Forecast: ${period.detailedForecast}
        """.trimIndent()
    }
}

// 指定された州の気象警報を取得する拡張関数
suspend fun HttpClient.getAlerts(state: String): List<String> {
    // 州コードを使用してアクティブな警報を取得
    val alerts = this.get("/alerts/active/area/$state").body<Alert>()

    // 各警報の詳細を整形して文字列のリストとして返す
    return alerts.features.map { feature ->
        """
            Event: ${feature.properties.event}
            Area: ${feature.properties.areaDesc}
            Severity: ${feature.properties.severity}
            Description: ${feature.properties.description}
            Instruction: ${feature.properties.instruction}
        """.trimIndent()
    }
}

// 座標に関するデータを格納するためのデータクラス
@Serializable
data class Points(
    val properties: Properties
) {
    // 予報URLを含むプロパティクラス
    @Serializable
    data class Properties(val forecast: String)
}

// 天気予報データを格納するためのデータクラス
@Serializable
data class Forecast(
    val properties: Properties
) {
    // 予報期間のリストを含むプロパティクラス
    @Serializable
    data class Properties(val periods: List<Period>)

    // 各予報期間の詳細情報を格納するデータクラス
    @Serializable
    data class Period(
        val number: Int,            // 期間番号
        val name: String,           // 期間名（例：「Tonight」、「Monday」）
        val startTime: String,      // 開始時間（ISO 8601形式）
        val endTime: String,        // 終了時間（ISO 8601形式）
        val isDaytime: Boolean,     // 日中かどうかのフラグ
        val temperature: Int,       // 気温
        val temperatureUnit: String, // 気温の単位（F：華氏、C：摂氏）
        val temperatureTrend: String, // 気温の傾向
        val probabilityOfPrecipitation: JsonObject, // 降水確率
        val windSpeed: String,      // 風速
        val windDirection: String,  // 風向き
        val shortForecast: String,  // 短い予報文
        val detailedForecast: String, // 詳細な予報文
    )
}

// 気象警報データを格納するためのデータクラス
@Serializable
data class Alert(
    val features: List<Feature>
) {
    // 各警報の特徴情報を格納するデータクラス
    @Serializable
    data class Feature(
        val properties: Properties
    )

    // 警報の詳細情報を格納するデータクラス
    @Serializable
    data class Properties(
        val event: String,          // イベント種類（例：「Flood Warning」）
        val areaDesc: String,       // 影響地域の説明
        val severity: String,       // 深刻度（例：「Severe」、「Moderate」）
        val description: String,    // 警報の詳細説明
        val instruction: String?,   // 住民への指示（null可能）
    )
}