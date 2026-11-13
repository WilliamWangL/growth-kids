package com.growthkids.api.who.importer;

/** WHO 数据导入异常（文件缺失/格式错误/校验失败）。导入必须在产生副作用前快速失败。 */
public class WhoDataException extends RuntimeException {

    public WhoDataException(String message) {
        super(message);
    }

    public WhoDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
