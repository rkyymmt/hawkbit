/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.hawkbit.repository.exception.ConstraintViolationException;
import org.eclipse.hawkbit.repository.exception.RolloutIllegalStateException;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.RolloutGroup;
import org.eclipse.hawkbit.repository.model.RolloutGroupConditions;

/**
 * A collection of static helper methods for the {@link RolloutManagement}
 */
public final class RolloutHelper {
    private RolloutHelper() {
    }

    /**
     * Verifies that the required success condition and action are actually set.
     * 
     * @param conditions
     *            input conditions and actions
     */
    public static void verifyRolloutGroupConditions(final RolloutGroupConditions conditions) {
        if (conditions.getSuccessCondition() == null) {
            throw new ConstraintViolationException("Rollout group is missing success condition");
        }
        if (conditions.getSuccessAction() == null) {
            throw new ConstraintViolationException("Rollout group is missing success action");
        }
    }

    /**
     * Verifies that the group has the required success condition and action and
     * a falid target percentage.
     * 
     * @param group
     *            the input group
     * @return the verified group
     */
    public static RolloutGroup verifyRolloutGroupHasConditions(final RolloutGroup group) {
        if (group.getTargetPercentage() < 1F || group.getTargetPercentage() > 100F) {
            throw new ConstraintViolationException("Target percentage has to be between 1 and 100");
        }

        if (group.getSuccessCondition() == null) {
            throw new ConstraintViolationException("Rollout group is missing success condition");
        }
        if (group.getSuccessAction() == null) {
            throw new ConstraintViolationException("Rollout group is missing success action");
        }
        return group;
    }

    /**
     * Verify if the supplied amount of groups is in range
     * 
     * @param amountGroup
     *            amount of groups
     */
    public static void verifyRolloutGroupParameter(final int amountGroup) {
        if (amountGroup <= 0) {
            throw new ConstraintViolationException("the amountGroup must be greater than zero");
        } else if (amountGroup > 500) {
            throw new ConstraintViolationException("the amountGroup must not be greater than 500");
        }
    }

    /**
     * Verify that the supplied percentage is in range
     * 
     * @param percentage
     *            the percentage
     */
    public static void verifyRolloutGroupTargetPercentage(final float percentage) {
        if (percentage <= 0) {
            throw new ConstraintViolationException("the percentage must be greater than zero");
        } else if (percentage > 100) {
            throw new ConstraintViolationException("the percentage must not be greater than 100");
        }
    }

    /**
     * Modifies the target filter query to only match targets that were created
     * after the Rollout.
     * 
     * @param rollout
     *            Rollout to derive the filter from
     * @return resulting target filter query
     */
    public static String getTargetFilterQuery(final Rollout rollout) {
        return getTargetFilterQuery(rollout.getTargetFilterQuery(), rollout.getCreatedAt());
    }

    /**
     * @param targetFilter
     *            the target filter tp be extended
     * @param createdAt
     *            timestamp
     * @return a target filter query that only matches targets that were created
     *         after the provided timestamp.
     */
    public static String getTargetFilterQuery(final String targetFilter, final Long createdAt) {
        if (createdAt != null) {
            return targetFilter + ";createdat=le=" + createdAt.toString();
        }
        return targetFilter;
    }

    /**
     * Verifies that the Rollout is in the required status.
     * 
     * @param rollout
     *            the Rollout
     * @param status
     *            the Status
     */
    public static void verifyRolloutInStatus(final Rollout rollout, final Rollout.RolloutStatus status) {
        if (!rollout.getStatus().equals(status)) {
            throw new RolloutIllegalStateException("Rollout is not in status " + status.toString());
        }
    }

    /**
     * Filters the groups of a Rollout to match a specific status and adds a
     * group to the result.
     * 
     * @param rollout
     *            the rollout
     * @param status
     *            the required status for the groups
     * @param group
     *            the group to add
     * @return list of groups
     */
    public static List<Long> getGroupsByStatusIncludingGroup(final Rollout rollout,
            final RolloutGroup.RolloutGroupStatus status, final RolloutGroup group) {
        return rollout.getRolloutGroups().stream()
                .filter(innerGroup -> innerGroup.getStatus().equals(status) || innerGroup.equals(group))
                .map(RolloutGroup::getId).collect(Collectors.toList());
    }

    /**
     * Returns the groups of a rollout by their Ids order
     * 
     * @param rollout
     *            the rollout
     * @return ordered list of groups
     */
    public static List<RolloutGroup> getOrderedGroups(final Rollout rollout) {
        return rollout.getRolloutGroups().stream().sorted((group1, group2) -> {
            if (group1.getId() < group2.getId()) {
                return -1;
            }
            if (group1.getId() > group2.getId()) {
                return 1;
            }
            return 0;
        }).collect(Collectors.toList());
    }

    /**
     * Creates an RSQL expression that matches all targets in the provided
     * groups. Links all target filter queries with OR.
     *
     * @param groups
     *            the rollout groups
     * @return RSQL string without base filter of the Rollout. Can be an empty
     *         string.
     */
    public static String getAllGroupsTargetFilter(final List<RolloutGroup> groups) {
        if (groups.stream().anyMatch(group -> StringUtils.isEmpty(group.getTargetFilterQuery()))) {
            return "";
        }

        return "(" + groups.stream().map(RolloutGroup::getTargetFilterQuery).distinct().sorted()
                .collect(Collectors.joining("),(")) + ")";
    }

    /**
     * Creates an RSQL Filter that matches all targets that are in the provided
     * group and in the provided groups.
     *
     * @param baseFilter
     *            the base filter from the rollout
     * @param groups
     *            the rollout groups
     * @param group
     *            the target group
     * @return RSQL string without base filter of the Rollout. Can be an empty
     *         string.
     */
    public static String getOverlappingWithGroupsTargetFilter(final String baseFilter, final List<RolloutGroup> groups,
            final RolloutGroup group) {
        final String groupFilter = group.getTargetFilterQuery();
        // when any previous group has the same filter as the target group the
        // overlap is 100%
        if (isTargetFilterInGroups(groupFilter, groups)) {
            return concatAndTargetFilters(baseFilter, groupFilter);
        }
        final String previousGroupFilters = getAllGroupsTargetFilter(groups);
        if (StringUtils.isNotEmpty(previousGroupFilters)) {
            if (StringUtils.isNotEmpty(groupFilter)) {
                return concatAndTargetFilters(baseFilter, groupFilter, previousGroupFilters);
            } else {
                return concatAndTargetFilters(baseFilter, previousGroupFilters);
            }
        }
        if (StringUtils.isNotEmpty(groupFilter)) {
            return concatAndTargetFilters(baseFilter, groupFilter);
        } else {
            return baseFilter;
        }
    }

    private static boolean isTargetFilterInGroups(final String groupFilter, final List<RolloutGroup> groups) {
        return StringUtils.isNotEmpty(groupFilter)
                && groups.stream().anyMatch(prevGroup -> StringUtils.isNotEmpty(prevGroup.getTargetFilterQuery())
                        && prevGroup.getTargetFilterQuery().equals(groupFilter));
    }

    private static String concatAndTargetFilters(final String... filters) {
        return "(" + Arrays.stream(filters).collect(Collectors.joining(");(")) + ")";
    }

    /**
     * @param baseFilter
     *            the base filter from the rollout
     * @param group
     *            group for which the filter string should be created
     * @return the final target filter query for a rollout group
     */
    static String getGroupTargetFilter(final String baseFilter, final RolloutGroup group) {
        if (StringUtils.isEmpty(group.getTargetFilterQuery())) {
            return baseFilter;
        } else {
            return concatAndTargetFilters(baseFilter, group.getTargetFilterQuery());
        }
    }

    /**
     * Verifies that no targets are left
     * 
     * @param targetCount
     *            the count of left targets
     */
    public static void verifyRemainingTargets(final long targetCount) {
        if (targetCount > 0) {
            throw new ConstraintViolationException(
                    "Rollout groups don't match all targets that are targeted by the rollout");
        }
        if (targetCount != 0) {
            throw new ConstraintViolationException("Rollout groups target count verification failed");
        }
    }

    public static void checkIfRolloutCanStarted(final Rollout rollout, final Rollout mergedRollout) {
        if (!(Rollout.RolloutStatus.READY.equals(mergedRollout.getStatus()))) {
            throw new RolloutIllegalStateException("Rollout can only be started in state ready but current state is "
                    + rollout.getStatus().name().toLowerCase());
        }
    }

}
