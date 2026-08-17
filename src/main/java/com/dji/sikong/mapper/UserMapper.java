package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}