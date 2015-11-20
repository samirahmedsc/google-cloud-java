/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gcloud.resourcemanager;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gcloud.resourcemanager.Policy.Binding;
import com.google.gcloud.resourcemanager.Policy.Member;
import com.google.gcloud.resourcemanager.Policy.RoleType;
import com.google.gcloud.spi.ResourceManagerRpc.Permission;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ProjectTest {
  private static final String ID = "project-id";
  private static final String NAME = "myProj";
  private static final Map<String, String> LABELS = ImmutableMap.of("k1", "v1", "k2", "v2");
  private static final Long NUMBER = 123L;
  private static final Long CREATE_TIME_MILLIS = 123456789L;
  private static final ProjectInfo.State STATE = ProjectInfo.State.DELETE_REQUESTED;
  private static final ResourceId PARENT = ResourceId.of("owner-id", "organization");
  private static final ProjectInfo PROJECT_INFO =
      ProjectInfo.builder(ID)
          .name(NAME)
          .labels(LABELS)
          .number(NUMBER)
          .createTimeMillis(CREATE_TIME_MILLIS)
          .state(STATE)
          .parent(PARENT)
          .build();
  private static final List<Member> OWNER_MEMBER_LIST = ImmutableList.of(
      Member.user("first-owner@email.com"), Member.group("group-of-owners@email.com"));
  private static final List<Member> EDITOR_MEMBER_LIST =
      ImmutableList.of(Member.serviceAccount("editor@someemail.com"));
  private static final List<Member> VIEWER_MEMBER_LIST =
      ImmutableList.of(Member.serviceAccount("app@someemail.com"), Member.user("viewer@email.com"));
  private static final Binding OWNER_BINDING =
      Policy.Binding.builder().role(RoleType.OWNER).members(OWNER_MEMBER_LIST).build();
  private static final Binding EDITOR_BINDING =
      Policy.Binding.builder().role(RoleType.EDITOR).members(EDITOR_MEMBER_LIST).build();
  private static final Binding VIEWER_BINDING =
      Policy.Binding.builder().role(RoleType.VIEWER).members(VIEWER_MEMBER_LIST).build();
  private static final Policy POLICY =
      Policy.builder()
          .addBinding(OWNER_BINDING)
          .addBinding(EDITOR_BINDING)
          .addBinding(VIEWER_BINDING)
          .version(1)
          .etag("some-etag-value")
          .build();
  private static final Permission[] PERMISSIONS_REQUESTED = {Permission.REPLACE, Permission.GET};
  private static final List<Boolean> PERMISSIONS_OWNED = ImmutableList.of(false, true);

  private ResourceManager resourceManager;
  private Project project;

  @Before
  public void setUp() throws Exception {
    resourceManager = createStrictMock(ResourceManager.class);
    project = new Project(resourceManager, PROJECT_INFO, POLICY);
  }

  @After
  public void tearDown() throws Exception {
    verify(resourceManager);
  }

  @Test
  public void testLoad() {
    expect(resourceManager.get(PROJECT_INFO.id())).andReturn(PROJECT_INFO);
    expect(resourceManager.getIamPolicy(PROJECT_INFO.id())).andReturn(POLICY);
    replay(resourceManager);
    Project loadedProject = Project.load(resourceManager, PROJECT_INFO.id());
    assertEquals(PROJECT_INFO, loadedProject.info());
    assertEquals(POLICY, loadedProject.policy());
  }

  @Test
  public void testReload() {
    ProjectInfo newInfo = PROJECT_INFO.toBuilder().addLabel("k3", "v3").build();
    Policy newPolicy = POLICY.toBuilder().removeBinding(VIEWER_BINDING).build();
    expect(resourceManager.get(PROJECT_INFO.id())).andReturn(newInfo);
    expect(resourceManager.getIamPolicy(PROJECT_INFO.id())).andReturn(newPolicy);
    replay(resourceManager);
    Project newProject = project.reload();
    assertSame(resourceManager, newProject.resourceManager());
    assertEquals(newInfo, newProject.info());
    assertEquals(newPolicy, newProject.policy());
  }

  @Test
  public void testPolicy() {
    replay(resourceManager);
    assertEquals(POLICY, project.policy());
  }

  @Test
  public void testInfo() {
    replay(resourceManager);
    assertEquals(PROJECT_INFO, project.info());
  }

  @Test
  public void testResourceManager() {
    replay(resourceManager);
    assertEquals(resourceManager, project.resourceManager());
  }

  @Test
  public void testDelete() {
    resourceManager.delete(PROJECT_INFO.id());
    expectLastCall();
    replay(resourceManager);
    project.delete();
  }

  @Test
  public void testUndelete() {
    resourceManager.undelete(PROJECT_INFO.id());
    expectLastCall();
    replay(resourceManager);
    project.undelete();
  }

  @Test
  public void testReplace() {
    ProjectInfo newInfo = PROJECT_INFO.toBuilder().addLabel("k3", "v3").build();
    expect(resourceManager.replace(newInfo)).andReturn(newInfo);
    replay(resourceManager);
    Project newProject = project.replace(newInfo);
    assertSame(resourceManager, newProject.resourceManager());
    assertEquals(newInfo, newProject.info());
    assertEquals(POLICY, newProject.policy());
  }

  @Test
  public void testReplaceIamPolicy() {
    Policy newPolicy = POLICY.toBuilder().removeBinding(VIEWER_BINDING).build();
    expect(resourceManager.replaceIamPolicy(PROJECT_INFO.id(), newPolicy)).andReturn(newPolicy);
    replay(resourceManager);
    Project newProject = project.replaceIamPolicy(newPolicy);
    assertSame(resourceManager, newProject.resourceManager());
    assertEquals(PROJECT_INFO, newProject.info());
    assertEquals(newPolicy, newProject.policy());
  }

  @Test
  public void testHasPermissions() {
    expect(resourceManager.hasPermissions(PROJECT_INFO.id(), PERMISSIONS_REQUESTED))
        .andReturn(PERMISSIONS_OWNED);
    replay(resourceManager);
    List<Boolean> response =
        project.hasPermissions(PERMISSIONS_REQUESTED[0], PERMISSIONS_REQUESTED[1]);
    assertEquals(PERMISSIONS_OWNED, response);
  }

  @Test
  public void testHasAllPermissions() {
    expect(resourceManager.hasPermissions(PROJECT_INFO.id(), PERMISSIONS_REQUESTED))
        .andReturn(PERMISSIONS_OWNED);
    Permission[] permissionsRequestAllOwned = {Permission.UNDELETE, Permission.DELETE};
    List<Boolean> permissionsResponseAllOwned = ImmutableList.of(true, true);
    expect(resourceManager.hasPermissions(PROJECT_INFO.id(), permissionsRequestAllOwned))
        .andReturn(permissionsResponseAllOwned);
    replay(resourceManager);
    assertFalse(project.hasAllPermissions(PERMISSIONS_REQUESTED));
    assertTrue(project.hasAllPermissions(permissionsRequestAllOwned));
  }
}
