package com.growthkids.api.deal;

/**
 * 优惠商品分类（TASK-022）。
 *
 * <p>code 入库并作为 API 契约，label 仅用于展示；前端通过
 * GET /api/v1/deals/categories 获取全量选项，无需在客户端硬编码分类表。
 */
public enum DealCategory {
    TOYS("toys", "玩具"),
    BOOKS("books", "绘本"),
    CLOTHING("clothing", "童装"),
    GEAR("gear", "出行装备"),
    NUTRITION("nutrition", "营养"),
    CARE("care", "洗护"),
    OTHER("other", "其他");

    /** Bean Validation 用（注解要求编译期常量）。 */
    public static final String PATTERN = "toys|books|clothing|gear|nutrition|care|other";

    private final String code;

    private final String label;

    DealCategory(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static DealCategory fromCode(String code) {
        for (DealCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown deal category: " + code);
    }
}
