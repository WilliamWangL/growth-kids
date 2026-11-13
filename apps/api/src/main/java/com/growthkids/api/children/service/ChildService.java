package com.growthkids.api.children.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.dto.CreateChildRequest;
import com.growthkids.api.children.dto.UpdateChildRequest;
import com.growthkids.api.children.entity.Child;
import com.growthkids.api.children.mapper.ChildMapper;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.growth.service.GrowthRecordService;
import com.growthkids.api.membership.dto.PlanPermissions;
import com.growthkids.api.membership.service.PlanResolver;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 孩子服务（TASK-006，规范 §28/§70）：
 * 所有操作强制 ownership 校验（user_id 条件），删除为软删除（status=deleted）。
 */
@Service
public class ChildService {

    private final ChildMapper childMapper;
    private final PlanResolver planResolver;
    private final GrowthRecordService growthRecordService;

    public ChildService(
            ChildMapper childMapper,
            PlanResolver planResolver,
            GrowthRecordService growthRecordService) {
        this.childMapper = childMapper;
        this.planResolver = planResolver;
        this.growthRecordService = growthRecordService;
    }

    @Transactional
    public ChildResponse create(long userId, CreateChildRequest request) {
        long existing =
                childMapper.selectCount(new LambdaQueryWrapper<Child>().eq(Child::getUserId, userId));
        // 限额按套餐解析（TASK-016，§64：Free=1 / Pro=5，-1 不限）
        long limit = planResolver.resolve(userId).permissions().childLimit();
        if (limit != PlanPermissions.UNLIMITED && existing >= limit) {
            throw new ApiException(ApiErrorCode.CHILD_LIMIT_REACHED);
        }
        validateBirthDate(request.birthDate());
        Child child = new Child();
        child.setUserId(userId);
        child.setName(request.name().trim());
        child.setGender(request.gender());
        child.setBirthDate(request.birthDate());
        child.setBirthHeightCm(request.birthHeightCm());
        child.setBirthWeightKg(request.birthWeightKg());
        child.setFatherHeightCm(request.fatherHeightCm());
        child.setMotherHeightCm(request.motherHeightCm());
        child.setAvatarUrl(normalizeOptional(request.avatarUrl()));
        child.setNotes(normalizeOptional(request.notes()));
        child.setStatus(Child.STATUS_ACTIVE);
        childMapper.insert(child);
        return ChildResponse.from(childMapper.selectById(child.getId()));
    }

    public PageResponse<ChildResponse> list(long userId, PageQuery query) {
        Page<Child> page = new Page<>(query.page(), query.pageSize());
        childMapper.selectPage(
                page,
                new LambdaQueryWrapper<Child>()
                        .eq(Child::getUserId, userId)
                        .orderByAsc(Child::getBirthDate)
                        .orderByAsc(Child::getId));
        return PageResponse.of(page).mapRecords(ChildResponse::from);
    }

    public ChildResponse get(long userId, long childId) {
        return ChildResponse.from(requireOwned(userId, childId));
    }

    @Transactional
    public ChildResponse update(long userId, long childId, UpdateChildRequest request) {
        requireOwned(userId, childId);
        LambdaUpdateWrapper<Child> update =
                new LambdaUpdateWrapper<Child>().eq(Child::getId, childId).eq(Child::getUserId, userId);
        boolean changed = false;
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new ApiException(
                        ApiErrorCode.CHILD_INVALID_DATA,
                        "Child name must not be blank",
                        Map.of("name", "must not be blank"));
            }
            update.set(Child::getName, name);
            changed = true;
        }
        if (request.gender() != null) {
            update.set(Child::getGender, request.gender());
            changed = true;
        }
        if (request.birthDate() != null) {
            validateBirthDate(request.birthDate());
            // 规范 §37：出生日期变化时重算全部成长记录年龄；存在早于新生日的记录则拒绝
            growthRecordService.onChildBirthDateChanged(childId, request.birthDate());
            update.set(Child::getBirthDate, request.birthDate());
            changed = true;
        }
        if (request.birthHeightCm() != null) {
            update.set(Child::getBirthHeightCm, request.birthHeightCm());
            changed = true;
        }
        if (request.birthWeightKg() != null) {
            update.set(Child::getBirthWeightKg, request.birthWeightKg());
            changed = true;
        }
        if (request.fatherHeightCm() != null) {
            update.set(Child::getFatherHeightCm, request.fatherHeightCm());
            changed = true;
        }
        if (request.motherHeightCm() != null) {
            update.set(Child::getMotherHeightCm, request.motherHeightCm());
            changed = true;
        }
        if (request.avatarUrl() != null) {
            update.set(Child::getAvatarUrl, normalizeOptional(request.avatarUrl()));
            changed = true;
        }
        if (request.notes() != null) {
            update.set(Child::getNotes, normalizeOptional(request.notes()));
            changed = true;
        }
        if (changed) {
            childMapper.update(null, update);
        }
        return ChildResponse.from(requireOwned(userId, childId));
    }

    /** 软删除（规范 §28）：@TableLogic 将 status 置为 deleted。 */
    @Transactional
    public void delete(long userId, long childId) {
        requireOwned(userId, childId);
        childMapper.delete(
                new LambdaQueryWrapper<Child>().eq(Child::getId, childId).eq(Child::getUserId, userId));
    }

    /** ownership 校验（规范 §70）：非本人孩子一律按不存在处理，避免泄露存在性。 */
    private Child requireOwned(long userId, long childId) {
        Child child =
                childMapper.selectOne(
                        new LambdaQueryWrapper<Child>().eq(Child::getId, childId).eq(Child::getUserId, userId));
        if (child == null) {
            throw new ApiException(ApiErrorCode.CHILD_NOT_FOUND);
        }
        return child;
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ApiException(
                    ApiErrorCode.CHILD_INVALID_DATA,
                    "Birth date must not be in the future",
                    Map.of("birthDate", "must not be in the future"));
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
