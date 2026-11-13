package com.growthkids.api.common.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 统一分页响应（规范 §87）：{ items: [], pagination: { page, pageSize, total, totalPages } }。 */
public record PageResponse<T>(List<T> items, Pagination pagination) {

    public record Pagination(long page, long pageSize, long total, long totalPages) {}

    public static <T> PageResponse<T> of(IPage<T> page) {
        return new PageResponse<>(
                page.getRecords(),
                new Pagination(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()));
    }

    /** 转换 items 元素类型，保留分页信息。 */
    public <R> PageResponse<R> mapRecords(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(items.size());
        for (T item : items) {
            mapped.add(mapper.apply(item));
        }
        return new PageResponse<>(mapped, pagination);
    }
}
