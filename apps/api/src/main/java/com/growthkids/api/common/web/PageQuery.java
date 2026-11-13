package com.growthkids.api.common.web;

/** 分页参数（规范 §87）：page 从 1 开始，pageSize 上限 100，超限收敛。 */
public record PageQuery(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static PageQuery of(Integer page, Integer pageSize) {
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedSize =
                pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new PageQuery(resolvedPage, resolvedSize);
    }
}
