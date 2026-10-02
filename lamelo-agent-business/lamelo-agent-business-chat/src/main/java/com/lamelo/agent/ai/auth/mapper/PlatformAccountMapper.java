package com.lamelo.agent.ai.auth.mapper;

import com.lamelo.agent.ai.auth.data.PlatformAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformAccountMapper {
    PlatformAccount selectActiveByUsername(@Param("username") String username);
    PlatformAccount selectActiveById(@Param("id") Long id);
    int insertAccount(PlatformAccount account);
    int updatePasswordHashById(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    int updateEnabledById(@Param("id") Long id, @Param("enabled") boolean enabled);
    Long selectRoleIdByCode(@Param("roleCode") String roleCode);
    int insertRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName);
    int insertAccountRole(@Param("accountId") Long accountId, @Param("roleId") Long roleId);
    List<String> selectRoleCodesByAccountId(@Param("accountId") Long accountId);
}
