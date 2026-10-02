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
    /** 同时更新账号的用户名与密码哈希（用于自动建号后补设凭据）。 */
    int updateUsernameAndPasswordById(@Param("id") Long id, @Param("username") String username,
                                      @Param("passwordHash") String passwordHash);
    Long selectRoleIdByCode(@Param("roleCode") String roleCode);
    int insertRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName);
    int insertAccountRole(@Param("accountId") Long accountId, @Param("roleId") Long roleId);
    List<String> selectRoleCodesByAccountId(@Param("accountId") Long accountId);
}
