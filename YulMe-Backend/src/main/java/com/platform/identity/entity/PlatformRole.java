package com.platform.identity.entity;

/**
 * What kind of platform actor an account is, everywhere - deliberately separate
 * from {@link com.platform.family.entity.MembershipRole} (OWNER/GUARDIAN), which
 * answers a different question scoped to one family. A PARENT platform account can
 * hold OWNER or GUARDIAN membership in one or more families; TEACHER/PRINCIPAL/ADMIN
 * accounts are not expected to hold family memberships at all (see
 * AccountRegistrationWriter). There is no CHILD value here: children never hold
 * their own Account/credentials (see Child's javadoc) - they're accessed through a
 * parent's family membership, not authenticated as a platform role in their own right.
 */
public enum PlatformRole {

    PARENT,
    TEACHER,
    PRINCIPAL,
    ADMIN

}
