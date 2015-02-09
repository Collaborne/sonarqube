/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.core.user;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.sonar.api.ServerComponent;
import org.sonar.core.persistence.DaoComponent;
import org.sonar.core.persistence.MyBatis;

import javax.annotation.Nullable;

import java.util.*;

import static com.google.common.collect.Maps.newHashMap;

public class AuthorizationDao implements ServerComponent, DaoComponent {

  private static final String USER_ID_PARAM = "userId";
  private final MyBatis mybatis;

  public AuthorizationDao(MyBatis mybatis) {
    this.mybatis = mybatis;
  }

  public Set<String> keepAuthorizedComponentKeys(Set<String> componentKeys, @Nullable Integer userId, String role) {
    SqlSession session = mybatis.openSession(false);
    try {
      return keepAuthorizedComponentKeys(componentKeys, userId, role, session);

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public Set<String> keepAuthorizedComponentKeys(Set<String> componentKeys, @Nullable Integer userId, String role, SqlSession session) {
    if (componentKeys.isEmpty()) {
      return Collections.emptySet();
    }
    String sql;
    Map<String, Object> params;
    if (userId == null) {
      sql = "keepAuthorizedComponentKeysForAnonymous";
      params = ImmutableMap.of("role", role, "componentKeys", componentKeys);
    } else {
      sql = "keepAuthorizedComponentKeysForUser";
      params = ImmutableMap.of(USER_ID_PARAM, userId, "role", role, "componentKeys", componentKeys);
    }

    return Sets.newHashSet(session.<String>selectList(sql, params));
  }

  public Set<String> keepAuthorizedComponentIds(SqlSession session, Set<Long> componentIds, @Nullable Integer userId, String role) {
    if (componentIds.isEmpty()) {
      return Collections.emptySet();
    }
    String sql;
    Map<String, Object> params;
    if (userId == null) {
      sql = "keepAuthorizedComponentIdsForAnonymous";
      params = ImmutableMap.of("role", role, "componentIds", componentIds);
    } else {
      sql = "keepAuthorizedComponentIdsForUser";
      params = ImmutableMap.of(USER_ID_PARAM, userId, "role", role, "componentIds", componentIds);
    }

    return Sets.newHashSet(session.<String>selectList(sql, params));
  }

  /**
   * Used by the Views Plugin
   */
  public boolean isAuthorizedComponentKey(String componentKey, @Nullable Integer userId, String role) {
    return keepAuthorizedComponentKeys(Sets.newHashSet(componentKey), userId, role).size() == 1;
  }

  public Collection<String> selectAuthorizedRootProjectsKeys(@Nullable Integer userId, String role) {
    SqlSession session = mybatis.openSession(false);
    try {
      return selectAuthorizedRootProjectsKeys(userId, role, session);

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public Collection<String> selectAuthorizedRootProjectsUuids(@Nullable Integer userId, String role) {
    SqlSession session = mybatis.openSession(false);
    try {
      return selectAuthorizedRootProjectsUuids(userId, role, session);

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public Collection<String> selectAuthorizedRootProjectsKeys(@Nullable Integer userId, String role, SqlSession session) {
    String sql;
    Map<String, Object> params = newHashMap();
    sql = "selectAuthorizedRootProjectsKeys";
    params.put(USER_ID_PARAM, userId);
    params.put("role", role);

    return session.selectList(sql, params);
  }

  public Collection<String> selectAuthorizedRootProjectsUuids(@Nullable Integer userId, String role, SqlSession session) {
    String sql;
    Map<String, Object> params = newHashMap();
    sql = "selectAuthorizedRootProjectsUuids";
    params.put(USER_ID_PARAM, userId);
    params.put("role", role);

    return session.selectList(sql, params);
  }

  public List<String> selectGlobalPermissions(@Nullable String userLogin) {
    SqlSession session = mybatis.openSession(false);
    try {
      Map<String, Object> params = newHashMap();
      params.put("userLogin", userLogin);
      return session.selectList("selectGlobalPermissions", params);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }
}
