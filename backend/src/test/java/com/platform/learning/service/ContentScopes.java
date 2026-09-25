package com.platform.learning.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Unit-test fixture for services that consult {@link PathContentScope} (N7) but whose
 * tests are about something else: every piece of content is in scope. The tenancy
 * rule itself is covered against PostgreSQL by {@code ContentTenancyIntegrationTests}.
 */
public final class ContentScopes {

    private ContentScopes() {
    }

    public static PathContentScope admittingEverything() {
        PathContentScope scope = mock(PathContentScope.class);
        lenient().when(scope.forPath(any())).thenReturn(PathContentScope.Scope.PLATFORM_ONLY);
        lenient().when(scope.forCurriculumVersion(any())).thenReturn(PathContentScope.Scope.PLATFORM_ONLY);
        lenient().when(scope.admitsActivity(any(), any())).thenReturn(true);
        lenient().when(scope.admitsActivityForChild(any(), any())).thenReturn(true);
        lenient().when(scope.isPlatformCurriculum(any())).thenReturn(true);
        return scope;
    }

}
