package com.daylog.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.daylog.modules.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper，继承 BaseMapper 即获得通用 CRUD
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
