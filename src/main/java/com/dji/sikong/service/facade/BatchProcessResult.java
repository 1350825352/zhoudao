package com.dji.sikong.service.facade;

import lombok.Data;

import java.io.Serializable;

/**
 * ImageRecognitionFacade.processImageBatch 返回值
 */
@Data
public class BatchProcessResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功提交 (仅代表入队，不等知眸回调) */
    private boolean success = true;
    /** 错误信息（success=false 时填充） */
    private String errorMsg;
    /** 图片数 */
    private int imageCount;
    /** 知眸返回的 taskId */
    private String zhimouTaskId;
    /** 知眸异常信息 */
    private String zhimouError;
    /** 自有模型成功数 */
    private int selfModelSuccess;
    /** 自有模型失败数 */
    private int selfModelFail;

    public static BatchProcessResult fail(String msg) {
        BatchProcessResult r = new BatchProcessResult();
        r.setSuccess(false);
        r.setErrorMsg(msg);
        return r;
    }
}
