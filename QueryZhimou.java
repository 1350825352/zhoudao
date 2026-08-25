import java.sql.*;

public class QueryZhimou {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://192.168.2.200:3306/sikong_cloud_api?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        try (Connection conn = DriverManager.getConnection(url, "szy02", "123456")) {
            // 1. 查 zhimou_detection_result 记录数
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM zhimou_detection_result")) {
                rs.next();
                System.out.println("zhimou_detection_result 记录数: " + rs.getInt(1));
            }

            // 2. 查最近5条
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, task_id, aircraft_sn, rule_name, event_time FROM zhimou_detection_result ORDER BY id DESC LIMIT 5")) {
                System.out.println("\n最近5条记录:");
                while (rs.next()) {
                    System.out.println("  id=" + rs.getLong("id") + " task=" + rs.getString("task_id") + " sn=" + rs.getString("aircraft_sn") + " rule=" + rs.getString("rule_name") + " event=" + rs.getString("event_time"));
                }
            }

            // 3. 查 zhimou_task 表里视频任务的历史记录
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, task_id, mode, aircraft_sn, status, create_time FROM zhimou_task ORDER BY id DESC LIMIT 5")) {
                System.out.println("\nzhimou_task 最近5条:");
                while (rs.next()) {
                    System.out.println("  id=" + rs.getLong("id") + " task=" + rs.getString("task_id") + " mode=" + rs.getString("mode") + " sn=" + rs.getString("aircraft_sn") + " status=" + rs.getInt("status") + " create=" + rs.getString("create_time"));
                }
            }
        }
    }
}
