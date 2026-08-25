# -*- coding: utf-8 -*-
"""
生成《产品二期设计方案》Word 文档
作者: AI 助手
日期: 2026-08-24
"""
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_ALIGN_VERTICAL
from docx.oxml.ns import qn
from docx.oxml import OxmlElement


def set_cell_bg(cell, color_hex):
    """设置表格单元格背景色"""
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), color_hex)
    tc_pr.append(shd)


def add_heading(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        run.font.name = '微软雅黑'
        run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
        if level == 0:
            run.font.size = Pt(22)
            run.font.color.rgb = RGBColor(0x1F, 0x49, 0x7D)
        elif level == 1:
            run.font.size = Pt(16)
            run.font.color.rgb = RGBColor(0x1F, 0x49, 0x7D)
        elif level == 2:
            run.font.size = Pt(14)
            run.font.color.rgb = RGBColor(0x2E, 0x74, 0xB5)
        else:
            run.font.size = Pt(12)
            run.font.color.rgb = RGBColor(0x2E, 0x74, 0xB5)
    return h


def add_para(doc, text, bold=False, color=None, size=11):
    p = doc.add_paragraph()
    run = p.add_run(text)
    run.font.name = '微软雅黑'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = color
    return p


def add_bullet(doc, text, level=0):
    p = doc.add_paragraph(style='List Bullet' if level == 0 else 'List Bullet 2')
    run = p.add_run(text)
    run.font.name = '微软雅黑'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    run.font.size = Pt(11)
    return p


def add_code_block(doc, code_text):
    """添加代码块（用浅灰底色的段落模拟）"""
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.5)
    run = p.add_run(code_text)
    run.font.name = 'Consolas'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), 'Consolas')
    run.font.size = Pt(10)
    # 设置背景色
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), 'F5F5F5')
    pPr.append(shd)
    return p


def add_table(doc, headers, rows, col_widths=None):
    """添加带样式的表格"""
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = 'Light Grid Accent 1'
    table.alignment = WD_ALIGN_PARAGRAPH.CENTER

    # 表头
    hdr_cells = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr_cells[i].text = ''
        p = hdr_cells[i].paragraphs[0]
        run = p.add_run(h)
        run.font.name = '微软雅黑'
        run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
        run.font.size = Pt(10)
        run.bold = True
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        set_cell_bg(hdr_cells[i], '4472C4')
        hdr_cells[i].vertical_alignment = WD_ALIGN_VERTICAL.CENTER

    # 数据行
    for row_data in rows:
        cells = table.add_row().cells
        for i, val in enumerate(row_data):
            cells[i].text = ''
            p = cells[i].paragraphs[0]
            run = p.add_run(str(val))
            run.font.name = '微软雅黑'
            run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
            run.font.size = Pt(10)
            cells[i].vertical_alignment = WD_ALIGN_VERTICAL.CENTER

    if col_widths:
        for i, w in enumerate(col_widths):
            for row in table.rows:
                row.cells[i].width = w
    return table


# ============================================================
# 开始生成文档
# ============================================================
doc = Document()

# 设置默认字体
style = doc.styles['Normal']
style.font.name = '微软雅黑'
style._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
style.font.size = Pt(11)

# ===== 封面 =====
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title_run = title.add_run('\n\n\n产品二期设计方案\n')
title_run.font.name = '微软雅黑'
title_run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
title_run.font.size = Pt(36)
title_run.font.color.rgb = RGBColor(0x1F, 0x49, 0x7D)
title_run.bold = True

subtitle = doc.add_paragraph()
subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
sub_run = subtitle.add_run('—— 知眸图片识别 + 自有模型识别 + 数据清洗一体化方案')
sub_run.font.name = '微软雅黑'
sub_run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
sub_run.font.size = Pt(14)
sub_run.font.color.rgb = RGBColor(0x59, 0x59, 0x59)

info = doc.add_paragraph()
info.alignment = WD_ALIGN_PARAGRAPH.CENTER
info_run = info.add_run('\n\n\n\n\n项目: 大疆司空2 + 上云API 整合后台服务\n客户: 石知韵智能科技\n文档版本: V2.0\n编制日期: 2026-08-24\n编制: AI 助手')
info_run.font.name = '微软雅黑'
info_run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
info_run.font.size = Pt(12)

doc.add_page_break()

# ===== 目录页占位 =====
add_heading(doc, '目  录', level=1)
add_para(doc, '(Word 中可在此处插入自动目录：引用 → 目录 → 自动目录)')
doc.add_page_break()

# ============================================================
# 第一章: 项目背景
# ============================================================
add_heading(doc, '第一章  项目背景', level=1)

add_heading(doc, '1.1 一期现状', level=2)
add_para(doc, '一期项目是大疆司空2 + 上云API 整合后台服务, 面向石知韵智能科技客户。主要功能包括:')
add_bullet(doc, '设备管理、航线管理、直播、工单等 14 个业务模块')
add_bullet(doc, '后端: Spring Boot 2.7.18 + Java 17 + MyBatis-Plus + MySQL + Redis')
add_bullet(doc, '前端: 三套独立 Vue3 应用 (admin-v3 管理后台 / h5-v3 移动端 / screen-v3 大屏)')
add_bullet(doc, '整合三套外部系统: 大疆上云API、大疆司空2 OpenAPI V2.0、智谋AI平台')
add_bullet(doc, '设计了 MQTT 自动回复、request-response 匹配等关键机制')
add_bullet(doc, '支持多租户配置, 有相应的部署脚本和部署形态')

add_heading(doc, '1.2 一期已实现的知眸视频识别功能', level=2)
add_para(doc, '一期已实现知眸视频流AI识别功能, 数据流转如下:')
add_bullet(doc, '飞机起飞 → 后端自动调用知眸 OpenAPI 创建 video 模式任务')
add_bullet(doc, '知眸分析视频流 → 命中目标后通过 HTTP 回调推送结果')
add_bullet(doc, '后端 handleCallback() 接收回调 → 存入 zhimou_detection_result 表')
add_bullet(doc, '前端三个项目均通过 HTTP 轮询查询数据库获取数据 (screen-v3 每 2 秒轮询)')
add_bullet(doc, '前端与知眸系统完全解耦, 仅与数据库交互')

add_heading(doc, '1.3 二期目标', level=2)
add_para(doc, '在保持一期视频识别功能不受影响的前提下, 二期需新增以下能力:')
add_bullet(doc, '新增图片识别场景: 无人机降落后, 对拍摄的照片做AI识别')
add_bullet(doc, '同时调用知眸图片识别 + 自有模型识别两套AI引擎')
add_bullet(doc, '通过双模型结果对比进行数据清洗, 识别并标记误识别数据')
add_bullet(doc, '视频和图片任务在生命周期、数据存储上完全解耦')
add_bullet(doc, '前端无需改动, 仅查询独立的图片识别结果表')

doc.add_page_break()

# ============================================================
# 第二章: 整体方案设计
# ============================================================
add_heading(doc, '第二章  整体方案设计', level=1)

add_heading(doc, '2.1 设计原则', level=2)
add_bullet(doc, '解耦原则: 视频和图片任务在生命周期和数据存储上完全独立')
add_bullet(doc, '复用原则: 前端、MQTT、部署框架等基础设施无需改动')
add_bullet(doc, '独立表原则: 三张识别结果表各自独立, 无外键关联')
add_bullet(doc, '可观测原则: 两表均保留原始回调/响应 JSON 用于排错审计')
add_bullet(doc, '渐进原则: 不创建复杂调度表, 用 Redis 轻量跟踪状态')

add_heading(doc, '2.2 整体数据流', level=2)
add_para(doc, '二期完整数据流如下:')

flow_text = """无人机降落
   |
   v
图片同步到 MinIO (第三方同步程序主动推送)
   |
   v
MinIO 触发 ObjectCreated 事件 (Webhook)
   |
   v
后端接收事件接口
   | (异步分发)
   +--> 知眸图片任务 (先创建任务→上报图片→等待异步回调)
   |        |
   |        v
   |    知眸回调 --> 入 zhimou_image_detection 表
   |
   +--> 自有模型同步识别 (POST /api/v1/detect)
            |
            v
        同步返回 --> 入 self_model_image_detection 表
   |
   v
Redis 跟踪两边完成状态 (img:rec:done:{local_image_name})
   | (两边都到达或超时60秒)
   v
数据清洗服务 (按中心点距离匹配)
   |
   v
判定结果写入两表的 clean_status 字段
   |
   v
前端通过 HTTP 轮询查询数据库获取结果"""
add_code_block(doc, flow_text)

add_heading(doc, '2.3 三张独立数据库表', level=2)
add_para(doc, '二期采用三张完全独立的数据库表, 互不引用:')

add_table(doc,
    headers=['表名', '数据来源', '用途', '状态'],
    rows=[
        ['zhimou_detection_result', '知眸视频任务异步回调', '视频识别结果(一期已存在)', '已存在, 不改动'],
        ['zhimou_image_detection', '知眸图片任务异步回调', '图片识别结果(知眸侧)', '已新建, 29字段7索引'],
        ['self_model_image_detection', '自有模型同步API响应', '图片识别结果(自有模型侧)', '已新建, 27字段6索引'],
    ],
    col_widths=[Cm(5), Cm(4.5), Cm(4.5), Cm(3)]
)

add_para(doc, '')
add_para(doc, '关键说明:', bold=True)
add_bullet(doc, '三表无外键关联, 各自保存完整回调/响应信息')
add_bullet(doc, 'local_image_name (本地图片文件名) 作为知眸和自有模型两表的数据清洗关联键')
add_bullet(doc, 'local_image_name 必须全局唯一, 用于数据清洗时关联同一张照片')
add_bullet(doc, '同一张图片同时有知眸和自有模型识别结果 → 判定为误识别')

doc.add_page_break()

# ============================================================
# 第三章: 调度表决策过程 (用户特别关注)
# ============================================================
add_heading(doc, '第三章  调度表 (image_recognition_job) 决策过程', level=1)

add_para(doc, '【说明】调度表是二期方案设计过程中讨论时间最长、变更最多的一个环节。本章完整记录其决策演化过程, 便于后续回溯。', bold=True, color=RGBColor(0xC0, 0x00, 0x00))

add_heading(doc, '3.1 第一阶段: 初步建议 (2026-08-18 14:16)', level=2)
add_para(doc, '在讨论"无人机降落后图片同步到 MinIO 服务器"的方案时, 为管理"从图片同步完成 → 取数据 → 发给知眸和自有模型识别"的完整流水线时序, 初步建议:')
add_bullet(doc, '创建 image_recognition_job 表用于管理识别任务流程')
add_bullet(doc, '该表负责记录每张图片从 MinIO 同步完成到两个模型识别完成的整个生命周期')
add_bullet(doc, '主要解决: 图片同步完成的判断、图片获取方式、异步解耦发送给两个模型')

add_heading(doc, '3.2 第二阶段: 替代方案讨论 (2026-08-18 14:21)', level=2)
add_para(doc, '进一步讨论"第三方同步程序往 MinIO 写文件时, 后端作为观察者如何知道新文件何时出现"时, 提出更具体的方案:')
add_bullet(doc, '通过 MinIO ObjectCreated 事件通知判断图片同步完成 (替代主动轮询)')
add_bullet(doc, '使用 MinIO getObject 或 presigned URL 获取图片')
add_bullet(doc, '通过 image_recognition_job 调度表异步解耦发送给两个模型')
add_para(doc, '此时 image_recognition_job 调度表仍是方案核心, 用于解耦异步分发。')

add_heading(doc, '3.3 第三阶段: 最终决策 (2026-08-18 14:42)', level=2)
add_para(doc, '在方案定稿时, 经过权衡后做出以下最终决策:', bold=True)

add_table(doc,
    headers=['决策项', '最初建议', '最终决策', '理由'],
    rows=[
        ['调度表 image_recognition_job', '创建独立调度表', '暂不创建', 'Redis 轻量跟踪足够, 避免表数量膨胀'],
        ['状态跟踪机制', 'DB 表字段', 'Redis Hash 队列', '减轻数据库压力, 高频读写场景Redis更合适'],
        ['超时兜底', '依赖调度表扫描', 'Redis TTL + 定时任务', '60秒超时, 兜底触发清洗'],
        ['任务失败重试', 'DB 记录失败状态', 'Redis 重试计数', '简化实现, 失败不影响主流程'],
    ],
    col_widths=[Cm(4), Cm(3.5), Cm(3.5), Cm(5)]
)

add_heading(doc, '3.4 替代方案: Redis 跟踪机制', level=2)
add_para(doc, '不创建调度表后, 采用 Redis Hash 跟踪两边模型完成状态:')

redis_text = """# Redis Key 设计
img:rec:done:{local_image_name}   # Hash 类型
  ├─ zhimou:      "1" / "0"      # 知眸回调是否到达 (1=已到达)
  ├─ self_model:  "1" / "0"      # 自有模型响应是否到达
  └─ create_time: "{timestamp}" # 首次写入时间, 用于超时判定

# 触发清洗的条件:
#   zhimou=1 AND self_model=1   → 立即触发清洗
#   60秒后两边都未到达          → 强制触发清洗 (按已到达部分判定)

# Key 过期时间: 24小时 (避免堆积)"""
add_code_block(doc, redis_text)

add_heading(doc, '3.5 决策依据与权衡', level=2)
add_para(doc, '选择 Redis 替代调度表的核心权衡:')
add_bullet(doc, '高频读写: 每张图片的回调/响应到达都会更新状态, Redis 性能远优于 DB')
add_bullet(doc, '临时数据: 状态跟踪是中间态, 完成后无需长期保留, Redis TTL 自动清理')
add_bullet(doc, '简化架构: 不增加表数量, 不需要 Mapper/Service/Entity 三层代码')
add_bullet(doc, '兜底可靠: 即使 Redis 宕机, 也可通过两表 clean_status=0 触发定时清洗任务补偿')
add_bullet(doc, '可观测性弱: 缺点是 Redis 数据不易审计排错, 但 raw_callback_json / raw_response_json 已在 DB 表中保留完整原始数据')

add_para(doc, '')
add_para(doc, '【风险提示】', bold=True, color=RGBColor(0xC0, 0x00, 0x00))
add_para(doc, '若后续业务需要保留每张图片的完整识别流水线记录 (例如统计平均耗时、失败率、重试次数等), 应重新评估是否恢复 image_recognition_job 调度表。当前阶段优先简化实现。')

doc.add_page_break()

# ============================================================
# 第四章: MinIO 事件接收与分发模块
# ============================================================
add_heading(doc, '第四章  MinIO 事件接收与分发模块', level=1)

add_heading(doc, '4.1 模块定位', level=2)
add_para(doc, '本模块是二期方案的入口, 负责感知"无人机降落后图片已同步到 MinIO"这一事件, 并触发后续两个AI模型的识别流程。')

add_heading(doc, '4.2 MinIO 端配置 (运维侧操作)', level=2)
add_para(doc, '需要在 MinIO 服务器侧完成以下配置:')
add_code_block(doc, """# 1. 启用 Webhook 通知目标 (编辑 ~/.minio/config.json 或 mc admin config set)
mc admin config set myminio notify_webhook:1 \\
  endpoint="http://192.168.2.130:8080/api/minio/notify" \\
  auth_token=""

# 2. 重启 MinIO 使配置生效
mc admin service restart myminio

# 3. 在目标 bucket 上开启事件通知
mc event add myminio/sikong-media arn:minio:sqs::1:webhook --event put

# 4. 验证事件通知已生效
mc event list myminio/sikong-media""")

add_heading(doc, '4.3 后端接收接口设计', level=2)
add_para(doc, '接口路径: POST /api/minio/notify')
add_para(doc, 'MinIO 推送的请求体结构:')
add_code_block(doc, """{
  "EventName": "s3:ObjectCreated:Put",
  "Key": "sikong-media/upload-dir/DJI_20260824_xxxx.jpeg",
  "Records": [{
    "s3": {
      "bucket": {"name": "sikong-media"},
      "object": {"key": "upload-dir/DJI_20260824_xxxx.jpeg", "size": 1234567}
    },
    "eventTime": "2026-08-24T10:00:00.000Z"
  }]
}""")

add_para(doc, '处理流程:')
add_bullet(doc, '1. 校验 EventName 是 s3:ObjectCreated:Put')
add_bullet(doc, '2. 从 Key 提取对象路径, 过滤非图片文件 (按扩展名 .jpg/.jpeg/.png)')
add_bullet(doc, '3. 从对象名提取 local_image_name (去掉路径和扩展名, 全局唯一)')
add_bullet(doc, '4. 异步分发 (@Async 或线程池) 到两个目标, 避免阻塞 MinIO 重试')
add_bullet(doc, '5. 立即返回 200 OK (MinIO 收到非 2xx 会重试, 可能造成重复处理)')

add_heading(doc, '4.4 图片获取方式决策', level=2)
add_para(doc, '从 MinIO 获取图片字节流有两种方式, 需结合"知眸 image 任务不支持 URL 传参"这一已确认决策点选择:')

add_table(doc,
    headers=['方案', '实现方式', '优点', '缺点', '可行性'],
    rows=[
        ['A: 预签名 URL', '后端生成 presigned URL 传给两个模型', '后端不消耗带宽和磁盘', '知眸任务不支持URL传参', '不可行'],
        ['B: 后端下载转发', '后端用 MinIO SDK getObject 拉到 bytes 再传给两个模型', '兼容知眸先落盘再上报模式、自有模型直接传 bytes', '多一次 IO, 需要临时磁盘空间', '可行(采纳)'],
    ],
    col_widths=[Cm(3), Cm(4), Cm(3.5), Cm(3.5), Cm(2)]
)

add_para(doc, '')
add_para(doc, '结合"知眸 image 任务不支持 URL 传参"这个已确认决策点, 方案 B 是唯一可行路径。具体流程:')
add_code_block(doc, """后端 getObject(Key) → bytes
   |
   +-- 1) 保存到本地 H:/traeproject/.../zhimou-images/yyyyMMdd/{local_image_name}.png
   |
   +-- 2) 调用 zhimouOpenApiClient.reportImage(taskId, imageId, sn, bytes) → 知眸
   |
   +-- 3) 调用自有模型 POST /api/v1/detect (传 base64 或 multipart) → 自有模型""")

add_heading(doc, '4.5 异步分发机制', level=2)
add_bullet(doc, '使用 Spring @Async("imageRecognitionExecutor") 独立线程池')
add_bullet(doc, '线程池参数: 核心 4, 最大 8, 队列 100 (待根据实际并发调整)')
add_bullet(doc, '不创建 image_recognition_job 调度表 (详见第三章决策)')
add_bullet(doc, '改用 Redis 队列 img:rec:pending:{local_image_name} 跟踪两个模型的完成状态')
add_bullet(doc, '超时判定 (参考已有 ZhimouCleanupRunner 思路): 知眸回调 60 秒未到 → 标记本次知眸识别超时')

doc.add_page_break()

# ============================================================
# 第五章: 知眸图片任务调用模块
# ============================================================
add_heading(doc, '第五章  知眸图片任务调用模块', level=1)

add_heading(doc, '5.1 模块定位', level=2)
add_para(doc, '本模块负责调用知眸图片识别 OpenAPI, 是异步回调模型。需先创建任务, 再上报图片, 然后等待知眸异步回调。')

add_heading(doc, '5.2 现有代码问题: 4 处字段错误', level=2)
add_para(doc, '现有 createImageTask() 方法 (ZhimouTaskServiceImpl.java line 1882-1914) 存在 4 处字段错误, 需要修复:')

add_table(doc,
    headers=['#', '现有错误代码', '正确写法 (参照 createVideoTask)', '说明'],
    rows=[
        ['1', 'body.put("app_id", appId)', 'body.put("app_ids", Arrays.asList(appId.split(",")))', 'app_id 应为 app_ids 数组'],
        ['2', 'body.put("application_package_id", ...)', 'body.put("zapk_id", applicationPackageId)', 'application_package_id 应为 zapk_id'],
        ['3', 'body.put("device", aircraftSn)', 'body.put("device_id", aircraftSn)', 'device 应为 device_id'],
        ['4', 'report.put("httpurl", ...); body.put("report", report)', 'body.put("report_url", properties.getOpenapi().getCallbackUrl())', '嵌套 report.httpurl 应为顶层 report_url'],
    ],
    col_widths=[Cm(1), Cm(5), Cm(5), Cm(5)]
)

add_heading(doc, '5.3 任务创建 → 图片上报 → 回调时序', level=2)
add_code_block(doc, """[后端] → POST /api/task (mode=image, app_ids, zapk_id, device_id, report_url)
   |  知眸返回 task_id (约 100-200ms)
   v
[后端] 持有 task_id
   |  循环每张图片:
   v
[后端] → POST /api/task/{task_id}/image?id={imageId}&device_id={sn}
        Content-Type: application/octet-stream
        Body: 图片二进制
   |  知眸处理约 28 秒/张
   v
[知眸] → POST {report_url} (回调: event=message, data.apps[].objects[])
   |  一次回调对应一张图片
   v
[后端] handleCallback() → 解析 → 存 zhimou_image_detection 表""")

add_heading(doc, '5.4 回调数据结构 (实测验证)', level=2)
add_para(doc, '以下数据结构来自 2026-08-18 实际测试验证:')
add_code_block(doc, """{
  "event": "message",
  "task_id": "tsk_xxx",
  "business_id": "我们传入的",
  "device_id": "飞机SN",
  "timestamp": 1787030348924,
  "data": {
    "frame": {
      "id": 0,
      "image": "/openapi/{task_id}/images/{ts}.jpg",
      "width": 1920,
      "height": 1080,
      "enhance": 1
    },
    "apps": [{
      "app_id": "napvhsbecn",
      "app_name": "小客车识别",
      "image": "/openapi/{task_id}/messages/{app_id}/{ts}.jpg",
      "objects": [
        {
          "class_id": "napvhsbecn",
          "name": "小客车",
          "color": "#FF0000",
          "x": 100,
          "y": 200,
          "track_id": "t1",
          "confidence": 0.85,
          "description": ""
        }
      ]
    }]
  }
}""")

add_heading(doc, '5.5 入库字段映射 (zhimou_image_detection 表)', level=2)
add_para(doc, '详细字段已定义在 sql/image_detection_tables.sql 中, 关键映射关系:')
add_table(doc,
    headers=['数据库字段', '回调数据来源', '说明'],
    rows=[
        ['local_image_name', 'MinIO 对象名去路径去扩展名', '数据清洗关联键, 全局唯一'],
        ['zhimou_image_path', 'data.frame.image', '知眸回调里原图路径'],
        ['zhimou_app_image', 'data.apps[0].image', '知眸app叠加标注后图片路径'],
        ['local_image_path', '后端本地落盘路径', 'yyyyMMdd/{local_image_name}.png'],
        ['targets_json', 'data.apps[].objects[]', '保留完整原文 JSON'],
        ['raw_callback_json', '完整回调 JSON', '排错/审计用'],
        ['callback_event', 'event 字段', 'start/message/stop'],
        ['event_timestamp_ms', 'timestamp', '毫秒时间戳'],
        ['event_time', 'timestamp 转 DATETIME', '事件时间'],
        ['hit_count', 'data.apps[].objects[] 总数', '本次回调命中目标总数'],
        ['clean_status', '清洗流程写入', '0未清洗/1已清洗/2误识别'],
    ],
    col_widths=[Cm(4), Cm(5), Cm(6)]
)

add_heading(doc, '5.6 关键实测结论 (2026-08-18)', level=2)
add_bullet(doc, '图片任务回调延迟约 28 秒')
add_bullet(doc, '一张图片对应一次 message 回调')
add_bullet(doc, '小客车识别 app (napvhsbecn) 可识别出小客车')
add_bullet(doc, '人员识别 app (3y14nhqxbd) 在测试图片中未命中结果')
add_bullet(doc, '成功回调需使用数组 app_ids、zapk_id、device_id 和顶层 report_url 字段')

doc.add_page_break()

# ============================================================
# 第六章: 自有模型同步调用模块
# ============================================================
add_heading(doc, '第六章  自有模型同步调用模块', level=1)

add_heading(doc, '6.1 模块定位', level=2)
add_para(doc, '本模块负责调用自有模型同步识别接口, 与知眸异步回调模式不同, 自有模型是同步调用, 请求即返回结果。')

add_heading(doc, '6.2 接口规范', level=2)
add_para(doc, '服务器地址: http://192.168.2.200:5173 (无身份验证)')
add_para(doc, '接口路径: POST /api/v1/detect')
add_para(doc, 'Content-Type: multipart/form-data')
add_para(doc, '请求参数:')
add_table(doc,
    headers=['参数名', '类型', '必填', '说明'],
    rows=[
        ['image', '文件 (multipart)', '是', '图片文件'],
        ['threshold', 'float', '否', '置信度阈值, 默认 0.5'],
        ['width', 'int', '否', '图片宽度 (像素)'],
        ['height', 'int', '否', '图片高度 (像素)'],
    ],
    col_widths=[Cm(3), Cm(4), Cm(2), Cm(6)]
)

add_heading(doc, '6.3 响应结构 (实测验证)', level=2)
add_code_block(doc, """{
  "code": 0,
  "message": "success",
  "data": {
    "request_id": "req_1787030348924_ab3d8f",
    "model_version": "mingp",
    "model_device": "cpu",
    "image_path": "data/images/req_xxx.png",
    "image_info": {
      "width": 1920,
      "height": 1080,
      "format": "png"
    },
    "detection_count": 2,
    "detections": [
      {
        "id": 1,
        "class_id": 0,
        "class_name": "person",
        "category": "厨余",
        "confidence": 0.75,
        "bbox": {
          "x1": 100,
          "y1": 200,
          "x2": 300,
          "y2": 400
        }
      }
    ],
    "processing_time_ms": 55,
    "processed_at": "2026-08-24T10:00:00.000Z",
    "latest_corrected_labels": null,
    "latest_feedback_id": null
  }
}""")

add_para(doc, '【关键发现】', bold=True, color=RGBColor(0xC0, 0x00, 0x00))
add_bullet(doc, '响应里有文档未提及的 category 字段 (如 "厨余"), 需保留入库')
add_bullet(doc, 'bbox 是绝对像素坐标 {x1,y1,x2,y2}, 无需坐标转换')
add_bullet(doc, '服务器生成 request_id (req_时间戳_hex 格式), 而非客户端指定')
add_bullet(doc, '详情接口 GET /api/v1/detect/{request_id} 响应里实际缺少 request_id 字段 (与文档不符)')
add_bullet(doc, '当前模型是 COCO 80 类 (版本 mingp), 识别效果较弱, 曾将航空照片误识别为 broccoli')

add_heading(doc, '6.4 调用状态管理', level=2)
add_table(doc,
    headers=['字段', '取值', '说明'],
    rows=[
        ['call_status', '0 未知 / 1 成功 / 2 失败', 'HTTP 非 200 或 code != 0 为失败'],
        ['error_message', '失败错误信息', '调用失败时记录'],
        ['call_time', '调用接口时间', 'DB 默认 CURRENT_TIMESTAMP'],
        ['processed_at', '服务器返回的 ISO 时间', '原样存为 VARCHAR'],
        ['processing_time_ms', '服务器处理耗时', '约 55ms'],
    ],
    col_widths=[Cm(4), Cm(5), Cm(6)]
)

add_heading(doc, '6.5 入库字段映射 (self_model_image_detection 表)', level=2)
add_para(doc, '详细字段已定义在 sql/image_detection_tables.sql 中, 关键映射关系:')
add_table(doc,
    headers=['数据库字段', '响应数据来源', '说明'],
    rows=[
        ['local_image_name', '与知眸表用同一个值', '数据清洗关联键'],
        ['local_image_path', '与知眸表用同一个本地落盘路径', '原图路径'],
        ['request_id', 'data.request_id', '服务器生成'],
        ['model_version', 'data.model_version', '如 mingp'],
        ['model_device', 'data.model_device', 'cpu/cuda:0'],
        ['targets_json', 'data.detections[]', '保留完整原文 JSON'],
        ['raw_response_json', '完整响应 JSON', '排错/审计用'],
        ['clean_status', '清洗流程写入', '0未清洗/1已清洗/2误识别'],
        ['call_status', 'HTTP/code 判定', '0未知/1成功/2失败'],
    ],
    col_widths=[Cm(4), Cm(5), Cm(6)]
)

doc.add_page_break()

# ============================================================
# 第七章: 数据清洗算法模块
# ============================================================
add_heading(doc, '第七章  数据清洗算法模块', level=1)

add_heading(doc, '7.1 模块定位', level=2)
add_para(doc, '本模块是二期的核心价值所在, 通过对比知眸和自有模型两套AI引擎的识别结果, 识别并标记误识别数据, 提升整体识别准确率。')

add_heading(doc, '7.2 核心思路', level=2)
add_para(doc, '判定原则: 同一张图片 (local_image_name 相同) 在 zhimou_image_detection 和 self_model_image_detection 两表中都有识别结果, 且目标位置匹配, 则判定为误识别。')
add_para(doc, '依据: 两个独立AI引擎对同一张图片都给出识别结果, 说明该图片存在容易误判的特征, 应标记为可疑数据待人工复核。')

add_heading(doc, '7.3 bbox 中心点距离匹配算法', level=2)
add_para(doc, '两表 bbox 格式不同, 需统一为中心点进行距离对比:')

algo_text = """知眸 bbox:   {x, y}                # 目标的某个点 (中心点或左上, 待确认)
自有模型 bbox: {x1, y1, x2, y2}     # 绝对像素矩形

统一为中心点:
  知眸:    center = (x, y)
  自有:    center = ((x1+x2)/2, (y1+y2)/2)

距离公式:
  dist = sqrt((cx1-cx2)^2 + (cy1-cy2)^2)

匹配阈值:
  dist < 50px  → 视为同一目标 (1920x1080 分辨率下约占 2.6%)
  阈值应配置化, 便于后续调整"""
add_code_block(doc, algo_text)

add_heading(doc, '7.4 误识别判定逻辑 (图片级)', level=2)
add_code_block(doc, """对一张图片 local_image_name:

Step 1: 查 zhimou_image_detection WHERE local_image_name = ?  → Z 记录
Step 2: 查 self_model_image_detection WHERE local_image_name = ?  → S 记录

情况 A: Z 和 S 都存在 且 都有命中目标
  → 进入"目标位置对比"流程
  → 对 Z 的每个目标, 找 S 中距离最近的目标
  → 如果所有 Z 目标都有匹配的 S 目标 (距离 < 阈值)
     → 判定为【误识别】clean_status = 2
  → 否则 (部分目标不匹配)
     → 判定为【正常识别】clean_status = 1

情况 B: 只有 Z 有结果, S 无结果或 S.call_status = 2
  → 判定为【正常识别】clean_status = 1

情况 C: 只有 S 有结果, Z 无结果或回调超时
  → 判定为【正常识别】clean_status = 1

情况 D: Z 和 S 都无结果
  → 不存在这种情况 (如果都没有, 根本不会进入清洗流程)""")

add_heading(doc, '7.5 clean_status 状态定义', level=2)
add_table(doc,
    headers=['状态值', '含义', '触发条件'],
    rows=[
        ['0', '未清洗', '新写入时的初始状态'],
        ['1', '已清洗 (正常)', '经过清洗流程判定为正常识别'],
        ['2', '误识别', '经过清洗流程判定为误识别'],
    ],
    col_widths=[Cm(2), Cm(5), Cm(8)]
)

add_para(doc, '')
add_para(doc, '【说明】两表的 clean_status 是各自独立的, 同一张图片在两表里可能有不同的 clean_status (例如知眸表是 2 但自有模型表是 1)。')

add_heading(doc, '7.6 触发时机', level=2)
add_bullet(doc, '触发条件: 一张图片的知眸回调和自有模型响应都到达后触发')
add_bullet(doc, 'Redis 跟踪: img:rec:done:{local_image_name} Hash 字段 zhimou/self_model 都为 1 时触发')
add_bullet(doc, '超时兜底: 60 秒后两边都未到达, 强制触发清洗 (按已到达部分判定)')
add_bullet(doc, '定时补偿: 每 5 分钟扫描 clean_status=0 的记录, 兜底未触发的清洗任务')

add_heading(doc, '7.7 阈值参数 (建议配置化)', level=2)
add_code_block(doc, """zhimou:
  cleanup:
    center-distance-threshold: 50    # 中心点距离匹配阈值 (像素)
    callback-timeout-sec: 60          # 回调超时秒数
    clean-task-cron: "0 */5 * * * ?"  # 每5分钟扫一次未清洗的""")

doc.add_page_break()

# ============================================================
# 第八章: 已确定的关键决策清单
# ============================================================
add_heading(doc, '第八章  已确定的关键决策清单', level=1)

add_para(doc, '本章汇总二期方案设计过程中已确定的关键决策, 作为后续实现的硬约束, 不可随意更改。')

add_heading(doc, '8.1 任务解耦相关', level=2)
add_bullet(doc, '视频和图像任务必须完全解耦: 在生命周期和数据存储上完全独立')
add_bullet(doc, '三张独立的数据库表: 知眸视频识别、知眸图片识别、自模型图像识别结果各自独立表, 无外键关系')
add_bullet(doc, '不复用现有 zhimou_detection_result 视频表: 图片用独立的 zhimou_image_detection 表')

add_heading(doc, '8.2 数据清洗关联相关', level=2)
add_bullet(doc, 'local_image_name 是关联键: 本地图像名必须同时存储在自模型和智谋图像识别表中以进行数据清洗关联')
add_bullet(doc, '图像文件名必须全局唯一: 用于数据清洗')
add_bullet(doc, '同图双识别结果判定为误识别: 同一张图片同时有智眸和自模型识别结果判定为误识别')

add_heading(doc, '8.3 知眸图片识别相关', level=2)
add_bullet(doc, '知眸图像任务回调数据必须包含所有回调信息和图像文件名')
add_bullet(doc, '知眸 image 任务不支持 URL 传参: 必须先落盘再上报 (实测确认)')
add_bullet(doc, '图片任务回调延迟约 28 秒')
add_bullet(doc, '一张图片对应一次 message 回调')
add_bullet(doc, '知眸请求体字段: app_ids (数组)、zapk_id、device_id、顶层 report_url')

add_heading(doc, '8.4 自有模型识别相关', level=2)
add_bullet(doc, '自模型图像识别结果必须包含本地图像路径以渲染边界框')
add_bullet(doc, '自模型检测使用绝对像素坐标 (x1,y1,x2,y2) 绘制边界框, 无需坐标转换')
add_bullet(doc, '自模型图像识别不返回标注图像路径: 必须使用检测结果和原始图像在本地渲染')
add_bullet(doc, '自模型检测结果包括 API 文档中未提及的 category 字段 (如 "厨余")')
add_bullet(doc, '自模型检测返回服务器生成的 request_id 而非客户端指定的标识符')
add_bullet(doc, '自模型检测是同步调用, 无任务管理概念')

add_heading(doc, '8.5 自有模型技术参数', level=2)
add_bullet(doc, '服务器地址: 192.168.2.200:5173, API 前缀 /api/v1')
add_bullet(doc, '无身份验证要求')
add_bullet(doc, '响应格式: {code, message, data}, code=0 表示成功')
add_bullet(doc, '处理时间约 55ms')
add_bullet(doc, '在 CPU 上运行 COCO 80 类模型 (版本 mingp), 识别性能较弱')
add_bullet(doc, '历史包含 1282 条记录, 支持分页')
add_bullet(doc, '检测结果包括 latest_corrected_labels 和 latest_feedback_id 字段用于标注修正')
add_bullet(doc, '模型信息可通过 GET /api/v1/models 接口访问')
add_bullet(doc, '支持通过 POST /api/v1/models/upload 接口上传模型')
add_bullet(doc, '使用 threshold 参数进行置信度过滤')
add_bullet(doc, '在 processing_time_ms 字段中返回处理时间')
add_bullet(doc, '请求包含图像宽度和高度参数')
add_bullet(doc, '响应包括 model_version 和 model_device 字段')

add_heading(doc, '8.6 调度表与状态跟踪相关', level=2)
add_bullet(doc, '调度表 image_recognition_job 暂不创建 (详见第三章决策过程)')
add_bullet(doc, '使用 Redis Hash 跟踪两边模型完成状态, 减轻数据库压力')
add_bullet(doc, 'Redis Key: img:rec:done:{local_image_name}, TTL 24小时')
add_bullet(doc, '超时 60 秒后强制触发清洗')
add_bullet(doc, '每 5 分钟定时扫描未清洗记录作为兜底')

add_heading(doc, '8.7 数据库表索引设计', level=2)
add_bullet(doc, '自模型检测表索引: request_id、local_image_name、model_version、call_time、clean_status')
add_bullet(doc, '智谋图像识别表索引: zhimou_task_id、business_id、local_image_name、device_id、event_time、clean_status')

add_heading(doc, '8.8 自模型检测表字段约束', level=2)
add_bullet(doc, '在数据库中存储原始响应 JSON (raw_response_json 字段)')
add_bullet(doc, '具有 call_status 和 error_message 字段用于跟踪请求成功/失败')
add_bullet(doc, '包括 call_time 和 processed_at 时间戳')
add_bullet(doc, '具有 clean_status、clean_time 和 clean_remark 字段用于数据清洗状态')
add_bullet(doc, '历史可通过 GET /api/v1/detect/history 接口访问')
add_bullet(doc, '详情接口 GET /api/v1/detect/{request_id} 响应数据中缺少 request_id (与文档不符)')

doc.add_page_break()

# ============================================================
# 第九章: 当前代码状态与待实现清单
# ============================================================
add_heading(doc, '第九章  当前代码状态与待实现清单', level=1)

add_heading(doc, '9.1 已完成项', level=2)
add_table(doc,
    headers=['项目', '状态', '说明'],
    rows=[
        ['zhimou_image_detection 表', '已创建', '29字段7索引, 已执行到 192.168.2.200 数据库'],
        ['self_model_image_detection 表', '已创建', '27字段6索引, 已执行到 192.168.2.200 数据库'],
        ['createImageTask() 方法', '已实现但有错误', 'ZhimouTaskServiceImpl.java line 1882-1914, 存在4处字段错误'],
        ['reportImage() 方法', '已实现', 'ZhimouOpenApiClient.java line 515-540, 图片上报接口'],
        ['handleCallback() 方法', '已实现', '当前写入视频表 zhimou_detection_result, 需要分流逻辑'],
        ['SQL 文件', '已生成', 'sql/image_detection_tables.sql'],
    ],
    col_widths=[Cm(5), Cm(3), Cm(8)]
)

add_heading(doc, '9.2 待实现清单', level=2)
add_table(doc,
    headers=['#', '模块', '待实现内容', '依赖'],
    rows=[
        ['1', 'MinIO事件接收', 'POST /api/minio/notify 接口, ObjectCreated 事件解析, 异步分发', 'MinIO端配置'],
        ['2', 'MinIO SDK接入', 'getObject 拉取图片字节流, 保存到本地', 'MinIO连接配置'],
        ['3', '知眸图片任务修复', '修复 createImageTask() 4处字段错误, 增加 mode 分流回调', '无'],
        ['4', '知眸回调分流', 'handleCallback() 按 zhimouTask.mode 分流到图片表/视频表', '图片任务修复'],
        ['5', '自有模型调用服务', 'POST /api/v1/detect 同步调用, 入库 self_model_image_detection', '无'],
        ['6', 'Redis状态跟踪', 'img:rec:done:{local_image_name} Hash 跟踪两边完成状态', 'Redis配置'],
        ['7', '数据清洗服务', '中心点距离匹配算法, 图片级 clean_status 综合判定', '前两项入库完成'],
        ['8', '超时兜底定时任务', '60秒超时强制清洗, 5分钟扫描未清洗记录', 'Redis跟踪'],
        ['9', '自有模型bbox本地渲染', '使用 detections[].bbox 在原图上画矩形框', '自有模型调用'],
        ['10', '配置项扩展', 'zhimou.cleanup.* 配置项, 阈值参数化', '无'],
    ],
    col_widths=[Cm(1), Cm(3.5), Cm(7), Cm(4.5)]
)

doc.add_page_break()

# ============================================================
# 第十章: 待确认问题清单
# ============================================================
add_heading(doc, '第十章  待确认问题清单', level=1)

add_para(doc, '以下问题在方案设计过程中提出但尚未最终确认, 需要在编码实现前与相关方对齐。')

add_heading(doc, '10.1 MinIO 相关', level=2)
add_bullet(doc, 'MinIO 服务器地址: 是 192.168.2.200:9000 还是其他? 现有代码里是 your-minio:9000 假值')
add_bullet(doc, 'Bucket 名: 是 sikong-media 还是其他?')
add_bullet(doc, 'MinIO 凭证: minioadmin/minioadmin 是否生产值?')
add_bullet(doc, '图片对象 Key 路径规则: 大疆无人机上传到 MinIO 的对象 Key 格式是什么? (决定如何提取 local_image_name)')

add_heading(doc, '10.2 知眸图片任务相关', level=2)
add_bullet(doc, '图片任务触发时机: 是 MinIO 事件到达时临时创建图片任务、上报完所有图片后再关闭? 还是预先创建一个长期运行的图片任务?')
add_bullet(doc, 'imageId 参数 (reportImage 中): 用 local_image_name 的 hash? 还是递增序列? 这关系到知眸回调里能否反向关联到本地图片')
add_bullet(doc, 'business_id 传什么: live_stream.id? work_order.id? 还是 local_image_name?')
add_bullet(doc, '现有 handleCallback() 是否复用: 当前是写入视频表, 图片任务回调需按 zhimouTask.mode="image" 字段分流到图片表')

add_heading(doc, '10.3 自有模型相关', level=2)
add_bullet(doc, '同步调用超时: 设多少毫秒合适? (参考: 识别本身约 55ms, 加上网络 200ms, 建议 5000ms)')
add_bullet(doc, '失败重试: 失败是否重试? 建议重试 1 次, 间隔 1 秒')
add_bullet(doc, '图片传输方式: multipart/form-data 还是 base64? (文档未明, 需要实测)')
add_bullet(doc, 'threshold 默认值: 用 0.5 还是 0.3? (影响命中数量)')
add_bullet(doc, '误识别数据回流: 自有模型支持 latest_corrected_labels 修正, 是否要把我们判定为误识别的数据回灌给自有模型训练?')

add_heading(doc, '10.4 数据清洗相关', level=2)
add_bullet(doc, '知眸 bbox 的 {x, y} 含义: 是中心点还是左上角? 需要根据知眸文档或实测确认')
add_bullet(doc, '距离阈值: 50 像素是否合适? 1920x1080 分辨率下 50 像素占比 2.6%, 可能太严')
add_bullet(doc, '判定方向: 是"知眸和自有模型都有结果 → 误识别"还是"知眸有但自有模型没有 → 误识别"? 根据硬约束是前者')
add_bullet(doc, '误识别后处理: clean_status=2 的记录是直接标记? 还是需要人工复核表?')
add_bullet(doc, '类目不一致算误识别吗: 比如知眸识别"小客车", 自有模型识别"person", 位置匹配但类目不同, 是否也算误识别?')

doc.add_page_break()

# ============================================================
# 第十一章: 风险与建议
# ============================================================
add_heading(doc, '第十一章  风险与建议', level=1)

add_heading(doc, '11.1 主要风险', level=2)

add_table(doc,
    headers=['风险点', '影响', '缓解措施'],
    rows=[
        ['知眸图片任务回调延迟28秒', '用户体验下降, 数据清洗触发延后', '前端轮询展示时显示"识别中"状态'],
        ['自有模型识别性能弱 (COCO80类)', '误判率高, 数据清洗负担重', '本期作为对比基准, 后续可上传专用模型'],
        ['MinIO事件通知丢失', '图片漏识别', '定时扫描 MinIO 对比 DB 兜底'],
        ['Redis 宕机状态丢失', '清洗流程中断', '每5分钟定时扫描 clean_status=0 兜底'],
        ['知眸 bbox {x,y} 含义不明', '距离匹配算法可能错误', '编码前用样本数据实测验证'],
        ['并发量大时线程池堆积', '事件处理延迟', '线程池参数化, 可动态调整'],
    ],
    col_widths=[Cm(4.5), Cm(5), Cm(6.5)]
)

add_heading(doc, '11.2 实施建议', level=2)
add_bullet(doc, '优先级: 先修复知眸图片任务 4 处字段错误 → 再实现自有模型调用 → 再实现数据清洗')
add_bullet(doc, '分阶段验证: 每个模块单独测试通过后再集成, 避免问题相互干扰')
add_bullet(doc, '保留原始数据: raw_callback_json 和 raw_response_json 必须完整保存, 排错关键')
add_bullet(doc, '阈值参数化: 距离阈值、超时时间、cron 表达式等都做成配置项, 不硬编码')
add_bullet(doc, '监控告警: 关键节点 (MinIO事件接收、知眸回调、自有模型响应、清洗触发) 增加日志和告警')

# ============================================================
# 附录
# ============================================================
doc.add_page_break()
add_heading(doc, '附录  关键文件路径索引', level=1)

add_table(doc,
    headers=['类别', '文件路径', '说明'],
    rows=[
        ['数据库SQL', 'sql/image_detection_tables.sql', '图片识别结果两张表DDL'],
        ['知眸客户端', 'src/main/java/com/dji/sikong/openapi/ZhimouOpenApiClient.java', '知眸 OpenAPI 调用封装'],
        ['知眸服务', 'src/main/java/com/dji/sikong/service/impl/ZhimouTaskServiceImpl.java', '知眸任务服务实现 (含 createImageTask 4处错误)'],
        ['知眸控制器', 'src/main/java/com/dji/sikong/controller/ZhimouController.java', '知眸 API 控制器 (含 callback 回调入口)'],
        ['知眸配置', 'src/main/java/com/dji/sikong/config/ZhimouProperties.java', '知眸配置类'],
        ['任务配置实体', 'src/main/java/com/dji/sikong/entity/ZhimouTaskConfig.java', 'zhimou_task_config 表实体'],
        ['MQTT客户端', 'src/main/java/com/dji/sikong/mqtt/DjiMqttClient.java', 'DJI MQTT 客户端 (含 MinIO 占位)'],
        ['部署配置', 'deploy-package/application-company-a.yml', '生产部署配置文件'],
        ['项目级约束', 'c:/Users/Administrator/.trae-cn/memory/projects/-h-traeproject-zhoudao--p2-cc292b0bf87c6272edf2/project_memory.md', '项目级硬约束记录'],
        ['会话级记忆', 'c:/Users/Administrator/.trae-cn/memory/projects/-h-traeproject-zhoudao--p2-cc292b0bf87c6272edf2/20260818/', '2026-08-18 设计讨论记忆'],
    ],
    col_widths=[Cm(3), Cm(8), Cm(5)]
)

# 文档结尾
doc.add_paragraph()
end_p = doc.add_paragraph()
end_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
end_run = end_p.add_run('—— 文档结束 ——')
end_run.font.name = '微软雅黑'
end_run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
end_run.font.size = Pt(11)
end_run.font.color.rgb = RGBColor(0x59, 0x59, 0x59)

# 保存文档
output_path = r'h:\traeproject\zhoudao\产品二期设计方案.docx'
doc.save(output_path)
print(f'文档已生成: {output_path}')
