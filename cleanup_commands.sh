mysql -u root -p123456 sikong_cloud_api -e "SELECT COUNT(*) FROM zhimou_detection_result WHERE deleted=0;"

mysql -u root -p123456 sikong_cloud_api -e "DELETE FROM work_order WHERE alarm_id NOT IN (SELECT id FROM (SELECT id FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC LIMIT 5) AS keep_ids);"

mysql -u root -p123456 sikong_cloud_api -e "DELETE FROM zhimou_detection_result WHERE deleted=0 AND id NOT IN (SELECT id FROM (SELECT id FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC LIMIT 5) AS keep_ids);"

sudo systemctl restart sikong-backend

mysql -u root -p123456 sikong_cloud_api -e "SELECT id, rule_name, event_time FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC;"